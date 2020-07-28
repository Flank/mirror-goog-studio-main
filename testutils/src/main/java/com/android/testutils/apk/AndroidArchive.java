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
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.mockito.CheckReturnValue;

public abstract class AndroidArchive extends Zip {

    public static final Pattern CLASS_FORMAT =
            Pattern.compile("^L([a-zA-Z][a-zA-Z0-9_$]*/)*[a-zA-Z0-9$._]+;$");

    public AndroidArchive(@NonNull Path file) throws IOException {
        super(file);
    }

    public static void checkValidClassName(@NonNull String className) {
        if (!CLASS_FORMAT.matcher(className).matches()) {
            throw new IllegalArgumentException(
                    String.format(
                            "class name '%1$s' must be in the format Lcom/foo/Main;", className));
        }
    }

    @CheckReturnValue
    public abstract boolean containsMainClass(@NonNull String name) throws IOException;

    @CheckReturnValue
    public abstract boolean containsSecondaryClass(@NonNull String name) throws IOException;

    @Nullable
    @CheckReturnValue
    public abstract Path getJavaResource(@NonNull String name) throws IOException;

    @CheckReturnValue
    public final boolean containsClass(@NonNull String name) throws IOException {
        return containsMainClass(name) || containsSecondaryClass(name);
    }

    @CheckReturnValue
    @Nullable
    public final Path getResource(@NonNull String name) {
        return getEntry("res/" + name);
    }

}
