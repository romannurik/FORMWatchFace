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

import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.wearable.view.WearableListView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

public class ConfigComplicationsFragment extends Fragment {
    private static final String[] PREF_KEYS = {
            "pref_show_notification_count",
            "pref_show_date",
            "pref_show_seconds",
    };

    private static final int[] PREF_TITLE_IDS = {
            R.string.pref_show_notification_count_title,
            R.string.pref_show_date_title,
            R.string.pref_show_seconds_title
    };

    private View mRootView;
    private SharedPreferences mSharedPreferences;

    public ConfigComplicationsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
    }

    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.config_complications_fragment, container, false);
        rebuildComplicationsList();
        return mRootView;
    }

    private void rebuildComplicationsList() {
        ViewGroup complicationsContainer = (ViewGroup) mRootView.findViewById(R.id.complications_list);
        complicationsContainer.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getActivity());

        for (int i = 0; i < PREF_KEYS.length; i++) {
            final String prefKey = PREF_KEYS[i];

            ViewGroup itemContainer = (ViewGroup) inflater.inflate(R.layout.config_complications_item,
                    complicationsContainer, false);
            final CheckedTextView titleView = (CheckedTextView) itemContainer.findViewById(android.R.id.text1);
            final View checkmarkView = itemContainer.findViewById(R.id.checkmark);

            titleView.setText(PREF_TITLE_IDS[i]);
            boolean isPrefOn = mSharedPreferences.getBoolean(prefKey, false);

            checkmarkView.setVisibility(isPrefOn ? View.VISIBLE : View.INVISIBLE);
            titleView.setChecked(isPrefOn);

            itemContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean isPrefOn = mSharedPreferences.getBoolean(prefKey, false);
                    mSharedPreferences.edit().putBoolean(prefKey, !isPrefOn).apply();
                    rebuildComplicationsList();
                }
            });

            complicationsContainer.addView(itemContainer);
        }
    }

    public void update() {
        if (mRootView != null) {
            rebuildComplicationsList();
        }
    }
}
