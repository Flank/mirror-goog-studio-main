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
package zipflinger;

import com.android.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ZipSource {
    private final File file;
    private FileChannel channel;
    private ZipMap map;

    private final List<ZipSourceEntry> selectedEntries = new ArrayList<>();

    public ZipSource(@NonNull File file) throws IOException {
        this.map = ZipMap.from(file, false);
        this.file = file;
    }

    @NonNull
    public ZipSourceEntry select(@NonNull String entryName, @NonNull String newName) {
        Entry entry = map.getEntries().get(entryName);
        if (entry == null) {
            throw new IllegalStateException(
                    String.format("Cannot find '%s' in archive '%s'", entryName, map.getFile()));
        }
        ZipSourceEntry entrySource = new ZipSourceEntry(newName, entry, this);
        selectedEntries.add(entrySource);
        return entrySource;
    }

    public Map<String, Entry> entries() {
        return map.getEntries();
    }

    @NonNull
    public static ZipSource selectAll(@NonNull File file) throws IOException {
        ZipSource source = new ZipSource(file);
        for (zipflinger.Entry e : source.entries().values()) {
            source.select(e.getName(), e.getName());
        }
        return source;
    }

    void open() throws IOException {
        channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
    }

    void close() throws IOException {
        if (channel != null) {
            channel.close();
        }
    }

    FileChannel getChannel() {
        return channel;
    }

    List<? extends Source> getSelectedEntries() {
        return selectedEntries;
    }
}
