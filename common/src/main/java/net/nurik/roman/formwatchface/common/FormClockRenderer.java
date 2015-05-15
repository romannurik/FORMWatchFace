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

package net.nurik.roman.formwatchface.common;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static net.nurik.roman.formwatchface.common.MathUtil.accelerate5;
import static net.nurik.roman.formwatchface.common.MathUtil.decelerate5;
import static net.nurik.roman.formwatchface.common.MathUtil.interpolate;
import static net.nurik.roman.formwatchface.common.MathUtil.progress;

public class FormClockRenderer {
    private static final long DEBUG_TIME_MILLIS = 0; //new Date(2015, 2, 22, 12, 59, 52).getTime();
    private static final long BOOT_TIME_MILLIS = System.currentTimeMillis();

    private static final String DEBUG_TIME = null;//"0123456789";
    private static final String DEBUG_GLYPH = null;//"2_3";
    private static final boolean DEBUG_SHOW_RECTS = false;

    private int[] mAnimatedGlyphIndices = new int[20];
    private int[] mTempAnimatedGlyphIndices = new int[20];
    private int mAnimatedGlyphIndexCount = 0;
    private Glyph[] mGlyphs = new Glyph[20];
    private int mGlyphCount = 0;

    private Options mOptions;
    private ClockPaints mPaints;
    private Font mFont = new Font();

    // for offscreen glyphs
    private Bitmap mOffsGlyphBitmap;
    private Canvas mOffsGlyphCanvas;
    private Paint mOffsGlyphPaint;
    private int mOffsGlyphBitmapUnpaddedSize;

    private Paint mDebugShowRectPaint;

    private long mAnimDuration;
    private long mAnimTime;

    private Calendar mTempCalendar;
    private long mTimeMillis;
    private long mMillisToNext;

    private PointF mMeasuredSize = new PointF();

    public FormClockRenderer(Options options, ClockPaints paints) {
        this.mOptions = options;
        this.mPaints = paints;
        this.mTempCalendar = Calendar.getInstance();
        updateTime();
        initOffsGlyphBitmap();

        if (DEBUG_SHOW_RECTS) {
            mDebugShowRectPaint = new Paint();
            mDebugShowRectPaint.setStrokeWidth(4);
            mDebugShowRectPaint.setColor(Color.RED);
            mDebugShowRectPaint.setStyle(Paint.Style.STROKE);
        }
    }

    private void initOffsGlyphBitmap() {
        mOffsGlyphBitmapUnpaddedSize = Font.DRAWHEIGHT * 5;
        while (mOptions.textSize < mOffsGlyphBitmapUnpaddedSize) {
            int newUnpaddedSize = mOffsGlyphBitmapUnpaddedSize;
            if (newUnpaddedSize > Font.DRAWHEIGHT) {
                newUnpaddedSize -= Font.DRAWHEIGHT;
            } else {
                newUnpaddedSize /= 2;
            }

            if (mOptions.textSize > newUnpaddedSize) {
                break;
            }

            mOffsGlyphBitmapUnpaddedSize = newUnpaddedSize;
        }

        mOffsGlyphBitmap = Bitmap.createBitmap(
                mOffsGlyphBitmapUnpaddedSize * 2,
                mOffsGlyphBitmapUnpaddedSize * 2,
                Bitmap.Config.ARGB_8888);
        mOffsGlyphCanvas = new Canvas(mOffsGlyphBitmap);
        mOffsGlyphPaint = new Paint();
        mOffsGlyphPaint.setFilterBitmap(true);
    }

    public void setPaints(ClockPaints paints) {
        mPaints = paints;
    }

    public void updateTime() {
        mTimeMillis = System.currentTimeMillis();
        if (DEBUG_TIME_MILLIS > 0) {
            long v = DEBUG_TIME_MILLIS + (System.currentTimeMillis() - BOOT_TIME_MILLIS);
            mTimeMillis = v;
        }

        mTempCalendar.setTimeInMillis(mTimeMillis);

        String currentTimeStr, nextTimeStr;
        mTempCalendar.set(Calendar.MILLISECOND, 0);
        if (mOptions.onlySeconds) {
            currentTimeStr = secondsString(mTempCalendar);
            mTempCalendar.add(Calendar.SECOND, 1);
            nextTimeStr = secondsString(mTempCalendar);
        } else {
            mTempCalendar.set(Calendar.SECOND, 0);
            currentTimeStr = hourMinString(mTempCalendar);
            mTempCalendar.add(Calendar.MINUTE, 1);
            nextTimeStr = hourMinString(mTempCalendar);
        }

        mMillisToNext = mTempCalendar.getTimeInMillis() - mTimeMillis;
        updateGlyphsAndAnimDuration(currentTimeStr, nextTimeStr);

        if (mMillisToNext < mAnimDuration) {
            // currently animating
            mAnimTime = mAnimDuration - mMillisToNext;
        } else {
            mAnimTime = 0;
        }
    }

