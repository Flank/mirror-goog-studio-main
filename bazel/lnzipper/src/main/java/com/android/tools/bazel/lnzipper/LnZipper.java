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

import com.android.zipflinger.FullFileSource;
import com.android.zipflinger.Source;
import com.android.zipflinger.Sources;
import com.android.zipflinger.ZipArchive;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Creates a ZIP archive, using zipflinger, optionally allowing the preservation of symbolic links.
 *
 * <p>If the zip_path is not given for a file, it is assumed to be the path of the file.
 *
 * <p>Usage: ziplinker [--compress] [--symlinks] [(@argfile | [zip_path=]file ...)]
 */
public class LnZipper {

    /** Entry point for the lnzipper binary. */
    public static void main(String[] args) {
        Options cliOptions = getCliOptions();
        HelpFormatter helpFormatter = new HelpFormatter();
        try {
            CommandLine commandLine = new DefaultParser().parse(cliOptions, args);
            new LnZipper(commandLine).execute();
        } catch (ParseException | IllegalArgumentException e) {
            if (e instanceof IllegalArgumentException) {
                System.err.printf("Error: %s\n", e.getMessage());
            }
            helpFormatter.printHelp(CMD_SYNTAX, cliOptions);
            System.exit(1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static final String CMD_SYNTAX =
            "ziplinker [options] foo.zip [(@argfile | [zip_path=]file ...)]";

    public static final Option compress =
            Option.builder("C")
                    .argName("compress")
                    .desc("Compress files when creating a new archive")
                    .build();

    public static final Option create =
            Option.builder("c").argName("create").desc("Create a new archive").build();

    public static final Option symlinks =
            Option.builder("s")
                    .argName("symlinks")
                    .longOpt("symlinks")
                    .desc("Whether to preserve symlinks in new archives")
                    .build();

    static Options getCliOptions() {
        Options opts = new Options();
        opts.addOption(compress);
        opts.addOption(create);
        opts.addOption(symlinks);
        return opts;
    }

    private final CommandLine cmdLine;

    public LnZipper(CommandLine cmdLine) {
        this.cmdLine = cmdLine;
    }

    public void execute() throws IOException {
        List<String> argList = cmdLine.getArgList();
        if (!cmdLine.hasOption(create.getOpt())) {
            throw new IllegalArgumentException("Only the create action is supported");
        }
        if (argList.size() < 1) {
            throw new IllegalArgumentException("Missing ZIP archive argument");
        }
        Path archive = Paths.get(argList.get(0));
        Map<String, Path> fileMap = getFileMapping(argList);

        createArchive(
                archive,
                fileMap,
                cmdLine.hasOption(compress.getOpt()),
                cmdLine.hasOption(symlinks.getOpt()));
    }

    private static void createArchive(
            Path dest, Map<String, Path> fileMap, boolean compress, boolean preserveSymlinks)
            throws IOException {
        try (ZipArchive archive = new ZipArchive(dest)) {
            BytesSourceFactory bytesSourceFactory =
                    preserveSymlinks
                            ? (file, entryName, compressionLevel) ->
                                    new FullFileSource(
                                            file,
                                            entryName,
                                            compressionLevel,
                                            FullFileSource.Symlink.DO_NOT_FOLLOW)
                            : Sources::from;
            int compressionLevel = compress ? Deflater.BEST_COMPRESSION : Deflater.NO_COMPRESSION;

            for (Map.Entry<String, Path> fileEntry : fileMap.entrySet()) {
                archive.add(
                        bytesSourceFactory.create(
                                fileEntry.getValue(), fileEntry.getKey(), compressionLevel));
            }
        }
    }

    /** Returns a mapping of ZIP entry name -> file path */
    private static Map<String, Path> getFileMapping(List<String> args) throws IOException {
        if (args.size() < 2) {
            throw new IllegalArgumentException("No file inputs given");
        }
        List<String> fileArgs = new ArrayList<>();
        if (args.get(1).startsWith("@")) {
            BufferedReader bufReader = Files.newBufferedReader(Paths.get(args.get(1).substring(1)));
            String line = bufReader.readLine();
            while (line != null) {
                fileArgs.add(line);
                line = bufReader.readLine();
            }
        } else {
            fileArgs.addAll(args.subList(1, args.size()));
        }
        Map<String, Path> fileMap = new HashMap<>();
        for (String fileArg : fileArgs) {
            // check if file arguments are in the format zip_path=file
            if (fileArg.contains("=")) {
                String[] split = fileArg.split("=");
                Path filePath = Paths.get(split[1]);
                fileMap.put(split[0], filePath);
            } else {
                Path filePath = Paths.get(fileArg);
                fileMap.put(fileArg, filePath);
            }
        }

        return Collections.unmodifiableMap(fileMap);
    }

    private interface BytesSourceFactory {

        Source create(Path file, String entryName, int compressionLevel) throws IOException;
    }
}
