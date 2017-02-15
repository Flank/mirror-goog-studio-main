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

package com.android.java.model.builder;

import com.android.java.model.JavaProject;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

/**
 * Builder for the custom Java model.
 */
public class JavaModelBuilder implements ToolingModelBuilder {

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(JavaProject.class.getName());
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        return null;
    }
}
