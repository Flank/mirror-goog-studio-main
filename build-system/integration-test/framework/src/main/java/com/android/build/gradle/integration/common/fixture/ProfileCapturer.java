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


package com.android.build.gradle.integration.common.fixture;

import com.android.annotations.NonNull;
import com.android.builder.utils.ExceptionRunnable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A helper class for finding and parsing GradleBuildProfile protos.
 *
 * <p>The way that our profiling works is that a flag is passed in to our Gradle plugin telling it
 * to enable profiling, and put the profiling results in a given directory. The job of this class is
 * to monitor that directory and give us any newly created profiles when we ask for them.
 *
 * <p>Usage:
 *
 * <pre>
 *     ProfileCapturer pc = new ProfileCapturer(Path.get("foo"));
 *     pc.getNewProfiles() // empty, because we haven't invoked Gradle at all
 *     // do some stuff here with the Gradle plugin, telling it to output to Path.get("foo")
 *     pc.getNewProfiles() // a list containing newly generated profiles
 * </pre>
 *
 * In tests, a new profile should be generated every time you use the {@code RunGradleTasks} class
 * with a benchmark recorder set.
 */
public final class ProfileCapturer {
    @NonNull private static final String PROFILE_SUFFIX = ".rawproto";

    @NonNull private Set<Path> knownProfiles = new HashSet<>();
    @NonNull private final Path profileDirectory;

    public ProfileCapturer(@NonNull Path profileDirectory) throws IOException {
        this.profileDirectory = profileDirectory;
        updateKnownProfiles();
    }

    public List<GradleBuildProfile> capture(ExceptionRunnable r) throws Exception {
        updateKnownProfiles();
        r.run();
        return findNewProfiles();
    }

    @NonNull
    public List<GradleBuildProfile> findNewProfiles() throws IOException {
        Set<Path> newProfiles = ImmutableSet.copyOf(Sets.difference(findProfiles(), knownProfiles));
        knownProfiles.addAll(newProfiles);

        if (newProfiles.isEmpty()) {
            return Collections.emptyList();
        }

        List<GradleBuildProfile> results = new ArrayList<>(newProfiles.size());
        for (Path path : newProfiles) {
            results.add(GradleBuildProfile.parseFrom(Files.readAllBytes(path)));
        }
        return results;
    }

    @NonNull
    private Set<Path> findProfiles() throws IOException {
        if (!Files.exists(profileDirectory)) {
            return Collections.emptySet();
        }

        return Files.walk(profileDirectory)
                .filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().endsWith(PROFILE_SUFFIX))
                .collect(Collectors.toSet());
    }

    private void updateKnownProfiles() throws IOException {
        knownProfiles.addAll(findProfiles());
    }
}
