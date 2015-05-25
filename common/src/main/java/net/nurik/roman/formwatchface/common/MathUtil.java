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

import android.graphics.Rect;
import android.util.FloatMath;

public class MathUtil {
    private MathUtil() {
    }

    public static int maxDistanceToCorner(Rect r, int x, int y) {
        return maxDistanceToCorner(r.left, r.top, r.right, r.bottom, x, y);
    }

    public static int maxDistanceToCorner(float l, float t, float r, float b, float x, float y) {
        int max = 0;
        max = Math.max((int) distance(l, t, x, y), max);
        max = Math.max((int) distance(r, t, x, y), max);
        max = Math.max((int) distance(l, b, x, y), max);
        max = Math.max((int) distance(r, b, x, y), max);
        return max;
    }

    public static float distance(float x1, float y1, float x2, float y2) {
        return FloatMath.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
    }

    public static float interpolate(float f, float min, float max) {
        return min + (max - min) * f;
    }

    public static float accelerate5(float t) {
        return FloatMath.pow(t, 5);
    }

    public static float decelerate5(float t) {
        return 1 - FloatMath.pow(1 - t, 5);
    }

    public static float decelerate3(float t) {
        return 1 - FloatMath.pow(1 - t, 3);
    }

    public static float decelerate2(float t) {
        return 1 - FloatMath.pow(1 - t, 2);
    }

    public static float progress(float v, float min, float max) {
        return constrain((v - min) / (max - min), 0, 1);
    }

    public static float constrain(float val, float min, float max) {
        if (val < min) {
            return min;
        } else if (val > max) {
            return max;
        } else {
            return val;
        }
    }
}
