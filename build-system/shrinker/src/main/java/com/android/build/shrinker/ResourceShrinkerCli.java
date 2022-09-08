/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.shrinker;

import com.android.build.shrinker.gatherer.ProtoResourceTableGatherer;
import com.android.build.shrinker.gatherer.ResourcesGatherer;
import com.android.build.shrinker.graph.ProtoResourcesGraphBuilder;
import com.android.build.shrinker.usages.DexUsageRecorder;
import com.android.build.shrinker.usages.ProtoAndroidManifestUsageRecorder;
import com.android.build.shrinker.usages.ResourceUsageRecorder;
import com.android.build.shrinker.usages.ToolsAttributeUsageRecorder;
import com.android.utils.FileUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

public class ResourceShrinkerCli {

    private static final String INPUT_ARG = "--input";
    private static final String OUTPUT_ARG = "--output";
    private static final String RES_ARG = "--raw_resources";
    private static final String HELP_ARG = "--help";

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String RESOURCES_PB = "resources.pb";
    private static final String RES_FOLDER = "res";

    private static class Options {
        private String input;
        private String output;
        private final List<String> rawResources = new ArrayList<>();
        private boolean help;

        private Options() {}

        public static Options parseOptions(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.startsWith(INPUT_ARG)) {
                    i++;
                    if (i == args.length) {
                        throw new ResourceShrinkingFailedException("No argument given for input");
                    }
                    if (options.input != null) {
                        throw new ResourceShrinkingFailedException(
                                "More than one input not supported");
                    }
                    options.input = args[i];
                } else if (arg.startsWith(OUTPUT_ARG)) {
                    i++;
                    if (i == args.length) {
                        throw new ResourceShrinkingFailedException("No argument given for output");
                    }
                    if (options.output != null) {
                        throw new ResourceShrinkingFailedException(
                                "More than one output not supported");
                    }
                    options.output = args[i];
                } else if (arg.startsWith(RES_ARG)) {
                    i++;
                    if (i == args.length) {
                        throw new ResourceShrinkingFailedException(
                                "No argument given for raw_resources");
                    }
                    options.rawResources.add(args[i]);
                } else if (arg.equals(HELP_ARG)) {
                    options.help = true;
                } else {
                    throw new ResourceShrinkingFailedException("Unknown argument " + arg);
                }
            }
            return options;
        }

        public String getInput() {
            return input;
        }

        public String getOutput() {
            return output;
        }

        public List<String> getRawResources() {
            return rawResources;
        }

        public boolean isHelp() {
            return help;
        }
    }

    public static void main(String[] args) {
        try {
            Options options = Options.parseOptions(args);
            if (options.isHelp()) {
                printUsage();
                return;
            }
            validateOptions(options);
            runResourceShrinking(options);
            System.out.println("Shrunken apk stored in:\n" + options.getOutput());
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new ResourceShrinkingFailedException(
                    "Failed running resource shrinking: " + e.getMessage(), e);
        }
    }

    private static void runResourceShrinking(Options options)
            throws IOException, ParserConfigurationException, SAXException {
        validateInput(options.getInput());
        Path protoApk = Paths.get(options.getInput());
        Path protoApkOut = Paths.get(options.getOutput());
        FileSystem fileSystemProto = FileUtils.createZipFilesystem(protoApk);

        List<ResourceUsageRecorder> resourceUsageRecorders = new ArrayList<>();
        resourceUsageRecorders.add(new DexUsageRecorder(fileSystemProto.getPath("")));
        resourceUsageRecorders.add(
                new ProtoAndroidManifestUsageRecorder(
                        fileSystemProto.getPath(ANDROID_MANIFEST_XML)));
        for (String rawResource : options.getRawResources()) {
            resourceUsageRecorders.add(new ToolsAttributeUsageRecorder(Paths.get(rawResource)));
        }
        ResourcesGatherer gatherer =
                new ProtoResourceTableGatherer(fileSystemProto.getPath(RESOURCES_PB));
        ProtoResourcesGraphBuilder res =
                new ProtoResourcesGraphBuilder(
                        fileSystemProto.getPath(RES_FOLDER), fileSystemProto.getPath(RESOURCES_PB));
        ResourceShrinker resourceShrinker =
                new ResourceShrinkerImpl(
                        List.of(gatherer),
                        null,
                        resourceUsageRecorders,
                        List.of(res),
                        NoDebugReporter.INSTANCE, // TODO(b/245721222): Add log output options
                        false, // TODO(b/245721267): Add support for bundles
                        true);
        resourceShrinker.analyze();
        resourceShrinker.rewriteResourcesInApkFormat(
                protoApk.toFile(), protoApkOut.toFile(), LinkedResourcesFormat.PROTO);
    }

    private static void validateInput(String input) throws IOException {
        ZipFile zipfile = new ZipFile(input);
        if (zipfile.getEntry(ANDROID_MANIFEST_XML) == null) {
            throw new ResourceShrinkingFailedException(
                    "Input must include " + ANDROID_MANIFEST_XML);
        }
        if (zipfile.getEntry(RESOURCES_PB) == null) {
            throw new ResourceShrinkingFailedException(
                    "Input must include "
                            + RESOURCES_PB
                            + ". Did you not convert the input apk"
                            + " to proto?");
        }
        if (zipfile.stream().noneMatch(zipEntry -> zipEntry.getName().startsWith(RES_FOLDER))) {
            throw new ResourceShrinkingFailedException(
                    "Input must include a " + RES_FOLDER + " folder");
        }
    }

    private static void validateFileExists(String file) {
        if (!Paths.get(file).toFile().exists()) {
            throw new RuntimeException("Can't find file: " + file);
        }
    }

    private static void validateOptions(Options options) {
        if (options.getInput() == null) {
            throw new ResourceShrinkingFailedException("No input given.");
        }
        if (options.getOutput() == null) {
            throw new ResourceShrinkingFailedException("No output destination given.");
        }
        validateFileExists(options.getInput());
        for (String rawResource : options.getRawResources()) {
            validateFileExists(rawResource);
        }
    }

    private static void printUsage() {
        PrintStream out = System.err;
        out.println("Usage:");
        out.println("  resourceshrinker ");
        out.println("    --input <input-file>");
        out.println("    --output <output-file>");
        out.println("    --raw_resource <xml-file or res directory>");
        out.println("      optional, more than one raw_resoures argument might be given");
        out.println("    --help prints this help message");
    }

    private static class ResourceShrinkingFailedException extends RuntimeException {
        public ResourceShrinkingFailedException(String message) {
            super(message);
        }

        public ResourceShrinkingFailedException(String message, Exception e) {
            super(message, e);
        }
    }
}
