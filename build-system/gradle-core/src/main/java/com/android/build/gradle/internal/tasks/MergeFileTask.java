/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/** Task to merge files. This appends all the files together into an output file. */
public class MergeFileTask extends AndroidVariantTask {

    private FileCollection mInputFiles;

    private Provider<RegularFile> mOutputFile;

    @TaskAction
    public void mergeFiles() throws IOException {

        Set<File> inputFiles = getInputFiles().getFiles();
        File output = getOutputFile().get().getAsFile();

        // filter out any non-existent files
        List<File> existingFiles =
                inputFiles.stream().filter(File::isFile).collect(Collectors.toList());

        if (existingFiles.size() == 1) {
            Files.copy(existingFiles.iterator().next(), output);
            return;
        }

        // first delete the current file
        FileUtils.deleteIfExists(output);

        // no input? done.
        if (existingFiles.isEmpty()) {
            return;
        }

        // otherwise put the all the files together
        for (File file : existingFiles) {
            String content = Files.toString(file, Charsets.UTF_8);
            Files.append(content, output, Charsets.UTF_8);
            Files.append("\n", output, Charsets.UTF_8);
        }
    }

    @InputFiles
    public FileCollection getInputFiles() {
        return mInputFiles;
    }

    public void setInputFiles(FileCollection inputFiles) {
        mInputFiles = inputFiles;
    }

    @OutputFile
    public Provider<RegularFile> getOutputFile() {
        return mOutputFile;
    }

    public void setOutputFile(Provider<RegularFile> outputFile) {
        mOutputFile = outputFile;
    }

}
