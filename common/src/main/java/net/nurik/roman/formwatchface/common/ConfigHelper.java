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

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.Calendar;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class ConfigHelper {
    private static final String TAG = "ConfigHelper";
    private final Context mContext;
    private GoogleApiClient mGoogleApiClient;

    public ConfigHelper(Context context) {
        mContext = context;
    }

    private boolean connect() {
        if (ConnectionResult.SUCCESS != GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext)
                || Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return false;
        }

        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addApi(Wearable.API)
                .build();
        ConnectionResult connectionResult = mGoogleApiClient.blockingConnect(5, TimeUnit.SECONDS);
        if (!connectionResult.isSuccess()) {
            Log.e(TAG, "Failed to connect to GoogleApiClient: " + connectionResult.getErrorCode());
            mGoogleApiClient = null;
            return false;
        }

        return true;
    }

    private void disconnect() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
            mGoogleApiClient = null;
        }
    }

    public void blockingPutTheme(String theme) {
        if (connect()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create("/theme");
            dataMapRequest.getDataMap().putString("theme", theme);
            // NOTE: Need to use timestamps because there's a separate data item for the companion
            // and the wearable
            // TODO: find a better way to get cross-device timestamps
            dataMapRequest.getDataMap().putLong("timestamp", Calendar.getInstance().getTimeInMillis());
            Wearable.DataApi.putDataItem(mGoogleApiClient, dataMapRequest.asPutDataRequest()).await();
            disconnect();
        }
    }

    public String blockingGetTheme() {
        String theme = Themes.DEFAULT_THEME.id;

        long latestTimestamp = 0;
        if (connect()) {
            // Read all DataItems
            DataItemBuffer dataItemBuffer = Wearable.DataApi.getDataItems(mGoogleApiClient).await();
            if (!dataItemBuffer.getStatus().isSuccess()) {
                Log.e(TAG, "Error getting all data items: " + dataItemBuffer.getStatus().getStatusMessage());
            }

            Iterator<DataItem> dataItemIterator = dataItemBuffer.singleRefIterator();
            while (dataItemIterator.hasNext()) {
                DataItem dataItem = dataItemIterator.next();
                if (!dataItem.getUri().getPath().equals("/theme")) {
                    Log.w(TAG, "Ignoring data item " + dataItem.getUri().getPath());
                    continue;
                }

                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap dataMap = dataMapItem.getDataMap();
                long timestamp = dataMap.getLong("timestamp");
                if (timestamp >= latestTimestamp) {
                    theme = dataMapItem.getDataMap().getString("theme", Themes.DEFAULT_THEME.id);
                    latestTimestamp = timestamp;
                }
            }

            dataItemBuffer.close();

            disconnect();
        }

        return theme;
    }
}
