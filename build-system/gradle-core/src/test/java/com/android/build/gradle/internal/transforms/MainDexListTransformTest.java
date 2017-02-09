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

package com.android.build.gradle.internal.transforms;

import static com.google.common.truth.Truth.assertThat;

import com.android.testutils.TestInputsGenerator;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MainDexListTransformTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void dxDirectoryHandling() throws Exception {
        Path dir = temporaryFolder.newFolder("dir").toPath();
        Path dirInput = dir.resolve("dirInput");
        Path jarInput = dir.resolve("jarInput.jar");
        TestInputsGenerator.dirWithEmptyClasses(
                dirInput, ImmutableList.of("com/example/Foo", "com/example/A"));
        TestInputsGenerator.jarWithEmptyClasses(jarInput, ImmutableList.of("com/example/Bar"));

        Path entryPoints = dir.resolve("entrypoint.jar");
        TestInputsGenerator.writeJarWithEmptyEntries(
                entryPoints, ImmutableList.of("com/example/Foo.class", "com/example/Bar.class"));

        Path userKeepClasses = dir.resolve("addtions.txt");
        Files.write(userKeepClasses, ImmutableList.of("com/example/Baz.class"));

        Set<String> keepList =
                MainDexListTransform.computeList(
                        Stream.of(jarInput, dirInput).map(Path::toFile).collect(Collectors.toSet()),
                        entryPoints.toFile(),
                        userKeepClasses.toFile(),
                        true);

        assertThat(keepList)
                .containsExactly(
                        "com/example/Foo.class", "com/example/Bar.class", "com/example/Baz.class");
    }
}
