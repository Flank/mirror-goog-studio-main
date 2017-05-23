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

package com.android.tools.apk.analyzer;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.NullLogger;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionDescriptor;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import joptsimple.internal.Rows;

public class ApkAnalyzerCli {
    private static final String TOOLSDIR = "com.android.sdklib.toolsdir";

    private static final String FLAG_FILES_ONLY = "files-only";
    private static final String FLAG_TYPE = "type";
    private static final String FLAG_PACKAGE = "package";
    private static final String FLAG_NAME = "name";
    private static final String FLAG_CONFIG = "config";
    private static final String FLAG_FILES = "files";
    private static final String FLAG_PROGUARD_USAGES = "proguard-usages";
    private static final String FLAG_PROGUARD_MAPPINGS = "proguard-mappings";
    private static final String FLAG_PROGUARD_SEEDS = "proguard-seeds";
    private static final String FLAG_PROGUARD_FOLDER = "proguard-folder";
    private static final String FLAG_SHOW_DEFINED_ONLY = "defined-only";
    private static final String FLAG_SHOW_REMOVED = "show-removed";
    private static final String FLAG_CLASS = "class";
    private static final String FLAG_METHOD = "method";
    private static final String FLAG_NOT_REQUIRED = "not-required";
    private static final String FLAG_PATCH_SIZE = "patch-size";
    private static final String FLAG_FILE_PATH = "file";
    private static final String FLAG_DIFF_ONLY = "different-only";
    private static final String FLAG_RAW_SIZE = "raw-size";
    private static final String FLAG_DOWNLOAD_SIZE = "download-size";
    private static final String FLAG_HUMAN_READABLE = "human-readable";
    private static final String APKANALYZER = "apkanalyzer";
    private static final String SUBJECT_APK = "apk";
    private static final String SUBJECT_FILES = "files";
    private static final String SUBJECT_MANIFEST = "manifest";
    private static final String SUBJECT_DEX = "dex";
    private static final String SUBJECT_RESOURCES = "resources";
    private static final String ACTION_SUMMARY = "summary";
    private static final String ACTION_RAW_SIZE = "file-size";
    private static final String ACTION_DOWNLOAD_SIZE = "download-size";
    private static final String ACTION_LIST = "list";
    private static final String ACTION_CAT = "cat";
    private static final String ACTION_PRINT = "print";
    private static final String ACTION_APPLICATION_ID = "application-id";
    private static final String ACTION_VERSION_NAME = "version-name";
    private static final String ACTION_VERSION_CODE = "version-code";
    private static final String ACTION_MIN_SDK = "min-sdk";
    private static final String ACTION_TARGET_SDK = "target-sdk";
    private static final String ACTION_PERMISSIONS = "permissions";
    private static final String ACTION_DEBUGGABLE = "debuggable";
    private static final String ACTION_REFERENCES = "references";
    private static final String ACTION_PACKAGES = "packages";
    private static final String ACTION_CODE = "code";
    private static final String ACTION_XML = "xml";
    private static final String ACTION_CONFIGS = "configs";
    private static final String ACTION_VALUE = "value";
    private static final String ACTION_FEATURES = "features";
    private static final String ACTION_COMPARE = "compare";
    private static final String ACTION_NAMES = "names";

    private final PrintStream out;
    private final PrintStream err;
    private final ApkAnalyzerImpl impl;

    private static final class HelpFormatter extends BuiltinHelpFormatter {
        /**
         * Makes a formatter with a given overall row width and column separator width.
         *
         * @param desiredOverallWidth how many characters wide to make the overall help display
         * @param desiredColumnSeparatorWidth how many characters wide to make the separation
         *     between option column and
         */
        public HelpFormatter() {
            super(120, 2);
        }

        @Override
        protected boolean shouldShowNonOptionArgumentDisplay(OptionDescriptor nonOptionDescriptor) {
            return false;
        }
    };

    public ApkAnalyzerCli(
            @NonNull PrintStream out, @NonNull PrintStream err, ApkAnalyzerImpl impl) {
        this.out = out;
        this.err = err;
        this.impl = impl;
    }

