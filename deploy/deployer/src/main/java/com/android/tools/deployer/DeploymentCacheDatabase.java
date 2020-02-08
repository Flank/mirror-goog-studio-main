/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.deployer;

import com.android.tools.deployer.model.Apk;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.List;

/**
 * In memory cache of APK info of a given device.
 *
 * <p>This is designed to be a quick look up for optimization purpose. The entries can be incorrect
 * or out-of-sync for reasons such as (and not limited to):
 *
 * <p>-Cache evicted due to memory limitation
 *
 * <p>-Installing APKs outside of studio
 *
 * <p>-Installing APKs in another workstation -Re-opening Studio
 */
public class DeploymentCacheDatabase {

    public static final int DEFAULT_SIZE = 100; // max number of cache entries.

    // Key = serial + ":" + appId.
    private final Cache<String, Entry> db;

    public class Entry {
        private final List<Apk> apks;
        private final OverlayId oid;

        public List<Apk> getApks() {
            return apks;
        }

        public OverlayId getOverlayId() {
            return oid;
        }

        private Entry(List<Apk> apks, OverlayId overlayId) {
            this.apks = apks;
            this.oid = overlayId;
        }
    }

    public DeploymentCacheDatabase() {
        this(DEFAULT_SIZE);
    }

    public DeploymentCacheDatabase(int size) {
        db = CacheBuilder.newBuilder().maximumSize(size).build();
    }

    public Entry get(String serial, String appId) {
        String key = String.format("%s:%s", serial, appId);
        Entry e = db.getIfPresent(key);
        return e;
    }

    public boolean store(
            String serial, String appId, List<Apk> newInstalledApks, OverlayId overlayId) {
        String key = String.format("%s:%s", serial, appId);
        db.put(key, new Entry(newInstalledApks, overlayId));
        return true;
    }
}