    public void updateGlyphsAndAnimDuration(String currentTimeStr, String nextTimeStr) {
        mAnimatedGlyphIndexCount = 0;
        mGlyphCount = 0;

        if (DEBUG_GLYPH != null) {
            mGlyphs[mGlyphCount++] = mFont.getGlyph(DEBUG_GLYPH);
            mTempAnimatedGlyphIndices[mAnimatedGlyphIndexCount++] = 0;
        } else if (DEBUG_TIME != null) {
            for (int i = 0; i < DEBUG_TIME.length(); i++) {
                mGlyphs[mGlyphCount++] = mFont.getGlyph(Character.toString(DEBUG_TIME.charAt(i)));
            }
        } else {
            int len = currentTimeStr.length();
            for (int i = 0; i < len; i++) {
                char c1 = currentTimeStr.charAt(i);
                char c2 = nextTimeStr.charAt(i);

                if (c1 == ':') {
                    mGlyphs[mGlyphCount++] = mFont.getGlyph(":");
                    continue;
                }

                if (c1 == c2) {
                    mGlyphs[mGlyphCount++] = mFont.getGlyph(String.valueOf(c1));
                } else {
                    mTempAnimatedGlyphIndices[mAnimatedGlyphIndexCount++] = i;
                    mGlyphs[mGlyphCount++] = mFont.getGlyph(c1 + "_" + c2);
                }
            }
        }

        // reverse animted glyph indices
        for (int i = 0; i < mAnimatedGlyphIndexCount; i++) {
            mAnimatedGlyphIndices[i] = mTempAnimatedGlyphIndices[mAnimatedGlyphIndexCount - i - 1];
        }

        mAnimDuration = mAnimatedGlyphIndexCount * mOptions.glyphAnimAverageDelay
                + mOptions.glyphAnimDuration;
        mAnimTime = 0;
    }

    public long timeToNextAnimation() {
        return mMillisToNext - mAnimDuration;
    }

    public PointF measure() {
        mMeasuredSize.set(0, 0);
        layoutPass(new LayoutPassCallback() {
            @Override
            public void visitGlyph(Glyph glyph, float glyphAnimProgress, RectF rect) {
                mMeasuredSize.x = Math.max(mMeasuredSize.x, rect.right);
                mMeasuredSize.y = Math.max(mMeasuredSize.y, rect.bottom);
            }
        }, new RectF());
        return mMeasuredSize;
    }

    private void layoutPass(LayoutPassCallback cb, RectF rectF) {
        float x = 0;

        for (int i = 0; i < mGlyphCount; i++) {
            Glyph glyph = mGlyphs[i];

            float t = getGlyphAnimProgress(i);
            float glyphWidth = glyph.getWidthAtProgress(t) * mOptions.textSize / Font.DRAWHEIGHT;

            rectF.set(x, 0, x + glyphWidth, mOptions.textSize);
            cb.visitGlyph(glyph, t, rectF);

            x += Math.floor(glyphWidth +
                    (i >= 0 ? mOptions.charSpacing : 0));
        }
    }

    public void draw(final Canvas canvas, float left, float top, final boolean offscreenGlyphs) {
        mFont.canvas = offscreenGlyphs ? mOffsGlyphCanvas : canvas;

        int sc = canvas.save();
        canvas.translate(left, top);

        layoutPass(new LayoutPassCallback() {
            @Override
            public void visitGlyph(Glyph glyph, float glyphAnimProgress, RectF rect) {
                int sc;

                if (glyphAnimProgress == 0) {
                    glyph = mFont.mGlyphMap.get(glyph.getCanonicalStartGlyph());
                } else if (glyphAnimProgress == 1) {
                    glyph = mFont.mGlyphMap.get(glyph.getCanonicalEndGlyph());
                    glyphAnimProgress = 0;
                }

                if (offscreenGlyphs) {
                    mOffsGlyphBitmap.eraseColor(Color.TRANSPARENT);
                    sc = mOffsGlyphCanvas.save();
                    mOffsGlyphCanvas.translate(
                            mOffsGlyphBitmapUnpaddedSize / 2,
                            mOffsGlyphBitmapUnpaddedSize / 2);
                    mOffsGlyphCanvas.scale(
                            mOffsGlyphBitmapUnpaddedSize * 1f / Font.DRAWHEIGHT,
                            mOffsGlyphBitmapUnpaddedSize * 1f / Font.DRAWHEIGHT);
                    glyph.draw(glyphAnimProgress);
                    mOffsGlyphCanvas.restoreToCount(sc);
                }

                if (DEBUG_SHOW_RECTS) {
                    canvas.drawRect(rect, mDebugShowRectPaint);
                }

                sc = canvas.save();
                canvas.translate(rect.left, rect.top);
                float scale = mOptions.textSize /
                        (offscreenGlyphs ? mOffsGlyphBitmapUnpaddedSize : Font.DRAWHEIGHT);
                canvas.scale(scale, scale);
                if (offscreenGlyphs) {
                    canvas.translate(-mOffsGlyphBitmapUnpaddedSize / 2, -mOffsGlyphBitmapUnpaddedSize / 2);
                    canvas.drawBitmap(mOffsGlyphBitmap, 0, 0, mOffsGlyphPaint);
                } else {
                    glyph.draw(glyphAnimProgress); // draws into mOffsGlyphCanvas
                }
                canvas.restoreToCount(sc);
            }
        }, new RectF());

        canvas.restoreToCount(sc);

        mFont.canvas = null;
    }

    private float getGlyphAnimProgress(int glyphIndex) {
        int indexIntoAnimatedGlyphs = -1;
        for (int i = 0; i < mAnimatedGlyphIndexCount; i++) {
            if (mAnimatedGlyphIndices[i] == glyphIndex) {
                indexIntoAnimatedGlyphs = i;
                break;
            }
        }

        if (indexIntoAnimatedGlyphs < 0) {
            return 0; // glyphs not currently animating are rendered at t=0
        }

        // this glyph is animating
        float glyphStartAnimTime = 0;
        if (mAnimatedGlyphIndexCount > 1) {
            glyphStartAnimTime = interpolate(accelerate5(
                            indexIntoAnimatedGlyphs * 1f / (mAnimatedGlyphIndexCount - 1)),
                    0, mAnimDuration - mOptions.glyphAnimDuration);
        }
        return progress(mAnimTime, glyphStartAnimTime, glyphStartAnimTime
                + mOptions.glyphAnimDuration);
    }

    String secondsString(Calendar c) {
        int s = c.get(Calendar.SECOND);
        return ":"
                + (s < 10 ? "0" : "")
                + s;
    }

