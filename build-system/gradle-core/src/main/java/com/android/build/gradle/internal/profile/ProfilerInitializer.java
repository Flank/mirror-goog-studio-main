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

package com.android.build.gradle.internal.profile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.ProcessProfileWriterFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.gradle.api.Project;
import org.gradle.initialization.BuildCompletionListener;

/**
 * Initialize the {@link ProcessProfileWriterFactory} using a given project.
 *
 * <p>Is separate from {@code ProcessProfileWriterFactory} as {@code ProcessProfileWriterFactory}
 * does not depend on gradle classes.
 */
public final class ProfilerInitializer {

    private static final String PROFILE_DIRECTORY = "android-profile";

    private static final DateTimeFormatter PROFILE_FILE_NAME =
            DateTimeFormatter.ofPattern("'profile-'YYYY-MM-dd-HH-mm-ss-SSS'.rawproto'", Locale.US);

    private static final Object lock = new Object();

    @Nullable private static volatile RecordingBuildListener recordingBuildListener;

    private ProfilerInitializer() {
        //Static singleton class.
    }

    /**
     * Initialize the {@link ProcessProfileWriterFactory}. Idempotent.
     *
     * @param project the current Gradle {@link Project}.
     * @param projectOptions the options
     */
    public static void init(@NonNull Project project, @NonNull ProjectOptions projectOptions) {
        synchronized (lock) {
            //noinspection VariableNotUsedInsideIf
            if (recordingBuildListener != null) {
                return;
            }
            ProcessProfileWriterFactory.initialize(
                    project.getRootProject().getProjectDir(),
                    project.getGradle().getGradleVersion(),
                    new LoggerWrapper(project.getLogger()),
                    projectOptions.get(BooleanOption.ENABLE_PROFILE_JSON));
            recordingBuildListener = new RecordingBuildListener(ProcessProfileWriter.get());
            project.getGradle().addListener(recordingBuildListener);
        }

        project.getGradle().addListener(new ProfileShutdownListener(project));
    }

    private static final class ProfileShutdownListener implements BuildCompletionListener {

        private final Project project;

        ProfileShutdownListener(@NonNull Project project) {
            this.project = project;
        }

        @Override
        public void completed() {
            try {
                synchronized (lock) {
                    if (recordingBuildListener != null) {
                        project.getGradle().removeListener(recordingBuildListener);
                        recordingBuildListener = null;
                        ProcessProfileWriterFactory.shutdownAndWrite(
                                project.getRootProject()
                                        .getBuildDir()
                                        .toPath()
                                        .resolve(PROFILE_DIRECTORY)
                                        .resolve(PROFILE_FILE_NAME.format(LocalDateTime.now())));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}

