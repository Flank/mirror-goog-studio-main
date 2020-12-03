/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.zipflinger;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;

public class Mapper {
    public static void main(String[] args) throws IOException {
        for (String archivePath : args) {
            map(archivePath);
        }
    }

    private static void map(String archivePath) throws IOException {
        Map<String, Entry> entries = ZipMap.from(Paths.get(archivePath), true).getEntries();
        ArrayList<Entry> sortedEntries = new ArrayList<>(entries.values());
        sortedEntries.sort((e1, e2) -> (int) (e1.getLocation().first - e2.getLocation().last));

        for (Entry e : sortedEntries) {
            Location l = e.getLocation();
            System.out.println(l.first + "-" + l.last + " (size=" + l.size() + ") :" + e.getName());
        }
    }
}
