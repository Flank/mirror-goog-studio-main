/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.bazel.lnzipper;

import static com.google.common.truth.Truth.assertThat;

import com.android.zipflinger.ZipArchive;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LnZipperTest {

    @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Test
    public void execute_createZip_withRegularFiles() throws IOException {
        Path fileA = newFile("fileA.txt", "foo");
        Path fileB = newFile("fileB.txt", "bar");
        Path output = tmpPath("output.zip");
        CommandLine commandLine =
                buildCmd(
                        ImmutableList.of(LnZipper.create),
                        ImmutableList.of(output.toString(), fileA.toString(), fileB.toString()));

        LnZipper lnZipper = new LnZipper(commandLine);
        lnZipper.execute();

        try (ZipArchive archive = new ZipArchive(output)) {
            assertThat(archive.listEntries()).containsExactly(fileA.toString(), fileB.toString());
        }
    }

    @Test
    public void execute_createZip_usingZipPaths() throws IOException {
        Path fileA = newFile("fileA.txt", "bar");
        Path output = tmpPath("output.zip");
        CommandLine commandLine =
                buildCmd(
                        ImmutableList.of(LnZipper.create),
                        ImmutableList.of(output.toString(), String.format("path/foo=%s", fileA)));

        LnZipper lnZipper = new LnZipper(commandLine);
        lnZipper.execute();

        try (ZipArchive archive = new ZipArchive(output)) {
            assertThat(archive.listEntries()).containsExactly("path/foo");
        }
    }

    @Test
    public void execute_createZip_withArgFile() throws IOException {
        Path fileA = newFile("fileA.txt", "foo");
        Path fileB = newFile("fileB.txt", "bar");
        Path argFile =
                createArgFile(
                        ImmutableList.of(
                                String.format("dir/fileA.txt=%s", fileA), fileB.toString()));
        Path output = tmpPath("output.zip");
        CommandLine commandLine =
                buildCmd(
                        ImmutableList.of(LnZipper.create),
                        ImmutableList.of(output.toString(), "@" + argFile));

        LnZipper lnZipper = new LnZipper(commandLine);
        lnZipper.execute();

        try (ZipArchive archive = new ZipArchive(output)) {
            assertThat(archive.listEntries()).containsExactly("dir/fileA.txt", fileB.toString());
        }
    }

    @Test
    public void execute_createZip_withSymlink() throws IOException {
        newFile("fileA.txt", "hello world");
        Path symlink = newSymlink("my-symlink", "fileA.txt");
        Path output = tmpPath("output.zip");
        CommandLine commandLine =
                buildCmd(
                        ImmutableList.of(LnZipper.create, LnZipper.symlinks),
                        ImmutableList.of(
                                output.toString(),
                                String.format("path/my-symlink=%s", symlink.toString())));

        LnZipper lnZipper = new LnZipper(commandLine);
        lnZipper.execute();

        try (ZipArchive archive = new ZipArchive(output)) {
            String symlinkTarget = new String(archive.getContent("path/my-symlink").array());
            assertThat(symlinkTarget).isEqualTo("fileA.txt");
        }
    }

    private Path tmpPath(String filename) {
        return Paths.get(tmpFolder.getRoot().toString(), filename);
    }

    private static CommandLine buildCmd(List<Option> options, List<String> args) {
        CommandLine.Builder builder = new CommandLine.Builder();
        for (Option opt : options) {
            builder.addOption(opt);
        }
        for (String arg : args) {
            builder.addArg(arg);
        }
        return builder.build();
    }

    private Path createArgFile(List<String> args) throws IOException {
        Path argFile = Paths.get(tmpFolder.getRoot().toString(), "argfile.res.lst");
        Files.write(argFile, args, StandardCharsets.UTF_8);
        return argFile;
    }

    private Path newFile(String name, String content) throws IOException {
        Path filepath = tmpFolder.newFile(name).toPath();
        Files.write(filepath, content.getBytes(StandardCharsets.UTF_8));
        return filepath;
    }

    private Path newSymlink(String name, String target) throws IOException {
        return Files.createSymbolicLink(
                Paths.get(tmpFolder.getRoot().toString(), name), Paths.get(target));
    }
}
