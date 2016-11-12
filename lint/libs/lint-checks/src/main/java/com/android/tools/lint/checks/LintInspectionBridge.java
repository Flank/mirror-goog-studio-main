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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Issue;
import com.google.common.collect.Maps;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Interface implemented by some lint checks which need to bridge to
 * IDE inspections. This lets these issues be reported from IDE inspections
 * that do not run as part of lint (with access to lint's contexts, projects, etc)
 * without duplicating the logic of the check on the IDE side. See
 * the ResourceTypeInspection in the IDE.
 */
public abstract class LintInspectionBridge {
    /**
     * Report the given issue
     */
    public abstract void report(@NonNull Issue issue,
      @NonNull PsiElement locationNode,
      @NonNull PsiElement scopeNode,
      @NonNull String message);

    public abstract boolean isTestSource();

    @Nullable
    public abstract Dependencies getDependencies();

    /**
     * Return the Gradle group id for the given element, <b>if</b> applicable. For example, for
     * a method in the appcompat library, this would return "com.android.support".
     */
    @Nullable
    public MavenCoordinates getLibrary(@NonNull PsiElement element) {
        String jarFile = getEvaluator().findJarPath(element);
        if (jarFile != null) {
            if (jarToGroup == null) {
                jarToGroup = Maps.newHashMap();
            }
            MavenCoordinates coordinates = jarToGroup.get(jarFile);
            if (coordinates == null) {
                Library library = findOwnerLibrary(jarFile);
                if (library != null) {
                    coordinates = library.getResolvedCoordinates();
                }
                if (coordinates == null) {
                    // Use string location to figure it out. Note however that
                    // this doesn't work when the build cache is in effect.
                    // Example:
                    // $PROJECT_DIRECTORY/app/build/intermediates/exploded-aar/com.android.support/
                    //          /appcompat-v7/25.0.0-SNAPSHOT/jars/classes.jar
                    // and we want to pick out "com.android.support" and "appcompat-v7"
                    int index = jarFile.indexOf("exploded-aar");
                    if (index != -1) {
                        index += 13; // "exploded-aar/".length()
                        for (int i = index; i < jarFile.length(); i++) {
                            char c = jarFile.charAt(i);
                            if (c == '/' || c == File.separatorChar) {
                                String groupId = jarFile.substring(index, i);
                                i++;
                                for (int j = i; j < jarFile.length(); j++) {
                                    c = jarFile.charAt(j);
                                    if (c == '/' || c == File.separatorChar) {
                                        String artifactId = jarFile.substring(i, j);
                                        coordinates = new MyMavenCoordinates(groupId, artifactId);
                                        break;
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
                if (coordinates == null) {
                    coordinates = MyMavenCoordinates.NONE;
                }
                jarToGroup.put(jarFile, coordinates);
            }
            return coordinates == MyMavenCoordinates.NONE ? null : coordinates;
        }

        return null;
    }

    @Nullable
    public Library findOwnerLibrary(@NonNull String jarFile) {
        Dependencies dependencies = getDependencies();
        if (dependencies != null) {
            Library match = findOwnerLibrary(dependencies.getLibraries(), jarFile);
            if (match != null) {
                return match;
            }
            match = findOwnerJavaLibrary(dependencies.getJavaLibraries(), jarFile);
            if (match != null) {
                return match;
            }
        }

        return null;
    }

    @Nullable
    private static Library findOwnerJavaLibrary(
            @NonNull Collection<? extends JavaLibrary> dependencies,
            @NonNull String jarFile) {
        for (JavaLibrary library : dependencies) {
            if (jarFile.equals(library.getJarFile().getPath())) {
                return library;
            }
            Library match = findOwnerJavaLibrary(library.getDependencies(), jarFile);
            if (match != null) {
                return match;
            }
        }

        return null;
    }

    @Nullable
    private static Library findOwnerLibrary(
            @NonNull Collection<? extends AndroidLibrary> dependencies,
            @NonNull String jarFile) {
        for (AndroidLibrary library : dependencies) {
            if (jarFile.equals(library.getJarFile().getPath())) {
                return library;
            }
            for (File jar : library.getLocalJars()) {
                if (jarFile.equals(jar.getPath())) {
                    return library;
                }
            }
            Library match = findOwnerLibrary(library.getLibraryDependencies(), jarFile);
            if (match != null) {
                return match;
            }

            match = findOwnerJavaLibrary(library.getJavaDependencies(), jarFile);
            if (match != null) {
                return match;
            }
        }

        return null;
    }

    @NonNull
    public abstract JavaEvaluator getEvaluator();

    /** Cache for {@link #getLibrary(PsiElement)} */
    private Map<String,MavenCoordinates> jarToGroup;

    /**
     * Dummy implementation of {@link com.android.builder.model.MavenCoordinates} which
     * only stores group and artifact id's for now
     */
    private static class MyMavenCoordinates implements MavenCoordinates {
        private static final MyMavenCoordinates NONE = new MyMavenCoordinates("", "");

        private final String groupId;
        private final String artifactId;

        public MyMavenCoordinates(@NonNull String groupId, @NonNull String artifactId) {
            this.groupId = groupId;
            this.artifactId = artifactId;
        }

        @NonNull
        @Override
        public String getGroupId() {
            return groupId;
        }

        @NonNull
        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @NonNull
        @Override
        public String getVersion() {
            return "";
        }

        @NonNull
        @Override
        public String getPackaging() {
            return "";
        }

        @Nullable
        @Override
        public String getClassifier() {
            return "";
        }

        @Override
        public String getVersionlessId() {
            return groupId + ':' + artifactId;
        }
    }
}
