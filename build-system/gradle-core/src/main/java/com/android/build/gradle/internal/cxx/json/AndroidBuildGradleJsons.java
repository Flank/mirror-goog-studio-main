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

package com.android.build.gradle.internal.cxx.json;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/** Methods for dealing with files and streams of type android_build_gradle.json. */
public class AndroidBuildGradleJsons {

    /**
     * Given a JsonReader that represents an android_build_gradle structure produce a small random
     * access structure called {@link NativeBuildConfigValueMini}
     *
     * @param reader the Json reader
     * @return the mini config
     * @throws IOException if there was an IO problem reading the Json.
     */
    @NonNull
    public static NativeBuildConfigValueMini parseToMiniConfig(@NonNull JsonReader reader)
            throws IOException {
        MiniConfigBuildingParser parser = new MiniConfigBuildingParser(reader);
        parser.parse();
        return parser.miniConfig;
    }

    /**
     * Given a File that contains an android_build_gradle structure produce a small random access
     * structure called {@link NativeBuildConfigValueMini}
     *
     * @param json the Json reader
     * @return the mini config
     * @throws IOException if there was an IO problem reading the Json.
     */
    public static NativeBuildConfigValueMini getNativeBuildMiniConfig(File json)
            throws IOException {
        return parseToMiniConfig(new JsonReader(new FileReader(json)));
    }

    /**
     * Given a list of Json files and the current variant name produce a list of
     * NativeBuildConfigValueMini. Json parsing is done in a streaming manner so that the entire
     * Json file is not read into memory at once.
     */
    public static List<NativeBuildConfigValueMini> getNativeBuildMiniConfigs(List<File> jsons)
            throws IOException {
        List<NativeBuildConfigValueMini> miniConfigs = Lists.newArrayList();
        for (File json : jsons) {
            miniConfigs.add(getNativeBuildMiniConfig(json));
        }
        return miniConfigs;
    }

    /**
     * Writes the given object as JSON to the given json file.
     *
     * @throws IOException I/O failure
     */
    public static void writeNativeBuildConfigValueToJsonFile(
            @NonNull File outputJson, @NonNull NativeBuildConfigValue nativeBuildConfigValue)
            throws IOException {
        Gson gson =
                new GsonBuilder()
                        .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                        .disableHtmlEscaping()
                        .setPrettyPrinting()
                        .create();

        FileWriter jsonWriter = new FileWriter(outputJson);
        gson.toJson(nativeBuildConfigValue, jsonWriter);
        jsonWriter.close();
    }

    /**
     * Streams over android_build_gradle.json and produces a random-access but small structure
     * called a NativeBuildConfigValueMini.
     */
    private static class MiniConfigBuildingParser extends AndroidBuildGradleJsonStreamingParser {
        @NonNull private final NativeBuildConfigValueMini miniConfig;
        @Nullable private String libraryName;

        MiniConfigBuildingParser(JsonReader reader) {
            super(reader);
            this.miniConfig = new NativeBuildConfigValueMini();
            libraryName = null;
        }

        @Override
        protected void beginLibrary(@NonNull String libraryName) {
            this.libraryName = libraryName;
            miniConfig.libraries.put(libraryName, new NativeLibraryValueMini());
        }

        @Override
        protected void visitLibraryAbi(@NonNull String abi) {
            miniConfig.libraries.get(libraryName).abi = abi;
        }

        @Override
        protected void visitLibraryArtifactName(@NonNull String artifactName) {
            miniConfig.libraries.get(libraryName).artifactName = artifactName;
        }

        @Override
        protected void visitLibraryBuildCommand(@NonNull String buildCommand) {
            miniConfig.libraries.get(libraryName).buildCommand = buildCommand;
        }

        @Override
        protected void visitCleanCommands(@NonNull String cleanCommand) {
            miniConfig.cleanCommands.add(cleanCommand);
        }

        @Override
        protected void visitLibraryOutput(@NonNull String output) {
            miniConfig.libraries.get(libraryName).output = new File(output);
        }

        @Override
        protected void visitBuildFile(@NonNull String buildFile) {
            miniConfig.buildFiles.add(new File(buildFile));
        }
    }
}
