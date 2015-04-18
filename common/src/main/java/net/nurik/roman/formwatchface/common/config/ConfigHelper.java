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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ConfigHelper {
    private static final String TAG = "ConfigHelper";

    public static final String KEY_THEME = "pref_theme";
    public static final String KEY_SHOW_NOTIFICATION_COUNT = "pref_show_notification_count";
    public static final String KEY_SHOW_DATE = "pref_show_date";
    public static final String KEY_SHOW_SECONDS = "pref_show_seconds";

    private static final Set<String> STRING_CONFIG_KEYS = new HashSet<>(Arrays.asList(
            KEY_THEME
    ));

    private static final Set<String> BOOLEAN_CONFIG_KEYS = new HashSet<>(Arrays.asList(
            KEY_SHOW_NOTIFICATION_COUNT,
            KEY_SHOW_DATE,
            KEY_SHOW_SECONDS
    ));

    private final Context mContext;
    private GoogleApiClient mGoogleApiClient;

    public ConfigHelper(Context context) {
        mContext = context;
    }

    public boolean connect() {
        if (mGoogleApiClient != null) {
            return true;
        }

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

    public String getLocalNodeId() {
        NodeApi.GetLocalNodeResult localNodeResult = Wearable.NodeApi.getLocalNode(mGoogleApiClient).await();
        if (!localNodeResult.getStatus().isSuccess()) {
            Log.e(TAG, "Error getting local node info: " + localNodeResult.getStatus().getStatusMessage());
        }

        return localNodeResult.getNode().getId();
    }

    public void disconnect() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
            mGoogleApiClient = null;
        }
    }

    public void putConfigSharedPrefsToDataLayer() {
        DataMap newDataMap = readConfigDataMapFromSharedPrefs();
        if (newDataMap == null) {
            return;
        }

        DataMap currentDataMap = readConfigDataMapFromDataLayer();
        boolean dirty = true;
        if (currentDataMap != null) {
            dirty = false;
            for (String key : newDataMap.keySet()) {
                Object newValue = newDataMap.get(key);
                if (newValue != null && !newValue.equals(currentDataMap.get(key))) {
                    dirty = true;
                    break;
                }
            }
        }

        if (dirty) {
            putConfigDataMapToDataLayer(newDataMap);
        }

        disconnect();
    }

    public void readConfigSharedPrefsFromDataLayer() {
        // Read all DataItems
        DataMap configDataMap = readConfigDataMapFromDataLayer();
        if (configDataMap != null) {
            putConfigDataMapToSharedPrefs(configDataMap);
        }

        disconnect();
    }

    private void putConfigDataMapToDataLayer(DataMap configDataMap) {
        PutDataMapRequest dataMapRequest = PutDataMapRequest.create("/config");
        dataMapRequest.getDataMap().putDataMap("config", configDataMap);

        // NOTE: Need to use timestamps because there's a separate data item for the companion
        // and the wearable
        // TODO: find a better way to get cross-device timestamps
        dataMapRequest.getDataMap().putLong("timestamp", Calendar.getInstance().getTimeInMillis());
        Wearable.DataApi.putDataItem(mGoogleApiClient, dataMapRequest.asPutDataRequest()).await();
    }

    // Assumes connect() has been called
    private DataMap readConfigDataMapFromDataLayer() {
        long latestTimestamp = 0;
        DataItemBuffer dataItemBuffer = Wearable.DataApi.getDataItems(mGoogleApiClient).await();
        if (!dataItemBuffer.getStatus().isSuccess()) {
            Log.e(TAG, "Error getting all data items: " + dataItemBuffer.getStatus().getStatusMessage());
        }

        DataMap configDataMap = null;

        Iterator<DataItem> dataItemIterator = dataItemBuffer.singleRefIterator();
        while (dataItemIterator.hasNext()) {
            DataItem dataItem = dataItemIterator.next();
            if (!dataItem.getUri().getPath().equals("/config")) {
                continue;
            }

            DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
            DataMap dataMap = dataMapItem.getDataMap();
            long timestamp = dataMap.getLong("timestamp");
            if (timestamp >= latestTimestamp) {
                configDataMap = dataMapItem.getDataMap().getDataMap("config");
                latestTimestamp = timestamp;
            }
        }

        dataItemBuffer.release();
        return configDataMap;
    }

    public static boolean isConfigPrefKey(String key) {
        return BOOLEAN_CONFIG_KEYS.contains(key) || STRING_CONFIG_KEYS.contains(key);
    }

    private DataMap readConfigDataMapFromSharedPrefs() {
        DataMap dataMap = new DataMap();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);

        for (String key : BOOLEAN_CONFIG_KEYS) {
            if (sp.contains(key)) {
                dataMap.putBoolean(key, sp.getBoolean(key, false));
            }
        }

        for (String key : STRING_CONFIG_KEYS) {
            String value = sp.getString(key, null);
            if (value != null) {
                dataMap.putString(key, value);
            }
        }

        return dataMap;
    }

    private void putConfigDataMapToSharedPrefs(DataMap dataMap) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = sp.edit();

        for (String key : BOOLEAN_CONFIG_KEYS) {
            if (dataMap.containsKey(key)) {
                boolean value = dataMap.getBoolean(key);
                if (sp.getBoolean(key, false) != value) {
                    editor.putBoolean(key, value);
                }
            }
        }

        for (String key : STRING_CONFIG_KEYS) {
            if (dataMap.containsKey(key)) {
                String value = dataMap.getString(key);
                if (!TextUtils.equals(sp.getString(key, null), value)) {
                    editor.putString(key, dataMap.getString(key));
                }
            }
        }

        editor.apply();
    }
}
