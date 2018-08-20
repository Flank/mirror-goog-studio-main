/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.builder.multidex;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.r8.GenerateMainDexList;
import com.android.tools.r8.GenerateMainDexListCommand;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** This is a utility class that is using D8 to get the main dex list. */
public final class D8MainDexList {

    public static class MainDexListException extends Exception {
        public MainDexListException(Throwable cause) {
            super(cause);
        }
    }

    private D8MainDexList() {}

    /**
     * Returns the list of classes that should be kept in the main dex file for legacy multidex.
     *
     * @param mainDexRules Proguard rules written as strings
     * @param mainDexRulesFiles files containing the Proguard rules
     * @param programFiles classes that will end up in the final binary
     * @param libraryFiles classes that are used only to resolve types in the program classes, but
     *     are not packaged in the final binary e.g. android.jar, provided classes etc.
     * @return a list of classes to be kept in the main dex file
     */
    @NonNull
    public static List<String> generate(
            @NonNull List<String> mainDexRules,
            @NonNull List<Path> mainDexRulesFiles,
            @NonNull Collection<Path> programFiles,
            @NonNull Collection<Path> libraryFiles)
            throws MainDexListException {
        try {
            GenerateMainDexListCommand.Builder command =
                    GenerateMainDexListCommand.builder()
                            .addMainDexRules(mainDexRules, Origin.unknown())
                            .addMainDexRulesFiles(mainDexRulesFiles)
                            .addLibraryFiles(libraryFiles);

            for (Path program : programFiles) {
                if (Files.isRegularFile(program)) {
                    command.addProgramFiles(program);
                } else {
                    try (Stream<Path> classFiles = Files.walk(program)) {
                        List<Path> allClasses = classFiles
                                .filter(p -> p.toString().endsWith(SdkConstants.DOT_CLASS))
                                .collect(Collectors.toList());
                        command.addProgramFiles(allClasses);
                    }
                }
            }

            return ImmutableList.copyOf(
                    GenerateMainDexList.run(command.build(), ForkJoinPool.commonPool()));
        } catch (Exception e) {
            throw new MainDexListException(e);
        }
    }
}
