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
import com.google.common.collect.Maps;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public static final int DEFAULT_SIZE = 25; // max number of cache entries.

    // Key = serial + ":" + appId.
    private final Cache<String, Entry> db;

    File persistFile = null;

    public static class Entry implements Serializable {
        private final List<Apk> apks;
        private final OverlayId oid;

        public List<Apk> getApks() {
            return apks;
        }

        public OverlayId getOverlayId() {
            return oid;
        }

        public OverlayId.Contents getOverlayContents() {
            return oid.getOverlayContents();
        }

        private Entry(List<Apk> apks, OverlayId overlayId) {
            this.apks = apks;
            this.oid = overlayId;
        }
    }

    public DeploymentCacheDatabase(int size) {
        this(size, null);
    }

    public DeploymentCacheDatabase(File persistFile) {
        this(DEFAULT_SIZE, persistFile);
    }

    public DeploymentCacheDatabase(int size, File persistFile) {
        db = CacheBuilder.newBuilder().maximumSize(size).build();
        this.persistFile = persistFile;

        if (persistFile == null) {
            return;
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(persistFile))) {
            HashMap<String, Entry> entries = (HashMap<String, Entry>) in.readObject();
            for (Map.Entry<String, Entry> e : entries.entrySet()) {
                db.put(e.getKey(), e.getValue());
            }
        } catch (FileNotFoundException fnf) {
            // ignored.
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public Entry get(String serial, String appId) {
        String key = String.format("%s:%s", serial, appId);
        return db.getIfPresent(key);
    }

    public boolean store(
            String serial, String appId, List<Apk> newInstalledApks, OverlayId overlayId) {
        String key = String.format("%s:%s", serial, appId);
        db.put(key, new Entry(newInstalledApks, overlayId));
        writeToFile();
        return true;
    }

    public boolean invalidate(String serial, String appId) {
        String key = String.format("%s:%s", serial, appId);
        db.invalidate(key);
        writeToFile();
        return true;
    }

    /** Write to persistent file should the cache database be created with a targeted file. */
    public boolean writeToFile() {
        if (persistFile == null) {
            return false;
        }

        if (persistFile.exists()) {
            persistFile.delete();
        }

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(persistFile))) {
            HashMap<String, Entry> entries = Maps.newHashMap(db.asMap());
            out.writeObject(entries);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }
}
