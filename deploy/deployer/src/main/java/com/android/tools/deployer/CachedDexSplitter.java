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
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CachedDexSplitter {

    private final ApkFileDatabase db;

    public CachedDexSplitter(ApkFileDatabase db) {
        this.db = db;
    }

    public List<DexClass> split(ApkEntry dex, boolean read, Predicate<DexClass> needsCode)
            throws DeployerException {
        // Try a cached version
        List<DexClass> classes = db.getClasses(dex);
        if (classes.isEmpty() || needsCode != null) {
            if (!read) {
                throw new DeployerException(
                        DeployerException.Error.REMOTE_APK_NOT_FOUND_ON_DB,
                        "Cannot generate classes for unknown dex");
            }
            byte[] code = dexProvider().apply(dex);
            classes = new DexSplitter().split(dex, code, needsCode);
            db.addClasses(classes);
        }
        return classes;
    }

    private Function<ApkEntry, byte[]> dexProvider() {
        // TODO Check if opening the file several times matters
        return (ApkEntry dex) -> {
            try (ZipFile file = new ZipFile(dex.apk.path)) {
                ZipEntry entry = file.getEntry(dex.name);
                return ByteStreams.toByteArray(file.getInputStream(entry));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    public boolean cache(List<ApkEntry> newFiles) throws DeployerException {
        for (ApkEntry file : newFiles) {
            if (file.name.endsWith(".dex")) {
                split(file, true, null);
            }
        }
        return true;
    }
}
