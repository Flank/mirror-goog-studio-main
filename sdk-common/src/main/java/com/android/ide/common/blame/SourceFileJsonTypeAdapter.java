/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.ide.common.blame;

import com.android.ide.common.resources.RelativeResourceUtils;
import com.google.common.base.Strings;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * JsonSerializer and Deserializer for {@link SourceFile}.
 *
 * <p>The JsonDeserialiser accepts either a string of the file path or a json object of the form
 *
 * <pre>{
 *     "path":"/path/to/file.java",
 *     "description": "short `human-readable description"
 * }</pre>
 *
 * where both properties are optionally present, so unknown is represented by the empty object.
 */
public class SourceFileJsonTypeAdapter extends TypeAdapter<SourceFile> {

    private static final String PATH = "path";

    private static final String DESCRIPTION = "description";

    @Override
    public void write(JsonWriter out, SourceFile src) throws IOException {
        String path = src.getSourcePath();
        String description = src.getDescription();

        if (description == null && path != null) {
            out.value(path);
            return;
        }
        out.beginObject();
        if (description != null) {
            out.name(DESCRIPTION).value(description);
        }
        if (path != null) {
            out.name(PATH).value(path);
        }
        out.endObject();
    }

    @Override
    public SourceFile read(JsonReader in) throws IOException {
        switch (in.peek()) {
            case BEGIN_OBJECT:
                in.beginObject();
                String filePath = null;
                String description = null;
                while (in.hasNext()) {
                    String name = in.nextName();
                    if (name.equals(PATH)) {
                        filePath = in.nextString();
                    } else if (DESCRIPTION.equals(name)) {
                        description = in.nextString();
                    } else {
                        in.skipValue();
                    }
                }
                in.endObject();
                if (!Strings.isNullOrEmpty(filePath)) {
                    File file = new File(filePath);
                    SourceFile sf;
                    if (!Strings.isNullOrEmpty(description)) {
                        sf = new SourceFile(file, description);
                    } else {
                        sf = new SourceFile(file);
                    }
                    return sf;
                } else {
                    if (!Strings.isNullOrEmpty(description)) {
                        return new SourceFile(description);
                    } else {
                        return SourceFile.UNKNOWN;
                    }
                }
            case STRING:
                String fileName = in.nextString();
                if (Strings.isNullOrEmpty(fileName)) {
                    return SourceFile.UNKNOWN;
                }
                return new SourceFile(new File(fileName));
            default:
                return SourceFile.UNKNOWN;
        }

    }
}
