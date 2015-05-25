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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.LoaderManager;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import net.nurik.roman.formwatchface.common.FormClockView;
import net.nurik.roman.formwatchface.common.MathUtil;
import net.nurik.roman.formwatchface.common.MuzeiArtworkImageLoader;
import net.nurik.roman.formwatchface.common.config.ConfigHelper;
import net.nurik.roman.formwatchface.common.config.Themes;
import net.nurik.roman.formwatchface.common.config.UpdateConfigIntentService;
import net.nurik.roman.formwatchface.ui.ScrimInsetsFrameLayout;
import net.nurik.roman.formwatchface.ui.SimplePagerHelper;
import net.nurik.roman.formwatchface.ui.SlidingTabLayout;

import java.util.ArrayList;

import static net.nurik.roman.formwatchface.common.MuzeiArtworkImageLoader.LoadedArtwork;
import static net.nurik.roman.formwatchface.common.config.Themes.MUZEI_THEME;
import static net.nurik.roman.formwatchface.common.config.Themes.Theme;

public class CompanionWatchFaceConfigActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<LoadedArtwork> {

    private static final String TAG = "CompanionWatchFaceConfigActivity";

    private static final int LOADER_MUZEI_ARTWORK = 1;

    private SharedPreferences mSharedPreferences;

    private ViewGroup mThemeItemContainer;
    private ArrayList<ThemeUiHolder> mThemeUiHolders = new ArrayList<>();
    private ThemeUiHolder mMuzeiThemeUiHolder;

    private ConfigComplicationsFragment mConfigComplicationsFragment;
    private ViewGroup mMainClockContainerView;
    private FormClockView mMainClockView;
    private ViewGroup mAnimateClockContainerView;
    private FormClockView mAnimateClockView;
    private Animator mCurrentRevealAnimator;
    private Theme mAnimatingTheme;

    private LoadedArtwork mMuzeiLoadedArtwork;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(R.layout.config_activity);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Set up general chrome
        ImageButton doneButton = (ImageButton) findViewById(R.id.done_button);
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ScrimInsetsFrameLayout scrimInsetsFrameLayout = (ScrimInsetsFrameLayout)
                findViewById(R.id.scrim_insets_frame_layout);
        scrimInsetsFrameLayout.setOnInsetsCallback(new ScrimInsetsFrameLayout.OnInsetsCallback() {
            @Override
            public void onInsetsChanged(Rect insets) {
                findViewById(R.id.chrome_container).setPadding(0, insets.top, 0, 0);
            }
        });

        // Set up theme list
        mMainClockContainerView = (ViewGroup) ((ViewGroup) findViewById(R.id.clock_container)).getChildAt(0);
        mMainClockView = (FormClockView) mMainClockContainerView.findViewById(R.id.clock);

        mAnimateClockContainerView = (ViewGroup) ((ViewGroup) findViewById(R.id.clock_container)).getChildAt(1);
        mAnimateClockView = (FormClockView) mAnimateClockContainerView.findViewById(R.id.clock);

        mAnimateClockContainerView.setVisibility(View.INVISIBLE);

        setupThemeList();
        String themeId = mSharedPreferences.getString("theme", Themes.DEFAULT_THEME.id);
        updateUIToSelectedTheme(themeId, false);

        registerSharedPrefsListener();

        // Set up complications config fragment
        mConfigComplicationsFragment = (ConfigComplicationsFragment) getFragmentManager()
                .findFragmentById(R.id.config_complications_container);
        if (mConfigComplicationsFragment == null) {
            mConfigComplicationsFragment = new ConfigComplicationsFragment();
            getFragmentManager()
                    .beginTransaction()
                    .add(R.id.config_complications_container, mConfigComplicationsFragment)
                    .commit();
        }

        // Set up tabs/pager
        final ViewPager pager = (ViewPager) findViewById(R.id.view_pager);
        pager.setPageMargin((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                getResources().getDisplayMetrics()));

        SimplePagerHelper helper = new SimplePagerHelper(this, pager);
        helper.addPage(R.string.title_theme, R.id.config_theme_container);
        helper.addPage(R.string.title_complications, R.id.config_complications_container);

        SlidingTabLayout slidingTabLayout = (SlidingTabLayout) findViewById(R.id.tabs);
        slidingTabLayout.setCustomTabView(R.layout.tab_indicator, android.R.id.text1);

        Resources res = getResources();
        slidingTabLayout.setSelectedIndicatorColors(res.getColor(R.color.tab_selected_strip));
        slidingTabLayout.setViewPager(pager);

