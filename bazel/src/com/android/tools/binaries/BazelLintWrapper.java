/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.binaries;

import static com.google.common.io.Files.getNameWithoutExtension;

import com.android.tools.lint.LintCliFlags;
import com.android.tools.lint.Main;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Wrapper around Lint's CLI that is aware of the Bazel test environment. Locates the input files,
 * picks the right output location, checks if Lint's report contains any issues and exits with the
 * appropriate exit code.
 */
public class BazelLintWrapper {

    private final Path outputXml;
    private final Path newBaseline;
    private final Path testXml;
    private final Path sandboxBase;
    private final Path outputDir;

    public static void main(String[] args) throws IOException {
        Path projectXml = Paths.get(args[0]);
        Path baseline = null;
        if (args.length > 1) {
            baseline = Paths.get(args[1]);
        }
        new BazelLintWrapper().run(projectXml, baseline);
    }

    private final DocumentBuilder documentBuilder;
    private final Class<?> exitException;
    private final Field statusField;

    public BazelLintWrapper() {
        testXml = Paths.get(System.getenv("XML_OUTPUT_FILE"));
        outputDir = Paths.get(System.getenv("TEST_UNDECLARED_OUTPUTS_DIR"));
        outputXml = outputDir.resolve("lint_output.xml");
        newBaseline = outputDir.resolve("lint_baseline.xml");

        // Find sandboxBase, so we can print out readable paths.
        if (outputXml.toString().contains("bazel-out")) {
            File base = outputXml.toFile();
            while (base != null && !base.getName().equals("bazel-out")) {
                base = base.getParentFile();
            }
            sandboxBase = base == null ? null : base.toPath().getParent();
        } else {
            sandboxBase = null;
        }

        try {
            documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            exitException = Class.forName("com.android.tools.lint.Main$ExitException");
            statusField = exitException.getDeclaredField("status");
            statusField.setAccessible(true);
        } catch (ParserConfigurationException | ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Path relativize(Path path) {
        return sandboxBase != null ? sandboxBase.relativize(path) : path;
    }

    private void run(Path projectXml, Path originalBaseline) throws IOException {
        if (!Files.exists(projectXml)) {
            System.err.println("Cannot find project XML: " + projectXml);
            System.exit(1);
        }

        // Lint will update the baseline file, and putting it in undeclared outputs means we can
        // download it from the CI server.
        if (originalBaseline != null) {
            Files.copy(originalBaseline, newBaseline);
        } else {
            Files.createFile(newBaseline);
        }

        // We want to reuse code for configuring lint from the project descriptor XML, which is
        // private to the custom client used by Main. So instead of creating a LintCliClient, for
        // now we call Main and deal with the exit exception (which is not public).
        Main lintMain = new Main();

        PrintStream stdOut = System.out;

        @SuppressWarnings("resource") // Closing a ByteArrayOutputStream has no effect.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(baos));
            lintMain.run(
                    new String[] {
                        "--project", projectXml.toString(),
                        "--xml", outputXml.toString(),
                        "--baseline", newBaseline.toString(),
                        "--update-baseline",
                    });
        } catch (Exception e) {
            if (exitException.isInstance(e)) {
                int status = 0;
                try {
                    status = (int) statusField.get(e);
                } catch (IllegalAccessException ex) {
                    ex.printStackTrace();
                    System.exit(1);
                }

                switch (status) {
                    case 0:
                    case LintCliFlags.ERRNO_CREATED_BASELINE:
                        break;
                    default:
                        System.exit(status);
                }
            } else {
                throw e;
            }
        } finally {
            System.setOut(stdOut);
        }

        boolean targetShouldPass = checkOutput(outputXml, newBaseline);
        if (!targetShouldPass) {
            System.out.println(
                    "Lint found new issues or the baseline was out of date. "
                            + "See "
                            + relativize(testXml));
            System.out.print("\n==================== ");
            System.out.println(
                    "Original lint output (note that paths in the sandbox may no longer exist):");
            System.out.println(baos.toString(StandardCharsets.UTF_8.name()));
            System.exit(1);
        }
    }

    private boolean checkOutput(Path outputXml, Path newBaseline) {
        try {
            Document lintXmlReport = documentBuilder.parse(outputXml.toFile());
            Document junitXml = documentBuilder.newDocument();
            boolean targetShouldPass = createJUnitXml(junitXml, lintXmlReport, newBaseline);

            try (FileWriter fileWriter = new FileWriter(testXml.toFile())) {
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                transformer.transform(new DOMSource(junitXml), new StreamResult(fileWriter));
            } catch (IOException | TransformerException e) {
                throw new RuntimeException(e);
            }

            return targetShouldPass;
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates content of the JUnit-like XML report used by Bazel and return a boolean value
     * indicating if the overall lint run should be considered as failed or not.
     *
     * @return true if the target should pass, false otherwise
     */
    private boolean createJUnitXml(Document junitXml, Document lintXmlReport, Path newBaseline) {
        boolean targetShouldPass = true;

        Table<File, String, List<String>> messagesByFileAndSummary = HashBasedTable.create();
        NodeList issues = lintXmlReport.getElementsByTagName("issue");
        Map<String, String> explanations = new HashMap<>();

        for (int i = 0; i < issues.getLength(); i++) {
            Element issue = (Element) issues.item(i);
            String id = issue.getAttribute("id");
            String message = issue.getAttribute("message");

            // Check if the XML report contains issues worth of breaking the test target, which is
            // any issue which is not just information about the baseline being applied. We cannot
            // just disable the LintBaseline check, since we do want to fail if the baseline gets
            // out of date and contains issues which are no longer in the project.
            if ("LintBaseline".equals(id) && message != null) {
                if (message.contains("were filtered out")) {
                    // Ignore these.
                    continue;
                }
                if (message.contains("perhaps they have been fixed")) {
                    // Custom message.
                    targetShouldPass = false;
                    String summary = "Baseline out of date";
                    messagesByFileAndSummary.put(
                            newBaseline.toFile(), summary, Collections.emptyList());
                    explanations.put(
                            summary,
                            "The baseline file contains issues which have been fixed in the project, "
                                    + "please remove them or use the regenerated baseline. When running "
                                    + "locally you can find in "
                                    + relativize(outputDir.resolve("outputs.zip"))
                                    + ", on CI you can download it from the Artifacts tab, under "
                                    + "Archives/undeclared_outputs.zip.");
                    continue;
                }
            }

            Element location = (Element) issue.getElementsByTagName("location").item(0);
            File file = new File(location.getAttribute("file"));
            String summary = issue.getAttribute("summary");
            String explanation = issue.getAttribute("explanation");
            String error1 = issue.getAttribute("errorLine1");
            String error2 = issue.getAttribute("errorLine2");

            StringBuilder fullMessage = new StringBuilder().append(file.toString());
            String lineAttr = location.getAttribute("line");
            if (!lineAttr.isEmpty()) {
                fullMessage.append(':').append((int) Integer.valueOf(lineAttr));
            }
            fullMessage
                    .append(' ')
                    .append(message)
                    .append(" [")
                    .append(id)
                    .append(']')
                    .append('\n')
                    .append(error1)
                    .append('\n')
                    .append(error2)
                    .append('\n');

            List<String> strings = messagesByFileAndSummary.get(file, summary);
            if (strings == null) {
                strings = new ArrayList<>();
                messagesByFileAndSummary.put(file, summary, strings);
            }
            strings.add(fullMessage.toString());
            explanations.putIfAbsent(summary, explanation);
            targetShouldPass = false;
        }

        Element testSuites = junitXml.createElement("testsuites");
        junitXml.appendChild(testSuites);

        for (File file : messagesByFileAndSummary.rowKeySet()) {
            Map<String, List<String>> messagesBySummary = messagesByFileAndSummary.row(file);
            String issuesCount = String.valueOf(messagesBySummary.size());

            Element testSuite = junitXml.createElement("testsuite");
            testSuite.setAttribute("name", getNameWithoutExtension(file.getName()));
            testSuite.setAttribute("tests", issuesCount);
            testSuite.setAttribute("failures", issuesCount);
            testSuites.appendChild(testSuite);

            for (Map.Entry<String, List<String>> entry : messagesBySummary.entrySet()) {
                Element testCase = junitXml.createElement("testcase");
                String summary = entry.getKey();
                testCase.setAttribute("name", summary);
                testSuite.appendChild(testCase);

                Element failure = junitXml.createElement("failure");
                failure.setAttribute(
                        "message",
                        explanations.get(summary)
                                + "\n\n=========\n\n"
                                + String.join("\n", entry.getValue()));
                testCase.appendChild(failure);
            }
        }

        return targetShouldPass;
    }
}
