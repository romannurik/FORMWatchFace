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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.view.WearableListView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import net.nurik.roman.formwatchface.common.ChangeConfigIntentService;
import net.nurik.roman.formwatchface.common.MuzeiArtworkImageLoader;
import net.nurik.roman.formwatchface.common.Themes;

public class WearableWatchFaceConfigActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.watch_face_config_activity);

        WearableListView listView = (WearableListView) findViewById(R.id.wearable_list);

        final boolean hasMuzeiArtwork = MuzeiArtworkImageLoader.hasMuzeiArtwork(this);

        listView.setAdapter(new WearableListView.Adapter() {
            private static final int TYPE_NORMAL = 1;
            private static final int TYPE_MUZEI = 2;

            @Override
            public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return new ItemViewHolder(LayoutInflater.from(WearableWatchFaceConfigActivity.this)
                        .inflate(R.layout.watch_face_config_color_item, parent, false));
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
                startService(new Intent(WearableWatchFaceConfigActivity.this,
                        ChangeConfigIntentService.class)
                        .putExtra(ChangeConfigIntentService.EXTRA_THEME, theme));
                finish();
            }

            @Override
            public void onTopEmptyRegionClick() {
            }
        });

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(
                WearableWatchFaceConfigActivity.this);

        int startingIndex = 0;
        String theme = sp.getString("theme", null);
        if (theme != null) {
            for (int i = 0; i < Themes.THEMES.length; i++) {
                if (Themes.THEMES[i].id.equals(theme)) {
                    startingIndex = i;
                    break;
                }
            }
        }

        listView.scrollToPosition(startingIndex);
    }

    public static class ItemViewHolder extends WearableListView.ViewHolder {
        private ImageView circleView;

        public ItemViewHolder(View itemView) {
            super(itemView);
            circleView = (ImageView) itemView.findViewById(R.id.circle);
        }
    }
}