    String hourMinString(Calendar c) {
        int h = c.get(mOptions.is24hour ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
        if (!mOptions.is24hour && h == 0) {
            h = 12;
        }
        int m = c.get(Calendar.MINUTE);
        return (h < 10 ? " " : "")
                + h
                + ":" + (m < 10 ? "0" : "")
                + m;
    }

    private interface LayoutPassCallback {
        void visitGlyph(Glyph glyph, float glyphAnimProgress, RectF rect);
    }

    public static class Options {
        public float textSize;
        public boolean onlySeconds;
        public float charSpacing;
        public boolean is24hour;
        public int glyphAnimAverageDelay;
        public int glyphAnimDuration;

        public Options() {
        }

        public Options(Options copy) {
            this.textSize = copy.textSize;
            this.onlySeconds = copy.onlySeconds;
            this.charSpacing = copy.charSpacing;
            this.is24hour = copy.is24hour;
            this.glyphAnimAverageDelay = copy.glyphAnimAverageDelay;
            this.glyphAnimDuration = copy.glyphAnimDuration;
        }
    }

    public static class ClockPaints {
        public Paint fills[] = new Paint[3];
        public Paint strokes[] = new Paint[3]; // optional
        public Paint date;
        public boolean hasStroke = false;
    }

    public interface Glyph {
        void draw(float t);
        float getWidthAtProgress(float t);
        String getCanonicalStartGlyph();
        String getCanonicalEndGlyph();
    }

    /**
     * Font data and common drawing operations.
     */
    private class Font {
        private static final int DRAWHEIGHT = 144;

        private static final int COLOR_1 = 0;
        private static final int COLOR_2 = 1;
        private static final int COLOR_3 = 2;

        private Map<String, Glyph> mGlyphMap = new HashMap<>();

        public Canvas canvas;
        private Path path = new Path();

        private RectF tempRectF = new RectF();

        public Font() {
            initGlyphs();
        }

        public Glyph getGlyph(String key) {
            Glyph glyph = mGlyphMap.get(key);
            if (glyph == null) { return mGlyphMap.get("0_1"); }
            return glyph;
        }

        private void scaleUniform(float s, float px, float py) {
            canvas.scale(s, s, px, py);
        }

        /*
            API 21 compat methods
         */

        private void arcTo(float l, float t, float r, float b, float startAngle, float sweepAngle, boolean forceMoveTo) {
            tempRectF.set(l, t, r, b);
            path.arcTo(tempRectF, startAngle, sweepAngle, forceMoveTo);
        }

        private void drawArc(float l, float t, float r, float b, float startAngle, float sweepAngle, boolean useCenter, Paint paint) {
            tempRectF.set(l, t, r, b);
            canvas.drawArc(tempRectF, startAngle, sweepAngle, useCenter, paint);
        }

        private void drawRoundRect(float l, float t, float r, float b, float rx, float ry, Paint paint) {
            tempRectF.set(l, t, r, b);
            canvas.drawRoundRect(tempRectF, rx, ry, paint);
        }

        private void drawOval(float l, float t, float r, float b, Paint paint) {
            tempRectF.set(l, t, r, b);
            canvas.drawOval(tempRectF, paint);
        }

        /*
            Stroke + fill drawing wrappers
         */

        private void drawArc(float l, float t, float r, float b, float startAngle, float sweepAngle, boolean useCenter, int color) {
            drawArc(l, t, r, b, startAngle, sweepAngle, useCenter, mPaints.fills[color]);
            if (mPaints.hasStroke) {
                drawArc(l, t, r, b, startAngle, sweepAngle, useCenter, mPaints.strokes[color]);
            }
        }

        private void drawRoundRect(float l, float t, float r, float b, float rx, float ry, int color) {
            drawRoundRect(l, t, r, b, rx, ry, mPaints.fills[color]);
            if (mPaints.hasStroke) {
                drawRoundRect(l, t, r, b, rx, ry, mPaints.strokes[color]);
            }
        }

        private void drawOval(float l, float t, float r, float b, int color) {
            drawOval(l, t, r, b, mPaints.fills[color]);
            if (mPaints.hasStroke) {
                drawOval(l, t, r, b, mPaints.strokes[color]);
            }
        }

        private void drawRect(float l, float t, float r, float b, int color) {
            canvas.drawRect(l, t, r, b, mPaints.fills[color]);
            if (mPaints.hasStroke) {
                canvas.drawRect(l, t, r, b, mPaints.strokes[color]);
            }
        }

        private void drawPath(Path path, int color) {
            canvas.drawPath(path, mPaints.fills[color]);
            if (mPaints.hasStroke) {
                canvas.drawPath(path, mPaints.strokes[color]);
            }
        }

        private void initGlyphs() {
            mGlyphMap.put("0_1", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "0";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return "1";
                }

                @Override
                public void draw(float t) {
                    float d1 = decelerate5(progress(t, 0, 0.5f));
                    float d2 = decelerate5(progress(t, 0.5f, 1));

                    // 0
                    canvas.save();

                    // temporarily make space for the squashed zero
                    canvas.translate(interpolate(d1, 0, interpolate(d2, 24, 0)), 0);

                    scaleUniform(interpolate(d1, 1, 2f / 3), 72, 144);
                    scaleUniform(interpolate(d2, 1, 0.7f), 72, 96);
                    canvas.rotate(interpolate(d1, 45, 0), 72, 72);

                    float stretchX = interpolate(d1, 0, interpolate(d2, 72, -36));

                    path.reset();
                    path.moveTo(72 - stretchX, 144);
                    arcTo(-stretchX, 0, 144 - stretchX, 144, 90, 180, true);
                    path.lineTo(72 + stretchX, 0);
                    path.lineTo(72 + stretchX, 144);
                    path.lineTo(72 - stretchX, 144);
                    path.close();
                    drawPath(path, COLOR_2);

                    path.reset();
                    arcTo(stretchX, 0, 144 + stretchX, 144, -90, 180, true);
                    path.close();
                    drawPath(path, COLOR_3);

                    canvas.restore();

                    // 1
                    if (d2 > 0) {
                        drawRect(
                                interpolate(d2, 28, 0), interpolate(d2, 72, 0), 100, interpolate(d2, 144, 48),
                                COLOR_2);

                        drawRect(28, interpolate(d2, 144, 48), 100, 144,
                                COLOR_3);
                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(
                            decelerate5(progress(t, 0.5f, 1)),
                            interpolate(decelerate5(progress(t, 0, 0.5f)), 144, 192), 100);
                }
            });

            mGlyphMap.put("1_2", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "1";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return "2";
                }

                @Override
                public void draw(float t) {
                    float d = 1 - decelerate5(progress(t, 0, 0.5f));
                    float d1 = decelerate5(progress(t, 0.3f, 0.8f));
                    float d2 = decelerate5(progress(t, 0.5f, 1.0f));

                    // 2
                    if (d1 > 0) {
                        canvas.save();
                        canvas.translate(interpolate(d2, 72, 0), 0);
                        path.reset();
                        path.moveTo(0, 144);
                        path.lineTo(72, 72);
                        path.lineTo(72, 144);
                        path.lineTo(0, 144);
                        drawPath(path, COLOR_3);
                        canvas.restore();

                        canvas.save();
                        // TODO: interpolate colors
                        //ctx.fillStyle = interpolateColors(d2, o.color2, o.color1);
                        canvas.translate(108, interpolate(d1, 72, 0));
                        //drawHorzHalfCircle(0, 0, 36, 72, true);
                        drawArc(-36, 0, 36, 72, -90, 180, true, COLOR_1);
                        canvas.restore();

                        canvas.save();
                        canvas.translate(0, interpolate(d1, 72, 0));
                        drawRect(interpolate(d2, 72, 8), 0, interpolate(d2, 144, 108), 72, COLOR_1);
                        canvas.restore();

                        drawRect(72, 72, 144, 144, COLOR_2);
                    }

                    // 1
                    if (d > 0) {
                        canvas.save();
                        canvas.translate(interpolate(d, 44, 0), 0);
                        drawRect(interpolate(d, 28, 0), interpolate(d, 72, 0), 100, interpolate(d, 144, 48), COLOR_2);
                        drawRect(28, interpolate(d, 144, 48), 100, 144, COLOR_3);
                        canvas.restore();
                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0f, 0.5f)), 100, 144);
                }
            });

            mGlyphMap.put("2_3", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "2";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return "3";
                }

                @Override
                public void draw(float t) {
                    float d = decelerate5(progress(t, 0, 0.5f));
                    float d1 = decelerate5(progress(t, 0.5f, 1.0f));

                    // 2
                    if (d < 1) {
                        canvas.save();
                        canvas.translate(interpolate(d, 0, -16), 0);

                        canvas.save();
                        canvas.translate(interpolate(d, 0, 72), 0);
                        path.reset();
                        path.moveTo(0, 144);
                        path.lineTo(72, 72);
                        path.lineTo(72, 144);
                        path.lineTo(0, 144);
                        drawPath(path, COLOR_3);
                        canvas.restore();

                        // TODO: interpolateColors
                        //.fillStyle = interpolateColors(d, o.color1, o.color2);
                        if (d == 0) {
                            path.reset();
                            path.moveTo(8, 0);
                            path.lineTo(108, 0);
                            arcTo(108 - 36, 0, 108 + 36, 72, -90, 180, true);
                            path.lineTo(108, 72);
                            path.lineTo(8, 72);
                            path.lineTo(8, 0);
                            path.close();
                            drawPath(path, COLOR_1);
                        } else {
                            drawArc(108 - 36, interpolate(d, 0, 72),
                                    108 + 36, 72 + interpolate(d, 0, 72),
                                    -90, 180, true, COLOR_1);
                            drawRect(interpolate(d, 8, 72), interpolate(d, 0, 72),
                                    interpolate(d, 108, 144), interpolate(d, 72, 144), COLOR_1);
                        }
                        drawRect(72, 72, 144, 144, COLOR_2);

                        canvas.restore();
                    } else {
                        // 3
                        // half-circle
                        canvas.save();
                        scaleUniform(interpolate(d1, 0.7f, 1), 128, 144);
                        drawArc(32, 48, 128, 144, -90, 180, true, COLOR_3);
                        canvas.restore();

                        // bottom rectangle
                        drawRect(
                                interpolate(d1, 56, 0), interpolate(d1, 72, 96),
                                interpolate(d1, 128, 80), interpolate(d1, 144, 144), COLOR_1);

                        // top part with triangle
                        canvas.save();
                        canvas.translate(0, interpolate(d1, 72, 0));
                        path.reset();
                        path.moveTo(128, 0);
                        path.lineTo(80, 48);
                        path.lineTo(80, 0);
                        path.close();
                        drawPath(path, COLOR_3);
                        drawRect(
                                interpolate(d1, 56, 0), 0,
                                interpolate(d1, 128, 80), interpolate(d1, 72, 48), COLOR_3);
                        canvas.restore();

                        // middle rectangle
                        canvas.save();
                        drawRect(
                                interpolate(d1, 56, 32), interpolate(d1, 72, 48),
                                interpolate(d1, 128, 80), interpolate(d1, 144, 96), COLOR_2);
                        canvas.restore();
                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0f, 0.5f)), 144, 128);
                }
            });

            mGlyphMap.put("3_4", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "3";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return "4";
                }

                @Override
                public void draw(float t) {
                    float d1 = 1 - decelerate5(progress(t, 0, 0.5f));
                    float d2 = decelerate5(progress(t, 0.5f, 1));

                    // 3
                    if (d1 > 0) {
                        canvas.save();
                        canvas.translate(interpolate(d1, 16, 0), 0);

                        // middle rectangle
                        canvas.save();
                        drawRect(
                                interpolate(d1, 56, 32), interpolate(d1, 72, 48),
                                interpolate(d1, 128, 80), interpolate(d1, 144, 96), COLOR_2);
                        canvas.restore();

                        // half-circle
                        canvas.save();
                        scaleUniform(interpolate(d1, 0.7f, 1), 128, 144);
                        drawArc(32, 48, 128, 144, -90, 180, true, COLOR_3);
                        canvas.restore();

                        // bottom rectangle
                        drawRect(
                                interpolate(d1, 56, 0), interpolate(d1, 72, 96),
                                interpolate(d1, 128, 80), interpolate(d1, 144, 144), COLOR_1);

                        // top part with triangle
                        canvas.save();
                        canvas.translate(0, interpolate(d1, 72, 0));
                        path.reset();
                        path.moveTo(80, 0);
                        path.lineTo(128, 0);
                        path.lineTo(80, 48);
                        if (d1 == 1) {
                            path.lineTo(0, 48);
                            path.lineTo(0, 0);
                            path.lineTo(80, 0);
                            path.close();
                            drawPath(path, COLOR_3);
                        } else {
                            path.close();
                            drawPath(path, COLOR_3);
                            drawRect(
                                    interpolate(d1, 56, 0), 0,
                                    interpolate(d1, 128, 80), interpolate(d1, 72, 48), COLOR_3);
                        }
                        canvas.restore();

                        canvas.restore();
                    } else {
                        // 4
                        // bottom rectangle
                        drawRect(72, interpolate(d2, 144, 108), 144, 144, COLOR_2);

                        // middle rectangle
                        drawRect(interpolate(d2, 72, 0), interpolate(d2, 144, 72), 144, interpolate(d2, 144, 108), COLOR_1);

                        // triangle
                        canvas.save();
                        scaleUniform(d2, 144, 144);
                        path.reset();
                        path.moveTo(72, 72);
                        path.lineTo(72, 0);
                        path.lineTo(0, 72);
                        path.lineTo(72, 72);
                        drawPath(path, COLOR_2);

                        canvas.restore();

                        // top rectangle
                        drawRect(72, interpolate(d2, 72, 0), 144, interpolate(d2, 144, 72), COLOR_3);
                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0.5f, 1f)), 128, 144);
                }
            });

            mGlyphMap.put("4_5", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "4";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return "5";
                }

                @Override
                public void draw(float t) {
                    float d = decelerate5(progress(t, 0, 0.5f));
                    float d1 = decelerate5(progress(t, 0.5f, 1));

                    // 4
                    if (d < 1) {
                        // bottom rectangle
                        drawRect(interpolate(d, 72, 0), 108, interpolate(d, 144, 72), 144, COLOR_2);

                        // top rectangle
                        drawRect(interpolate(d, 72, 0), interpolate(d, 0, 72),
                                interpolate(d, 144, 72), interpolate(d, 72, 144), COLOR_3);

                        // triangle
                        canvas.save();
                        scaleUniform(1 - d, 0, 144);
                        path.reset();
                        path.moveTo(72, 72);
                        path.lineTo(72, 0);
                        path.lineTo(0, 72);
                        path.lineTo(72, 72);
                        drawPath(path, COLOR_2);

                        canvas.restore();

                        // middle rectangle
                        drawRect(0, 72,
                                interpolate(d, 144, 72), interpolate(d, 108, 144), COLOR_1);
                    } else {
                        // 5
                        // wing rectangle
                        canvas.save();
                        drawRect(
                                80, interpolate(d1, 72, 0),
                                interpolate(d1, 80, 128), interpolate(d1, 144, 48), COLOR_2);
                        canvas.restore();

                        // half-circle
                        canvas.save();
                        scaleUniform(interpolate(d1, 0.75f, 1), 0, 144);
                        canvas.translate(interpolate(d1, -48, 0), 0);
                        drawArc(32, 48, 128, 144, -90, 180, true, COLOR_3);
                        canvas.restore();

                        // bottom rectangle
                        drawRect(0, 96, 80, 144, COLOR_2);

                        // middle rectangle
                        drawRect(
                                0, interpolate(d1, 72, 0),
                                80, interpolate(d1, 144, 96), COLOR_1);
                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0f, 0.5f)), 144, 128);
                }
            });

            mGlyphMap.put("5_6", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "5";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return "6";
                }

                @Override
                public void draw(float t) {
                    float d = decelerate5(progress(t, 0, 0.7f));
                    float d1 = decelerate5(progress(t, 0.1f, 1));

                    // 5 (except half-circle)
                    if (d < 1) {
                        canvas.save();
                        scaleUniform(interpolate(d, 1, 0.25f), 108, 96);

                        // wing rectangle
                        drawRect(80, 0, 128, 48, COLOR_2);

                        // bottom rectangle
                        drawRect(0, 96, 80, 144, COLOR_2);

                        // middle rectangle
                        drawRect(0, 0, 80, 96, COLOR_1);

                        canvas.restore();
                    }

                    // half-circle
                    canvas.save();

                    canvas.rotate(interpolate(d1, 0, 90), 72, 72);

                    if (d1 == 0) {
                        drawArc(
                                32, 48,
                                128, 144, -90, 180, true, COLOR_3);
                    } else {
                        scaleUniform(interpolate(d1, 2f / 3, 1), 80, 144);
                        canvas.translate(interpolate(d1, 8, 0), 0);
                        drawArc(
                                0, 0,
                                144, 144, -90, 180, true, COLOR_3);
                    }

                    // 6 (just the parallelogram)
                    if (d1 > 0) {
                        canvas.save();
                        canvas.rotate(-90, 72, 72);
                        path.reset();
                        path.moveTo(0, 72);
                        path.lineTo(interpolate(d1, 0, 36), interpolate(d1, 72, 0));
                        path.lineTo(interpolate(d1, 72, 108), interpolate(d1, 72, 0));
                        path.lineTo(72, 72);
                        path.lineTo(0, 72);
                        drawPath(path, COLOR_2);

                        canvas.restore();
                    }

                    canvas.restore();
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0.1f, 1f)), 128, 144);
                }
            });

            mGlyphMap.put("6_7", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "6";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return "7";
                }

                @Override
                public void draw(float t) {
                    float d = decelerate5(t);

                    // 7 rectangle
                    drawRect(interpolate(d, 72, 0), 0, 72, 72, COLOR_3);

                    // 6 circle
                    canvas.save();

                    canvas.translate(interpolate(d, 0, 36), 0);

                    if (d < 1) {
                        drawArc(0, 0, 144, 144,
                                interpolate(d, 180, -64f),
                                -180, true, COLOR_3);
                    }

                    // parallelogram
                    path.reset();
                    path.moveTo(36, 0);
                    path.lineTo(108, 0);
                    path.lineTo(interpolate(d, 72, 36), interpolate(d, 72, 144));
                    path.lineTo(interpolate(d, 0, -36), interpolate(d, 72, 144));
                    path.close();
                    drawPath(path, COLOR_2);

                    canvas.restore();
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return 144;
                }
            });

            mGlyphMap.put("7_8", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "7";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return "8";
                }

                @Override
                public void draw(float t) {
                    float d = decelerate5(progress(t, 0, 0.5f));
                    float d1 = decelerate5(progress(t, 0.2f, 0.5f));
                    float d2 = decelerate5(progress(t, 0.5f, 1));

                    // 8
                    if (d1 > 0) {
                        if (d2 > 0) {
                            // top
                            canvas.save();
                            canvas.translate(0, interpolate(d2, 96, 0));
                            drawRoundRect(24, 0, 120, 48, 24, 24, COLOR_3);
                            canvas.restore();
                        }

                        // left bottom
                        canvas.save();
                        canvas.translate(interpolate(d1, 24, 0), 0);
                        scaleUniform(interpolate(d2, 0.5f, 1), 48, 144);
                        drawArc(0, 48, 96, 144, 90, 180, true, COLOR_1);
                        canvas.restore();

                        // right bottom
                        canvas.save();
                        canvas.translate(interpolate(d1, -24, 0), 0);
                        scaleUniform(interpolate(d2, 0.5f, 1), 96, 144);
                        drawArc(48, 48, 144, 144, -90, 180, true, COLOR_2);
                        canvas.restore();

                        // bottom middle
                        canvas.save();
                        canvas.scale(interpolate(d1, 0, 1), 1, 72, 0);
                        drawRect(48, interpolate(d2, 96, 48), 96, 144, COLOR_1);
                        drawRect(interpolate(d2, 48, 96), interpolate(d2, 96, 48), 96, 144, COLOR_2);
                        canvas.restore();
                    }

                    if (d < 1) {
                        // 7 rectangle
                        drawRect(
                                interpolate(d, 0, 48), interpolate(d, 0, 96),
                                interpolate(d, 72, 96), interpolate(d, 72, 144), COLOR_3);

                        // 7 parallelogram
                        path.reset();
                        path.moveTo(interpolate(d, 72, 48), interpolate(d, 0, 96));
                        path.lineTo(interpolate(d, 144, 96), interpolate(d, 0, 96));
                        path.lineTo(interpolate(d, 72, 96), 144);
                        path.lineTo(interpolate(d, 0, 48), 144);
                        path.close();
                        drawPath(path, COLOR_2);

                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return 144;
                }
            });

            mGlyphMap.put("8_9", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "8";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return "9";
                }

                @Override
                public void draw(float t) {
                    float d = decelerate5(progress(t, 0, 0.5f));
                    float d1 = decelerate5(progress(t, 0.5f, 1));

                    // 8
                    if (d < 1) {
                        // top
                        canvas.save();
                        canvas.translate(0, interpolate(d, 0, 48));
                        drawRoundRect(24, 0, 120, 48, 24, 24, COLOR_3);
                        canvas.restore();

                        if (d == 0) {
                            // left + middle bottom
                            canvas.save();
                            path.reset();
                            path.moveTo(48, 48);
                            path.lineTo(96, 48);
                            path.lineTo(96, 144);
                            path.lineTo(48, 144);
                            arcTo(0, 48, 96, 144, 90, 180, true);
                            drawPath(path, COLOR_1);
                            canvas.restore();

                            // right bottom
                            drawArc(48, 48, 144, 144, -90, 180, true, COLOR_2);
                        } else {
                            // bottom middle
                            drawRect(interpolate(d, 48, 72) - 2, interpolate(d, 48, 0),
                                    interpolate(d, 96, 72) + 2, 144, COLOR_1);

                            // left bottom
                            canvas.save();
                            scaleUniform(interpolate(d, 2f/3, 1), 0, 144);
                            drawArc(0, 0, 144, 144, 90, 180, true, COLOR_1);
                            canvas.restore();

                            // right bottom
                            canvas.save();
                            scaleUniform(interpolate(d, 2f/3, 1), 144, 144);
                            drawArc(0, 0, 144, 144, -90, 180, true, COLOR_2);
                            canvas.restore();
                        }
                    } else {
                        // 9
                        canvas.save();

                        canvas.rotate(interpolate(d1, -90, -180), 72, 72);

                        // parallelogram
                        path.reset();
                        path.moveTo(0, 72);
                        path.lineTo(interpolate(d1, 0, 36), interpolate(d1, 72, 0));
                        path.lineTo(interpolate(d1, 72, 108), interpolate(d1, 72, 0));
                        path.lineTo(72, 72);
                        path.lineTo(0, 72);
                        drawPath(path, COLOR_3);

                        // vanishing arc
                        drawArc(0, 0, 144, 144,
                                -180,
                                interpolate(d1, 180, 0), true, COLOR_1);

                        // primary arc
                        drawArc(0, 0, 144, 144, 0, 180, true, COLOR_2);

                        canvas.restore();
                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return 144;
                }
            });

            mGlyphMap.put("9_0", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "9";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return "0";
                }

                @Override
                public void draw(float t) {
                    float d = decelerate5(t);

                    // 9
                    canvas.save();

                    canvas.rotate(interpolate(d, -180, -225), 72, 72);

                    // parallelogram
                    canvas.save();
                    path.reset();
                    path.moveTo(0, 72);
                    path.lineTo(interpolate(d, 36, 0), interpolate(d, 0, 72));
                    path.lineTo(interpolate(d, 108, 72), interpolate(d, 0, 72));
                    path.lineTo(72, 72);
                    path.lineTo(0, 72);
                    drawPath(path, COLOR_3);

                    canvas.restore();

                    // TODO: interpolate colors
                    //ctx.fillStyle = interpolateColors(d, COLOR_1, COLOR_3);
                    drawArc(0, 0, 144, 144,
                            0, interpolate(d, 0, -180), true, COLOR_3);

                    drawArc(0, 0, 144, 144, 0, 180, true, COLOR_2);

                    canvas.restore();
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return 144;
                }
            });

            mGlyphMap.put(" _1", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return " ";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return "1";
                }

                @Override
                public void draw(float t) {
                    float d1 = decelerate5(progress(t, 0, 0.5f));
                    float d2 = decelerate5(progress(t, 0.5f, 1));

                    // 1
                    scaleUniform(interpolate(d1, 0, 1), 0, 144);
                    drawRect(
                            interpolate(d2, 28, 0), interpolate(d2, 72, 0),
                            100, interpolate(d2, 144, 48), COLOR_2);

                    if (d2 > 0) {
                        drawRect(28, interpolate(d2, 144, 48), 100, 144, COLOR_3);
                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0, 0.5f)), 0, 100);
                }
            });

            mGlyphMap.put("1_ ", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "1";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return " ";
                }

                @Override
                public void draw(float t) {
                    float d1 = decelerate5(progress(t, 0, 0.5f));
                    float d2 = decelerate5(progress(t, 0.5f, 1));

                    scaleUniform(interpolate(d2, 1, 0), 0, 144);
                    drawRect(
                            interpolate(d1, 0, 28), interpolate(d1, 0, 72),
                            100, interpolate(d1, 48, 144), COLOR_2);

                    if (d1 < 1) {
                        drawRect(28, interpolate(d1, 48, 144), 100, 144, COLOR_3);
                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0.5f, 1)), 100, 0);
                }
            });

            // 24 hour only
            mGlyphMap.put("2_ ", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "2";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return " ";
                }

                @Override
                public void draw(float t) {
                    float d = decelerate5(progress(t, 0, 0.5f));
                    float d1 = decelerate5(progress(t, 0.5f, 1.0f));

                    // 2
                    canvas.save();
                    canvas.translate(interpolate(d, 0, -72), 0);

                    if (d < 1) {
                        canvas.save();
                        canvas.translate(interpolate(d, 0, 72), 0);
                        path.reset();
                        path.moveTo(0, 144);
                        path.lineTo(72, 72);
                        path.lineTo(72, 144);
                        path.lineTo(0, 144);
                        drawPath(path, COLOR_3);
                        canvas.restore();

                        canvas.save();
                        canvas.translate(0, interpolate(d, 0, 72));
                        canvas.translate(108, 0);
                        drawArc(-36, 0, 36, 72, -90, 180, true, COLOR_1);
                        canvas.restore();

                        canvas.save();
                        drawRect(interpolate(d, 8, 72), interpolate(d, 0, 72),
                                interpolate(d, 108, 144), interpolate(d, 72, 144), COLOR_1);
                        canvas.restore();
                    }

                    canvas.save();
                    scaleUniform(interpolate(d1, 1, 0), 72, 144);
                    drawRect(72, 72, 144, 144, COLOR_2);
                    canvas.restore();

                    canvas.restore();
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0, 0.5f)), 144,
                            interpolate(decelerate5(progress(t, 0.5f, 1)), 72, 0));
                }
            });

            // 24 hour only
            mGlyphMap.put("3_0", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "3";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return "0";
                }

                @Override
                public void draw(float t) {
                    float d1 = 1 - decelerate5(progress(t, 0, 0.5f));
                    float d2 = decelerate5(progress(t, 0.5f, 1));

                    canvas.save();
                    canvas.rotate(interpolate(d2, 0, 45), 72, 72);
                    canvas.translate(interpolate(d1, interpolate(d2, 16, -8), 0), 0);

                    if (d1 > 0) {
                        // top part of 3 with triangle
                        canvas.save();
                        canvas.translate(0, interpolate(d1, 48, 0));
                        float x = interpolate(d1, 48, 0);
                        path.reset();
                        path.moveTo(128 - x, 0);
                        path.lineTo(80 - x, 48);
                        path.lineTo(80 - x, 0);
                        drawPath(path, COLOR_3);
                        drawRect(interpolate(d1, 32, 0), 0, 80, 48, COLOR_3);
                        canvas.restore();
                    }

                    // bottom rectangle in 3
                    drawRect(
                            interpolate(d1, interpolate(d2, 32, 80), 0), 96,
                            80, 144, COLOR_1);

                    // middle rectangle
                    drawRect(
                            interpolate(d2, 32, 80), 48,
                            80, 96, COLOR_2);

                    // 0

                    scaleUniform(interpolate(d2, 2f/3, 1), 80, 144);

                    // half-circles
                    canvas.translate(8, 0);
                    if (d2 > 0) {
                        canvas.save();
                        canvas.rotate(interpolate(d2, -180, 0), 72, 72);
                        drawArc(
                                0, 0,
                                144, 144, 90, 180, true, COLOR_2);
                        canvas.restore();
                    }

                    drawArc(
                            0, 0,
                            144, 144, -90, 180, true, COLOR_3);

                    canvas.restore();
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0, 0.5f)), 128, 144);
                }
            });

            mGlyphMap.put("5_0", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "5";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return "0";
                }

                @Override
                public void draw(float t) {
                    float d = decelerate5(progress(t, 0, 0.5f));
                    float d1 = decelerate5(progress(t, 0.5f, 1));

                    canvas.save();
                    canvas.rotate(interpolate(d1, 0, 45), 72, 72);

                    // 5 (except half-circle)
                    if (d < 1) {
                        // wing rectangle
                        canvas.save();
                        drawRect(
                                80, interpolate(d, 0, 48),
                                interpolate(d, 128, 80), interpolate(d, 48, 144), COLOR_2);
                        canvas.restore();

                        // bottom rectangle
                        drawRect(0, 96, 80, 144, COLOR_2);
                    }

                    // middle rectangle
                    drawRect(
                            interpolate(d1, 0, 80), interpolate(d, 0, interpolate(d1, 48, 0)),
                            80, interpolate(d, 96, 144), COLOR_1);

                    scaleUniform(interpolate(d1, 2f/3, 1), 80, 144);

                    // half-circles
                    if (d1 > 0) {
                        canvas.save();
                        canvas.rotate(interpolate(d1, -180, 0), 72, 72);
                        drawArc(
                                0, 0,
                                144, 144, 90, 180, true, COLOR_2);
                        canvas.restore();
                    }

                    canvas.translate(interpolate(d1, 8, 0), 0);
                    drawArc(
                            0, 0,
                            144, 144, -90, 180, true, COLOR_3);

                    canvas.restore();
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0, 0.5f)), 128, 144);
                }
            });

            mGlyphMap.put("2_1", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return "2";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return "1";
                }

                @Override
                public void draw(float t) {
                    float d = decelerate5(progress(t, 0, 0.5f));
                    float d1 = decelerate5(progress(t, 0.2f, 0.5f));
                    float d2 = decelerate5(progress(t, 0.5f, 1));

                    // 2
                    if (d1 < 1) {
                        canvas.save();
                        canvas.translate(interpolate(d, 0, 28), 0);
                        path.reset();
                        path.moveTo(0, 144);
                        path.lineTo(72, 72);
                        path.lineTo(72, 144);
                        path.lineTo(0, 144);
                        drawPath(path, COLOR_3);
                        canvas.restore();

                        canvas.save();
                        // TODO: interpolate colors
                        //ctx.fillStyle = interpolateColors(d1, COLOR_1, COLOR_2);
                        canvas.translate(interpolate(d, 108, 64), interpolate(d1, 0, 72));
                        drawArc(-36, 0, 36, 72, -90, 180, true, COLOR_1);
                        canvas.restore();

                        canvas.save();
                        canvas.translate(0, interpolate(d1, 0, 72));
                        drawRect(interpolate(d, 8, 28), 0, interpolate(d, 108, 100), 72, COLOR_1);
                        canvas.restore();

                        canvas.save();
                        canvas.translate(interpolate(d, 0, -44), 0);
                        drawRect(72, 72, 144, 144, COLOR_2);
                        canvas.restore();
                    } else {
                        // 1
                        canvas.save();
                        drawRect(interpolate(d2, 28, 0), interpolate(d2, 72, 0), 100, interpolate(d2, 144, 48), COLOR_2);

                        drawRect(28, interpolate(d2, 144, 48), 100, 144, COLOR_3);
                        canvas.restore();
                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0, 0.5f)), 144, 100);
                }
            });

            mGlyphMap.put(":", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return ":";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return ":";
                }

                @Override
                public void draw(float t) {
                    drawOval(0, 0, 48, 48, COLOR_2);
                    drawOval(0, 96, 48, 144, COLOR_3);
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return 48;
                }
            });

            mGlyphMap.put(" ", new Glyph() {
                @Override
                public String getCanonicalStartGlyph() {
                    return " ";
                }

                @Override
                public String getCanonicalEndGlyph() {
                    return " ";
                }

                @Override
                public void draw(float t) {
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return 0;
                }
            });

            mGlyphMap.put("0", mGlyphMap.get("0_1"));
            mGlyphMap.put("1", mGlyphMap.get("1_2"));
            mGlyphMap.put("2", mGlyphMap.get("2_3"));
            mGlyphMap.put("3", mGlyphMap.get("3_4"));
            mGlyphMap.put("4", mGlyphMap.get("4_5"));
            mGlyphMap.put("5", mGlyphMap.get("5_6"));
            mGlyphMap.put("6", mGlyphMap.get("6_7"));
            mGlyphMap.put("7", mGlyphMap.get("7_8"));
            mGlyphMap.put("8", mGlyphMap.get("8_9"));
            mGlyphMap.put("9", mGlyphMap.get("9_0"));
        }
    }
}
