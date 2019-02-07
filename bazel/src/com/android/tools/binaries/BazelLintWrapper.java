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

import com.android.tools.lint.Main;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
    public static void main(String[] args) {
        new BazelLintWrapper().run(Paths.get(args[0]));
    }

    private final DocumentBuilder documentBuilder;
    private final Class<?> exitException;
    private final Field statusField;

    public BazelLintWrapper() {
        try {
            documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            exitException = Class.forName("com.android.tools.lint.Main$ExitException");
            statusField = exitException.getDeclaredField("status");
            statusField.setAccessible(true);
        } catch (ParserConfigurationException | ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void run(Path projectXml) {
        if (!Files.exists(projectXml)) {
            System.err.println("Cannot find project XML: " + projectXml);
            System.exit(1);
        }

        Path outputDir = Paths.get(System.getenv("TEST_UNDECLARED_OUTPUTS_DIR"));
        Path outputXml = outputDir.resolve("lint_output.xml");

        // We want to reuse code for configuring lint from the project descriptor XML, which is
        // private to the custom client used by Main. So instead of creating a LintCliClient, for
        // now we call Main and deal with the exit exception (which is not public).
        Main lintMain = new Main();
        try {
            lintMain.run(
                    new String[] {
                        "--project", projectXml.toString(),
                        "--xml", outputXml.toString(),
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
                if (status != 0) {
                    System.exit(status);
                }
            } else {
                throw e;
            }
        }

        checkOutput(outputXml);
    }

    private void checkOutput(Path outputXml) {
        try {
            Document lintXmlReport = documentBuilder.parse(outputXml.toFile());
            Document junitXml = documentBuilder.newDocument();
            boolean targetShouldPass = createJUnitXml(junitXml, lintXmlReport);

            try (FileWriter fileWriter = new FileWriter(System.getenv("XML_OUTPUT_FILE"))) {
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                transformer.transform(new DOMSource(junitXml), new StreamResult(fileWriter));
            } catch (IOException | TransformerException e) {
                throw new RuntimeException(e);
            }

            if (!targetShouldPass) {
                System.exit(1);
            }
        } catch (SAXException | IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Creates content of the JUnit-like XML report used by Bazel and return a boolean value
     * indicating if the overall lint run should be considered as failed or not.
     *
     * @return true if the target should pass, false otherwise
     */
    private static boolean createJUnitXml(Document junitXml, Document lintXmlReport) {
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
            if ("LintBaseline".equals(id)
                    && message != null
                    && message.contains("were filtered out")) {
                // Ignore these.
                continue;
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
