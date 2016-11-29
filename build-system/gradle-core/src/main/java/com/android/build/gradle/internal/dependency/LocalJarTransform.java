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

package com.android.build.gradle.internal.dependency;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_JAR_SUB_PROJECTS_LOCAL_DEPS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_LOCAL_JARS;

import com.android.SdkConstants;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformTargets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ArtifactAttributes;

/**
 * This is a transform to go from a folder of local jars to a list of files in the folder.
 *
 * When a library's local jars are published we don't know how many jars there are. Therefore we
 * can only publish the folder.
 * On the consuming side the transform looks at the content of the folder (which is now populated),
 * and returns a list of its content as artifact type 'jar'
 */
public class LocalJarTransform extends ArtifactTransform {

    private static final Attribute<String> ARTIFACT_FORMAT = ArtifactAttributes.ARTIFACT_FORMAT;

    @Override
    public void configure(AttributeContainer from, ArtifactTransformTargets targets) {
        from.attribute(ARTIFACT_FORMAT, TYPE_LOCAL_JARS);

        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_JAR);
        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_JAR_SUB_PROJECTS_LOCAL_DEPS);
    }

    @Override
    public List<File> transform(File input, AttributeContainer target) {
        // this is the same answer no matter the target type
        File[] jars = input.listFiles((dir, name) -> name.endsWith(SdkConstants.DOT_JAR));

        if (jars == null) {
            return Collections.emptyList();
        }

        return Arrays.asList(jars);
    }
}
