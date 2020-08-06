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
package com.android.tools.apk.analyzer;

import com.android.annotations.NonNull;
import java.nio.file.Files;
import java.nio.file.Path;

public class PathUtils {
    @NonNull
    public static String pathWithTrailingSeparator(@NonNull Path path) {
        String pathString = path.toString();
        if (Files.isDirectory(path)) {
            String separator = path.getFileSystem().getSeparator();
            if (!pathString.endsWith(separator)) {
                return pathString + separator;
            }
        }
        return pathString;
    }

    @NonNull
    public static String fileNameWithTrailingSeparator(@NonNull Path path) {
        String pathString = path.getFileName().toString();
        if (Files.isDirectory(path)) {
            String separator = path.getFileSystem().getSeparator();
            if (!pathString.endsWith(separator)) {
                return pathString + separator;
            }
        }
        return pathString;
    }
}
