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

package com.android.build.gradle.integration.performance;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

public final class PerformanceTestUtilTest {
    @Rule public TemporaryFolder dir = new TemporaryFolder();

    private File createActivity() throws IOException, TimeoutException, InterruptedException {
        File activity = dir.newFile("MyActivity.java");

        /*
         * We cheat a little bit here because we know that the only thing we need for our "Activity"
         * is an onCreate method, which the modifying methods use as a
         * "thing-that-we-know-will-be-present-in-all-activities" anchor.
         */
        Files.write(
                activity.toPath(),
                ("public final class MyActivity {\n"
                                + "  public void onCreate() {\n"
                                + "    System.out.println(\"Hello, world!\");\n"
                                + "  }\n"
                                + "}\n")
                        .getBytes());

        assertValidJava(activity);
        return activity;
    }

    private File createResources() throws IOException {
        File resources = dir.newFile("resources.xml");

        /*
         * We cheat a little bit here because we know that the only thing we need for our "Activity"
         * is an onCreate method, which the modifying methods use as a
         * "thing-that-we-know-will-be-present-in-all-activities" anchor.
         */
        Files.write(
                resources.toPath(),
                ("<resources>\n" + "<string name=\"hello\">Bore da!</string>\n" + "</resources>\n")
                        .getBytes());

        return resources;
    }

    private void assertValidXML(File f) throws IOException, ParserConfigurationException {
        try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
        } catch (SAXException e) {
            throw new AssertionError(f.getAbsolutePath() + " does not contain valid xml: " + e);
        }
    }

    private void assertValidJava(File f) {
        StandardJavaFileManager manager =
                ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null);

        List<Diagnostic> diagnostics = new ArrayList<>();
        JavaCompiler.CompilationTask task =
                ToolProvider.getSystemJavaCompiler()
                        .getTask(
                                null,
                                manager,
                                diagnostics::add,
                                ImmutableList.of("-d", f.getParent()),
                                null,
                                manager.getJavaFileObjectsFromFiles(ImmutableList.of(f)));

        if (task.call()) {
            return;
        }

        throw new AssertionError(
                f.getAbsolutePath()
                        + " does not contain valid java: "
                        + Joiner.on(", ").join(diagnostics));
    }

    @Test
    public void checkGetEditType() {
        Set<PerformanceTestUtil.EditType> editTypes =
                EnumSet.allOf(PerformanceTestUtil.EditType.class);
        for (Logging.BenchmarkMode mode: PerformanceTestUtil.BENCHMARK_MODES) {
            try {
                PerformanceTestUtil.EditType editType =
                        PerformanceTestUtil.getEditType(mode);
                // This is essentially a typo check.
                assertThat(mode.name()).contains(editType.name());
                editTypes.remove(editType);
            } catch (IllegalStateException ignored) {
                // Some benchmark modes don't have edit types.
            }
        }

        assertThat(editTypes).named("Edit types without a benchmark mode").isEmpty();
    }

    @Test
    public void checkGetgetSubProjectType() {
        Set<PerformanceTestUtil.SubProjectType> subprojectTypes =
                EnumSet.allOf(PerformanceTestUtil.SubProjectType.class);
        for (Logging.BenchmarkMode mode: PerformanceTestUtil.BENCHMARK_MODES) {
            try {
                PerformanceTestUtil.SubProjectType subProjectType =
                        PerformanceTestUtil.getSubProjectType(mode);
                // This is essentially a typo check.
                assertThat(mode.name()).contains(subProjectType.name());
                subprojectTypes.remove(subProjectType);
            } catch (IllegalStateException ignored) {
                // Some benchmark modes don't have subproject types types.
            }
        }

        assertThat(subprojectTypes).named("Subproject types without a benchmark mode").isEmpty();
    }

    /**
     * This test makes sure that for many different list sizes, all shards reconstruct back to the
     * original list.
     */
    @Test
    public void shardCorrectnessTest() {
        for (int listSize = 0; listSize < 20; listSize++) {
            List<Integer> list = new ArrayList<>(listSize);
            for (int i = 0; i < listSize; i++) {
                list.add(i);
            }

            for (int numShards = 1; numShards <= listSize; numShards++) {
                List<Integer> sharded = new ArrayList<>(listSize);
                for (int i = 0; i < numShards; i++) {
                    sharded.addAll(PerformanceTestUtil.shard(list, i, numShards));
                }

                assertThat(sharded).containsExactlyElementsIn(list);
            }
        }
    }

    /**
     * This test makes sure that shards are equally balanced. We don't want any shards to be
     * significantly larger than the others.
     */
    @Test
    public void shardBalanceTest() {
        for (int listSize = 1; listSize < 20; listSize++) {
            List<Integer> list = new ArrayList<>(listSize);
            for (int i = 0; i < listSize; i++) {
                list.add(i);
            }

            for (int numShards = 1; numShards <= listSize; numShards++) {
                List<Integer> shardSizes = new ArrayList<>(listSize);
                for (int i = 0; i < numShards; i++) {
                    shardSizes.add(PerformanceTestUtil.shard(list, i, numShards).size());
                }

                int range = Collections.max(shardSizes) - Collections.min(shardSizes);
                assertThat(range).isLessThan(2);
            }
        }
    }

    @Test
    public void addMethodToActivity() throws Exception {
        File activity = createActivity();
        String before =
                Hashing.sha512().hashBytes(Files.readAllBytes(activity.toPath())).toString();

        PerformanceTestUtil.addMethodToActivity(activity);
        String after = Hashing.sha512().hashBytes(Files.readAllBytes(activity.toPath())).toString();

        assertValidJava(activity);
        assertThat(before).isNotEqualTo(after);
    }

    @Test
    public void changeActivity() throws Exception {
        File activity = createActivity();
        String before =
                Hashing.sha512().hashBytes(Files.readAllBytes(activity.toPath())).toString();

        PerformanceTestUtil.changeActivity(activity);
        String after = Hashing.sha512().hashBytes(Files.readAllBytes(activity.toPath())).toString();

        assertValidJava(activity);
        assertThat(before).isNotEqualTo(after);
    }

    @Test
    public void changeStringResource() throws Exception {
        File resources = createResources();
        String before =
                Hashing.sha512().hashBytes(Files.readAllBytes(resources.toPath())).toString();

        PerformanceTestUtil.changeStringResource(resources);
        String after =
                Hashing.sha512().hashBytes(Files.readAllBytes(resources.toPath())).toString();

        assertValidXML(resources);
        assertThat(before).isNotEqualTo(after);
    }

    @Test
    public void addStringResource() throws Exception {
        File resources = createResources();
        String before =
                Hashing.sha512().hashBytes(Files.readAllBytes(resources.toPath())).toString();

        PerformanceTestUtil.addStringResource(resources);
        String after =
                Hashing.sha512().hashBytes(Files.readAllBytes(resources.toPath())).toString();

        assertValidXML(resources);
        assertThat(before).isNotEqualTo(after);
    }
}
