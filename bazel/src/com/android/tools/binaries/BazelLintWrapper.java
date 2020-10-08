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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
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
    private final Path testXml;
    private final Path sandboxBase;
    private final Path outputDir;

    public static void main(String[] args) throws IOException {
        Path projectXml = Paths.get(args[0]);
        Path baseline = null;
        if (args.length > 1) {
            baseline = Paths.get(args[1]);
        }
        boolean update = System.getenv("UPDATE_LINT_BASELINE") != null;
        new BazelLintWrapper().run(projectXml, baseline, update);
    }

    private final DocumentBuilder documentBuilder;

    public BazelLintWrapper() {
        testXml = Paths.get(System.getenv("XML_OUTPUT_FILE"));
        outputDir = Paths.get(System.getenv("TEST_UNDECLARED_OUTPUTS_DIR"));
        outputXml = outputDir.resolve("lint_output.xml");

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
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private Path relativize(Path path) {
        return sandboxBase != null ? sandboxBase.relativize(path) : path;
    }

    private void run(Path projectXml, Path originalBaseline, boolean update) throws IOException {
        if (!Files.exists(projectXml)) {
            System.err.println("Cannot find project XML: " + projectXml);
            System.exit(1);
        }
        Path newBaseline;

        // Lint will update the baseline file, and putting it in undeclared outputs means we can
        // download it from the CI server.
        if (update) {
            if (originalBaseline == null) {
                throw new IllegalArgumentException(
                        "Cannot update a baseline file that does not exist.");
            }
            newBaseline = originalBaseline;
        } else if (originalBaseline != null) {
            newBaseline = outputDir.resolve(originalBaseline.getFileName());
            Files.copy(originalBaseline, newBaseline);
        } else {
            newBaseline = outputDir.resolve("lint_baseline.xml");
            Files.createFile(newBaseline);
        }

        Path lintConfig = outputDir.resolve("lint.xml");

        Files.write(
                lintConfig,
                ImmutableList.of(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                        "<lint checkTestSources=\"true\">",
                        "</lint>"));

        // We want to reuse code for configuring lint from the project descriptor XML, which is
        // private to the custom client used by Main. So instead of creating a LintCliClient, for
        // now we call Main and deal with the exit exception (which is not public).
        Main lintMain = new Main();

        PrintStream stdOut = System.out;

        @SuppressWarnings("resource") // Closing a ByteArrayOutputStream has no effect.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(baos));
            int status =
                    lintMain.run(
                            new String[] {
                                "--project", projectXml.toString(),
                                "--xml", outputXml.toString(),
                                "--baseline", newBaseline.toString(),
                                "--config", lintConfig.toString(),
                                "--update-baseline",
                            });
            switch (status) {
                case 0:
                case LintCliFlags.ERRNO_CREATED_BASELINE:
                    break;
                default:
                    System.exit(status);
            }
        } finally {
            System.setOut(stdOut);
            Files.delete(lintConfig);
        }

        Result result = checkOutput(outputXml, newBaseline);
        switch (result) {
            case PASS:
                return;
            case BASELINE_CONTAINS_FIXED_ISSUES:
                if (update) {
                    System.out.print("Lint baseline updated.");
                    return;
                }
                System.out.print("Lint baseline contains issues that are now fixed. See ");
                System.out.println(relativize(testXml));
                System.out.println();
                System.out.println("Execute ");
                System.out.println("  bazel run \\");
                System.out.println("    --test_env=UPDATE_LINT_BASELINE=1 \\");
                System.out.print("    ");
                System.out.println(System.getenv("TEST_TARGET"));
                System.out.println("to update the baseline file in place.");
                System.exit(1);
                break;
            case NEW_ISSUES_FOUND:
                System.out.println("Lint found new issues. See " + relativize(testXml));
                System.exit(1);
                break;
        }
    }

    private enum Result {
        PASS,
        BASELINE_CONTAINS_FIXED_ISSUES,
        NEW_ISSUES_FOUND,
    }

    private Result checkOutput(Path outputXml, Path newBaseline) {
        try {
            Document lintXmlReport = documentBuilder.parse(outputXml.toFile());
            Document junitXml = documentBuilder.newDocument();
            Result result = createJUnitXml(junitXml, lintXmlReport, newBaseline);

            try (FileWriter fileWriter = new FileWriter(testXml.toFile())) {
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                transformer.transform(new DOMSource(junitXml), new StreamResult(fileWriter));
            } catch (IOException | TransformerException e) {
                throw new RuntimeException(e);
            }

            return result;
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String BASELINE_MESSAGE =
            "The baseline file contains issues which have "
                    + "been fixed in the project. Please remove the fixed lint issues from the "
                    + "lint_baseline.xml file in your module or regenerate it. Add the edited or "
                    + "regenerated file to your CL and try again. See "
                    + "tools/base/lint/studio-checks/README.md for more information.\n\n"
                    + ""
                    + "Do not add anything new to the baseline file; suppress new issues with "
                    + "@SuppressWarnings in Java or @Suppress in Kotlin.\n\n"
                    + ""
                    + "If you want to regenerate the file, run\n"
                    + "  bazel run --test_env=UPDATE_LINT_BASELINE=1 \\\n"
                    + "    %1$s\n"
                    + "You can also find the regenerated file in your presubmit results for the %1$s target, "
                    + "under the Artifacts tab, in Archives/undeclared_outputs.zip";

    /**
     * Creates content of the JUnit-like XML report used by Bazel and return a boolean value
     * indicating if the overall lint run should be considered as failed or not.
     *
     * @return true if the target should pass, false otherwise
     */
    private Result createJUnitXml(Document junitXml, Document lintXmlReport, Path newBaseline) {
        Result result = Result.PASS;

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
                if (message.contains("filtered out because")) {
                    // Ignore these.
                    continue;
                }
                if (message.contains("perhaps they have been fixed")) {
                    // Custom message.
                    if (result == Result.PASS) {
                        result = Result.BASELINE_CONTAINS_FIXED_ISSUES;
                    }
                    String summary = "Baseline out of date";
                    messagesByFileAndSummary.put(
                            newBaseline.toFile(), summary, Collections.emptyList());
                    explanations.put(
                            summary, String.format(BASELINE_MESSAGE, System.getenv("TEST_TARGET")));
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
            result = Result.NEW_ISSUES_FOUND;
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

        return result;
    }
}
