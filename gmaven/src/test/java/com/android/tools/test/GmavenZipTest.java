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

package com.android.tools.test;


import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.common.truth.Expect;
import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Verifies what we distribute to the Google maven repository
 *
 * <p>To update the snapshot file run: <code>
 * bazel test //tools/base/gmaven:tests --nocache_test_results --strategy=TestRunner=standalone --jvmopt="-DUPDATE_TEST_SNAPSHOTS=$(bazel info workspace)" --test_output=streamed
 * </code>
 */
public class GmavenZipTest {

    private static final String UPDATE_TEST_SNAPSHOTS = System.getProperty("UPDATE_TEST_SNAPSHOTS");
    private static final String EXPECTATION_RESOURCE_DIR =
            "tools/base/gmaven/src/test/resources/com/android/tools/test/";

    private static final Set<String> LICENSE_NAMES =
            ImmutableSet.of("NOTICE", "NOTICE.txt", "LICENSE");

    private static final String GMAVEN_ZIP = "tools/base/gmaven/gmaven.zip";

    private static final String R8_NAMESPACE = "com/android/tools/r8/";
    private static final String R8_PACKAGE_PREFIX = "com.android.tools.r8";

    private static final Set<String> MISSING_LICENSE =
            ImmutableSet.of(
                    "androidx/databinding/databinding-adapters",
                    "androidx/databinding/databinding-adapters:sources",
                    "androidx/databinding/databinding-ktx",
                    "androidx/databinding/databinding-ktx:sources",
                    "androidx/databinding/databinding-runtime",
                    "androidx/databinding/databinding-runtime:sources",
                    "androidx/databinding/viewbinding",
                    "androidx/databinding/viewbinding:sources",
                    "com/android/databinding/library",
                    "com/android/databinding/library:sources",
                    "com/android/databinding/viewbinding",
                    "com/android/databinding/viewbinding:sources",
                    "com/android/databinding/adapters",
                    "com/android/databinding/adapters:sources",
                    "com/android/signflinger",
                    "com/android/signflinger:sources",
                    "com/android/tools/emulator/proto",
                    "com/android/tools/emulator/proto:sources",
                    "com/android/tools/utp/android-test-plugin-host-device-info-proto",
                    "com/android/tools/utp/android-test-plugin-host-device-info-proto:sources",
                    "com/android/tools/utp/android-test-plugin-host-coverage",
                    "com/android/tools/utp/android-test-plugin-host-coverage:sources",
                    "com/android/tools/utp/android-test-plugin-host-additional-test-output",
                    "com/android/tools/utp/android-test-plugin-host-additional-test-output:sources",
                    "com/android/tools/utp/android-test-plugin-result-listener-gradle",
                    "com/android/tools/utp/android-test-plugin-result-listener-gradle:sources",
                    "com/android/tools/utp/android-test-plugin-host-logcat",
                    "com/android/tools/utp/android-test-plugin-host-logcat:sources",
                    "com/android/tools/utp/android-device-provider-ddmlib",
                    "com/android/tools/utp/android-device-provider-ddmlib:sources",
                    "com/android/tools/utp/android-device-provider-gradle",
                    "com/android/tools/utp/android-device-provider-gradle:sources",
                    "com/android/tools/utp/android-test-plugin-host-device-info",
                    "com/android/tools/utp/android-test-plugin-host-device-info:sources",
                    "com/android/tools/utp/android-test-plugin-host-retention",
                    "com/android/tools/utp/android-test-plugin-host-retention:sources",
                    "com/android/zipflinger",
                    "com/android/zipflinger:sources");

    private static class PomInfo implements Comparable<PomInfo> {

        private static final DocumentBuilder documentBuilder;
        private static final XPathFactory xPathFactory = XPathFactory.newInstance();
        private static final XPathExpression projectPath;
        private static final XPathExpression groupIdPath;
        private static final XPathExpression artifactIdPath;
        private static final XPathExpression pomNamePath;
        private static final XPathExpression descriptionPath;
        private static final XPathExpression dependenciesPath;
        private static final XPathExpression scopePath;

