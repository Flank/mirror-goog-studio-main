/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint.client.api;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.lint.checks.AbstractCheckTest;
import com.android.tools.lint.checks.UnusedResourceDetector;
import com.android.tools.lint.checks.infrastructure.LoggingTestLintClient;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Project;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProjectTest extends AbstractCheckTest {
    @Override
    protected boolean ignoreSystemErrors() {
        return false;
    }

    public void testCycle() throws Exception {
        // Ensure that a cycle in library project dependencies doesn't cause
        // infinite directory traversal
        //noinspection all // Sample code
        File main =
                getProjectDir(
                        "MainProject",
                        // Main project
                        manifest().pkg("foo.main").minSdk(14),
                        projectProperties()
                                .property("android.library.reference.1", "../LibraryProject"),
                        java(
                                ""
                                        + "package foo.main;\n"
                                        + "\n"
                                        + "public class MainCode {\n"
                                        + "    static {\n"
                                        + "        System.out.println(R.string.string2);\n"
                                        + "    }\n"
                                        + "}\n"));
        //noinspection all // Sample code
        File library =
                getProjectDir(
                        "LibraryProject",
                        // Library project
                        manifest().pkg("foo.library").minSdk(14),
                        projectProperties()
                                .property("android.library.reference.1", "../LibraryProject"),
                        // RECURSIVE - points to self
                        java(
                                ""
                                        + "package foo.library;\n"
                                        + "\n"
                                        + "public class LibraryCode {\n"
                                        + "    static {\n"
                                        + "        System.out.println(R.string.string1);\n"
                                        + "    }\n"
                                        + "}\n"),
                        xml(
                                "res/values/strings.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<resources>\n"
                                        + "\n"
                                        + "    <string name=\"app_name\">LibraryProject</string>\n"
                                        + "    <string name=\"string1\">String 1</string>\n"
                                        + "    <string name=\"string2\">String 2</string>\n"
                                        + "    <string name=\"string3\">String 3</string>\n"
                                        + "\n"
                                        + "</resources>\n"));

        assertEquals(
                ""
                        + "MainProject/project.properties: Error: Circular library dependencies; check your project.properties files carefully [LintError]\n"
                        + "1 errors, 0 warnings\n",
                checkLint(Arrays.asList(main, library)));
    }

    public void testNonCanonicalPaths() {
        LoggingTestLintClient client = new LoggingTestLintClient();
        // path which should trigger IO exception in File.canonicalFile
        File dir = new File("project/\u0000");
        Project project1 = Project.create(client, dir, dir);
        client.registerProject(dir, project1);
        assertNotNull(client.getProject(dir, dir));
        assertTrue(client.isKnownProjectDir(dir));
    }

    public void testInvalidLibraryReferences1() {
        LoggingTestLintClient client = new LoggingTestLintClient();
        File dir = new File("project");
        Project project1 = Project.create(client, dir, dir);
        client.registerProject(dir, project1);
        project1.setDirectLibraries(Collections.singletonList(project1));
        List<Project> libraries = project1.getAllLibraries();
        assertNotNull(libraries);
        assertEquals(
                "Warning: Internal lint error: cyclic library dependency for Project [dir=project]",
                client.getLoggedOutput());
    }

    public void testInvalidLibraryReferences2() {
        LoggingTestLintClient client = new LoggingTestLintClient();
        File dir1 = new File("project1");
        File dir2 = new File("project2");
        Project project1 = Project.create(client, dir1, dir1);
        client.registerProject(dir1, project1);
        Project project2 = Project.create(client, dir2, dir2);
        client.registerProject(dir2, project2);
        project2.setDirectLibraries(Collections.singletonList(project1));
        project1.setDirectLibraries(Collections.singletonList(project2));
        List<Project> libraries = project1.getAllLibraries();
        assertNotNull(libraries);
        assertEquals(
                "Warning: Internal lint error: cyclic library dependency for Project [dir=project1]",
                client.getLoggedOutput());
        assertEquals(1, libraries.size());
        assertSame(project2, libraries.get(0));
        assertEquals(1, project2.getAllLibraries().size());
        assertSame(project1, project2.getAllLibraries().get(0));
    }

    public void testOkLibraryReferences() {
        LoggingTestLintClient client = new LoggingTestLintClient();
        File dir1 = new File("project1");
        File dir2 = new File("project2");
        File dir3 = new File("project3");
        Project project1 = Project.create(client, dir1, dir1);
        client.registerProject(dir1, project1);
        Project project2 = Project.create(client, dir2, dir2);
        client.registerProject(dir2, project2);
        Project project3 = Project.create(client, dir3, dir3);
        client.registerProject(dir3, project3);
        project1.setDirectLibraries(Arrays.asList(project2, project3));
        project2.setDirectLibraries(Collections.singletonList(project3));
        project3.setDirectLibraries(Collections.emptyList());
        List<Project> libraries = project1.getAllLibraries();
        assertNotNull(libraries);
        assertEquals("", client.getLoggedOutput());
        assertEquals(2, libraries.size());
        assertTrue(libraries.contains(project2));
        assertTrue(libraries.contains(project3));
        assertEquals(1, project2.getAllLibraries().size());
        assertSame(project3, project2.getAllLibraries().get(0));
        assertTrue(project3.getAllLibraries().isEmpty());
    }

    public void testDependsOn1() {
        List<Project> projects =
                lint().files(
                                manifest().minSdk(14),
                                jar("libs/android-support-v4.jar") // just a placeholder
                                )
                        .createProjects(true);

        assertThat(projects).hasSize(1);
        Project project1 = projects.get(0);
        assertNull(project1.dependsOn("unknown:library"));
        assertTrue(project1.dependsOn("com.android.support:appcompat-v7"));
    }

    public void testDependsOn2() {
        List<Project> projects =
                lint().files(
                                manifest().minSdk(14),
                                jar("libs/support-v4-13.0.0-f5279ca6f213451a9dfb870f714ce6e6.jar")
                                // just a placeholder
                                )
                        .createProjects(true);

        assertThat(projects).hasSize(1);
        Project project = projects.get(0);
        assertNull(project.dependsOn("unknown:library"));
        assertTrue(project.dependsOn("com.android.support:appcompat-v7"));
    }

    @Override
    protected Detector getDetector() {
        return new UnusedResourceDetector();
    }
}
