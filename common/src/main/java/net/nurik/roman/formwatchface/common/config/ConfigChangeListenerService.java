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

import android.net.Uri;
import android.text.TextUtils;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;

public class ConfigChangeListenerService extends WearableListenerService {
    @Override
    public void onDataChanged(final DataEventBuffer dataEvents) {
        ConfigHelper configHelper = new ConfigHelper(this);
        if (!configHelper.connect()) {
            return;
        }

        String localNodeId = configHelper.getLocalNodeId();

        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                continue;
            }

            Uri uri = dataEvent.getDataItem().getUri();
            if (!TextUtils.equals(uri.getHost(), localNodeId) &&
                    uri.getPath().equals("/config")) {
                configHelper.readConfigSharedPrefsFromDataLayer();
            }
        }

        configHelper.disconnect();
    }
}
