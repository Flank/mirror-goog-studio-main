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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class IrProject {
    public List<IrModule> modules = new ArrayList<>();
    private File baseDir;
    private String projectPath;

    /**
     * The id of the project. This is used to support multiple projects in the same BUILD files.
     * It is used to prefix rules, to annotate managed markers, and when it's needed to
     * differentiate between rules. If empty, this behaves as a single/main project.
     */
    private String id;

    public IrProject(File baseDir, String projectPath, String id) {
        this.baseDir = baseDir;
        this.projectPath = projectPath;
        this.id = id;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String id() {
        return id;
    }
}
