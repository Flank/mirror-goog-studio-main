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

package com.android.build.gradle.internal.dependency;

import static org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT;
import static org.gradle.api.plugins.JavaPlugin.CLASS_DIRECTORY;
import static org.gradle.api.plugins.JavaPlugin.RESOURCES_DIRECTORY;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformTargets;
import org.gradle.api.attributes.AttributeContainer;

/**
 * Transform to go from external jars to CLASS and RESOURCE artifact.
 *
 * This returns the same exact file but with a different type, since a jar file can contain both.
 */
public class JarTransform extends ArtifactTransform {

    @Override
    public void configure(AttributeContainer from, ArtifactTransformTargets targets) {
        from.attribute(ARTIFACT_FORMAT, "jar");

        targets.newTarget().attribute(ARTIFACT_FORMAT, CLASS_DIRECTORY);
        targets.newTarget().attribute(ARTIFACT_FORMAT, RESOURCES_DIRECTORY);
    }

    @Override
    public List<File> transform(File file, AttributeContainer attributeContainer) {
        return ImmutableList.of(file);
    }
}
