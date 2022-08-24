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

package com.android.tools.maven;

import org.apache.maven.model.Model;
import org.apache.maven.repository.internal.ArtifactDescriptorReaderDelegate;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.util.artifact.DelegatingArtifact;

public class HandleAarDescriptorReaderDelegate extends ArtifactDescriptorReaderDelegate {
    @Override
    public void populateResult(
            RepositorySystemSession session, ArtifactDescriptorResult result, Model model) {
        super.populateResult(session, result, model);

        if (model.getPackaging().equals("pom") &&!result.getArtifact().getClassifier().isEmpty()) {
            // We consider it OK to have a JAR dependency to an artifact that has packaging=pom as long as the
            // dependency also has a classifier. In this case, we don't need to overwrite the dependency type.
            //
            // For instance, the artifact com.google.protobuf:protoc:3.10.0 contains protoc compiler
            // executables as classified files, such as protoc-3.10.0-linux-x86_64.exe. As long as the
            // dependency is specified as com.google.protobuf:exe:linux-x86_64:protoc:3.10.0, we are fine.
            return;
        }

        if (!MavenRepository.getArtifactExtension(model)
                .equals(result.getArtifact().getExtension())) {
            // When the dependency type does not match the packaging type of the target, we use the packaging
            // type of the target to clarify the dependency type.
            //
            // Example: An aar artifact can refer to other aar artifacts without explicitly stating the
            // dependency type to be "aar". Aether default is "jar", so here we have to convert it back to "aar".
            //
            // Example: lint-gradle depends on groovy-all without expressing a dependency type. This defaults to
            // "jar" dependency type, but groovy-all has packaging type "pom", so we have to convert it to "pom".
            //
            // This is something that Gradle handles automatically by looking at the packaging type of the target
            // artifact, so we do the same here.
            result.setArtifact(
                    new DifferentExtensionArtifact(
                            MavenRepository.getArtifactExtension(model), result.getArtifact()));
        }

        // Workaround for https://youtrack.jetbrains.com/issue/KT-53670, which is present in
        // 1.7.20-Beta but will be fixed in 1.7.20-RC+.
        // TODO: Remove this workaround when we're no longer using 1.7.20-Beta.
        if (result.getArtifact().toString().contains("org.jetbrains.kotlin:kotlin-gradle-plugin-idea-proto:jar:1.7.20-Beta")) {
            result.setDependencies(new java.util.ArrayList());
        }
    }

    private static class DifferentExtensionArtifact extends DelegatingArtifact {

        private final String extension;

        public DifferentExtensionArtifact(String extension, Artifact delegate) {
            super(delegate);
            this.extension = extension;
        }

        @Override
        protected DelegatingArtifact newInstance(Artifact delegate) {
            return new DifferentExtensionArtifact(extension, delegate);
        }

        @Override
        public String getExtension() {
            return extension;
        }
    }
}