        slidingTabLayout.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                float translationX = -pager.getWidth();
                if (position == 0) {
                    translationX = -positionOffsetPixels;
                }
                mMainClockView.setTranslationX(translationX);
                mAnimateClockView.setTranslationX(translationX);
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
                        CompanionWatchFaceConfigActivity.this);

                if (mConfigComplicationsFragment != null) {
                    mConfigComplicationsFragment.update();
                }

                if (ConfigHelper.KEY_THEME.equals(key)) {
                    String themeId = mSharedPreferences
                            .getString(ConfigHelper.KEY_THEME, Themes.DEFAULT_THEME.id);
                    updateUIToSelectedTheme(themeId, true);
                }
            }
        }
    };

    private void setupThemeList() {
        mThemeUiHolders.clear();
        mThemeItemContainer = (ViewGroup) findViewById(R.id.theme_list);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final Theme theme : Themes.THEMES) {
            ThemeUiHolder holder = new ThemeUiHolder();

            holder.theme = theme;
            holder.container = inflater.inflate(R.layout.config_theme_item, mThemeItemContainer, false);
            holder.button = (ImageButton) holder.container.findViewById(R.id.button);

            LayerDrawable bgDrawable = (LayerDrawable)
                    getResources().getDrawable(R.drawable.theme_item_bg).mutate();

            GradientDrawable gd = (GradientDrawable) bgDrawable.findDrawableByLayerId(R.id.color);
            gd.setColor(getResources().getColor(theme.midRes));
            holder.button.setBackground(bgDrawable);

            holder.button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateAndPersistTheme(theme);
                }
            });

            mThemeUiHolders.add(holder);
            mThemeItemContainer.addView(holder.container);
        }

        loadMuzei();
    }

    private void loadMuzei() {
        if (!MuzeiArtworkImageLoader.hasMuzeiArtwork(this)) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        ThemeUiHolder holder = new ThemeUiHolder();

        final Theme theme = Themes.MUZEI_THEME;
        holder.theme = theme;
        holder.container = inflater.inflate(R.layout.config_theme_item, mThemeItemContainer, false);
        holder.button = (ImageButton) holder.container.findViewById(R.id.button);

        LayerDrawable bgDrawable = (LayerDrawable)
                getResources().getDrawable(R.drawable.theme_muzei_item_bg).mutate();
        holder.button.setBackground(bgDrawable);

        holder.button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateAndPersistTheme(theme);
            }
        });

        mThemeUiHolders.add(holder);
        mThemeItemContainer.addView(holder.container);
        mMuzeiThemeUiHolder = holder;

        // begin load using fragments
        getLoaderManager().initLoader(LOADER_MUZEI_ARTWORK, null, this);
    }

    private void updateAndPersistTheme(Theme theme) {
        mSharedPreferences.edit().putString(ConfigHelper.KEY_THEME, theme.id).apply();
    }

    @Override
    public Loader<LoadedArtwork> onCreateLoader(int id, Bundle args) {
        return new MuzeiArtworkImageLoader(this);
    }

    @Override
    public void onLoadFinished(Loader<LoadedArtwork> loader, LoadedArtwork data) {
        mMuzeiLoadedArtwork = data;
        if (mMuzeiThemeUiHolder.selected) {
            updatePreviewView(MUZEI_THEME, mMainClockContainerView, mMainClockView);
        }
    }

    @Override
    public void onLoaderReset(Loader<LoadedArtwork> loader) {
        mMuzeiLoadedArtwork = null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void updateUIToSelectedTheme(final String themeId, final boolean animate) {
        for (final ThemeUiHolder holder : mThemeUiHolders) {
            boolean selected = holder.theme.id.equals(themeId);

            holder.button.setSelected(selected);

            if (holder.selected != selected && selected) {
                if (mCurrentRevealAnimator != null) {
                    mCurrentRevealAnimator.end();
                    updatePreviewView(mAnimatingTheme, mMainClockContainerView, mMainClockView);
                }

                if (animate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mAnimatingTheme = holder.theme;
                    updatePreviewView(mAnimatingTheme, mAnimateClockContainerView, mAnimateClockView);

                    Rect buttonRect = new Rect();
                    Rect clockContainerRect = new Rect();
                    holder.button.getGlobalVisibleRect(buttonRect);
                    mMainClockContainerView.getGlobalVisibleRect(clockContainerRect);

                    int cx = buttonRect.centerX() - clockContainerRect.left;
                    int cy = buttonRect.centerY() - clockContainerRect.top;
                    clockContainerRect.offsetTo(0, 0);

                    mCurrentRevealAnimator = ViewAnimationUtils.createCircularReveal(
                            mAnimateClockContainerView, cx, cy, 0,
                            MathUtil.maxDistanceToCorner(clockContainerRect, cx, cy));
                    mAnimateClockContainerView.setVisibility(View.VISIBLE);
                    mCurrentRevealAnimator.setDuration(300);
                    mCurrentRevealAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (mCurrentRevealAnimator == animation) {
                                mAnimateClockContainerView.setVisibility(View.INVISIBLE);
                                updatePreviewView(holder.theme, mMainClockContainerView, mMainClockView);
                            }
                        }
                    });

                    mAnimateClockView.postInvalidateOnAnimation();
                    mCurrentRevealAnimator.start();
                } else {
                    updatePreviewView(holder.theme, mMainClockContainerView, mMainClockView);
                }
            }

            holder.selected = selected;
        }
    }

    private void updatePreviewView(Theme theme, ViewGroup clockContainerView, FormClockView clockView) {
        if (theme == Themes.MUZEI_THEME) {
            if (mMuzeiLoadedArtwork != null) {
                ((ImageView) clockContainerView.findViewById(R.id.background_image))
                        .setImageBitmap(mMuzeiLoadedArtwork.bitmap);
                clockView.setColors(
                        mMuzeiLoadedArtwork.color1,
                        mMuzeiLoadedArtwork.color2,
                        Color.WHITE);
            }
            clockContainerView.setBackgroundColor(Color.BLACK);
        } else {
            ((ImageView) clockContainerView.findViewById(R.id.background_image))
                    .setImageDrawable(null);
            final Resources res = getResources();
            clockView.setColors(
                    res.getColor(theme.lightRes),
                    res.getColor(theme.midRes),
                    Color.WHITE);
            clockContainerView.setBackgroundColor(
                    res.getColor(theme.darkRes));
        }
    }

    private static class ThemeUiHolder {
        Theme theme;
        View container;
        ImageButton button;
        boolean selected;
    }
}
