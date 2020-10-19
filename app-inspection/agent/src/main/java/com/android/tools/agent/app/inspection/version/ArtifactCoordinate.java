/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.agent.app.inspection.version;

/**
 * Contains all of the information required to perform version check.
 *
 * <p>Note: This data structure is also initialized and used in the JNI layer. Changes to this may
 * require updating app_inspection_agent_command.cc.
 */
public final class ArtifactCoordinate {

    public final String groupId;
    public final String artifactId;
    public final String version;

    public ArtifactCoordinate(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String toVersionFileName() {
        return groupId + "_" + artifactId + ".version";
    }
}
