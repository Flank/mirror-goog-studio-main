/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.GuardedBy;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import groovy.lang.Closure;

/**
 * Cache to Atom prepareTask.
 *
 * Each project creates its own version of AtomDependency, but they all represent the
 * same atom. This creates a single task that will unarchive the atom so that this is done only
 * once even for multi-module projects where 2+ modules depend on the same atom.
 *
 * The prepareTask is created in the root project always.
 */
public class AtomCache {

    @NonNull
    private static final AtomCache sCache = new AtomCache();

    @NonNull
    public static AtomCache getCache() {
        return sCache;
    }

    public synchronized void unload() {
        bundleLatches.clear();
    }

    @GuardedBy("this")
    private final Map<String, CountDownLatch> bundleLatches = Maps.newHashMap();

    public static void unzipAtom(final File bundle, final File folderOut, final Project project) {
        for (File f : Files.fileTreeTraverser().postOrderTraversal(folderOut)) {
            f.delete();
        }

        folderOut.mkdirs();

        project.copy(new Closure(AtomCache.class) {
            public Object doCall(CopySpec cs) {
                cs.from(project.zipTree(bundle));
                cs.into(folderOut);

                return cs;
            }
        });
    }
}
