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

package com.android.builder.symbols;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;

public final class SymbolIo {

    private SymbolIo() {}

    /**
     * Loads a symbol table from a symbol file.
     *
     * @param file the symbol file
     * @return the table read
     * @throws IOException failed to read the table
     */
    @NonNull
    public static SymbolTable load(@NonNull File file) throws IOException {
        List<String> lines = Files.readLines(file, Charsets.UTF_8);

        SymbolTable table = new SymbolTable();

        int lineIndex = 1;
        String line = null;
        try {
            final int count = lines.size();
            for (; lineIndex <= count ; lineIndex++) {
                line = lines.get(lineIndex - 1);

                // format is "<type> <class> <name> <value>"
                // don't want to split on space as value could contain spaces.
                int pos = line.indexOf(' ');
                String type = line.substring(0, pos);
                int pos2 = line.indexOf(' ', pos + 1);
                String className = line.substring(pos + 1, pos2);
                int pos3 = line.indexOf(' ', pos2 + 1);
                String name = line.substring(pos2 + 1, pos3);
                String value = line.substring(pos3 + 1);

                table.add(new Symbol(className, name, type, value));
            }
        } catch (IndexOutOfBoundsException e) {
            throw new IOException(
                    String.format(
                            "File format error reading %s line %d: '%s'",
                            file.getAbsolutePath(),
                            lineIndex,
                            line),
                    e);
        }

        return table;
    }
}
