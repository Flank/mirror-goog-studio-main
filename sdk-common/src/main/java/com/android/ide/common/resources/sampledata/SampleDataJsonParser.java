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
package com.android.ide.common.resources.sampledata;

import com.android.annotations.NonNull;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SampleDataJsonParser {
    private final JsonObject myRootObject;

    private SampleDataJsonParser(@NonNull JsonObject rootObject) {
        myRootObject = rootObject;
    }

    public static SampleDataJsonParser parse(Reader reader) {
        JsonParser parser = new JsonParser();
        try {
            return new SampleDataJsonParser(parser.parse(reader).getAsJsonObject());
        } catch (Throwable ignored) {
        }
        return null;
    }

    @NonNull
    public List<String> getContentFromPath(String path) {
        ImmutableList<String> pathItems = ImmutableList.copyOf(Splitter.on('/').splitToList(path));
        ArrayList<String> content = new ArrayList<>();
        visitElementAndGetContent(myRootObject, pathItems, content);

        return content;
    }

    public Set<String> getPossiblePaths() {
        Set<String> paths = new HashSet<>();
        visitElementAndGetPath(myRootObject, "", paths);

        return paths;
    }

    static void visitElementAndGetPath(
            JsonElement name, @NonNull String pathSoFar, @NonNull Set<String> pathOut) {
        if (name == null) {
            return;
        }

        if (name.isJsonPrimitive()) {
            pathOut.add(pathSoFar);
        } else if (name.isJsonArray()) {
            JsonArray array = name.getAsJsonArray();

            for (int i = 0; i < array.size(); i++) {
                visitElementAndGetPath(array.get(i), pathSoFar, pathOut);
            }
        } else if (name.isJsonObject()) {
            JsonObject object = name.getAsJsonObject();

            object.entrySet()
                    .forEach(
                            entry ->
                                    visitElementAndGetPath(
                                            entry.getValue(),
                                            pathSoFar + "/" + entry.getKey(),
                                            pathOut));
        }
    }

    static void visitElementAndGetContent(
            JsonElement name, @NonNull List<String> path, @NonNull List<String> contentOut) {
        if (name == null) {
            return;
        }

        if (path.isEmpty()) {
            contentOut.add(name.getAsString());
            return;
        }

        if (name.isJsonArray()) {
            JsonArray array = name.getAsJsonArray();

            for (int i = 0; i < array.size(); i++) {
                visitElementAndGetContent(array.get(i), path, contentOut);
            }
        } else if (name.isJsonObject()) {
            JsonObject object = name.getAsJsonObject();

            String pathItem = path.get(0);
            visitElementAndGetContent(
                    object.get(pathItem), path.subList(1, path.size()), contentOut);
        }
    }
}
