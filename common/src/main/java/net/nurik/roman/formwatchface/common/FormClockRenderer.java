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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static net.nurik.roman.formwatchface.common.MathUtil.accelerate2;
import static net.nurik.roman.formwatchface.common.MathUtil.constrain;
import static net.nurik.roman.formwatchface.common.MathUtil.decelerate5;
import static net.nurik.roman.formwatchface.common.MathUtil.interpolate;
import static net.nurik.roman.formwatchface.common.MathUtil.progress;

public class FormClockRenderer {
    private static final String DEBUG_TIME = null;//"1234567890";
    private static final String DEBUG_GLYPH = null;//"2_3";
    private static final boolean OFFSCREEN_GYLPHS = true;
    private static final boolean DEBUG_SHOW_RECTS = false;

    private ArrayList<Integer> mAnimatedGlyphIndices = new ArrayList<>();
    private ArrayList<Glyph> mGlyphs = new ArrayList<>();
    private Options mOptions;
    private ClockPaints mPaints;
    private Font mFont = new Font();

    // for offscreen glyphs
    private Bitmap mOffsGlyphBitmap;
    private Canvas mOffsGlyphCanvas;
    private Paint mOffsGlyphPaint;
    private int mOffsGlyphBitmapUnpaddedSize;

    private Paint mDebugShowRectPaint;

    private float mLastGlyphStartAnimTime;
    private float mEndAnimTime;
    private float mAnimTime;

    private PointF mMeasuredSize = new PointF();

    public FormClockRenderer(Options options, ClockPaints paints) {
        this.mOptions = options;
        this.mPaints = paints;
        initGlyphBitmap();

        if (DEBUG_SHOW_RECTS) {
            mDebugShowRectPaint = new Paint();
            mDebugShowRectPaint.setStrokeWidth(4);
            mDebugShowRectPaint.setColor(Color.RED);
            mDebugShowRectPaint.setStyle(Paint.Style.STROKE);
        }
    }

