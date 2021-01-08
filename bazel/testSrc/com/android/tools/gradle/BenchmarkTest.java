/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.gradle;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.diff.UnifiedDiff;
import com.android.tools.gradle.benchmarkassertions.BenchmarkProjectAssertion;
import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.PerfData;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

public class BenchmarkTest {

    private static final String ROOT = "prebuilts/studio/";

    private File distribution = null;
    private File repo = null;
    private String project = null;
    private String benchmarkName = null;
    private String benchmarkBaseName = null;
    private String benchmarkCodeType = null;
    private String benchmarkFlag = null;
    private String benchmarkSize = null;
    private String benchmarkType = null;
    private String testProjectGradleRootFromSourceRoot = ".";
    private List<String> setupDiffs = new ArrayList<>();
    private int warmUps = 0;
    private int iterations = 0;
    private int removeUpperOutliers = 0;
    private int removeLowerOutliers = 0;
    private List<String> tasks = new ArrayList<>();
    private List<String> startups = new ArrayList<>();
    private List<String> cleanups = new ArrayList<>();
    private List<File> mutations = new ArrayList<>();
    private List<String> mutationDiffs = new ArrayList<>();
    private List<BenchmarkProjectAssertion> pre_mutate_assertions = new ArrayList<>();
    private List<BenchmarkProjectAssertion> post_mutate_assertions = new ArrayList<>();
    private List<String> buildProperties = new ArrayList<>();
    private List<BenchmarkListener> listeners = new ArrayList<>();
    private boolean fromStudio = false;
    @Nullable private String agpVersion = null;
    private boolean failOnWarning = true;
    private boolean reuseTransformCache = true;
    private boolean useSeparateGradleHome = false;
    private boolean enableYourKit = false;
    private String yourKitAgentPath = null;
    private String yourKitLibraryPath = null;
    private String yourKitSettingsPath = null;

    @Before
    public void setUp() throws Exception {
        // See http://cs/android/prebuilts/studio/buildbenchmarks/scenarios.bzl for meaning of
        // these flags.
        String value;
        project = System.getProperty("project");
        distribution = getFileProperty("distribution");
        repo = getFileProperty("repo");

        benchmarkBaseName = getStringProperty("benchmark_base_name");
        benchmarkCodeType = getStringProperty("benchmark_code_type");
        benchmarkFlag = getStringProperty("benchmark_flag");
        benchmarkSize = getStringProperty("benchmark_size");
        benchmarkType = getStringProperty("benchmark_type");

        warmUps = getIntProperty("warmups");
        iterations = getIntProperty("iterations");
        removeUpperOutliers = getIntProperty("remove_upper_outliers");
        removeLowerOutliers = getIntProperty("remove_lower_outliers");

        value = System.getProperty("startup_task");
        if (value != null && !value.isEmpty()) {
            Collections.addAll(startups, value.split(","));
        }
        value = System.getProperty("task");
        if (value != null && !value.isEmpty()) {
            Collections.addAll(tasks, value.split(","));
        }
        value = System.getProperty("cleanup_task");
        if (value != null && !value.isEmpty()) {
            Collections.addAll(cleanups, value.split(","));
        }
        benchmarkName = System.getProperty("benchmark");
        value = System.getProperty("setup-diff");
        if (value != null && !value.isEmpty()) {
            Collections.addAll(setupDiffs, value.split(","));
        }
        value = System.getProperty("mutation");
        if (value != null && !value.isEmpty()) {
            for (String mutation : value.split(",")) {
                mutations.add(new File(mutation));
            }
        }
        value = System.getProperty("mutation-diff");
        if (value != null && !value.isEmpty()) {
            Collections.addAll(mutationDiffs, value.split(","));
        }
        value = System.getProperty("build_property");
        if (value != null && !value.isEmpty()) {
            Collections.addAll(buildProperties, value.split(","));
        }
        value = System.getProperty("listener");
        if (value != null && !value.isEmpty()) {
            for (String listener : value.split(",")) {
                listeners.add(locateListener(listener).newInstance());
            }
        }
        value = System.getProperty("from-studio");
        if (value != null && !value.isEmpty()) {
            fromStudio = Boolean.parseBoolean(value);
        }
        testProjectGradleRootFromSourceRoot = System.getProperty("gradle-root");
        value = System.getProperty("pre_mutate_assertion");
        if (value != null && !value.isEmpty()) {
            for (String assertion : value.split(",")) {
                pre_mutate_assertions.add(instantiateAssertion(assertion));
            }
        }
        value = System.getProperty("post_mutate_assertion");
        if (value != null && !value.isEmpty()) {
            for (String assertion : value.split(",")) {
                post_mutate_assertions.add(instantiateAssertion(assertion));
            }
        }
        value = System.getProperty("agp_version");
        if (value != null && !value.isEmpty()) {
            agpVersion = value;
        }
        value = System.getProperty("fail_on_warning");
        if (value != null && !value.isEmpty()) {
            failOnWarning = Boolean.parseBoolean(value);
        }
        value = System.getProperty("reuse_transform_cache");
        if (value != null && !value.isEmpty()) {
            reuseTransformCache = Boolean.parseBoolean(value);
        }
        value = System.getProperty("use_separate_gradle_home");
        if (value != null && !value.isEmpty()) {
            useSeparateGradleHome = Boolean.parseBoolean(value);
        }
        value = System.getProperty("enable_yourkit");
        if (value != null && !value.isEmpty()) {
            enableYourKit = Boolean.parseBoolean(value);
        }
        yourKitAgentPath = getStringProperty("yourkit_agent_path");
        yourKitLibraryPath = getStringProperty("yourkit_library_path");
        yourKitSettingsPath = getStringProperty("yourkit_settings");
    }

