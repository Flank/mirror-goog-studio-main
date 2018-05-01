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

package com.android.build.gradle.internal.tasks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.build.gradle.tasks.VerifyLibraryResourcesTask;
import com.android.builder.internal.aapt.AaptException;
import com.android.ide.common.resources.CompileResourceRequest;
import com.android.ide.common.resources.FileStatus;
import com.android.ide.common.resources.QueueableResourceCompiler;
import com.android.utils.FileUtils;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/*
 * Unit tests for {@link VerifyLibraryResourcesTask}.
 */
public class VerifyLibraryResourcesTaskTest {

    private static class FakeAapt implements QueueableResourceCompiler {

        @NonNull
        @Override
        public ListenableFuture<File> compile(@NonNull CompileResourceRequest request)
                throws Exception {
            File outputPath = compileOutputFor(request);
            Files.copy(request.getInputFile(), outputPath);
            return Futures.immediateFuture(outputPath);
        }

        @Override
        public void close() {}

        @Override
        public File compileOutputFor(@NonNull CompileResourceRequest request) {
            return new File(request.getOutputDirectory(), request.getInputFile().getName() + "-c");
        }

    }

    /** Temporary folder to use in tests. */
    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void directoriesShouldBeIgnored()
            throws IOException, InterruptedException, ExecutionException, AaptException {
        Map<File, FileStatus> inputs = new HashMap<>();
        File mergedDir = new File(mTemporaryFolder.newFolder("merged"), "release");
        FileUtils.mkdirs(mergedDir);

        File file = new File(new File(mergedDir, "values"), "file.xml");
        FileUtils.createFile(file, "content");
        assertTrue(file.exists());
        inputs.put(file, FileStatus.NEW);

        File directory = new File(mergedDir, "layout");
        FileUtils.mkdirs(directory);
        assertTrue(directory.exists());
        inputs.put(directory, FileStatus.NEW);

        File outputDir = mTemporaryFolder.newFolder("output");
        QueueableResourceCompiler aapt = new FakeAapt();

        VerifyLibraryResourcesTask.compileResources(inputs, outputDir, aapt, null, null, mergedDir);

        File fileOut = aapt.compileOutputFor(new CompileResourceRequest(file, outputDir, "values"));
        assertTrue(fileOut.exists());

        File dirOut =
                aapt.compileOutputFor(
                        new CompileResourceRequest(directory, outputDir, mergedDir.getName()));
        assertFalse(dirOut.exists());
    }

    @Test
    public void manifestShouldNotBeCompiled()
            throws IOException, InterruptedException, ExecutionException, AaptException {
        Map<File, FileStatus> inputs = new HashMap<>();
        File mergedDir = new File(mTemporaryFolder.newFolder("merged"), "release");
        FileUtils.mkdirs(mergedDir);

        File file = new File(new File(mergedDir, "values"), "file.xml");
        FileUtils.createFile(file, "content");
        assertTrue(file.exists());
        inputs.put(file, FileStatus.NEW);

        File manifest =
                new File(mTemporaryFolder.newFolder("merged_manifest"), "AndroidManifest.xml");
        FileUtils.createFile(manifest, "manifest content");
        assertTrue(manifest.exists());
        inputs.put(manifest, FileStatus.NEW);

        File outputDir = mTemporaryFolder.newFolder("output");
        QueueableResourceCompiler aapt = new FakeAapt();

        VerifyLibraryResourcesTask.compileResources(inputs, outputDir, aapt, null, null, mergedDir);

        File fileOut = aapt.compileOutputFor(new CompileResourceRequest(file, outputDir, "values"));
        assertTrue(fileOut.exists());

        // Real AAPT would fail trying to compile the manifest, but the fake one would just copy it
        // so we need to check that it wasn't copied into the output directory.
        File manifestOut =
                aapt.compileOutputFor(
                        new CompileResourceRequest(manifest, outputDir, "merged_manifest"));
        assertFalse(manifestOut.exists());
    }
}