    private void initGlyphBitmap() {
        if (!OFFSCREEN_GYLPHS) {
            return;
        }

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

    public void setTime(TimeInfo ti) {
        String str1 = ti.timeString();
        String str2 = ti.next().timeString();

        mAnimatedGlyphIndices.clear();
        mGlyphs.clear();

        if (DEBUG_GLYPH != null) {
            mGlyphs.add(mFont.getGlyph(DEBUG_GLYPH));
            mAnimatedGlyphIndices.add(0);
        } else if (DEBUG_TIME != null) {
            for (int i = 0; i < DEBUG_TIME.length(); i++) {
                mGlyphs.add(mFont.getGlyph(Character.toString(DEBUG_TIME.charAt(i))));
            }
        } else {
            int len = str1.length();
            for (int i = 0; i < len; i++) {
                char c1 = str1.charAt(i);
                char c2 = str2.charAt(i);

                if (c1 == ':') {
                    mGlyphs.add(mFont.getGlyph(":"));
                    continue;
                }

                if (c1 == c2) {
                    mGlyphs.add(mFont.getGlyph(String.valueOf(c1)));
                } else {
                    mAnimatedGlyphIndices.add(i);
                    mGlyphs.add(mFont.getGlyph(c1 + "_" + c2));
                }
            }
        }

        Collections.reverse(mAnimatedGlyphIndices);
        mLastGlyphStartAnimTime = mAnimatedGlyphIndices.size() * mOptions.glyphAnimAverageDelay;
        mEndAnimTime = mLastGlyphStartAnimTime + mOptions.glyphAnimDuration;
        mAnimTime = 0;
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

        for (int i = 0; i < mGlyphs.size(); i++) {
            Glyph glyph = mGlyphs.get(i);

            float t = getGlyphAnimProgress(i);
            float glyphWidth = glyph.getWidthAtProgress(t) * mOptions.textSize / Font.DRAWHEIGHT;

            rectF.set(x, 0, x + glyphWidth, mOptions.textSize);
            cb.visitGlyph(glyph, t, rectF);

            x += Math.floor(glyphWidth +
                    (i >= 0 ? mOptions.charSpacing : 0));
        }
    }

    public void draw(final Canvas canvas, float left, float top) {
        mFont.canvas = OFFSCREEN_GYLPHS ? mOffsGlyphCanvas : canvas;

        int sc = canvas.save();
        canvas.translate(left, top);

        layoutPass(new LayoutPassCallback() {
            @Override
            public void visitGlyph(Glyph glyph, float glyphAnimProgress, RectF rect) {
                int sc;

                if (OFFSCREEN_GYLPHS) {
                    mOffsGlyphBitmap.eraseColor(Color.TRANSPARENT);
                    sc = mOffsGlyphCanvas.save();
                    mOffsGlyphCanvas.translate(
                            mOffsGlyphBitmapUnpaddedSize / 2,
                            mOffsGlyphBitmapUnpaddedSize / 2);
                    mOffsGlyphCanvas.scale(
                            mOffsGlyphBitmapUnpaddedSize * 1f / Font.DRAWHEIGHT,
                            mOffsGlyphBitmapUnpaddedSize * 1f / Font.DRAWHEIGHT);
                    glyph.draw(glyphAnimProgress); // draws into mOffsGlyphCanvas
                    mOffsGlyphCanvas.restoreToCount(sc);
                }

                if (DEBUG_SHOW_RECTS) {
                    canvas.drawRect(rect, mDebugShowRectPaint);
                }

                sc = canvas.save();
                canvas.translate(rect.left, rect.top);
                float scale = mOptions.textSize /
                        (OFFSCREEN_GYLPHS ? mOffsGlyphBitmapUnpaddedSize : Font.DRAWHEIGHT);
                canvas.scale(scale, scale);
                if (OFFSCREEN_GYLPHS) {
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
        int indexIntoAnimatedGlyphs = mAnimatedGlyphIndices.indexOf(glyphIndex);
        if (indexIntoAnimatedGlyphs < 0) {
            return 0; // glyphs not currently animating are rendered at t=0
        }

        // this glyph is animating
        float glyphStartAnimTime = interpolate(accelerate2(
                        indexIntoAnimatedGlyphs * 1f / mAnimatedGlyphIndices.size()),
                0, mLastGlyphStartAnimTime);
        return progress(mAnimTime, glyphStartAnimTime, glyphStartAnimTime
                + mOptions.glyphAnimDuration);
    }

    public void setAnimTime(float t) {
        mAnimTime = constrain(t, 0, mEndAnimTime);
    }

    public float getEndTime() {
        return mEndAnimTime;
    }

    private static interface LayoutPassCallback {
        public void visitGlyph(Glyph glyph, float glyphAnimProgress, RectF rect);
    }

    public static class TimeInfo {
        private static final int EMPTY_VALUE = Integer.MIN_VALUE; // don't use -1!!
        public int h;
        public int m;
        public int s;
        public boolean is24Hour;

        public TimeInfo(int h, int m, int s) {
            this.h = h;
            this.m = m;
            this.s = s;
        }

        public TimeInfo(int h, int m) {
            this(h, m, EMPTY_VALUE);
        }

        public TimeInfo(int s) {
            this(EMPTY_VALUE, EMPTY_VALUE, s);
        }

        public TimeInfo(TimeInfo other) {
            this(other.h, other.m, other.s);
        }

        public boolean hasSeconds() {
            return s != EMPTY_VALUE;
        }

        public boolean hasHoursMinutes() {
            return h != EMPTY_VALUE && m != EMPTY_VALUE;
        }

        public TimeInfo clone() {
            return new TimeInfo(this);
        }

        public TimeInfo removeSeconds() {
            s = EMPTY_VALUE;
            return this;
        }

        public TimeInfo removeHoursMinutes() {
            h = m = EMPTY_VALUE;
            return this;
        }

        public TimeInfo next() {
            TimeInfo next = new TimeInfo(this.h, this.m, this.s);

            if (next.hasSeconds()) {
                ++next.s;
                if (next.s >= 60) {
                    next.s = 0;
                    if (next.hasHoursMinutes()) {
                        ++next.m;
                    }
                }
            } else if (next.hasHoursMinutes()) {
                ++next.m;
            }

            if (next.hasHoursMinutes()) {
                if (next.m >= 60) {
                    next.m = 0;
                    ++next.h;
                }

                if (is24Hour) {
                    if (next.h >= 24) {
                        next.h = 0;
                    }
                } else {
                    if (next.h >= 13) {
                        next.h = 1;
                    }
                }

                return next;
            }

            return next;
        }

        public TimeInfo previous() {
            TimeInfo prev = new TimeInfo(this.h, this.m, this.s);

            if (prev.hasSeconds()) {
                --prev.s;
                if (prev.s < 0) {
                    prev.s = 59;
                    if (prev.hasHoursMinutes()) {
                        --prev.m;
                    }
                }
            } else if (prev.hasHoursMinutes()) {
                --prev.m;
            }

            if (prev.hasHoursMinutes()) {
                if (prev.m < 0) {
                    prev.m = 59;
                    --prev.h;
                }

                if (is24Hour) {
                    if (prev.h < 0) {
                        prev.h = 23;
                    }
                } else {
                    if (prev.h < 1) {
                        prev.h = 12;
                    }
                }

                return prev;
            }

            return prev;
        }

        private static StringBuilder mTimeStrSB = new StringBuilder();

        public String timeString() {
            mTimeStrSB.setLength(0);
            if (h >= 0 && m >= 0) {
                mTimeStrSB.append(h < 10 ? " " : "").append(h);
                mTimeStrSB.append(":");
                mTimeStrSB.append(m < 10 ? "0" : "").append(m);
            }
            if (s >= 0) {
                mTimeStrSB.append(":");
                mTimeStrSB.append(s < 10 ? "0" : "").append(s);
            }
            return mTimeStrSB.toString();
        }
    }

    public static class Options {
        public float textSize;
        public float charSpacing;
        public boolean onlySeconds;
        public boolean is24hour;
        public float glyphAnimAverageDelay;
        public float glyphAnimDuration;

        public Options() {
        }

        public Options(Options copy) {
            this.textSize = copy.textSize;
            this.charSpacing = copy.charSpacing;
            this.onlySeconds = copy.onlySeconds;
            this.is24hour = copy.is24hour;
            this.glyphAnimAverageDelay = copy.glyphAnimAverageDelay;
            this.glyphAnimDuration = copy.glyphAnimDuration;
        }
    }

    public static class ClockPaints {
        public Paint paint1;
        public Paint paint2;
        public Paint paint3;
    }

    public interface Glyph {
        void draw(float t);
        float getWidthAtProgress(float t);
    }

    /**
     * Font data and common drawing operations.
     */
    private class Font {
        private static final int DRAWHEIGHT = 144;

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

        // API 21 compat
        private void arcTo(float l, float t, float r, float b, float startAngle, float sweepAngle, boolean forceMoveTo) {
            tempRectF.set(l, t, r, b);
            path.arcTo(tempRectF, startAngle, sweepAngle, forceMoveTo);
        }

        // API 21 compat
        private void drawArc(float l, float t, float r, float b, float startAngle, float sweepAngle, boolean useCenter, Paint paint) {
            tempRectF.set(l, t, r, b);
            canvas.drawArc(tempRectF, startAngle, sweepAngle, useCenter, paint);
        }

        // API 21 compat
        private void drawRoundRect(float l, float t, float r, float b, float rx, float ry, Paint paint) {
            tempRectF.set(l, t, r, b);
            canvas.drawRoundRect(tempRectF, rx, ry, paint);
        }

        // API 21 compat
        private void drawOval(float l, float t, float r, float b, Paint paint) {
            tempRectF.set(l, t, r, b);
            canvas.drawOval(tempRectF, paint);
        }

        private void initGlyphs() {
            mGlyphMap.put("0_1", new Glyph() {
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
                    canvas.drawPath(path, mPaints.paint2);

                    path.reset();
                    arcTo(stretchX, 0, 144 + stretchX, 144, -90, 180, true);
                    path.close();
                    canvas.drawPath(path, mPaints.paint3);

                    canvas.restore();

                    // 1
                    if (d2 > 0) {
                        canvas.drawRect(
                                interpolate(d2, 28, 0), interpolate(d2, 72, 0), 100, interpolate(d2, 144, 48),
                                mPaints.paint2);

                        canvas.drawRect(28, interpolate(d2, 144, 48), 100, 144,
                                mPaints.paint3);
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
                        canvas.drawPath(path, mPaints.paint3);
                        canvas.restore();

                        canvas.save();
                        // TODO: interpolate colors
                        //ctx.fillStyle = interpolateColors(d2, o.color2, o.color1);
                        canvas.translate(108, interpolate(d1, 72, 0));
                        //drawHorzHalfCircle(0, 0, 36, 72, true);
                        drawArc(-36, 0, 36, 72, -90, 180, true, mPaints.paint2);
                        canvas.restore();

                        canvas.save();
                        canvas.translate(0, interpolate(d1, 72, 0));
                        canvas.drawRect(interpolate(d2, 72, 8), 0, interpolate(d2, 144, 108), 72, mPaints.paint1);
                        canvas.restore();

                        canvas.drawRect(72, 72, 144, 144, mPaints.paint2);
                    }

                    // 1
                    if (d > 0) {
                        canvas.save();
                        canvas.translate(interpolate(d, 44, 0), 0);
                        canvas.drawRect(interpolate(d, 28, 0), interpolate(d, 72, 0), 100, interpolate(d, 144, 48), mPaints.paint2);
                        canvas.drawRect(28, interpolate(d, 144, 48), 100, 144, mPaints.paint3);
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
                        canvas.drawPath(path, mPaints.paint3);
                        canvas.restore();

                        canvas.save();
                        canvas.translate(0, interpolate(d, 0, 72));
                        // TODO: interpolateColors
                        //.fillStyle = interpolateColors(d, o.color1, o.color2);
                        canvas.translate(108, 0);
                        drawArc(-36, 0, 36, 72, -90, 180, true, mPaints.paint2);
                        canvas.restore();

                        canvas.drawRect(interpolate(d, 8, 72), interpolate(d, 0, 72),
                                interpolate(d, 108, 144), interpolate(d, 72, 144), mPaints.paint1);
                        canvas.drawRect(72, 72, 144, 144, mPaints.paint2);

                        canvas.restore();
                    } else {
                        // 3
                        // half-circle
                        canvas.save();
                        scaleUniform(interpolate(d1, 0.7f, 1), 128, 144);
                        drawArc(32, 48, 128, 144, -90, 180, true, mPaints.paint3);
                        canvas.restore();

                        // bottom rectangle
                        canvas.drawRect(
                                interpolate(d1, 56, 0), interpolate(d1, 72, 96),
                                interpolate(d1, 128, 80), interpolate(d1, 144, 144), mPaints.paint1);

                        // top part with triangle
                        canvas.save();
                        canvas.translate(0, interpolate(d1, 72, 0));
                        path.reset();
                        path.moveTo(128, 0);
                        path.lineTo(80, 48);
                        path.lineTo(80, 0);
                        canvas.drawPath(path, mPaints.paint3);
                        canvas.drawRect(
                                interpolate(d1, 56, 0), 0,
                                interpolate(d1, 128, 80), interpolate(d1, 72, 48), mPaints.paint3);
                        canvas.restore();

                        // middle rectangle
                        canvas.save();
                        canvas.drawRect(
                                interpolate(d1, 56, 32), interpolate(d1, 72, 48),
                                interpolate(d1, 128, 80), interpolate(d1, 144, 96), mPaints.paint2);
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
                public void draw(float t) {
                    float d1 = 1 - decelerate5(progress(t, 0, 0.5f));
                    float d2 = decelerate5(progress(t, 0.5f, 1));

                    // 3
                    if (d1 > 0) {
                        canvas.save();
                        canvas.translate(interpolate(d1, 16, 0), 0);

                        // middle rectangle
                        canvas.save();
                        canvas.drawRect(
                                interpolate(d1, 56, 32), interpolate(d1, 72, 48),
                                interpolate(d1, 128, 80), interpolate(d1, 144, 96), mPaints.paint2);
                        canvas.restore();

                        // half-circle
                        canvas.save();
                        scaleUniform(interpolate(d1, 0.7f, 1), 128, 144);
                        drawArc(32, 48, 128, 144, -90, 180, true, mPaints.paint3);
                        canvas.restore();

                        // bottom rectangle
                        canvas.drawRect(
                                interpolate(d1, 56, 0), interpolate(d1, 72, 96),
                                interpolate(d1, 128, 80), interpolate(d1, 144, 144), mPaints.paint1);

                        // top part with triangle
                        canvas.save();
                        canvas.translate(0, interpolate(d1, 72, 0));
                        path.reset();
                        path.moveTo(128, 0);
                        path.lineTo(80, 48);
                        path.lineTo(80, 0);
                        canvas.drawPath(path, mPaints.paint3);
                        canvas.drawRect(
                                interpolate(d1, 56, 0), 0,
                                interpolate(d1, 128, 80), interpolate(d1, 72, 48), mPaints.paint3);
                        canvas.restore();

                        canvas.restore();
                    } else {
                        // 4
                        // bottom rectangle
                        canvas.drawRect(72, interpolate(d2, 144, 108), 144, 144, mPaints.paint2);

                        // middle rectangle
                        canvas.drawRect(interpolate(d2, 72, 0), interpolate(d2, 144, 72), 144, interpolate(d2, 144, 108), mPaints.paint1);

                        // triangle
                        canvas.save();
                        scaleUniform(d2, 144, 144);
                        path.reset();
                        path.moveTo(72, 72);
                        path.lineTo(72, 0);
                        path.lineTo(0, 72);
                        path.lineTo(72, 72);
                        canvas.drawPath(path, mPaints.paint2);

                        canvas.restore();

                        // top rectangle
                        canvas.drawRect(72, interpolate(d2, 72, 0), 144, interpolate(d2, 144, 72), mPaints.paint3);
                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0.5f, 1f)), 128, 144);
                }
            });

            mGlyphMap.put("4_5", new Glyph() {
                @Override
                public void draw(float t) {
                    float d = decelerate5(progress(t, 0, 0.5f));
                    float d1 = decelerate5(progress(t, 0.5f, 1));

                    // 4
                    if (d < 1) {
                        // bottom rectangle
                        canvas.drawRect(interpolate(d, 72, 0), 108, interpolate(d, 144, 72), 144, mPaints.paint2);

                        // top rectangle
                        canvas.drawRect(interpolate(d, 72, 0), interpolate(d, 0, 72),
                                interpolate(d, 144, 72), interpolate(d, 72, 144), mPaints.paint3);

                        // triangle
                        canvas.save();
                        scaleUniform(1 - d, 0, 144);
                        path.reset();
                        path.moveTo(72, 72);
                        path.lineTo(72, 0);
                        path.lineTo(0, 72);
                        path.lineTo(72, 72);
                        canvas.drawPath(path, mPaints.paint2);

                        canvas.restore();

                        // middle rectangle
                        canvas.drawRect(0, 72,
                                interpolate(d, 144, 72), interpolate(d, 108, 144), mPaints.paint1);
                    } else {
                        // 5
                        // wing rectangle
                        canvas.save();
                        canvas.drawRect(
                                80, interpolate(d1, 72, 0),
                                interpolate(d1, 80, 128), interpolate(d1, 144, 48), mPaints.paint2);
                        canvas.restore();

                        // half-circle
                        canvas.save();
                        scaleUniform(interpolate(d1, 0.75f, 1), 0, 144);
                        canvas.translate(interpolate(d1, -48, 0), 0);
                        drawArc(32, 48, 128, 144, -90, 180, true, mPaints.paint3);
                        canvas.restore();

                        // bottom rectangle
                        canvas.drawRect(0, 96, 80, 144, mPaints.paint2);

                        // middle rectangle
                        canvas.drawRect(
                                0, interpolate(d1, 72, 0),
                                80, interpolate(d1, 144, 96), mPaints.paint1);
                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0f, 0.5f)), 144, 128);
                }
            });

            mGlyphMap.put("5_6", new Glyph() {
                @Override
                public void draw(float t) {
                    float d = decelerate5(progress(t, 0, 0.7f));
                    float d1 = decelerate5(progress(t, 0.1f, 1));

                    // 5 (except half-circle)
                    if (d < 1) {
                        canvas.save();
                        scaleUniform(interpolate(d, 1, 0.25f), 108, 96);

                        // wing rectangle
                        canvas.drawRect(80, 0, 128, 48, mPaints.paint2);

                        // bottom rectangle
                        canvas.drawRect(0, 96, 80, 144, mPaints.paint2);

                        // middle rectangle
                        canvas.drawRect(0, 0, 80, 96, mPaints.paint1);

                        canvas.restore();
                    }

                    // half-circle
                    canvas.save();

                    canvas.rotate(interpolate(d1, 0, 90), 72, 72);
                    scaleUniform(interpolate(d1, 2f / 3, 1), 80, 144);
                    canvas.translate(interpolate(d1, 8, 0), 0);
                    drawArc(
                            0, 0,
                            144, 144, -90, 180, true, mPaints.paint3);

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
                        canvas.drawPath(path, mPaints.paint2);

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
                public void draw(float t) {
                    float d = decelerate5(t);

                    // 7 rectangle
                    canvas.drawRect(interpolate(d, 72, 0), 0, 72, 72, mPaints.paint3);

                    // 6 circle
                    canvas.save();

                    canvas.translate(interpolate(d, 0, 36), 0);

                    if (d < 1) {
                        drawArc(0, 0, 144, 144,
                                interpolate(d, 180, -64f),
                                -180, true, mPaints.paint3);
                    }

                    // parallelogram
                    path.reset();
                    path.moveTo(36, 0);
                    path.lineTo(108, 0);
                    path.lineTo(interpolate(d, 72, 36), interpolate(d, 72, 144));
                    path.lineTo(interpolate(d, 0, -36), interpolate(d, 72, 144));
                    canvas.drawPath(path, mPaints.paint2);

                    canvas.restore();
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return 144;
                }
            });

            mGlyphMap.put("7_8", new Glyph() {
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
                            drawRoundRect(24, 0, 120, 48, 24, 24, mPaints.paint3);
                            canvas.restore();
                        }

                        // left bottom
                        canvas.save();
                        canvas.translate(interpolate(d1, 24, 0), 0);
                        scaleUniform(interpolate(d2, 0.5f, 1), 48, 144);
                        drawArc(0, 48, 96, 144, 90, 180, true, mPaints.paint1);
                        canvas.restore();

                        // right bottom
                        canvas.save();
                        canvas.translate(interpolate(d1, -24, 0), 0);
                        scaleUniform(interpolate(d2, 0.5f, 1), 96, 144);
                        drawArc(48, 48, 144, 144, -90, 180, true, mPaints.paint2);
                        canvas.restore();

                        // bottom middle
                        canvas.save();
                        canvas.scale(interpolate(d1, 0, 1), 1, 72, 0);
                        canvas.drawRect(48, interpolate(d2, 96, 48), 96, 144, mPaints.paint1);
                        canvas.drawRect(interpolate(d2, 48, 96), interpolate(d2, 96, 48), 96, 144, mPaints.paint2);
                        canvas.restore();
                    }

                    if (d < 1) {
                        // 7 rectangle
                        canvas.drawRect(
                                interpolate(d, 0, 48), interpolate(d, 0, 96),
                                interpolate(d, 72, 96), interpolate(d, 72, 144), mPaints.paint3);

                        // 7 parallelogram
                        path.reset();
                        path.moveTo(interpolate(d, 72, 48), interpolate(d, 0, 96));
                        path.lineTo(interpolate(d, 144, 96), interpolate(d, 0, 96));
                        path.lineTo(interpolate(d, 72, 96), 144);
                        path.lineTo(interpolate(d, 0, 48), 144);
                        canvas.drawPath(path, mPaints.paint2);

                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return 144;
                }
            });

            mGlyphMap.put("8_9", new Glyph() {
                @Override
                public void draw(float t) {
                    float d = decelerate5(progress(t, 0, 0.5f));
                    float d1 = decelerate5(progress(t, 0.5f, 1));

                    // 8
                    if (d < 1) {
                        // top
                        canvas.save();
                        canvas.translate(0, interpolate(d, 0, 48));
                        drawRoundRect(24, 0, 120, 48, 24, 24, mPaints.paint3);
                        canvas.restore();

                        // bottom middle
                        canvas.drawRect(interpolate(d, 48, 72) - 2, interpolate(d, 48, 0),
                                interpolate(d, 96, 72) + 2, 144, mPaints.paint1);

                        // left bottom
                        canvas.save();
                        scaleUniform(interpolate(d, 2f/3, 1), 0, 144);
                        drawArc(0, 0, 144, 144, 90, 180, true, mPaints.paint1);
                        canvas.restore();

                        // right bottom
                        canvas.save();
                        scaleUniform(interpolate(d, 2f/3, 1), 144, 144);
                        drawArc(0, 0, 144, 144, -90, 180, true, mPaints.paint2);
                        canvas.restore();
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
                        canvas.drawPath(path, mPaints.paint3);

                        // vanishing arc
                        drawArc(0, 0, 144, 144,
                                -180,
                                interpolate(d1, 180, 0), true, mPaints.paint1);

                        // primary arc
                        drawArc(0, 0, 144, 144, 0, 180, true, mPaints.paint2);

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
                    canvas.drawPath(path, mPaints.paint3);

                    canvas.restore();

                    // TODO: interpolate colors
                    //ctx.fillStyle = interpolateColors(d, mPaints.paint1, mPaints.paint3);
                    drawArc(0, 0, 144, 144,
                            0, interpolate(d, 0, -180), true, mPaints.paint3);

                    drawArc(0, 0, 144, 144, 0, 180, true, mPaints.paint2);

                    canvas.restore();
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return 144;
                }
            });

            mGlyphMap.put(" _1", new Glyph() {
                @Override
                public void draw(float t) {
                    float d1 = decelerate5(progress(t, 0, 0.5f));
                    float d2 = decelerate5(progress(t, 0.5f, 1));

                    // 1
                    scaleUniform(interpolate(d1, 0, 1), 0, 144);
                    canvas.drawRect(
                            interpolate(d2, 28, 0), interpolate(d2, 72, 0),
                            100, interpolate(d2, 144, 48), mPaints.paint2);

                    if (d2 > 0) {
                        canvas.drawRect(28, interpolate(d2, 144, 48), 100, 144, mPaints.paint3);
                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0, 0.5f)), 0, 100);
                }
            });

            mGlyphMap.put("1_ ", new Glyph() {
                @Override
                public void draw(float t) {
                    float d1 = decelerate5(progress(t, 0, 0.5f));
                    float d2 = decelerate5(progress(t, 0.5f, 1));

                    scaleUniform(interpolate(d2, 1, 0), 0, 144);
                    canvas.drawRect(
                            interpolate(d1, 0, 28), interpolate(d1, 0, 72),
                            100, interpolate(d1, 48, 144), mPaints.paint2);

                    if (d1 < 1) {
                        canvas.drawRect(28, interpolate(d1, 48, 144), 100, 144, mPaints.paint3);
                    }
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0.5f, 1)), 100, 0);
                }
            });

            mGlyphMap.put("5_0", new Glyph() {
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
                        canvas.drawRect(
                                80, interpolate(d, 0, 48),
                                interpolate(d, 128, 80), interpolate(d, 48, 144), mPaints.paint2);
                        canvas.restore();

                        // bottom rectangle
                        canvas.drawRect(0, 96, 80, 144, mPaints.paint2);
                    }

                    // middle rectangle
                    canvas.drawRect(
                            interpolate(d1, 0, 80), interpolate(d, 0, interpolate(d1, 48, 0)),
                            80, interpolate(d, 96, 144), mPaints.paint1);

                    scaleUniform(interpolate(d1, 2f/3, 1), 80, 144);

                    // half-circles
                    if (d1 > 0) {
                        canvas.save();
                        canvas.rotate(interpolate(d1, -180, 0), 72, 72);
                        drawArc(
                                0, 0,
                                144, 144, 90, 180, true, mPaints.paint2);
                        canvas.restore();
                    }

                    canvas.translate(interpolate(d1, 8, 0), 0);
                    drawArc(
                            0, 0,
                            144, 144, -90, 180, true, mPaints.paint3);

                    canvas.restore();
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return interpolate(decelerate5(progress(t, 0, 0.5f)), 128, 144);
                }
            });

            mGlyphMap.put("2_1", new Glyph() {
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
                        canvas.drawPath(path, mPaints.paint3);
                        canvas.restore();

                        canvas.save();
                        // TODO: interpolate colors
                        //ctx.fillStyle = interpolateColors(d1, mPaints.paint1, mPaints.paint2);
                        canvas.translate(interpolate(d, 108, 64), interpolate(d1, 0, 72));
                        drawArc(-36, 0, 36, 72, -90, 180, true, mPaints.paint2);
                        canvas.restore();

                        canvas.save();
                        canvas.translate(0, interpolate(d1, 0, 72));
                        canvas.drawRect(interpolate(d, 8, 28), 0, interpolate(d, 108, 100), 72, mPaints.paint1);
                        canvas.restore();

                        canvas.save();
                        canvas.translate(interpolate(d, 0, -44), 0);
                        canvas.drawRect(72, 72, 144, 144, mPaints.paint2);
                        canvas.restore();
                    } else {
                        // 1
                        canvas.save();
                        canvas.drawRect(interpolate(d2, 28, 0), interpolate(d2, 72, 0), 100, interpolate(d2, 144, 48), mPaints.paint2);

                        canvas.drawRect(28, interpolate(d2, 144, 48), 100, 144, mPaints.paint3);
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
                public void draw(float t) {
                    drawOval(0, 0, 48, 48, mPaints.paint2);
                    drawOval(0, 96, 48, 144, mPaints.paint3);
                }

                @Override
                public float getWidthAtProgress(float t) {
                    return 48;
                }
            });

            mGlyphMap.put(" ", new Glyph() {
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