        static {
            try {
                documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                projectPath = xPathFactory.newXPath().compile("/project");
                groupIdPath = xPathFactory.newXPath().compile("groupId");
                artifactIdPath = xPathFactory.newXPath().compile("artifactId");
                pomNamePath = xPathFactory.newXPath().compile("name");
                descriptionPath = xPathFactory.newXPath().compile("description");
                dependenciesPath = xPathFactory.newXPath().compile("dependencies/dependency");
                scopePath = xPathFactory.newXPath().compile("scope");
            } catch (XPathExpressionException | ParserConfigurationException e) {
                throw new IllegalStateException(e);
            }
        }

        private static class Dependency {
            private final String groupId;
            private final String artifactId;
            private final String scope;

            private Dependency(String groupId, String artifactId, String scope) {
                this.groupId = groupId;
                this.artifactId = artifactId;
                this.scope = scope;
            }

            static Dependency fromNode(Node n) throws XPathExpressionException {
                return new Dependency(
                        groupIdPath.evaluate(n), artifactIdPath.evaluate(n), scopePath.evaluate(n));
            }

            @Override
            public String toString() {
                return groupId + ':' + artifactId + " (" + scope + ")";
            }
        }

        private final String groupId;
        private final String artifactId;
        private final String pomName;
        private final String description;
        private final List<Dependency> dependencies;

        PomInfo(
                String artifactAddress,
                String artifactId,
                String pomName,
                String description,
                List<Dependency> dependencies) {
            this.groupId = artifactAddress;
            this.artifactId = artifactId;
            this.pomName = pomName;
            this.description = description;
            this.dependencies = dependencies;
        }

        public static PomInfo fromFile(Path file) {
            try {
                String text = Joiner.on('\n').join(Files.readAllLines(file));
                documentBuilder.reset();
                Document xml;
                try (InputStream stream = new BufferedInputStream(Files.newInputStream(file))) {
                    xml = documentBuilder.parse(stream);
                }
                Node project = (Node) projectPath.evaluate(xml, XPathConstants.NODE);

                NodeList dependencyNodes =
                        (NodeList) dependenciesPath.evaluate(project, XPathConstants.NODESET);
                List<Dependency> dependencies = new ArrayList<>(dependencyNodes.getLength());
                for (int i = 0; i < dependencyNodes.getLength(); i++) {
                    dependencies.add(i, Dependency.fromNode(dependencyNodes.item(i)));
                }

                return new PomInfo(
                        groupIdPath.evaluate(project),
                        artifactIdPath.evaluate(project),
                        pomNamePath.evaluate(project),
                        descriptionPath.evaluate(project),
                        dependencies);
            } catch (IOException | SAXException | XPathExpressionException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            StringBuilder stringBuilder =
                    new StringBuilder()
                            .append(groupId)
                            .append(':')
                            .append(artifactId)
                            .append('\n')
                            .append("  pomName=")
                            .append(pomName)
                            .append('\n')
                            .append("  description=")
                            .append(description)
                            .append('\n');
            if (!dependencies.isEmpty()) {
                stringBuilder.append("  dependencies:\n");
                for (Dependency dependency : dependencies) {
                    stringBuilder.append("    ").append(dependency.toString()).append("\n");
                }
            }
            return stringBuilder.toString();
        }

        @Override
        public int compareTo(PomInfo o) {
            int group = this.groupId.compareTo(o.groupId);
            if (group != 0) {
                return group;
            }
            return this.artifactId.compareTo(o.artifactId);
        }
    }

    public static class ZipInfo implements Comparable<ZipInfo> {

        private final String name;
        private final List<String> entries;

        public ZipInfo(String name, List<String> entries) {
            this.name = name;
            this.entries = entries;
        }

        public static ZipInfo fromFile(Path zip, Path repo) {
            String name = jarRelativePathWithoutVersionWithClassifier(zip, repo);
            List<String> entries = new ArrayList<>();

            try (ZipInputStream zipInputStream =
                    new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    Set<String> filesFromEntry =
                            getCheckableFilesFromEntry(
                                    entry, new NonClosingInputStream(zipInputStream), "");
                    entries.addAll(
                            filesFromEntry.stream()
                                    // Packages under the R8 namespace are renamed.
                                    .filter(
                                            path ->
                                                    !path.startsWith(R8_NAMESPACE)
                                                            || path.equals(R8_NAMESPACE))
                                    // Services used by R8 are reloacted and renamed.
                                    .map(
                                            path ->
                                                    path.startsWith(
                                                                    "META-INF/services/"
                                                                            + R8_PACKAGE_PREFIX)
                                                            ? "META-INF/services/"
                                                                    + R8_PACKAGE_PREFIX
                                                            : path)
                                    .collect(Collectors.toList()));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            entries.sort(String::compareTo);
            return new ZipInfo(name, Collections.unmodifiableList(entries));
        }

        @Override
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder().append(name).append('\n');
            for (String entry : entries) {
                stringBuilder.append("  ").append(entry).append('\n');
            }
            return stringBuilder.toString();
        }

