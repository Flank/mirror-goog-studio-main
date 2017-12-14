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

import static com.android.testutils.truth.MoreTruth.assertThat;

import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.fixtures.DirectWorkerExecutor;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Testing {@link JacocoTransform}. */
public class JacocoTransformTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private TestTransformOutputProvider outputProvider;
    private File outputDir;

    @Before
    public void setUp() throws IOException {
        outputDir = tmp.newFolder("out");
        outputProvider = new TestTransformOutputProvider(outputDir.toPath());
    }

    @Test
    public void testCopyFiles() throws IOException, TransformException, InterruptedException {

        File inputDir = tmp.newFolder("in");
        Path inputDirPath = inputDir.toPath();
        Files.createDirectory(inputDirPath.resolve("META-INF"));
        Files.createFile(inputDirPath.resolve("META-INF/copiedFile.kotlin_module"));
        Files.createFile(inputDirPath.resolve("META-INF/MANIFEST.MF"));

        TransformInput directoryInput =
                TransformTestHelper.directoryBuilder(inputDir)
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .setContentType(QualifiedContent.DefaultContentType.CLASSES)
                        .build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(ImmutableSet.of(directoryInput))
                        .setTransformOutputProvider(outputProvider)
                        .setGradleWorkerExecutor(new DirectWorkerExecutor())
                        .build();

        JacocoTransform transform = new JacocoTransform();
        transform.transform(invocation);

        // "in" is added by the output provider based on the name of the input.
        assertThat(new File(outputDir, "in/META-INF/copiedFile.kotlin_module")).exists();
        assertThat(new File(outputDir, "in/META-INF/MANIFEST.MF")).doesNotExist();
    }
}
