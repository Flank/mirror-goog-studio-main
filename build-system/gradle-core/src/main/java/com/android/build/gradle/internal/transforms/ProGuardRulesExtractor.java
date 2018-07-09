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

package com.android.build.gradle.internal.transforms;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.annotations.NonNull;
import com.android.build.api.transform.TransformInput;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ProGuardRulesExtractor {

    private static Predicate<ZipEntry> isRule =
            e -> {
                String name = e.getName().toLowerCase();
                return !e.isDirectory()
                        && (name.startsWith("meta-inf/proguard/")
                                || name.startsWith("/meta-inf/proguard/"));
            };
    /**
     * For each *.jar file in a collection of *.jar files returns a mapping between rule file entry
     * name and its textual content. Rule file is assumed to be any text file in a *.jar which path
     * starts with meta-inf/proguard/ or /meta-inf/proguard/ (case insensitive)
     *
     * @return Map<String, Map<String, String> a mapping between *.jar file name and a map of rule
     *     files. A map of rule files is a mapping between a rule file name and its content
     */
    @NonNull
    public static ImmutableMap<String, ImmutableMap<String, String>> extractRulesTexts(
            @NonNull Collection<TransformInput> inputs) {
        return TransformInputUtil.getAllFiles(inputs, false, true)
                .stream()
                .distinct()
                .collect(toImmutableMap(File::getPath, f -> extractRulesTexts(f)));
    }

    @NonNull
    static ImmutableMap<String, String> extractRulesTexts(@NonNull File file) {
        try (ZipFile zipFile = new ZipFile(file, StandardCharsets.UTF_8)) {
            return zipFile.stream()
                    .filter(isRule)
                    .collect(
                            toImmutableMap(
                                    ZipEntry::getName, e -> extractRuleFileText(zipFile, e)));
        } catch (IOException ioe) {
            throw new UncheckedIOException("Error while reading '" + file.getName() + "'", ioe);
        }
    }

    @NonNull
    private static String extractRuleFileText(@NonNull ZipFile zipFile, @NonNull ZipEntry entry) {
        try {
            return CharStreams.toString(
                    new InputStreamReader(zipFile.getInputStream(entry), Charsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
