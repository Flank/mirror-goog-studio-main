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
package com.android.tools.deployer;

import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import java.util.List;
import java.util.function.Predicate;

public class CachedDexSplitter implements DexSplitter {

    private final ApkFileDatabase db;
    private final DexSplitter splitter;

    public CachedDexSplitter(ApkFileDatabase db, DexSplitter splitter) {
        this.db = db;
        this.splitter = splitter;
    }

    @Override
    public List<DexClass> split(ApkEntry dex, Predicate<DexClass> keepCode)
            throws DeployerException {
        // Try a cached version
        List<DexClass> classes = db.getClasses(dex);
        if (classes.isEmpty() || keepCode != null) {
            if (dex.apk.path == null) {
                throw new DeployerException(
                        DeployerException.Error.REMOTE_APK_NOT_FOUND_ON_DB,
                        "Cannot generate classes for unknown dex");
            }
            classes = splitter.split(dex, keepCode);
            db.addClasses(classes);
        }
        return classes;
    }

    public boolean cache(List<ApkEntry> newFiles) throws DeployerException {
        for (ApkEntry file : newFiles) {
            if (file.name.endsWith(".dex")) {
                split(file, null);
            }
        }
        return true;
    }
}