        @Override
        public int compareTo(ZipInfo o) {
            return name.compareTo(o.name);
        }
    }

    @Rule public Expect expect = Expect.createAndEnableStackTrace();

    @Test
    public void checkTools() throws Exception {
        Path repo = getRepo();

        List<Path> files =
                Files.walk(repo).filter(Files::isRegularFile).collect(Collectors.toList());

        List<Path> sourcesJars = new ArrayList<>();
        List<Path> poms = new ArrayList<>();
        List<Path> aars = new ArrayList<>();
        List<Path> jars = new ArrayList<>();
        for (Path file : files) {
            String a = file.toString();
            if (a.endsWith(".pom")) {
                poms.add(file);
            } else if (a.endsWith("-sources.jar")) {
                sourcesJars.add(file);
            } else //noinspection StatementWithEmptyBody
            if (a.endsWith("-javadoc.jar")) {
                // Ignore
            } else if (a.endsWith(".aar")) {
                aars.add(file);
            } else if (a.endsWith(".jar")) {
                jars.add(file);
            } else //noinspection StatementWithEmptyBody
            if (a.endsWith("maven-metadata.xml")
                    || a.endsWith(".md5")
                    || a.endsWith(".sha1")
                    || a.endsWith(".sha256")
                    || a.endsWith(".sha512")
                    || a.endsWith(".module")) {
                // Ignore
            } else {
                expect.fail("Unexpected file " + file);
            }
        }

        for (Path sourcesJar : sourcesJars) {
            checkSourcesJar(sourcesJar, repo);
        }
        for (Path jar : jars) {
            checkLicense(jar, repo);
        }
        for (Path aar : aars) {
            checkLicense(aar, repo);
        }

        String aarsContent =
                aars.stream()
                        .map(zip -> ZipInfo.fromFile(zip, repo))
                        .sorted()
                        .map(ZipInfo::toString)
                        .collect(Collectors.joining("\n"));
        check("gmaven-aars.txt", aarsContent);
        String jarsContent =
                jars.stream()
                        .map(zip -> ZipInfo.fromFile(zip, repo))
                        .sorted()
                        .map(ZipInfo::toString)
                        .collect(Collectors.joining("\n"));
        check("gmaven-jars.txt", jarsContent);
        String pomContent =
                poms.stream()
                        .map(PomInfo::fromFile)
                        .sorted()
                        .map(PomInfo::toString)
                        .collect(Collectors.joining("\n"));
        check("gmaven-poms.txt", pomContent);
    }

    private void check(String name, String actual) throws IOException {
        URL resource = this.getClass().getResource(name);
        String expected = Resources.asCharSource(resource, StandardCharsets.UTF_8).read();
        if (UPDATE_TEST_SNAPSHOTS != null) {
            if (UPDATE_TEST_SNAPSHOTS.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "UPDATE_TEST_SNAPSHOTS must point to the workspace location");
            }
            Path expectationFile =
                    Paths.get(UPDATE_TEST_SNAPSHOTS).resolve(EXPECTATION_RESOURCE_DIR + name);
            System.err.println("Updating " + expectationFile);

            if (expected.equals(actual)) {
                System.err.println(name + " up to date");
            } else {
                Files.write(expectationFile, actual.getBytes(StandardCharsets.UTF_8));
                System.err.println(name + " updated");
            }
        } else {
            expect.withMessage(
                            "There has been a change to what is published to gmaven.\n"
                                    + "\n"
                                    + "If that change is intentional, update the expectation files.\n"
                                    + "\n"
                                    + "To update the files, download and unzip the outputs.zip:\n"
                                    + "  unzip -d $(bazel info workspace) -o outputs.zip\n"
                                    + "\n"
                                    + "For a local invocation, outputs.zip will be in bazel-testlogs:\n"
                                    + "  unzip -d $(bazel info workspace) -o \\\n"
                                    + "    $(bazel info bazel-testlogs)/tools/base/gmaven/tests/test.outputs/outputs.zip\n"
                                    + "\n"
                                    + "Or, to re-run the test and update the expectations in place, run: \n"
                                    + "  bazel test //tools/base/gmaven:tests \\\n"
                                    + "    --nocache_test_results \\\n"
                                    + "    --strategy=TestRunner=standalone \\\n"
                                    + "    --jvmopt=\"-DUPDATE_TEST_SNAPSHOTS=$(bazel info workspace)\" \\\n"
                                    + "    --test_output=streamed\n"
                                    + "\n"
                                    + "NB: All the commands above assume 'tools/base/bazel' is on your path.")
                    .that(actual)
                    .named(name)
                    .isEqualTo(expected);
            if (!expected.equals(actual)) {
                Path testOutputDir = TestUtils.getTestOutputDir().resolve(EXPECTATION_RESOURCE_DIR);
                Files.createDirectories(testOutputDir);
                Path testOutput = testOutputDir.resolve(name);
                Files.write(testOutput, actual.getBytes(StandardCharsets.UTF_8));
            }
        }
    }



