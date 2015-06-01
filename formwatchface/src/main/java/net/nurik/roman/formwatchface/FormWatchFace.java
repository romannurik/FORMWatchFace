/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;

import com.google.android.apps.muzei.api.MuzeiContract;

import net.nurik.roman.formwatchface.common.FormClockRenderer;
import net.nurik.roman.formwatchface.common.MathUtil;
import net.nurik.roman.formwatchface.common.config.ConfigHelper;
import net.nurik.roman.formwatchface.common.config.Themes;

import java.util.Calendar;

import static net.nurik.roman.formwatchface.LogUtil.LOGD;
import static net.nurik.roman.formwatchface.common.FormClockRenderer.ClockPaints;
import static net.nurik.roman.formwatchface.common.MathUtil.constrain;
import static net.nurik.roman.formwatchface.common.MathUtil.decelerate3;
import static net.nurik.roman.formwatchface.common.MathUtil.interpolate;
import static net.nurik.roman.formwatchface.common.MuzeiArtworkImageLoader.LoadedArtwork;
import static net.nurik.roman.formwatchface.common.config.Themes.MUZEI_THEME;
import static net.nurik.roman.formwatchface.common.config.Themes.Theme;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class FormWatchFace extends CanvasWatchFaceService {
    private static final String TAG = "FormWatchFace";

    private static final int UPDATE_THEME_ANIM_DURATION = 1000;

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
        private int mDisplayMetricsWidth = 0;
        private int mDisplayMetricsHeight = 0;

        private Handler mMainThreadHandler = new Handler();

        // For Muzei
        private WatchfaceArtworkImageLoader mMuzeiLoader;
        private Paint mMuzeiArtworkPaint;
        private LoadedArtwork mMuzeiLoadedArtwork;

        // FORM clock renderer specific stuff
        private FormClockRenderer mHourMinRenderer;
        private FormClockRenderer mSecondsRenderer;
        private long mUpdateThemeStartAnimTimeMillis;
        private long mLastDrawTimeMin;
        private String mDateStr;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        private boolean mShowNotificationCount;
        private boolean mShowSeconds;
        private boolean mShowDate;

        private Typeface mDateTypeface;
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

            updateDateStr();

            mMute = getInterruptionFilter() == WatchFaceService.INTERRUPTION_FILTER_NONE;
            handleConfigUpdated();

            mDateTypeface = Typeface.createFromAsset(getAssets(), "VT323-Regular.ttf");
            initClockRenderers();

            registerSystemSettingsListener();
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
            mNormalPaints.date = new Paint(paint);
            mNormalPaints.date.setTypeface(mDateTypeface);
            mNormalPaints.date.setTextSize(
                    getResources().getDimensionPixelSize(R.dimen.seconds_clock_height));

            rebuildAmbientPaints();

            // General config
            FormClockRenderer.Options options = new FormClockRenderer.Options();

            options.is24hour = DateFormat.is24HourFormat(FormWatchFace.this);
            options.textSize = getResources().getDimensionPixelSize(R.dimen.main_clock_height);
            options.charSpacing = getResources().getDimensionPixelSize(R.dimen.main_clock_spacing);
            options.glyphAnimAverageDelay = getResources().getInteger(R.integer.main_clock_glyph_anim_delay);
            options.glyphAnimDuration = getResources().getInteger(R.integer.main_clock_glyph_anim_duration);

            mHourMinRenderer = new FormClockRenderer(options, mNormalPaints);

            options = new FormClockRenderer.Options(options);
            options.textSize = getResources().getDimensionPixelSize(R.dimen.seconds_clock_height);
            options.onlySeconds = true;
            options.charSpacing = getResources().getDimensionPixelSize(R.dimen.seconds_clock_spacing);
            options.glyphAnimAverageDelay = getResources().getInteger(R.integer.seconds_clock_glyph_anim_delay);
            options.glyphAnimDuration = getResources().getInteger(R.integer.seconds_clock_glyph_anim_duration);

            mSecondsRenderer = new FormClockRenderer(options, mNormalPaints);
        }

        private void handleConfigUpdated() {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(FormWatchFace.this);
            String themeId = sp.getString(ConfigHelper.KEY_THEME, Themes.DEFAULT_THEME.id);
            Theme newCurrentTheme = Themes.getThemeById(themeId);
            if (newCurrentTheme != mCurrentTheme) {
                mAnimateFromTheme = mCurrentTheme;
                mCurrentTheme = newCurrentTheme;
                mUpdateThemeStartAnimTimeMillis = System.currentTimeMillis() + 200;
            }

            mShowNotificationCount = sp.getBoolean(ConfigHelper.KEY_SHOW_NOTIFICATION_COUNT, false);
            mShowSeconds = sp.getBoolean(ConfigHelper.KEY_SHOW_SECONDS, false);
            mShowDate = sp.getBoolean(ConfigHelper.KEY_SHOW_DATE, false);

            updateWatchFaceStyle();
            postInvalidate();
        }

        private void updateWatchFaceStyle() {
            setWatchFaceStyle(new WatchFaceStyle.Builder(FormWatchFace.this)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setPeekOpacityMode(WatchFaceStyle.PEEK_OPACITY_MODE_TRANSLUCENT)
                    .setStatusBarGravity(Gravity.TOP | Gravity.CENTER)
                    .setHotwordIndicatorGravity(Gravity.TOP | Gravity.CENTER)
                    .setViewProtection(0)
                    .setShowUnreadCountIndicator(mShowNotificationCount && !mMute)
                    .build());
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;

            DisplayMetrics dm = getResources().getDisplayMetrics();
            mDisplayMetricsWidth = dm.widthPixels;
            mDisplayMetricsHeight = dm.heightPixels;

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
            unregisterSystemSettingsListener();
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

        private void registerSystemSettingsListener() {
            getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.TIME_12_24),
                    false, mSystemSettingsObserver);
        }

        private void unregisterSystemSettingsListener() {
            getContentResolver().unregisterContentObserver(mSystemSettingsObserver);
        }

        private ContentObserver mSystemSettingsObserver = new ContentObserver(mMainThreadHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                initClockRenderers();
                invalidate();
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
                if (ConfigHelper.isConfigPrefKey(key)) {
                    handleConfigUpdated();
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

                mAmbientPaints.date = new Paint(paint);
                mAmbientPaints.date.setColor(Color.WHITE);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5,
                        getResources().getDisplayMetrics()));
                paint.setStrokeJoin(Paint.Join.BEVEL);
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

                mAmbientPaints.date = new Paint(paint);
                mAmbientPaints.date.setColor(0xFFCCCCCC);
            }

            mAmbientPaints.date.setTypeface(mDateTypeface);
            mAmbientPaints.date.setTextSize(
                    getResources().getDimensionPixelSize(R.dimen.seconds_clock_height));
        }

        @Override
        public void onPeekCardPositionUpdate(Rect bounds) {
            super.onPeekCardPositionUpdate(bounds);
            LOGD(TAG, "onPeekCardPositionUpdate: " + bounds);
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
            boolean ambientMode = isInAmbientMode();

            updatePaintsForTheme(mCurrentTheme);

            // Figure out what to animate
            long currentTimeMillis = System.currentTimeMillis();
            long currentTimeMin = currentTimeMillis / 60000;
            if (currentTimeMin != mLastDrawTimeMin) {
                mLastDrawTimeMin = currentTimeMin;
                updateDateStr();
            }

            mHourMinRenderer.setPaints(ambientMode ? mAmbientPaints : mNormalPaints);
            mSecondsRenderer.setPaints(ambientMode ? mAmbientPaints : mNormalPaints);

            mHourMinRenderer.updateTime();

            if (mShowSeconds) {
                mSecondsRenderer.updateTime();
            }

            if (ambientMode) {
                drawClock(canvas);
            } else {
                int sc = -1;
                if (isAnimatingThemeChange()) {
                    // show a reveal animation
                    updatePaintsForTheme(mAnimateFromTheme);
                    drawClock(canvas);

                    sc = canvas.save(Canvas.CLIP_SAVE_FLAG);

                    mUpdateThemeClipPath.reset();
                    float cx = mWidth / 2;
                    float bottom = (Float) mBottomBoundAnimator.getAnimatedValue();
                    float cy = bottom / 2;
                    float maxRadius = MathUtil.maxDistanceToCorner(0, 0, mWidth, mHeight, cx, cy);
                    float radius = interpolate(
                            decelerate3(constrain(
                                    (currentTimeMillis - mUpdateThemeStartAnimTimeMillis)
                                            * 1f / UPDATE_THEME_ANIM_DURATION,
                                    0 , 1)),
                            0 , maxRadius);

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

            if (mBottomBoundAnimator.isRunning() || isAnimatingThemeChange()) {
                postInvalidate();
            } else if (isVisible() && !ambientMode) {
                float secondsOpacity = (Float) mSecondsAlphaAnimator.getAnimatedValue();
                boolean showingSeconds = mShowSeconds && secondsOpacity > 0;
                long timeToNextSecondsAnimation = showingSeconds
                        ? mSecondsRenderer.timeToNextAnimation()
                        : 10000;
                long timeToNextHourMinAnimation = mHourMinRenderer.timeToNextAnimation();
                if (timeToNextHourMinAnimation < 0 || timeToNextSecondsAnimation < 0) {
                    postInvalidate();
                } else {
                    mInvalidateHandler.sendEmptyMessageDelayed(0,
                            Math.min(timeToNextHourMinAnimation, timeToNextSecondsAnimation));
                }
            }
        }

        private boolean isAnimatingThemeChange() {
            return mAnimateFromTheme != null
                    && System.currentTimeMillis() - mUpdateThemeStartAnimTimeMillis
                    < UPDATE_THEME_ANIM_DURATION;
        }

        private void updateDateStr() {
            mDateStr = DateFormat.format("EEE d", Calendar.getInstance()).toString().toUpperCase();
        }

        private void updatePaintsForTheme(Theme theme) {
            if (theme == MUZEI_THEME) {
                mBackgroundPaint.setColor(Color.BLACK);
                if (mMuzeiLoadedArtwork != null) {
                    mNormalPaints.fills[0].setColor(mMuzeiLoadedArtwork.color1);
                    mNormalPaints.fills[1].setColor(mMuzeiLoadedArtwork.color2);
                    mNormalPaints.fills[2].setColor(Color.WHITE);
                    mNormalPaints.date.setColor(mMuzeiLoadedArtwork.color1);
                }
                mDrawMuzeiBitmap = true;
            } else {
                mBackgroundPaint.setColor(getResources().getColor(theme.darkRes));
                mNormalPaints.fills[0].setColor(getResources().getColor(theme.lightRes));
                mNormalPaints.fills[1].setColor(getResources().getColor(theme.midRes));
                mNormalPaints.fills[2].setColor(Color.WHITE);
                mNormalPaints.date.setColor(getResources().getColor(theme.lightRes));
                mDrawMuzeiBitmap = false;
            }
        }

        private void drawClock(Canvas canvas) {
            boolean ambientMode = isInAmbientMode();
            boolean offscreenGlyphs = !ambientMode;

            boolean allowAnimate = !ambientMode;

            if (ambientMode) {
                canvas.drawRect(0, 0, mWidth, mHeight, mAmbientBackgroundPaint);
            } else if (mDrawMuzeiBitmap && mMuzeiLoadedArtwork != null) {
                canvas.drawRect(0, 0, mWidth, mHeight, mAmbientBackgroundPaint);
                canvas.drawBitmap(mMuzeiLoadedArtwork.bitmap,
                        (mDisplayMetricsWidth - mMuzeiLoadedArtwork.bitmap.getWidth()) / 2,
                        (mDisplayMetricsHeight - mMuzeiLoadedArtwork.bitmap.getHeight()) / 2,
                        mMuzeiArtworkPaint);
            } else {
                canvas.drawRect(0, 0, mWidth, mHeight, mBackgroundPaint);
            }

            float bottom = (Float) mBottomBoundAnimator.getAnimatedValue();

            PointF hourMinSize = mHourMinRenderer.measure(allowAnimate);
            mHourMinRenderer.draw(canvas,
                    (mWidth - hourMinSize.x) / 2, (bottom - hourMinSize.y) / 2,
                    allowAnimate,
                    offscreenGlyphs);

            float clockSecondsSpacing = getResources().getDimension(R.dimen.clock_seconds_spacing);
            float secondsOpacity = (Float) mSecondsAlphaAnimator.getAnimatedValue();
            if (mShowSeconds && !ambientMode && secondsOpacity > 0) {
                PointF secondsSize = mSecondsRenderer.measure(allowAnimate);
                int sc = -1;
                if (secondsOpacity != 1) {
                    sc = canvas.saveLayerAlpha(0, 0, canvas.getWidth(), canvas.getHeight(),
                            (int) (secondsOpacity * 255));
                }
                mSecondsRenderer.draw(canvas,
                        (mWidth + hourMinSize.x) / 2 - secondsSize.x,
                        (bottom + hourMinSize.y) / 2 + clockSecondsSpacing,
                        allowAnimate,
                        offscreenGlyphs);
                if (sc >= 0) {
                    canvas.restoreToCount(sc);
                }
            }

            if (mShowDate) {
                Paint paint = ambientMode ? mAmbientPaints.date : mNormalPaints.date;
                float x = (mWidth - hourMinSize.x) / 2;
                if (!mShowSeconds) {
                    x = (mWidth - paint.measureText(mDateStr)) / 2;
                }
                canvas.drawText(
                        mDateStr,
                        x,
                        (bottom + hourMinSize.y) / 2 + clockSecondsSpacing - paint.ascent(),
                                paint);
            }
        }

        private Handler mInvalidateHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                postInvalidate();
            }
        };
    }
}
