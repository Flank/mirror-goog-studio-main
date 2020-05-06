/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deployer.model;

import com.android.tools.deployer.ZipUtils;
import com.google.common.annotations.VisibleForTesting;
import java.io.Serializable;

public class ApkEntry implements Serializable {
    private final String name;
    private final long checksum;
    private final ZipUtils.ZipEntry entry;
    private Apk apk;

    ApkEntry(ZipUtils.ZipEntry entry) {
        this.name = entry.name;
        this.checksum = entry.crc;
        this.entry = entry;
    }

    @VisibleForTesting
    public ApkEntry(String name, long checksum, Apk apk) {
        this.name = name;
        this.checksum = checksum;
        this.entry = null;
        this.apk = apk;
    }

    public String getName() {
        return name;
    }

    public long getChecksum() {
        return checksum;
    }

    public ZipUtils.ZipEntry getZipEntry() {
        return entry;
    }

    public Apk getApk() {
        return apk;
    }

    public String getQualifiedPath() {
        return String.format("%s/%s", apk.name, name);
    }

    void setApk(Apk apk) {
        this.apk = apk;
    }
}
