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

        if (!model.getPackaging().equals(result.getArtifact().getExtension())
                && !model.getPackaging().equals("bundle")) {
            // This is something that Gradle seems to handle automatically, we have to do the same.
            result.setArtifact(
                    new DifferentExtensionArtifact(model.getPackaging(), result.getArtifact()));
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