    @Nullable
    private static File getFileProperty(@NonNull String name) {
        String value;
        value = System.getProperty(name);
        if (value != null && !value.isEmpty()) {
            return new File(value);
        }
        return null;
    }

    @Nullable
    private static String getStringProperty(@NonNull String name) {
        String value;
        value = System.getProperty(name);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return null;
    }

    @Nullable
    private static Integer getIntProperty(@NonNull String name) {
        String value;
        value = System.getProperty(name);
        if (value != null && !value.isEmpty()) {
            return Integer.valueOf(value);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<? extends T> loadClass(Package relativeBase, String className)
            throws ClassNotFoundException {
        String fqcn =
                className.indexOf('.') != -1 ? className : relativeBase.getName() + "." + className;
        return (Class<? extends T>) BenchmarkTest.class.getClassLoader().loadClass(fqcn);
    }

    private static Class<? extends BenchmarkListener> locateListener(String className)
            throws ClassNotFoundException {
        return loadClass(BenchmarkTest.class.getPackage(), className);
    }

    private static BenchmarkProjectAssertion instantiateAssertion(String argString)
            throws ClassNotFoundException, IllegalAccessException, InvocationTargetException,
                    InstantiationException {
        List<String> args = new ArrayList<>(Splitter.on(';').splitToList(argString));
        Class<? extends BenchmarkProjectAssertion> assertionClass =
                loadClass(BenchmarkProjectAssertion.class.getPackage(), args.remove(0));

        Constructor<?>[] constructors = assertionClass.getConstructors();
        if (constructors.length != 1) {
            throw new RuntimeException(
                    "Expected exactly one constructor in BenchmarkProjectAssertion class "
                            + assertionClass);
        }
        if (constructors[0].getParameterCount() != args.size()) {
            throw new RuntimeException(
                    "Constructor in BenchmarkProjectAssertion class "
                            + assertionClass
                            + " has "
                            + constructors[0].getParameterCount()
                            + " parameters, but "
                            + args.size()
                            + " parameters passed ["
                            + argString
                            + "] (semi-colon separated)");
        }
        return (BenchmarkProjectAssertion) constructors[0].newInstance((Object[]) args.toArray());

    }

    private String getLocalGradleVersion() throws IOException {
        if (agpVersion != null) {
            return agpVersion;
        }
        try (FileInputStream fis = new FileInputStream("tools/buildSrc/base/version.properties")) {
            Properties properties = new Properties();
            properties.load(fis);
            return properties.getProperty("buildVersion");
        }
    }

    @Test
    public void run() throws Exception {

        BenchmarkRun benchmarkRun =
                new BenchmarkRun(warmUps, iterations, removeUpperOutliers, removeLowerOutliers);

        Benchmark.Builder benchmarkBuilder =
                new Benchmark.Builder(benchmarkName).setProject("Android Studio Gradle");
        ImmutableMap.Builder<String, String> mapBuilder = ImmutableMap.builder();
        mapBuilder.put("benchmarkBaseName", benchmarkBaseName);
        if (benchmarkCodeType != null) {
            mapBuilder.put("benchmarkCodeType", benchmarkCodeType);
        }
        if (benchmarkFlag != null) {
            mapBuilder.put("benchmarkFlag", benchmarkFlag);
        }
        if (benchmarkSize != null) {
            // temporary put both for migrating from one to the other.
            mapBuilder.put("benchmarkCategory", benchmarkSize);
            mapBuilder.put("benchmarkSize", benchmarkSize);
        }
        if (benchmarkType != null) {
            mapBuilder.put("benchmarkType", benchmarkType);
        }
        mapBuilder.put("fromStudio", Boolean.toString(fromStudio));
        mapBuilder.put("benchmarkHost", hostName());
        benchmarkBuilder.setMetadata(mapBuilder.build());

        Benchmark benchmark = benchmarkBuilder.build();

        File data = new File(ROOT + "buildbenchmarks/" + project);
        File out = new File(System.getenv("TEST_TMPDIR"), "tmp_gradle_out");
        File src = new File(System.getenv("TEST_TMPDIR"), "tmp_gradle_src");
        File home = new File(System.getenv("TEST_TMPDIR"), "tmp_home");
        home.mkdirs();

        Gradle.unzip(new File(data, "src.zip"), src);
        for (String setupDiff : setupDiffs) {
            UnifiedDiff diff = new UnifiedDiff(new File(data, setupDiff));
            diff.apply(src, 3);
        }

        mutations.addAll(
                mutationDiffs.stream().map(s -> new File(data, s)).collect(Collectors.toList()));
        UnifiedDiff[] diffs = new UnifiedDiff[mutations.size()];
        for (int i = 0; i < mutations.size(); i++) {
            diffs[i] = new UnifiedDiff(mutations.get(i));
        }

        File projectRoot = new File(src, testProjectGradleRootFromSourceRoot);
        addJvmArgs(
                new File(projectRoot, "gradle.properties"), enableYourKit, src, yourKitAgentPath);
        try (Gradle gradle = new Gradle(projectRoot, out, distribution)) {
            gradle.addRepo(repo);
            gradle.addRepo(new File(data, "repo.zip"));
            gradle.addArgument("-Dcom.android.gradle.version=" + getLocalGradleVersion());
            gradle.addArgument("-Duser.home=" + home.getAbsolutePath());
            if (fromStudio) {
                gradle.addArgument("-Pandroid.injected.invoked.from.ide=true");
                gradle.addArgument("-Pandroid.injected.testOnly=true");
                gradle.addArgument(
                        "-Pandroid.injected.build.api=10000"); // as high as possible so we never need to change it.
                gradle.addArgument("-Pandroid.injected.build.abi=arm64-v8a");
                gradle.addArgument("-Pandroid.injected.build.density=xhdpi");
                gradle.addArgument(
                        "-Pandroid.injected.attribution.file.location="
                                + new File(out, "attribution_out").getAbsolutePath());
            }
            if (failOnWarning) {
                gradle.addArgument("--warning-mode=fail");
            }
            if (enableYourKit) {
                gradle.withYourKit(new File(yourKitLibraryPath), new File(yourKitSettingsPath));
            }
            buildProperties.forEach(gradle::addArgument);

            listeners.forEach(it -> it.configure(home, gradle, benchmarkRun));

            gradle.run(startups);

            listeners.forEach(it -> it.benchmarkStarting(benchmark));

            for (int i = 0; i < benchmarkRun.warmUps + benchmarkRun.iterations; i++) {
                if (!reuseTransformCache) {
                    gradle.modifyBuildscriptClasspath();
                }

                if (!cleanups.isEmpty()) {
                    for (String cleanupTask : cleanups) {
                        gradle.run(cleanupTask);
                    }
                }

                if (i == benchmarkRun.warmUps && enableYourKit) {
                    gradle.startYourKitProfiling();
                }

                for (int j = 0; j < diffs.length; j++) {
                    diffs[j].apply(src, 3);
                    diffs[j] = diffs[j].invert();
                }
                if (i >= benchmarkRun.warmUps) {
                    listeners.forEach(it -> it.iterationStarting());
                }
                if (useSeparateGradleHome) {
                    gradle.run(
                            tasks,
                            System.out,
                            System.err,
                            new File(out, "measured-build-gradle-home").getAbsoluteFile());
                } else {
                    gradle.run(tasks);
                }
                if (i >= benchmarkRun.warmUps) {
                    listeners.forEach(BenchmarkListener::iterationDone);
                }
                try {
                    checkResult(
                            projectRoot,
                            i % 2 == 0, // Even benchmarks have the diff and odd ones do not.
                            pre_mutate_assertions,
                            post_mutate_assertions);
                } catch (AssertionError e) {
                    throw new AssertionError(
                            String.format(
                                    "Benchmark %s$1 assertion failed at iteration %d$2",
                                    benchmarkName, i),
                            e);
                }
            }

            listeners.forEach(BenchmarkListener::benchmarkDone);

            if (benchmarkRun.iterations > 0) {
                PerfData perfData = new PerfData();
                perfData.addBenchmark(benchmark);
                perfData.commit();
            }

            if (enableYourKit) {
                gradle.captureYourKitSnapshot();
            }
        }
    }

    private void addJvmArgs(
            File gradleProperties, boolean enableYourKit, File srcDir, String yourKitAgentPath)
            throws Exception {
        Properties p = new Properties();

        if (gradleProperties.exists()) {
            try (FileInputStream fis = new FileInputStream(gradleProperties)) {
                p.load(fis);
            }
        }

        String jvmArgs = p.getProperty("org.gradle.jvmargs", "");
        jvmArgs += " -XX:+UseParallelGC";
        // See https://www.yourkit.com/docs/java/help/startup_options.jsp for a comprehensive list
        // of all agent options.
        if (enableYourKit) {
            File snapshotsDir = new File(System.getenv("TEST_UNDECLARED_OUTPUTS_DIR"), "snapshots");
            snapshotsDir.mkdirs();
            jvmArgs +=
                    " -agentpath:"
                            + new File(yourKitAgentPath).getAbsolutePath()
                            + "="
                            + String.join(
                                    ",",
                                    // Workaround for YourKit instrumentation bug (b/142887436)
                                    "disableall",
                                    "exceptions=off",
                                    // make sure we collect the agent log, useful for
                                    // troubleshooting
                                    "logdir=" + snapshotsDir.getAbsolutePath(),
                                    // snapshot config
                                    "dir=" + snapshotsDir.getAbsolutePath(),
                                    "snapshot_name_format="
                                            + benchmarkName
                                            + "-{sessionname}-{datetime}");
        }

        p.put("org.gradle.jvmargs", jvmArgs);

        try (FileWriter fw = new FileWriter(gradleProperties)) {
            p.store(fw, "");
        }
    }

    private static String hostName() {
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX) {
            return "Linux";
        } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
            return "Mac";
        } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            return "Windows";
        }

        throw new RuntimeException("Unexpected platform.");
    }

    private void checkResult(
            File projectRoot,
            boolean expectMutated,
            List<BenchmarkProjectAssertion> pre_mutate_assertions,
            List<BenchmarkProjectAssertion> post_mutate_assertions)
            throws Exception {
        List<BenchmarkProjectAssertion> assertions;
        if (expectMutated) {

            assertions = post_mutate_assertions;
        } else {
            assertions = pre_mutate_assertions;
        }
        for (BenchmarkProjectAssertion assertion : assertions) {
            assertion.checkProject(projectRoot.toPath());
        }
    }
}
