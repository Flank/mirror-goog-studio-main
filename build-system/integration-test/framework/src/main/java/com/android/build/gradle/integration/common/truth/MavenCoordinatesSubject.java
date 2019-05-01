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

package com.android.build.gradle.integration.common.truth;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.Preconditions;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;


public class MavenCoordinatesSubject extends Subject<MavenCoordinatesSubject, MavenCoordinates> {

    public static Subject.Factory<MavenCoordinatesSubject, MavenCoordinates> mavenCoordinates() {
        return MavenCoordinatesSubject::new;
    }

    public MavenCoordinatesSubject(
            @NonNull FailureMetadata failureMetadata, @NonNull MavenCoordinates subject) {
        super(failureMetadata, subject);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void isEqualTo(String groupId, String artifactId, String version) {
        isEqualTo(groupId, artifactId, version, null, null);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void isEqualTo(
            @Nullable String groupId,
            @Nullable String artifactId,
            @Nullable String version,
            @Nullable String packaging,
            @Nullable String classifier) {
        Preconditions.checkState(groupId != null || artifactId != null || version != null || packaging != null || classifier != null);

        MavenCoordinates coordinates = actual();

        if (groupId != null && !groupId.equals(coordinates.getGroupId())) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "Not true that groupId of %s is equal to %s. It is %s",
                                    actualAsString(), groupId, coordinates.getGroupId())));
        }

        if (artifactId != null && !artifactId.equals(coordinates.getArtifactId())) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "Not true that artifactId of %s is equal to %s. It is %s",
                                    actualAsString(), artifactId, coordinates.getArtifactId())));
        }

        if (version != null && !version.equals(coordinates.getVersion())) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "Not true that version of %s is equal to %s. It is %s",
                                    actualAsString(), version, coordinates.getVersion())));
        }

        if (packaging != null && !packaging.equals(coordinates.getPackaging())) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "Not true that packaging of %s is equal to %s. It is %s",
                                    actualAsString(), packaging, coordinates.getPackaging())));
        }

        if (classifier != null && !classifier.equals(coordinates.getClassifier())) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "Not true that classifier of %s is equal to %s. It is %s",
                                    actualAsString(), classifier, coordinates.getClassifier())));
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasVersion(@NonNull String version) {
        MavenCoordinates coordinates = actual();

        if (!version.equals(coordinates.getVersion())) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "Not true that version of %s is equal to %s. It is %s",
                                    actualAsString(), version, coordinates.getVersion())));
        }
    }
}
