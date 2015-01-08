/*
 * Copyright 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nurik.roman.formwatchface;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;

import net.nurik.roman.formwatchface.common.FormClockRenderer;
import net.nurik.roman.formwatchface.common.MathUtil;
import net.nurik.roman.formwatchface.common.Themes;

import java.util.Calendar;

import static net.nurik.roman.formwatchface.LogUtil.LOGD;
import static net.nurik.roman.formwatchface.common.FormClockRenderer.ClockPaints;
import static net.nurik.roman.formwatchface.common.FormClockRenderer.TimeInfo;
import static net.nurik.roman.formwatchface.common.MathUtil.decelerate2;
import static net.nurik.roman.formwatchface.common.MathUtil.interpolate;
import static net.nurik.roman.formwatchface.common.Themes.Theme;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class FormWatchFace extends CanvasWatchFaceService {
    private static final String TAG = "FormClockWatchFace";

    private static final long DEBUG_BASE_TIME_MILLIS = 0;//new Date(2014, 1, 1, 11, 59, 53).getTime();
    private static final long BOOT_TIME_MILLIS = System.currentTimeMillis();

    private static final int UPDATE_THEME_ANIM_DURATION = 2000;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private Paint mAmbientBackgroundPaint;
        private Paint mBackgroundPaint;

        private boolean mMute;
        private Rect mCardBounds = new Rect();
        private ValueAnimator mBottomBoundAnimator = new ValueAnimator();
        private ValueAnimator mSecondsAlphaAnimator = new ValueAnimator();
        private int mWidth = 0;
        private int mHeight = 0;

        // FORM clock renderer specific stuff
        private FormClockRenderer mHourMinRenderer;
        private FormClockRenderer mSecondsRenderer;

        private long mHourMinStartAnimTimeMillis;
        private long mSecondsStartAnimTimeMillis;
        private long mUpdateThemeStartAnimTimeMillis;

        private long mLastAnimatedCurrentTimeSec;
        private boolean mFirstFrame = true;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private boolean mIsRound;

        private ClockPaints mNormalPaints;
        private ClockPaints mAmbientPaints;
        private Theme mCurrentTheme;
        private Theme mAnimateFromTheme;
        private Path mUpdateThemeClipPath = new Path();
        private RectF mTempRectF = new RectF();

        @Override
        public void onCreate(SurfaceHolder holder) {
            LOGD(TAG, "onCreate");
            super.onCreate(holder);

            mMute = getInterruptionFilter() == WatchFaceService.INTERRUPTION_FILTER_NONE;
            updateWatchFaceStyle();

            initClockRenderers();

            registerSharedPrefsListener();
        }

        private void initClockRenderers() {
            // Init paints
            mAmbientBackgroundPaint = new Paint();
            mAmbientBackgroundPaint.setColor(Color.BLACK);
            mBackgroundPaint = new Paint();

            mNormalPaints = new ClockPaints();
            mNormalPaints.paint1 = new Paint();
            mNormalPaints.paint1.setAntiAlias(true);
            mNormalPaints.paint2 = new Paint(mNormalPaints.paint1);
            mNormalPaints.paint3 = new Paint(mNormalPaints.paint1);

            mAmbientPaints = new ClockPaints();
            mAmbientPaints.paint1 = new Paint();
            mAmbientPaints.paint1.setAntiAlias(true);
            mAmbientPaints.paint2 = new Paint(mAmbientPaints.paint1);
            mAmbientPaints.paint3 = new Paint(mAmbientPaints.paint1);
            mAmbientPaints.paint1.setColor(0xFFCCCCCC);
            mAmbientPaints.paint2.setColor(0xFFAAAAAA);
            mAmbientPaints.paint3.setColor(Color.WHITE);

            updateThemeColors();

            // General config
            FormClockRenderer.Options options = new FormClockRenderer.Options();

            options.textSize = getResources().getDimensionPixelSize(R.dimen.main_clock_height);
            options.charSpacing = getResources().getDimensionPixelSize(R.dimen.main_clock_spacing);
            options.glyphAnimAverageDelay = getResources().getInteger(R.integer.main_clock_glyph_anim_delay);
            options.glyphAnimDuration = getResources().getInteger(R.integer.main_clock_glyph_anim_duration);

            mHourMinRenderer = new FormClockRenderer(options, mNormalPaints);

            options = new FormClockRenderer.Options(options);
            options.onlySeconds = true;
            options.textSize = getResources().getDimensionPixelSize(R.dimen.seconds_clock_height);
            options.charSpacing = getResources().getDimensionPixelSize(R.dimen.seconds_clock_spacing);
            options.glyphAnimAverageDelay = getResources().getInteger(R.integer.seconds_clock_glyph_anim_delay);
            options.glyphAnimDuration = getResources().getInteger(R.integer.seconds_clock_glyph_anim_duration);

            mSecondsRenderer = new FormClockRenderer(options, mNormalPaints);
        }

        private void updateThemeColors() {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(FormWatchFace.this);
            String themeId = sp.getString("theme", Themes.DEFAULT_THEME.id);
            mAnimateFromTheme = mCurrentTheme;
            mCurrentTheme = Themes.getThemeById(themeId);
        }

        private void updateWatchFaceStyle() {
            setWatchFaceStyle(new WatchFaceStyle.Builder(FormWatchFace.this)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setPeekOpacityMode(WatchFaceStyle.PEEK_OPACITY_MODE_TRANSLUCENT)
                    .setStatusBarGravity(Gravity.TOP | Gravity.CENTER)
                    .setHotwordIndicatorGravity(Gravity.TOP | Gravity.CENTER)
                    .setViewProtection(0)
                    .setShowUnreadCountIndicator(!mMute)
                    .build());
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;

            mBottomBoundAnimator.cancel();
            mBottomBoundAnimator.setFloatValues(mHeight, mHeight);
            mBottomBoundAnimator.setInterpolator(new DecelerateInterpolator(3));
            mBottomBoundAnimator.setDuration(0);
            mBottomBoundAnimator.start();

            mSecondsAlphaAnimator.cancel();
            mSecondsAlphaAnimator.setFloatValues(1f, 1f);
            mSecondsAlphaAnimator.setDuration(0);
            mSecondsAlphaAnimator.start();
        }

        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            mIsRound = insets.isRound();
            updateWatchFaceStyle();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                postInvalidate();
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            unregisterSharedPrefsListener();
        }

        private void registerSharedPrefsListener() {
            PreferenceManager.getDefaultSharedPreferences(FormWatchFace.this)
                    .registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
        }

        private void unregisterSharedPrefsListener() {
            PreferenceManager.getDefaultSharedPreferences(FormWatchFace.this)
                    .unregisterOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
        }

        private SharedPreferences.OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener
                = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if ("theme".equals(key)) {
                    mUpdateThemeStartAnimTimeMillis = System.currentTimeMillis();
                    updateThemeColors();
                }
            }
        };

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);

            mAmbientPaints.paint1.setAntiAlias(!mLowBitAmbient);
            mAmbientPaints.paint2.setAntiAlias(!mLowBitAmbient);
            mAmbientPaints.paint3.setAntiAlias(!mLowBitAmbient);

            LOGD(TAG, "onPropertiesChanged: burn-in protection = " + mBurnInProtection
                    + ", low-bit ambient = " + mLowBitAmbient);
        }

        @Override
        public void onPeekCardPositionUpdate(Rect bounds) {
            super.onPeekCardPositionUpdate(bounds);
            LOGD(TAG, "onPeekCardPositionUpdate: " + bounds);
            super.onPeekCardPositionUpdate(bounds);
            if (!bounds.equals(mCardBounds)) {
                mCardBounds.set(bounds);

                mBottomBoundAnimator.cancel();
                mBottomBoundAnimator.setFloatValues(
                        (Float) mBottomBoundAnimator.getAnimatedValue(),
                        mCardBounds.top > 0 ? mCardBounds.top : mHeight);
                mBottomBoundAnimator.setDuration(200);
                mBottomBoundAnimator.start();

                mSecondsAlphaAnimator.cancel();
                mSecondsAlphaAnimator.setFloatValues(
                        (Float) mSecondsAlphaAnimator.getAnimatedValue(),
                        mCardBounds.top > 0 ? 0f : 1f);
                mSecondsAlphaAnimator.setDuration(200);
                mSecondsAlphaAnimator.start();

                LOGD(TAG, "onPeekCardPositionUpdate: " + mCardBounds);
                postInvalidate();
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            LOGD(TAG, "onTimeTick: ambient = " + isInAmbientMode());
            postInvalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            LOGD(TAG, "onAmbientModeChanged: " + inAmbientMode);
            super.onAmbientModeChanged(inAmbientMode);
            postInvalidate();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            LOGD(TAG, "onInterruptionFilterChanged: " + interruptionFilter);
            super.onInterruptionFilterChanged(interruptionFilter);

            boolean inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE;

            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                updateWatchFaceStyle();
                postInvalidate();
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            int width = canvas.getWidth();
            int height = canvas.getHeight();

            boolean ambientMode = isInAmbientMode();

            mBackgroundPaint.setColor(getResources().getColor(mCurrentTheme.darkRes));
            mNormalPaints.paint1.setColor(getResources().getColor(mCurrentTheme.lightRes));
            mNormalPaints.paint2.setColor(getResources().getColor(mCurrentTheme.midRes));
            mNormalPaints.paint3.setColor(Color.WHITE);

            // Figure out what to animate
            long currentTimeMillis = System.currentTimeMillis();

            long currentTimeSec = System.currentTimeMillis() / 1000;
            if (mFirstFrame) {
                // show minutes and seconds at t=1
                mHourMinStartAnimTimeMillis = 0;
                mSecondsStartAnimTimeMillis = 0;

                TimeInfo ti = getCurrentTimeInfo();
                mHourMinRenderer.setTime(ti.clone().removeSeconds().previous());
                mSecondsRenderer.setTime(ti.removeHoursMinutes().previous());
                mFirstFrame = false;

            } else if (mLastAnimatedCurrentTimeSec != currentTimeSec) {
                // kick off seconds change animation
                mLastAnimatedCurrentTimeSec = currentTimeSec;

                TimeInfo ti = getCurrentTimeInfo();
                TimeInfo prevTi = ti.previous();

                mSecondsStartAnimTimeMillis = currentTimeMillis;
                mSecondsRenderer.setTime(prevTi.clone().removeHoursMinutes());
                mHourMinRenderer.setTime(ti.removeSeconds().previous());

                if (prevTi.m != ti.m) {
                    // kick off minute change animation
                    mHourMinStartAnimTimeMillis = currentTimeMillis;
                }
            }

            mHourMinRenderer.setAnimTime(
                    ambientMode ? Integer.MAX_VALUE : (currentTimeMillis - mHourMinStartAnimTimeMillis));

            mHourMinRenderer.setPaints(ambientMode ? mAmbientPaints : mNormalPaints);
            mSecondsRenderer.setPaints(ambientMode ? mAmbientPaints : mNormalPaints);

            if (ambientMode) {
                drawClock(canvas);
            } else {
                int sc = -1;
                if (currentTimeMillis - mUpdateThemeStartAnimTimeMillis < UPDATE_THEME_ANIM_DURATION && mAnimateFromTheme != null) {
                    // show a reveal animation
                    mBackgroundPaint.setColor(getResources().getColor(mAnimateFromTheme.darkRes));
                    mNormalPaints.paint1.setColor(getResources().getColor(mAnimateFromTheme.lightRes));
                    mNormalPaints.paint2.setColor(getResources().getColor(mAnimateFromTheme.midRes));
                    mNormalPaints.paint3.setColor(Color.WHITE);
                    drawClock(canvas);

                    sc = canvas.save(Canvas.CLIP_SAVE_FLAG);

                    mUpdateThemeClipPath.reset();
                    float cx = mWidth / 2;
                    float bottom = (Float) mBottomBoundAnimator.getAnimatedValue();
                    float cy = bottom / 2;
                    float maxRadius = MathUtil.maxDistanceToCorner(0, 0, mWidth, mHeight, cx, cy);
                    float radius = decelerate2(interpolate(
                            (currentTimeMillis - mUpdateThemeStartAnimTimeMillis)
                                    * 1f / UPDATE_THEME_ANIM_DURATION,
                            0, maxRadius));

                    mTempRectF.set(cx - radius, cy - radius, cx + radius, cy + radius);
                    mUpdateThemeClipPath.addOval(mTempRectF, Path.Direction.CW);
                    canvas.clipPath(mUpdateThemeClipPath);
                }

                mBackgroundPaint.setColor(getResources().getColor(mCurrentTheme.darkRes));
                mNormalPaints.paint1.setColor(getResources().getColor(mCurrentTheme.lightRes));
                mNormalPaints.paint2.setColor(getResources().getColor(mCurrentTheme.midRes));
                mNormalPaints.paint3.setColor(Color.WHITE);
                drawClock(canvas);

                if (sc >= 0) {
                    canvas.restoreToCount(sc);
                }
            }

            if ((isVisible() && !ambientMode) || mBottomBoundAnimator.isRunning()) {
                postInvalidate();
            }
        }

        private void drawClock(Canvas canvas) {
            boolean ambientMode = isInAmbientMode();
            canvas.drawRect(0, 0, mWidth, mHeight,
                    ambientMode ? mAmbientBackgroundPaint : mBackgroundPaint);

            float bottom = (Float) mBottomBoundAnimator.getAnimatedValue();

            PointF hourMinSize = mHourMinRenderer.measure();
            mHourMinRenderer.draw(canvas, (mWidth - hourMinSize.x) / 2, (bottom - hourMinSize.y) / 2);

            float secondsOpacity = (Float) mSecondsAlphaAnimator.getAnimatedValue();
            if (!ambientMode && secondsOpacity > 0) {
                mSecondsRenderer.setAnimTime(System.currentTimeMillis() - mSecondsStartAnimTimeMillis);

                PointF secondsSize = mSecondsRenderer.measure();
                int sc = -1;
                if (secondsOpacity != 1) {
                    sc = canvas.saveLayerAlpha(0, 0, canvas.getWidth(), canvas.getHeight(),
                            (int) (secondsOpacity * 255));
                }
                mSecondsRenderer.draw(canvas,
                        (mWidth + hourMinSize.x) / 2 - secondsSize.x,
                        (bottom + hourMinSize.y) / 2
                                + TypedValue.applyDimension(5, TypedValue.COMPLEX_UNIT_DIP,
                                getResources().getDisplayMetrics()));
                if (sc >= 0) {
                    canvas.restoreToCount(sc);
                }
            }
        }

        private TimeInfo getCurrentTimeInfo() {
            Calendar now = Calendar.getInstance();
            if (DEBUG_BASE_TIME_MILLIS != 0) {
                long v = DEBUG_BASE_TIME_MILLIS + (System.currentTimeMillis() - BOOT_TIME_MILLIS);
                now.setTimeInMillis(v);
            }
            int h = now.get(Calendar.HOUR);
            return new TimeInfo(
                    h == 0 ? 12 : h,
                    now.get(Calendar.MINUTE),
                    now.get(Calendar.SECOND));
        }
    }
}
