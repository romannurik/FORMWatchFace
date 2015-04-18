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
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.wearable.view.WearableListView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import net.nurik.roman.formwatchface.common.MuzeiArtworkImageLoader;
import net.nurik.roman.formwatchface.common.config.ConfigHelper;
import net.nurik.roman.formwatchface.common.config.Themes;
import net.nurik.roman.formwatchface.common.config.UpdateConfigIntentService;

public class ConfigThemeFragment extends Fragment {
    private View mRootView;
    private SharedPreferences mSharedPreferences;

    public ConfigThemeFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
    }

    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.config_theme_fragment, container, false);

        WearableListView listView = (WearableListView) mRootView.findViewById(R.id.wearable_list);
        listView.setGreedyTouchMode(true);

        final boolean hasMuzeiArtwork = MuzeiArtworkImageLoader.hasMuzeiArtwork(getActivity());

        listView.setAdapter(new WearableListView.Adapter() {
            private static final int TYPE_NORMAL = 1;
            private static final int TYPE_MUZEI = 2;

            @Override
            public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return new ItemViewHolder(inflater
                        .inflate(R.layout.config_theme_color_item, parent, false));
            }

            @Override
            public int getItemViewType(int position) {
                return (position >= Themes.THEMES.length) ? TYPE_MUZEI : TYPE_NORMAL;
            }

            @Override
            public void onBindViewHolder(WearableListView.ViewHolder holder, int position) {
                ItemViewHolder itemHolder = (ItemViewHolder) holder;
                Themes.Theme theme;
                if (getItemViewType(position) == TYPE_MUZEI) {
                    theme = Themes.MUZEI_THEME;
                    itemHolder.circleView.setImageResource(R.drawable.muzei_icon);
                } else {
                    theme = Themes.THEMES[position];
                    ((GradientDrawable) itemHolder.circleView.getDrawable()).setColor(
                            getResources().getColor(theme.darkRes));
                }
                holder.itemView.setTag(theme.id);
            }

            @Override
            public int getItemCount() {
                return Themes.THEMES.length + (hasMuzeiArtwork ? 1 : 0);
            }
        });

        listView.setClickListener(new WearableListView.ClickListener() {
            @Override
            public void onClick(WearableListView.ViewHolder viewHolder) {
                String theme = viewHolder.itemView.getTag().toString();
                mSharedPreferences.edit().putString(ConfigHelper.KEY_THEME, theme).apply();
                getActivity().finish();
            }

            @Override
            public void onTopEmptyRegionClick() {
            }
        });

        int startingIndex = 0;
        String theme = mSharedPreferences.getString(ConfigHelper.KEY_THEME, null);
        if (theme != null) {
            for (int i = 0; i < Themes.THEMES.length; i++) {
                if (Themes.THEMES[i].id.equals(theme)) {
                    startingIndex = i;
                    break;
                }
            }
        }

        listView.scrollToPosition(startingIndex);
        return mRootView;
    }

    public static class ItemViewHolder extends WearableListView.ViewHolder {
        private ImageView circleView;

        public ItemViewHolder(View itemView) {
            super(itemView);
            circleView = (ImageView) itemView.findViewById(R.id.circle);
        }
    }
}
