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

package net.nurik.roman.formwatchface.common;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

public class FormClockView extends View {
    private Handler mMainThreadHandler = new Handler();
    private FormClockRenderer mHourMinRenderer;
    private FormClockRenderer mSecondsRenderer;

    private int mWidth, mHeight;

    private int mColor1, mColor2, mColor3;

    private FormClockRenderer.Options mHourMinOptions, mSecondsOptions;

    public FormClockView(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public FormClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public FormClockView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public FormClockView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        // Attribute initialization
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FormClockView,
                defStyleAttr, defStyleRes);

        // Configure renderers
        mHourMinOptions = new FormClockRenderer.Options();
        mHourMinOptions.textSize = a.getDimension(R.styleable.FormClockView_textSize,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20,
                        getResources().getDisplayMetrics()));
        mHourMinOptions.charSpacing = a.getDimension(R.styleable.FormClockView_charSpacing,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 6,
                        getResources().getDisplayMetrics()));
        mHourMinOptions.is24hour = DateFormat.is24HourFormat(context);

        mHourMinOptions.glyphAnimAverageDelay = 500;
        mHourMinOptions.glyphAnimDuration = 2000;

        mSecondsOptions = new FormClockRenderer.Options(mHourMinOptions);
        mSecondsOptions.onlySeconds = true;
        mSecondsOptions.textSize /= 2;
        mSecondsOptions.glyphAnimAverageDelay = 0;
        mSecondsOptions.glyphAnimDuration = 750;

        mColor1 = a.getColor(R.styleable.FormClockView_color1, 0xff000000);
        mColor2 = a.getColor(R.styleable.FormClockView_color2, 0xff888888);
        mColor3 = a.getColor(R.styleable.FormClockView_color3, 0xffcccccc);

        a.recycle();

        regenerateRenderers();
    }

    private void regenerateRenderers() {
        mHourMinRenderer = new FormClockRenderer(mHourMinOptions, null);
        mSecondsRenderer = new FormClockRenderer(mSecondsOptions, null);
        updatePaints();
    }

    private void updatePaints() {
        FormClockRenderer.ClockPaints paints = new FormClockRenderer.ClockPaints();
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        paint.setColor(mColor1);
        paints.fills[0] = paint;

        paint = new Paint(paint);
        paint.setColor(mColor2);
        paints.fills[1] = paint;

        paint = new Paint(paint);
        paint.setColor(mColor3);
        paints.fills[2] = paint;

        mHourMinRenderer.setPaints(paints);
        mSecondsRenderer.setPaints(paints);
        invalidate();
    }

    public void setColors(int color1, int color2, int color3) {
        mColor1 = color1;
        mColor2 = color2;
        mColor3 = color3;
        updatePaints();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mHeight = h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        mHourMinRenderer.updateTime();
        PointF hourMinSize = mHourMinRenderer.measure();
        mHourMinRenderer.draw(canvas,
                (mWidth - hourMinSize.x) / 2,
                (mHeight - hourMinSize.y) / 2,
                false);

        mSecondsRenderer.updateTime();
        PointF secondsSize = mSecondsRenderer.measure();
        mSecondsRenderer.draw(canvas,
                (mWidth + hourMinSize.x) / 2 - secondsSize.x,
                (mHeight + hourMinSize.y) / 2
                        + TypedValue.applyDimension(5, TypedValue.COMPLEX_UNIT_DIP,
                        getResources().getDisplayMetrics()),
                false);

        long timeToNextSecondsAnimation = mSecondsRenderer.timeToNextAnimation();
        long timeToNextHourMinAnimation = mHourMinRenderer.timeToNextAnimation();
        if (timeToNextHourMinAnimation < 0 || timeToNextSecondsAnimation < 0) {
            postInvalidateOnAnimation();
        } else {
            postInvalidateDelayed(Math.min(timeToNextHourMinAnimation, timeToNextSecondsAnimation));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        registerSystemSettingsListener();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unregisterSystemSettingsListener();
    }

    private void registerSystemSettingsListener() {
        getContext().getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.TIME_12_24),
                false, mSystemSettingsObserver);
    }

    private void unregisterSystemSettingsListener() {
        getContext().getContentResolver().unregisterContentObserver(mSystemSettingsObserver);
    }

    private ContentObserver mSystemSettingsObserver = new ContentObserver(mMainThreadHandler) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mHourMinOptions.is24hour = DateFormat.is24HourFormat(getContext());
            mSecondsOptions.is24hour = mHourMinOptions.is24hour;
            regenerateRenderers();
        }
    };
}
