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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
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

import com.google.android.apps.muzei.api.MuzeiContract;

import net.nurik.roman.formwatchface.common.FormClockRenderer;
import net.nurik.roman.formwatchface.common.MathUtil;
import net.nurik.roman.formwatchface.common.Themes;

import java.util.Calendar;

import static net.nurik.roman.formwatchface.LogUtil.LOGD;
import static net.nurik.roman.formwatchface.common.FormClockRenderer.ClockPaints;
import static net.nurik.roman.formwatchface.common.FormClockRenderer.TimeInfo;
import static net.nurik.roman.formwatchface.common.MathUtil.decelerate2;
import static net.nurik.roman.formwatchface.common.MathUtil.interpolate;
import static net.nurik.roman.formwatchface.common.MuzeiArtworkImageLoader.LoadedArtwork;
import static net.nurik.roman.formwatchface.common.Themes.MUZEI_THEME;
import static net.nurik.roman.formwatchface.common.Themes.Theme;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class FormWatchFace extends CanvasWatchFaceService {
    private static final String TAG = "FormClockWatchFace";

    private static final long DEBUG_BASE_TIME_MILLIS = 0;//new Date(2015, 1, 1, 11, 59, 53).getTime();
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

        // For Muzei
        private WatchfaceArtworkImageLoader mMuzeiLoader;
        private Paint mMuzeiArtworkPaint;
        private LoadedArtwork mMuzeiLoadedArtwork;

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
        private boolean mDrawMuzeiBitmap;
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

            initMuzei();
        }

        private void initClockRenderers() {
            // Init paints
            mAmbientBackgroundPaint = new Paint();
            mAmbientBackgroundPaint.setColor(Color.BLACK);
            mBackgroundPaint = new Paint();

            Paint paint = new Paint();
            paint.setAntiAlias(true);
            mNormalPaints = new ClockPaints();
            mNormalPaints.fills[0] = paint;
            mNormalPaints.fills[1] = new Paint(paint);
            mNormalPaints.fills[2] = new Paint(paint);

            updateThemeColors();

            rebuildAmbientPaints();

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
            destroyMuzei();
        }

        private void initMuzei() {
            mMuzeiArtworkPaint = new Paint();
            mMuzeiArtworkPaint.setAlpha(102);
            mMuzeiLoader = new WatchfaceArtworkImageLoader(FormWatchFace.this);
            mMuzeiLoader.registerListener(0, mMuzeiLoadCompleteListener);
            mMuzeiLoader.startLoading();

            // Watch for artwork changes
            IntentFilter artworkChangedIntent = new IntentFilter();
            artworkChangedIntent.addAction(MuzeiContract.Artwork.ACTION_ARTWORK_CHANGED);
            registerReceiver(mMuzeiArtworkChangedReceiver, artworkChangedIntent);
        }

        private BroadcastReceiver mMuzeiArtworkChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mMuzeiLoader.startLoading();
            }
        };

        private void destroyMuzei() {
            unregisterReceiver(mMuzeiArtworkChangedReceiver);
            if (mMuzeiLoader != null) {
                mMuzeiLoader.unregisterListener(mMuzeiLoadCompleteListener);
                mMuzeiLoader.reset();
                mMuzeiLoader = null;
            }
        }

        private Loader.OnLoadCompleteListener<LoadedArtwork> mMuzeiLoadCompleteListener
                = new Loader.OnLoadCompleteListener<LoadedArtwork>() {
            public void onLoadComplete(Loader<LoadedArtwork> loader, LoadedArtwork data) {
                if (data != null) {
                    mMuzeiLoadedArtwork = data;
                } else {
                    mMuzeiLoadedArtwork = null;
                }
                postInvalidate();
            }
        };

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

            rebuildAmbientPaints();

            LOGD(TAG, "onPropertiesChanged: burn-in protection = " + mBurnInProtection
                    + ", low-bit ambient = " + mLowBitAmbient);
        }

        private void rebuildAmbientPaints() {
            Paint paint = new Paint();
            mAmbientPaints = new ClockPaints();
            if (mBurnInProtection || mLowBitAmbient) {
                paint.setAntiAlias(false);
                paint.setColor(Color.BLACK);
                mAmbientPaints.fills[0] = mAmbientPaints.fills[1] = mAmbientPaints.fills[2] = paint;

                paint = new Paint();
                paint.setAntiAlias(!mLowBitAmbient);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5,
                        getResources().getDisplayMetrics()));
                paint.setColor(Color.WHITE);
                mAmbientPaints.strokes[0] = mAmbientPaints.strokes[1] = mAmbientPaints.strokes[2]
                        = paint;
                mAmbientPaints.hasStroke = true;

            } else {
                paint.setAntiAlias(true);
                mAmbientPaints.fills[0] = paint;
                mAmbientPaints.fills[0].setColor(0xFFCCCCCC);

                mAmbientPaints.fills[1] = new Paint(paint);
                mAmbientPaints.fills[1].setColor(0xFFAAAAAA);

                mAmbientPaints.fills[2] = new Paint(paint);
                mAmbientPaints.fills[2].setColor(Color.WHITE);
            }
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

            updatePaintsForTheme(mCurrentTheme);

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
                    updatePaintsForTheme(mAnimateFromTheme);
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

                updatePaintsForTheme(mCurrentTheme);
                drawClock(canvas);

                if (sc >= 0) {
                    canvas.restoreToCount(sc);
                }
            }

            if ((isVisible() && !ambientMode) || mBottomBoundAnimator.isRunning()) {
                postInvalidate();
            }
        }

        private void updatePaintsForTheme(Theme theme) {
            if (theme == MUZEI_THEME) {
                mBackgroundPaint.setColor(Color.BLACK);
                if (mMuzeiLoadedArtwork != null) {
                    mNormalPaints.fills[0].setColor(mMuzeiLoadedArtwork.color1);
                    mNormalPaints.fills[1].setColor(mMuzeiLoadedArtwork.color2);
                    mNormalPaints.fills[2].setColor(Color.WHITE);
                }
                mDrawMuzeiBitmap = true;
            } else {
                mBackgroundPaint.setColor(getResources().getColor(theme.darkRes));
                mNormalPaints.fills[0].setColor(getResources().getColor(theme.lightRes));
                mNormalPaints.fills[1].setColor(getResources().getColor(theme.midRes));
                mNormalPaints.fills[2].setColor(Color.WHITE);
                mDrawMuzeiBitmap = false;
            }
        }

        private void drawClock(Canvas canvas) {
            boolean ambientMode = isInAmbientMode();

            if (ambientMode) {
                canvas.drawRect(0, 0, mWidth, mHeight, mAmbientBackgroundPaint);
            } else if (mDrawMuzeiBitmap && mMuzeiLoadedArtwork != null) {
                canvas.drawRect(0, 0, mWidth, mHeight, mAmbientBackgroundPaint);
                canvas.drawBitmap(mMuzeiLoadedArtwork.bitmap,
                        (mWidth - mMuzeiLoadedArtwork.bitmap.getWidth()) / 2,
                        (mHeight - mMuzeiLoadedArtwork.bitmap.getHeight()) / 2,
                        mMuzeiArtworkPaint);
            } else {
                canvas.drawRect(0, 0, mWidth, mHeight, mBackgroundPaint);
            }

            float bottom = (Float) mBottomBoundAnimator.getAnimatedValue();

            PointF hourMinSize = mHourMinRenderer.measure();
            mHourMinRenderer.draw(canvas, (mWidth - hourMinSize.x) / 2, (bottom - hourMinSize.y) / 2,
                    !ambientMode);

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
                                getResources().getDisplayMetrics()),
                        !ambientMode);
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