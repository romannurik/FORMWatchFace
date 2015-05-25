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

package net.nurik.roman.formwatchface.common.config;

import net.nurik.roman.formwatchface.common.R;

import java.util.HashMap;
import java.util.Map;

public class Themes {
    private Themes() {
    }

    private static Map<String, Theme> THEMES_BY_ID = new HashMap<>();

    public static final Theme[] THEMES = new Theme[] {
            new Theme("blue", R.color.form_blue_light, R.color.form_blue_mid, R.color.form_blue_dark),
            new Theme("teal", R.color.form_teal_light, R.color.form_teal_mid, R.color.form_teal_dark),
            new Theme("red", R.color.form_red_light, R.color.form_red_mid, R.color.form_red_dark),
            new Theme("yellow", R.color.form_yellow_light, R.color.form_yellow_mid, R.color.form_yellow_dark),
            new Theme("gray", R.color.form_gray_light, R.color.form_gray_mid, R.color.form_gray_dark),
    };

    public static Theme MUZEI_THEME = new Theme("muzei", 0, 0, 0);

    public static final Theme DEFAULT_THEME = THEMES[0];

    static {
        for (Theme theme : THEMES) {
            THEMES_BY_ID.put(theme.id, theme);
        }
    }

    public static Theme getThemeById(String id) {
        if ("muzei".equals(id)) {
            return MUZEI_THEME;
        }

        return THEMES_BY_ID.get(id);
    }

    public static class Theme {
        public int lightRes;
        public int midRes;
        public int darkRes;
        public String id;

        private Theme(String id, int lightRes, int midRes, int darkRes) {
            this.id = id;
            this.lightRes = lightRes;
            this.midRes = midRes;
            this.darkRes = darkRes;
        }
    }
}