    private void checkSourcesJar(Path jarPath, Path repo) throws IOException {
        checkLicense(jarPath, repo);
    }

    private void checkLicense(Path jarPath, Path repo) throws IOException {
        boolean found = false;
        try (ZipInputStream zipInputStream =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(jarPath)))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (LICENSE_NAMES.contains(entry.getName())) {
                    found = true;
                }
            }
        }
        String key = jarRelativePathWithoutVersionWithClassifier(jarPath, repo);
        boolean knownMissing = MISSING_LICENSE.contains(key);
        if (found && knownMissing) {
            expect.fail(
                    "Licence file unexpectedly present in "
                            + jarPath
                            + " from "
                            + repo
                            + ".\n"
                            + "Remove it from MISSING_LICENSE");
        } else if (!found && !knownMissing) {
            expect.fail("No license file in " + jarPath + " with key " + key);
        }
    }

    private static String jarRelativePathWithoutVersionWithClassifier(Path jar, Path repo) {
        String pathWithoutVersion = repo.relativize(jar).getParent().getParent().toString();

        String name = jar.getParent().getParent().getFileName().toString();
        String revision = jar.getParent().getFileName().toString();
        String expectedNameNoClassifier = name + "-" + revision;
        String filename = jar.getFileName().toString();
        String path = FileUtils.toSystemIndependentPath(pathWithoutVersion);
        if (!filename.substring(0, filename.length() - 4).equals(expectedNameNoClassifier)) {
            String classifier =
                    filename.substring(
                            expectedNameNoClassifier.length() + 1,
                            filename.length() - ".jar".length());
            return path + ":" + classifier;
        }
        return path;
    }

    private static boolean shouldCheckFile(String fileName) {
        if (fileName.endsWith(".class")) {
            return false;
        }

        if (fileName.endsWith(".kotlin_builtins")) {
            return false;
        }

        if (fileName.endsWith(".kotlin_metadata")) {
            return false;
        }

        return true;
    }

    private static Set<String> getCheckableFilesFromEntry(
            ZipEntry entry, NonClosingInputStream entryInputStream, String prefix)
            throws Exception {
        Set<String> files = new HashSet<>();
        if (shouldCheckFile(entry.getName())) {
            String fileName = prefix + entry.getName();
            files.add(fileName);
            if (fileName.endsWith(".jar")) {
                files.addAll(getFilesFromInnerJar(entryInputStream, fileName + ":"));
            }
        }
        return files;
    }

    private static Set<String> getFilesFromInnerJar(InputStream entryInputStream, String prefix)
            throws Exception {
        Set<String> files = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(entryInputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                files.addAll(
                        getCheckableFilesFromEntry(entry, new NonClosingInputStream(zis), prefix));
            }
        }
        return files;
    }

    private static Path getRepo() throws IOException {
        return FileSystems.newFileSystem(TestUtils.resolveWorkspacePath(GMAVEN_ZIP), null)
                .getPath("/");
    }

    private static class NonClosingInputStream extends FilterInputStream {

        protected NonClosingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
            // Do nothing.
        }
    }
}
