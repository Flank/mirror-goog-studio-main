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

package com.android.testutils.apk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Immutable
public final class AtomBundle extends DexAndroidArchive {

    public AtomBundle(@NonNull Path file) throws IOException {
        super(file, "dex/classes.dex", "dex/classes%d.dex");
    }

    public AtomBundle(@NonNull File apk) throws IOException {
        this(apk.toPath());
    }

    @Nullable
    @Override
    public Path getJavaResource(@NonNull String resourcePath) throws IOException {
        return getEntry("resources/" + resourcePath);
    }

    @Override
    public String toString() {
        return "AtomBundle<" + getFile() + ">";
    }
}
