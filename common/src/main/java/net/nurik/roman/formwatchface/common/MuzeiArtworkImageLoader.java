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

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.util.Pair;

import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.MuzeiContract;

import java.io.FileNotFoundException;

/**
 * AsyncTaskLoader which provides access to the current Muzei artwork image. It also
 * registers a ContentObserver to ensure the image stays up to date
 */
public class MuzeiArtworkImageLoader extends AsyncTaskLoader<MuzeiArtworkImageLoader.LoadedArtwork> {
    private ContentObserver mContentObserver;

    public MuzeiArtworkImageLoader(Context context) {
        super(context);
    }

    public static boolean hasMuzeiArtwork(Context context) {
        Artwork currentArtwork = MuzeiContract.Artwork.getCurrentArtwork(context);
        return currentArtwork != null;
    }

    @Override
    protected void onStartLoading() {
        if (mContentObserver == null) {
            mContentObserver = new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange) {
                    onContentChanged();
                }
            };
            getContext().getContentResolver().registerContentObserver(
                    MuzeiContract.Artwork.CONTENT_URI, true, mContentObserver);
        }
        forceLoad();
    }

    @Override
    public MuzeiArtworkImageLoader.LoadedArtwork loadInBackground() {
        try {
            Bitmap bitmap = MuzeiContract.Artwork.getCurrentArtworkBitmap(getContext());
            if (bitmap == null) {
                return null;
            }
            Pair<Integer, Integer> p = extractColors(bitmap);
            return new LoadedArtwork(bitmap, p.first, p.second);
        } catch (FileNotFoundException e) {
            Log.e(MuzeiArtworkImageLoader.class.getSimpleName(), "Error getting artwork image", e);
        }
        return null;
    }

    private Pair<Integer, Integer> extractColors(Bitmap bitmap) {
        Palette palette = Palette.generate(bitmap, 16);
        int midColor = palette.getVibrantColor(
                palette.getDarkVibrantColor(
                        palette.getMutedColor(
                                palette.getDarkMutedColor(Color.GRAY))));
        int lightColor = palette.getLightMutedColor(
                palette.getLightVibrantColor(
                        palette.getMutedColor(Color.BLACK)));
        if (lightColor == Color.BLACK) {
            lightColor = lighten(midColor, 0.2f);
        }
        return new Pair<>(lightColor, midColor);
    }

    private static int lighten(int color, float amount) {
        float hsv[] = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.max(0f, Math.min(1f, hsv[2] + amount));
        return Color.HSVToColor(hsv);
    }

    @Override
    protected void onReset() {
        if (mContentObserver != null) {
            getContext().getContentResolver().unregisterContentObserver(mContentObserver);
        }
    }

    public static class LoadedArtwork {
        public Bitmap bitmap;
        public int color1;
        public int color2;

        public LoadedArtwork(Bitmap bitmap, int color1, int color2) {
            this.bitmap = bitmap;
            this.color1 = color1;
            this.color2 = color2;
        }
    }
}
