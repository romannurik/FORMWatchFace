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

package net.nurik.roman.formwatchface.ui;

import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * A helper class for creating swipeable tabs without the use of {@link android.app.ActionBar} APIs.
 */
public class SimplePagerHelper {
    private Context mContext;
    private ViewPager mPager;
    private List<Integer> mPageContentIds = new ArrayList<>();
    private List<CharSequence> mPageLabels = new ArrayList<>();

    public SimplePagerHelper(Context context, ViewPager pager) {
        mContext = context;
        mPager = pager;
        pager.setAdapter(mAdapter);
    }

    public void addPage(int labelResId, int contentViewId) {
        addPage(mContext.getString(labelResId), contentViewId);
    }

    public void addPage(CharSequence label, int contentViewId) {
        mPageLabels.add(label);
        mPageContentIds.add(contentViewId);
        mAdapter.notifyDataSetChanged();
    }

    private PagerAdapter mAdapter = new PagerAdapter() {
        @Override
        public int getCount() {
            return mPageContentIds.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object o) {
            return view == o;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            return mPager.findViewById(mPageContentIds.get(position));
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mPageLabels.get(position);
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            // No-op
        }
    };
}