    public static void main(String[] args) {
        ApkAnalyzerCli instance =
                new ApkAnalyzerCli(
                        System.out,
                        System.err,
                        new ApkAnalyzerImpl(System.out, getAaptInvokerFromSdk(null)));
        instance.run(args);
    }

    @VisibleForTesting
    void run(String... args) {
        OptionParser verbParser = new OptionParser();
        verbParser.posixlyCorrect(true);
        verbParser.allowsUnrecognizedOptions();
        OptionSpecBuilder humanReadableSpec =
                verbParser.accepts(FLAG_HUMAN_READABLE, "Print sizes in human readable format");
        NonOptionArgumentSpec<String> verbSpec = verbParser.nonOptions().ofType(String.class);
        verbParser.formatHelpWith(new HelpFormatter());

        OptionSet parsed = verbParser.parse(args);
        List<String> list = parsed.valuesOf(verbSpec);

        if (list.isEmpty()) {
            printArgsList(null);
        } else if (list.size() == 1) {
            printArgsList(list.get(0));
        } else {
            List<Action> actions = Action.findActions(list.get(0), list.get(1));
            if (actions.isEmpty()) {
                actions = Action.findActions(list.get(0), null);
                if (actions.isEmpty()) {
                    printArgsList(null);
                } else {
                    printArgsList(list.get(0));
                }
            } else {
                impl.setHumanReadableFlag(parsed.has(humanReadableSpec));
                try {
                    actions.get(0)
                            .execute(out, err, impl, Arrays.copyOfRange(args, parsed.has(humanReadableSpec)?3:2, args.length));
                } catch (RuntimeException e) {
                    if (e.getCause() instanceof OptionException) {
                        err.println();
                        err.println("ERROR: " + e.getCause().getMessage());
                    } else {
                        err.println();
                        err.println("ERROR: " + e.getMessage());
                    }
                    exit(1);
                }
                return;
            }
        }

        try {
            err.println(
                    "Usage:"
                            + System.lineSeparator()
                            + APKANALYZER
                            + " [global options] <subject> <verb> [options] <apk> [<apk2>]"
                            + System.lineSeparator());
            verbParser.printHelpOn(err);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void exit(int code) {
        System.exit(code);
    }

    private void printArgsList(@Nullable String subject) {
        if (subject == null) {
            String subjects =
                    Arrays.stream(Action.values())
                            .map(action -> action.getSubject())
                            .distinct()
                            .collect(Collectors.joining(", "));
            err.println("Subject must be one of: " + subjects);
            err.println();
            Rows rows = new Rows(120, 2);
            for (Action action : Action.values()) {
                rows.add(action.getSubject() + " " + action.getVerb(), action.getDescription());
            }
            rows.fitToWidth();
            err.println(rows.render());
        } else {
            List<Action> actions = Action.findActions(subject, null);
            String verbs =
                    actions.stream()
                            .map(action -> action.getVerb())
                            .collect(Collectors.joining(", "));
            err.println("Verb must be one of: " + verbs);
            err.println();
            for (Action action : actions) {
                err.println("==============================");
                err.println(action.getSubject() + " " + action.getVerb() + ":");
                err.println(action.getDescription());
                err.println();
                try {
                    action.getParser().printHelpOn(err);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                err.println();
            }
        }
    }

    public static AaptInvoker getAaptInvokerFromSdk(@Nullable String osSdkFolder) {
        if (osSdkFolder == null) {
            // We get passed a property for the tools dir
            String toolsDirProp = System.getProperty(TOOLSDIR);
            if (toolsDirProp == null) {
                // for debugging, it's easier to override using the process environment
                toolsDirProp = System.getenv(TOOLSDIR);
            }

            if (toolsDirProp != null) {
                // got back a level for the SDK folder
                File tools;
                if (!toolsDirProp.isEmpty()) {
                    try {
                        tools = new File(toolsDirProp).getCanonicalFile();
                        osSdkFolder = tools.getParent();
                    } catch (IOException e) {
                        // try using "." below
                    }
                }
                if (osSdkFolder == null) {
                    try {
                        tools = new File(".").getCanonicalFile();
                        osSdkFolder = tools.getParent();
                    } catch (IOException e) {
                        // Will print an error below since mSdkFolder is not defined
                    }
                }
            }

            if (osSdkFolder == null) {
                String cmdName =
                        APKANALYZER
                                + (System.getProperty("os.name").startsWith("Windows")
                                        ? ".bat"
                                        : "");

                throw new IllegalStateException(
                        String.format(
                                "The tools directory property is not set, please make sure you are "
                                        + "executing %1$s",
                                cmdName));
            }
        }
        AndroidSdkHandler sdkHandler = AndroidSdkHandler.getInstance(new File(osSdkFolder));
        return new AaptInvoker(sdkHandler, new NullLogger());
    }

    static enum Action {
        APK_SUMMARY(
                SUBJECT_APK,
                ACTION_SUMMARY,
                "Prints the application Id, version code and version name.") {

            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.apkSummary(opts.valueOf(getFileSpec()).toPath());
            }
        },
        APK_RAW_SIZE(SUBJECT_APK, ACTION_RAW_SIZE, "Prints the file size of the APK.") {
            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.apkRawSize(opts.valueOf(getFileSpec()).toPath());
            }
        },
        APK_DOWNLOAD_SIZE(
                SUBJECT_APK,
                ACTION_DOWNLOAD_SIZE,
                "Prints an estimate of the download size of the APK.") {
            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionSet opts = parseOrPrintHelp(getParser(), err, args);
                impl.apkDownloadSize(opts.valueOf(getFileSpec()).toPath());
            }
        },
        APK_FEATURES(SUBJECT_APK, ACTION_FEATURES, "Prints features used by the APK.") {

            @Nullable public OptionSpecBuilder notRequiredSpec;
            @Nullable private OptionParser parser;

            @NonNull
            @Override
            public OptionParser getParser() {
                if (parser == null) {
                    parser = super.getParser();
                    notRequiredSpec =
                            parser.accepts(
                                    FLAG_NOT_REQUIRED, "Include features marked as not required");
                }
                return parser;
            }

            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.apkFeatures(opts.valueOf(getFileSpec()).toPath(), opts.has(notRequiredSpec));
            }
        },
        APK_COMPARE(SUBJECT_APK, ACTION_COMPARE, "Compares the sizes of two APKs.") {

            @Nullable private OptionSpecBuilder diffOnlySpec;
            @Nullable private OptionSpecBuilder filesOnlySpec;
            @Nullable private OptionSpecBuilder patchSpec;
            @Nullable private OptionParser parser;

            @NonNull
            @Override
            public OptionParser getParser() {
                if (parser == null) {
                    parser = super.getParser();
                    patchSpec =
                            parser.accepts(
                                    FLAG_PATCH_SIZE,
                                    "Show an estimate of the file-by-file patch instead of raw difference.");
                    filesOnlySpec =
                            parser.accepts(
                                    FLAG_FILES_ONLY, "Don't print directory entries in output.");
                    diffOnlySpec =
                            parser.accepts(
                                    FLAG_DIFF_ONLY,
                                    "Only print directories/files with differences.");
                }
                return parser;
            }

            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                List<File> files = opts.valuesOf(getFileSpec());
                if (files.size() < 2) {
                    throw new RuntimeException(
                            "This method requires two APK paths - old_apk new_apk");
                }
                impl.apkCompare(
                        files.get(0).toPath(),
                        files.get(1).toPath(),
                        opts.has(patchSpec),
                        opts.has(filesOnlySpec),
                        opts.has(diffOnlySpec));
            }
        },
        FILES_LIST(SUBJECT_FILES, ACTION_LIST, "Lists all files in the zip.") {
            @Nullable private OptionSpecBuilder rawSizeSpec;
            @Nullable private OptionSpecBuilder downloadSizeSpec;
            @Nullable private OptionSpecBuilder filesOnlySpec;
            @Nullable private OptionParser parser;

            @Override
            @NonNull
            public OptionParser getParser() {
                if (parser == null) {
                    parser = super.getParser();
                    rawSizeSpec = parser.accepts(FLAG_RAW_SIZE, "Show raw sizes of files.");
                    downloadSizeSpec =
                            parser.accepts(
                                    FLAG_DOWNLOAD_SIZE, "Show estimated download sizes of files.");
                    filesOnlySpec =
                            parser.accepts(
                                    FLAG_FILES_ONLY, "Don't include directory entries in output.");
                }
                return parser;
            }

            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = super.getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.filesList(
                        opts.valueOf(getFileSpec()).toPath(),
                        opts.has(rawSizeSpec),
                        opts.has(downloadSizeSpec),
                        opts.has(filesOnlySpec));
            }
        },
        FILES_CAT(SUBJECT_FILES, ACTION_CAT, "Prints the given file contents to stdout") {
            @Nullable private ArgumentAcceptingOptionSpec<String> filePathSpec;
            @Nullable private OptionParser parser;

            @NonNull
            @Override
            public OptionParser getParser() {
                if (parser == null) {
                    parser = super.getParser();
                    filePathSpec =
                            parser.accepts(FLAG_FILE_PATH, "File path within the APK.")
                                    .withRequiredArg()
                                    .ofType(String.class);
                }
                return parser;
            }

            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                assert filePathSpec != null;
                impl.filesCat(opts.valueOf(getFileSpec()).toPath(), opts.valueOf(filePathSpec));
            }
        },
        MANIFEST_PRINT(SUBJECT_MANIFEST, ACTION_PRINT, "Prints the manifest in XML format") {
            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.manifestPrint(opts.valueOf(getFileSpec()).toPath());
            }
        },
        MANIFEST_APPLICATION_ID(
                SUBJECT_MANIFEST, ACTION_APPLICATION_ID, "Prints the application id.") {

            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.manifestAppId(opts.valueOf(getFileSpec()).toPath());
            }
        },
        MANIFEST_VERSION_NAME(SUBJECT_MANIFEST, ACTION_VERSION_NAME, "Prints the version name.") {
            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.manifestVersionName(opts.valueOf(getFileSpec()).toPath());
            }
        },
        MANIFEST_VERSION_CODE(SUBJECT_MANIFEST, ACTION_VERSION_CODE, "Prints the version code.") {
            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.manifestVersionCode(opts.valueOf(getFileSpec()).toPath());
            }
        },
        MANIFEST_MIN_SDK(SUBJECT_MANIFEST, ACTION_MIN_SDK, "Prints the minimum sdk.") {
            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.manifestMinSdk(opts.valueOf(getFileSpec()).toPath());
            }
        },
        MANIFEST_TARGET_SDK(SUBJECT_MANIFEST, ACTION_TARGET_SDK, "Prints the target sdk") {
            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.manifestTargetSdk(opts.valueOf(getFileSpec()).toPath());
            }
        },
        MANIFEST_PERMISSIONS(
                SUBJECT_MANIFEST, ACTION_PERMISSIONS, "Prints a list of used permissions") {
            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.manifestPermissions(opts.valueOf(getFileSpec()).toPath());
            }
        },
        MANIFEST_DEBUGGABLE(
                SUBJECT_MANIFEST, ACTION_DEBUGGABLE, "Prints if the app is debuggable") {
            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.manifestDebuggable(opts.valueOf(getFileSpec()).toPath());
            }
        },
        DEX_LIST(SUBJECT_DEX, ACTION_LIST, "Prints a list of dex files in the APK") {
            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.dexList(opts.valueOf(getFileSpec()).toPath());
            }
        },
        DEX_REFERENCES(SUBJECT_DEX, ACTION_REFERENCES, "Prints number of references in dex files") {

            @Nullable private OptionParser parser;
            @Nullable ArgumentAcceptingOptionSpec<String> filesSpec;

            @Override
            @NonNull
            public OptionParser getParser() {
                if (parser == null) {
                    parser = super.getParser();
                    filesSpec =
                            parser.accepts(
                                            FLAG_FILES,
                                            "Dex file names to include. Default: all dex files.")
                                    .withRequiredArg()
                                    .ofType(String.class);
                }
                return parser;
            }

            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                assert filesSpec != null;
                impl.dexReferences(opts.valueOf(getFileSpec()).toPath(), opts.valuesOf(filesSpec));
            }
        },
        DEX_PACKAGES(
                SUBJECT_DEX,
                ACTION_PACKAGES,
                "Prints the class tree from DEX.\n"
                        + "P,C,M,F: indicates packages, classes methods, fields\n"
                        + "x,k,r,d: indicates removed, kept, referenced and defined nodes") {

            public ArgumentAcceptingOptionSpec<String> filesSpec;
            public OptionSpecBuilder showRemovedSpec;
            public OptionSpecBuilder definedOnlySpec;
            public ArgumentAcceptingOptionSpec<File> pgUsagesSpec;
            public ArgumentAcceptingOptionSpec<File> pgSeedsSpec;
            public ArgumentAcceptingOptionSpec<File> pgMappingSpec;
            public ArgumentAcceptingOptionSpec<File> pgFolderSpec;
            @Nullable OptionParser parser;

            @NonNull
            @Override
            public OptionParser getParser() {
                if (parser == null) {
                    parser = super.getParser();
                    pgFolderSpec =
                            parser.accepts(
                                            FLAG_PROGUARD_FOLDER,
                                            "The Proguard output folder to search for mappings.")
                                    .withRequiredArg()
                                    .ofType(File.class);
                    pgMappingSpec =
                            parser.accepts(FLAG_PROGUARD_MAPPINGS, "The Proguard mappings file.")
                                    .withRequiredArg()
                                    .ofType(File.class);
                    pgSeedsSpec =
                            parser.accepts(FLAG_PROGUARD_SEEDS, "The Proguard seeds file.")
                                    .withRequiredArg()
                                    .ofType(File.class);
                    pgUsagesSpec =
                            parser.accepts(FLAG_PROGUARD_USAGES, "The Proguard usages file.")
                                    .withRequiredArg()
                                    .ofType(File.class);
                    definedOnlySpec =
                            parser.accepts(
                                    FLAG_SHOW_DEFINED_ONLY,
                                    "Only include classes defined in the APK in the output.");
                    showRemovedSpec =
                            parser.accepts(
                                    FLAG_SHOW_REMOVED,
                                    "Show classes and members that were removed by Proguard.");
                    filesSpec =
                            parser.accepts(
                                            FLAG_FILES,
                                            "Dex file names to include. Default: all dex files.")
                                    .withRequiredArg()
                                    .ofType(String.class);
                }
                return parser;
            }

            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.dexPackages(
                        opts.valueOf(getFileSpec()).toPath(),
                        opts.has(pgFolderSpec) ? opts.valueOf(pgFolderSpec).toPath() : null,
                        opts.has(pgMappingSpec) ? opts.valueOf(pgMappingSpec).toPath() : null,
                        opts.has(pgSeedsSpec) ? opts.valueOf(pgSeedsSpec).toPath() : null,
                        opts.has(pgUsagesSpec) ? opts.valueOf(pgUsagesSpec).toPath() : null,
                        opts.has(definedOnlySpec),
                        opts.has(showRemovedSpec),
                        opts.valuesOf(filesSpec));
            }
        },
        DEX_CODE(
                SUBJECT_DEX,
                ACTION_CODE,
                "Prints the bytecode of a class or method in smali format") {
            public ArgumentAcceptingOptionSpec<String> classSpec;
            public ArgumentAcceptingOptionSpec<String> methodSpec;
            @Nullable public OptionParser parser;

            @NonNull
            @Override
            public OptionParser getParser() {
                if (parser == null) {
                    parser = super.getParser();
                    classSpec =
                            parser.accepts(FLAG_CLASS, "Fully qualified class name to decompile.")
                                    .withRequiredArg()
                                    .ofType(String.class)
                                    .required();
                    methodSpec =
                            parser.accepts(
                                            FLAG_METHOD,
                                            "Method to decompile. Format: name(params)returnType, e.g. someMethod(Ljava/lang/String;I)V")
                                    .withRequiredArg()
                                    .ofType(String.class);
                }
                return parser;
            }

            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.dexCode(
                        opts.valueOf(getFileSpec()).toPath(),
                        opts.valueOf(classSpec),
                        opts.valueOf(methodSpec));
            }
        },
        RESOURCES_PACKAGES(
                SUBJECT_RESOURCES,
                ACTION_PACKAGES,
                "Prints a list of packages in resources table") {
            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.resPackages(opts.valueOf(getFileSpec()).toPath());
            }
        },
        RESOURCES_CONFIGS(
                SUBJECT_RESOURCES, ACTION_CONFIGS, "Prints a list of configurations for a type") {
            public ArgumentAcceptingOptionSpec<String> packageSpec;
            public ArgumentAcceptingOptionSpec<String> typeSpec;
            @Nullable public OptionParser parser;

            @NonNull
            @Override
            public OptionParser getParser() {
                if (parser == null) {
                    parser = super.getParser();
                    typeSpec =
                            parser.accepts(FLAG_TYPE, "The resource type, e.g. string")
                                    .withRequiredArg()
                                    .ofType(String.class)
                                    .required();
                    packageSpec =
                            parser.accepts(FLAG_PACKAGE, "The resource table package name")
                                    .withRequiredArg()
                                    .ofType(String.class);
                }
                return parser;
            }

            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();

                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.resConfigs(
                        opts.valueOf(getFileSpec()).toPath(),
                        opts.valueOf(typeSpec),
                        opts.valueOf(packageSpec));
            }
        },
        RESOURCES_VALUE(SUBJECT_RESOURCES, ACTION_VALUE, "Prints the given resource's value") {
            private ArgumentAcceptingOptionSpec<String> packageSpec;
            private ArgumentAcceptingOptionSpec<String> nameSpec;
            private ArgumentAcceptingOptionSpec<String> configSpec;
            private ArgumentAcceptingOptionSpec<String> typeSpec;
            @Nullable public OptionParser parser;

            @NonNull
            @Override
            public OptionParser getParser() {
                if (parser == null) {
                    parser = super.getParser();
                    typeSpec =
                            parser.accepts(FLAG_TYPE, "The resource type, e.g. string")
                                    .withRequiredArg()
                                    .ofType(String.class)
                                    .required();
                    configSpec =
                            parser.accepts(FLAG_CONFIG, "The resource configuration")
                                    .withRequiredArg()
                                    .ofType(String.class)
                                    .required();
                    nameSpec =
                            parser.accepts(FLAG_NAME, "The resource name")
                                    .withRequiredArg()
                                    .ofType(String.class)
                                    .required();
                    packageSpec =
                            parser.accepts(FLAG_PACKAGE, "The resource table package name")
                                    .withRequiredArg()
                                    .ofType(String.class);
                }
                return parser;
            }

            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                impl.resValue(
                        opts.valueOf(getFileSpec()).toPath(),
                        opts.valueOf(typeSpec),
                        opts.valueOf(configSpec),
                        opts.valueOf(nameSpec),
                        opts.valueOf(packageSpec));
            }
        },
        RESOURCES_NAMES(
                SUBJECT_RESOURCES, ACTION_NAMES, "Prints a list of resource names for a type") {
            @Nullable public OptionParser parser;
            @Nullable private ArgumentAcceptingOptionSpec<String> packageSpec;
            @Nullable private ArgumentAcceptingOptionSpec<String> configSpec;
            @Nullable private ArgumentAcceptingOptionSpec<String> typeSpec;

            @NonNull
            @Override
            public OptionParser getParser() {
                if (parser == null) {
                    parser = super.getParser();
                    typeSpec =
                            parser.accepts(FLAG_TYPE, "The resource type, e.g. string")
                                    .withRequiredArg()
                                    .ofType(String.class)
                                    .required();
                    configSpec =
                            parser.accepts(FLAG_CONFIG, "The resource configuration")
                                    .withRequiredArg()
                                    .ofType(String.class)
                                    .required();
                    packageSpec =
                            parser.accepts(FLAG_PACKAGE, "The resource table package name")
                                    .withRequiredArg()
                                    .ofType(String.class);
                }
                return parser;
            }

            @Override
            public void execute(
                    PrintStream out,
                    PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                assert typeSpec != null;
                assert configSpec != null;
                assert packageSpec != null;
                impl.resNames(
                        opts.valueOf(getFileSpec()).toPath(),
                        opts.valueOf(typeSpec),
                        opts.valueOf(configSpec),
                        opts.valueOf(packageSpec));
            }
        },
        RESOURCES_XML(
                SUBJECT_RESOURCES, ACTION_XML, "Prints the human readable form of a binary XML") {
            @Nullable public OptionParser parser;
            @Nullable private ArgumentAcceptingOptionSpec<String> filePathSpec;

            @NonNull
            @Override
            public OptionParser getParser() {
                if (parser == null) {
                    parser = super.getParser();
                    filePathSpec = parser
                            .accepts(FLAG_FILE_PATH, "File path within the APK.")
                            .withRequiredArg()
                            .ofType(String.class);
                }
                return parser;

            }

            @Override
            public void execute(PrintStream out, PrintStream err,
                    @NonNull ApkAnalyzerImpl impl,
                    @NonNull String... args) {
                OptionParser parser = getParser();
                OptionSet opts = parseOrPrintHelp(parser, err, args);
                assert filePathSpec != null;
                impl.resXml(
                        opts.valueOf(getFileSpec()).toPath(), opts.valueOf(filePathSpec));
            }
        },
        ;

        private final String description;
        private final String verb;
        private final String subject;
        private OptionParser parser;
        private NonOptionArgumentSpec<File> fileSpec;

        Action(String subject, String verb, String description) {
            this.subject = subject;
            this.verb = verb;
            this.description = description;
        }

        private void initParser(){
            parser = new OptionParser();
            parser.formatHelpWith(new HelpFormatter());
            fileSpec =
                    parser.nonOptions("apk").describedAs("APK file path").ofType(File.class);
        }

        @NonNull
        public OptionParser getParser(){
            if (parser == null){
                initParser();
            }
            return parser;
        }

        @NonNull
        public NonOptionArgumentSpec<File> getFileSpec(){
            if (parser == null){
                initParser();
            }
            return fileSpec;
        }

        public abstract void execute(
                PrintStream out, PrintStream err, @NonNull ApkAnalyzerImpl impl,
                @NonNull String... args);

        @NonNull
        public String getVerb() {
            return verb;
        }

        @NonNull
        public String getSubject() {
            return subject;
        }

        @NonNull
        public static List<Action> findActions(@NonNull String subject, @Nullable String verb) {
            ArrayList<Action> actions = new ArrayList<>();
            for (Action action : Action.values()) {
                if (subject.equals(action.subject) && (verb == null || verb.equals(action.verb))) {
                    actions.add(action);
                }
            }
            return actions;
        }

        public String getDescription() {
            return description;
        }

        private static OptionSet parseOrPrintHelp(@NonNull OptionParser parser, @NonNull PrintStream err, String... args) {
            try {
                OptionSet opts = parser.parse(args);
                List<?> files = opts.nonOptionArguments();
                if (files.isEmpty()) {
                    try {
                        parser.printHelpOn(err);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    throw new RuntimeException("You must specify an apk file.");
                }
                return opts;
            } catch (OptionException e) {
                try {
                    parser.printHelpOn(err);
                } catch (IOException e1) {
                    throw new UncheckedIOException(e1);
                }
                throw new RuntimeException(e);
            }
        }
    }
}
