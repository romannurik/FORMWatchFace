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

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.FragmentGridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;

import net.nurik.roman.formwatchface.common.config.ConfigHelper;
import net.nurik.roman.formwatchface.common.config.Themes;
import net.nurik.roman.formwatchface.common.config.UpdateConfigIntentService;

public class WearableWatchFaceConfigActivity extends Activity {
    private GridViewPager mGridViewPager;
    private DotsPageIndicator mPagerIndicator;
    private View mContainerView;

    private static float ROUND_FACTOR = 0.146467f; //(1 - sqrt(2)/2)/2 (from BoxInsetLayout)

    private Rect mInsetsRect = new Rect();
    private boolean mIsRound;

    private SharedPreferences mSharedPreferences;
    private ConfigComplicationsFragment mConfigComplicationsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.config_watch_face_activity);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        registerSharedPrefsListener();

        mGridViewPager = (GridViewPager) findViewById(R.id.pager);
        mGridViewPager.setAdapter(new FragmentGridPagerAdapter(getFragmentManager()) {
            @Override
            public Fragment getFragment(int row, int column) {
                switch (column) {
                    case 0:
                        return new ConfigThemeFragment();
                    case 1:
                        mConfigComplicationsFragment = new ConfigComplicationsFragment();
                        return mConfigComplicationsFragment;
                }

                return null;
            }

            @Override
            public int getRowCount() {
                return 1;
            }

            @Override
            public int getColumnCount(int row) {
                return 2;
            }
        });

        mPagerIndicator = (DotsPageIndicator) findViewById(R.id.pager_indicator);
        mPagerIndicator.setPager(mGridViewPager);

        mContainerView = findViewById(R.id.container);
        mContainerView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                mIsRound = insets.isRound();
                mInsetsRect.set(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom());

                mPagerIndicator.setTranslationY(-mInsetsRect.bottom);

                if (mIsRound) {
                    mInsetsRect.left = Math.max((int) (v.getWidth() * ROUND_FACTOR), mInsetsRect.left);
                    mInsetsRect.right = Math.max((int) (v.getWidth() * ROUND_FACTOR), mInsetsRect.right);
                    mInsetsRect.top = Math.max((int) (v.getWidth() * ROUND_FACTOR), mInsetsRect.top);
                    mInsetsRect.bottom = Math.max((int) (v.getWidth() * ROUND_FACTOR), mInsetsRect.bottom);
                }

                return mContainerView.onApplyWindowInsets(insets);
            }
        });

        ViewTreeObserver vto = mContainerView.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mContainerView.requestApplyInsets();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterSharedPrefsListener();
    }

    private void registerSharedPrefsListener() {
        mSharedPreferences.registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
    }

    private void unregisterSharedPrefsListener() {
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (ConfigHelper.isConfigPrefKey(key)) {
                UpdateConfigIntentService.startConfigChangeService(
                        WearableWatchFaceConfigActivity.this);

                if (mConfigComplicationsFragment != null) {
                    mConfigComplicationsFragment.update();
                }
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
//        if (isFinishing()) {
//            UpdateConfigIntentService.startConfigChangeService(this);
//        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mContainerView.requestApplyInsets();
    }
}
