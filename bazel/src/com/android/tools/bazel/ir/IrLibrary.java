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

package com.android.tools.bazel.ir;

import com.google.common.io.Files;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class IrLibrary extends IrNode {
    public String name;
    public IrModule owner;
    private List<File> files;

    public IrLibrary(String name, IrModule owner) {
        this.name = name;
        this.owner = owner;
        this.files = new ArrayList<>();
    }

    public void addFile(File file) {
        if (file.toString().contains("$MAVEN_REPOSITORY$")) {
            String owned = owner == null ? "" : " (Owned by " + owner.getName() + ")";
            throw new IllegalStateException(
                    "Library: "
                            + name
                            + owned
                            + " cannot use $MAVEN_REPOSITORY$, "
                            + "please point to the jar file in prebuilts instead.");
        }
        files.add(file);
    }

    public List<File> getFiles() {
        return files;
    }

    public String getName() {
        if (name.startsWith("#") || name.isEmpty()) {
            for (File file : files) {
                return Files.getNameWithoutExtension(file.getName());
            }
        }
        return name;
    }
}
