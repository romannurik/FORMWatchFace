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

import android.content.Context;
import android.support.wearable.view.WearableListView;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class ColorItemLayout extends LinearLayout implements WearableListView.OnCenterProximityListener {
    private ImageView mCircleView;

    public ColorItemLayout(Context context) {
        this(context, null);
    }

    public ColorItemLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorItemLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    // Get references to the icon and text in the item layout definition
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mCircleView = (ImageView) findViewById(R.id.circle);
    }

    @Override
    public void onCenterPosition(boolean animate) {
        mCircleView.setScaleX(1f);
        mCircleView.setScaleY(1f);
        mCircleView.setAlpha(1f);
    }

    @Override
    public void onNonCenterPosition(boolean animate) {
        mCircleView.setScaleX(0.7f);
        mCircleView.setScaleY(0.7f);
        mCircleView.setAlpha(0.5f);
    }
}