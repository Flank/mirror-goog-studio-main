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

package com.android.tools.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility for merging Java properties files.
 */
public class PropertiesMerger {

    public static void main(String[] args) throws IOException {
        Set<Path> inputs = new HashSet<>();
        Path output = null;
        Map<String, List<String>> mappings = new HashMap<>();

        Iterator<String> iterator = Arrays.asList(args).iterator();
        while (iterator.hasNext()) {
            String arg = iterator.next();
            switch (arg) {
                case "--input":
                    inputs.add(Paths.get(iterator.next()));
                    break;
                case "--output":
                    //noinspection VariableNotUsedInsideIf
                    if (output != null) {
                        throw new IllegalArgumentException("--output specified twice.");
                    }
                    output = Paths.get(iterator.next());
                    break;
                case "--mapping":
                    String[] keyValue = iterator.next().split(":");
                    List<String> list =
                            mappings.computeIfAbsent(keyValue[0], s -> new ArrayList<>());
                    list.add(keyValue[1]);
                    break;
            }
        }


        if (output == null) {
            throw new IllegalArgumentException("--output not specified.");
        }

        if (mappings.isEmpty()) {
            throw new IllegalArgumentException("No --mapping specified.");
        }

        run(inputs, output, mappings);
    }

    private static void run(Set<Path> inputs, Path output, Map<String, List<String>> mappings)
            throws IOException {
        Properties result = new Properties();
        for (Path input : inputs) {
            try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(input))) {
                Properties inputProperties = new Properties();
                inputProperties.load(inputStream);
                for (Object key : inputProperties.keySet()) {
                    List<String> mappedKeys = mappings.get(key);
                    if (mappedKeys != null) {
                        Object value = inputProperties.get(key);
                        for (String mappedKey : mappedKeys) {
                            result.put(mappedKey, value);
                        }
                    }
                }
            }
        }

        ByteArrayOutputStream outputContent = new ByteArrayOutputStream();
        result.store(outputContent, null);
        List<String> lines =
                Stream.of(outputContent.toString().split(System.lineSeparator()))
                        .filter(s -> !s.startsWith("#"))
                        .collect(Collectors.toList());

        Files.write(output, lines);
    }
}
