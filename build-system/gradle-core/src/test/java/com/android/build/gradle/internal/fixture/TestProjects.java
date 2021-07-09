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

package com.android.build.gradle.internal.fixture;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.internal.SdkLocator;
import com.android.build.gradle.internal.plugins.AppPlugin;
import com.android.build.gradle.internal.plugins.LibraryPlugin;
import com.android.build.gradle.options.Option;
import com.android.testutils.OsType;
import com.android.testutils.TestUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.gradle.api.Project;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.provider.Provider;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.initialization.GradlePropertiesController;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.scopes.GradleScopeServices;
import org.gradle.internal.service.scopes.ProjectScopeServices;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.tooling.events.OperationCompletionListener;

public class TestProjects {

    public enum Plugin {
        APP("com.android.application", AppPlugin.class, AppExtension.class),
        LIBRARY("com.android.library", LibraryPlugin.class, LibraryExtension.class),
        ;

        @NonNull private final String pluginName;
        @NonNull private final Class<? extends org.gradle.api.Plugin> pluginClass;
        @NonNull private final Class<? extends BaseExtension> extensionClass;

        Plugin(
                @NonNull String pluginName,
                @NonNull Class<? extends org.gradle.api.Plugin> pluginClass,
                @NonNull Class<? extends BaseExtension> extensionClass) {
            this.pluginName = pluginName;
            this.pluginClass = pluginClass;
            this.extensionClass = extensionClass;
        }

        @NonNull
        public String getPluginName() {
            return pluginName;
        }

        @NonNull
        public Class<? extends org.gradle.api.Plugin> getPluginClass() {
            return pluginClass;
        }

        @NonNull
        public Class<? extends BaseExtension> getExtensionClass() {
            return extensionClass;
        }


        @Override
        public String toString() {
            return pluginName;
        }
    }

    private static final String MANIFEST_TEMPLATE =
            // language=xml
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><manifest package=\"%s\"></manifest>";

    @NonNull
    public static Builder builder(@NonNull Path projectDir) {
        return new Builder(projectDir);
    }

    public static class Builder {
        @NonNull private String manifestContent = MANIFEST_TEMPLATE;
        @NonNull private String applicationId = "com.android.tools.test";
        @NonNull private Path projectDir;
        @NonNull private Plugin plugin = Plugin.APP;
        @NonNull private Map<String, String> properties = new HashMap<>();
        @Nullable private Project parentProject = null;
        @Nullable private String projectName = "test";

        public Builder(@NonNull Path projectDir) {
            this.projectDir = projectDir;
        }

        @NonNull
        public Builder withPlugin(@NonNull Plugin plugin) {
            this.plugin = plugin;
            return this;
        }

        @NonNull
        public Builder withParentProject(@NonNull Project parentProject) {
            this.parentProject = parentProject;
            return this;
        }

        @NonNull
        public Builder withProjectName(@NonNull String projectName) {
            this.projectName = projectName;
            return this;
        }

        @NonNull
        public Builder withProperty(@NonNull String property, @NonNull String value) {
            this.properties.put(property, value);
            return this;
        }

        @NonNull
        public <T> Builder withProperty(@NonNull Option<T> option, T value) {
            this.properties.put(option.getPropertyName(), String.valueOf(value));
            return this;
        }

        @NonNull
        public Project build() throws IOException {
            SdkLocator.setSdkTestDirectory(TestUtils.getSdk().toFile());

            Path manifest = projectDir.resolve("src/main/AndroidManifest.xml");

            String content;
            if (manifestContent.equals(MANIFEST_TEMPLATE)) {
                content = String.format(manifestContent, applicationId);
            } else {
                content = manifestContent;
            }
            Files.createDirectories(manifest.getParent());
            Files.write(manifest, ImmutableList.of(content));

            ProjectBuilder projectBuilder =
                    ProjectBuilder.builder()
                            .withProjectDir(projectDir.toFile())
                            .withName(projectName);

            if (parentProject != null) {
                projectBuilder.withParent(parentProject);
            }

            if (OsType.getHostOs() == OsType.WINDOWS) {
                // On Windows Gradle assumes the user home $PROJECT_DIR/userHome and unzips some DLLs
                // there that this JVM will load, so they cannot be deleted. Below we set things up so
                // that all tests use a single userHome directory and project dirs can be deleted.
                File tmpdir = new File(System.getProperty("java.io.tmpdir"));
                projectBuilder.withGradleUserHomeDir(new File(tmpdir, "testGradleUserHome"));
            }

            Project project = projectBuilder.build();

            for (Map.Entry<String, String> entry : this.properties.entrySet()) {
                project.getExtensions().getExtraProperties().set(entry.getKey(), entry.getValue());
            }

            prepareProject(project, properties);

            project.apply(ImmutableMap.of("plugin", plugin.getPluginName()));

            return project;
        }
    }

    static class FakeBuildEventsListenerRegistry implements BuildEventsListenerRegistry {
        @Override
        public void onTaskCompletion(Provider<? extends OperationCompletionListener> provider) {}
    }

    public static void prepareProject(
            @NonNull Project project, @NonNull Map<String, String> gradleProperties) {
        try {
            addFakeService(project);
            loadGradleProperties(project, gradleProperties);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * In Gradle 6.7-rc-1 BuildEventsListenerRegistry service is not created in we need it in order
     * to instantiate AGP. This creates a fake one and injects it - http://b/168630734.
     */
    private static void addFakeService(Project project) {
        try {
            ProjectScopeServices gss =
                    (ProjectScopeServices) ((DefaultProject) project).getServices();

            Field state = ProjectScopeServices.class.getSuperclass().getDeclaredField("state");
            state.setAccessible(true);
            AtomicReference<Object> stateValue = (AtomicReference<Object>) state.get(gss);
            Class<?> enumClass = Class.forName(DefaultServiceRegistry.class.getName() + "$State");
            stateValue.set(enumClass.getEnumConstants()[0]);

            // add service and set state so that future mutations are not allowed
            gss.add(BuildEventsListenerRegistry.class, new FakeBuildEventsListenerRegistry());
            stateValue.set(enumClass.getEnumConstants()[1]);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // TODO(bingran) remove this workaround when gradle issue #13122 is fixed
    // https://github.com/gradle/gradle/issues/13122
    private static void loadGradleProperties(
            @NonNull Project project, @NonNull Map<String, String> gradleProperties)
            throws IOException {
        File propertiesFile = new File(project.getProjectDir(), "gradle.properties");
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : gradleProperties.entrySet()) {
            stringBuilder
                    .append(entry.getKey())
                    .append("=")
                    .append(entry.getValue())
                    .append(System.lineSeparator());
        }
        Files.write(propertiesFile.toPath(), stringBuilder.toString().getBytes());
        ((DefaultProject) project)
                .getServices()
                .get(GradlePropertiesController.class)
                .loadGradlePropertiesFrom(project.getProjectDir());
    }
}
