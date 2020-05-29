/*
 * Copyright (C) 2020 The Android Open Source Project
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

@file:Suppress("SpellCheckingInspection")

package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import org.junit.Test

class HtmlPaneDetectorTest {

    @Test
    fun testProblems() {
        studioLint()
            .files(
                java(
                    """
                    package test.pkg;
                    import javax.swing.JEditorPane;

                    @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class EditorPaneTest {
                        public static final String HTML_KIT = "text/html";

                        public void test1() {
                            JEditorPane pane = new JEditorPane();
                            pane.setContentType("text/html"); // ERROR
                        }

                        public void test2() {
                            JEditorPane pane = new JEditorPane(
                                    "http://google.com",
                                    "text/html"); // ERROR
                        }

                        public void test3(EditorKit kit) {
                            JEditorPane pane = new JEditorPane();
                            pane.setContentType("text/html"); // OK
                            pane.setEditorKit(kit);
                        }

                        public void test4(EditorKit kit) {
                            JEditorPane pane = new JEditorPane(
                                    "http://google.com",
                                    "text/html"); // OK
                            pane.setEditorKit(kit);
                        }
                    }
                   """
                ).indented(),
                kotlin(
                    """
                    package test.pkg
                    import javax.swing.JEditorPane

                    class EditorPaneTest2 {
                        fun test1() {
                            val pane = JEditorPane()
                            pane.setContentType("text/html") // ERROR
                        }

                        fun test1b() {
                            val pane = JEditorPane()
                             pane.contentType = "text/html" // ERROR
                        }

                        fun test2() {
                            val pane =
                                JEditorPane(
                                    "http://google.com",
                                    "text/html" // ERROR
                                )
                        }

                        fun test3(kit: EditorKit?) {
                            val pane = JEditorPane()
                            pane.setContentType("text/html") // OK
                            pane.editorKit = kit
                        }

                        fun test4(kit: EditorKit?) {
                            val pane =
                                JEditorPane(
                                    "http://google.com",
                                    "text/html" // OK
                                )
                            pane.editorKit = kit
                        }

                        private val kit: EditorKit? = null

                        val test5 = object : JEditorPane("text/html", "") {} // OK
                            .also {
                                it.editorKit = kit
                            }

                        @Suppress("MethodMayBeStatic")val test6 = object : JEditorPane() {}
                            .also {
                                it.contentType = "text/html" // ERROR
                            }
                }
                """
                ).indented(),
                // Stubs
                java(
                    """
                    package javax.swing;
                    @SuppressWarnings("all")
                    public class JEditorPane {
                        public JEditorPane() {}
                        public JEditorPane(String url) {}
                        public JEditorPane(String url, String contentType) { }
                        public void setContentType(String type) { }
                        public String getContentType() { return null; }
                        public void setEditorKitForContentType(String type, EditorKit k) { }
                        public void setEditorKit(EditorKit kit) { }
                        public EditorKit getEditorKit() { return null; }
                    }
                    """
                ),
                java(
                    """
                    package javax.swing;
                    @SuppressWarnings("all")
                    public class EditorKit {
                    }
                    """
                )
            )
            .issues(HtmlPaneDetector.ISSUE)
            .checkUInjectionHost(false)
            .run()
            .expect(
                """
                src/test/pkg/EditorPaneTest.java:10: Error: Constructing an HTML JEditorPane directly can lead to subtle theming bugs; either set the editor kit directly (setEditorKit(UIUtil.getHTMLEditorKit())) or better yet use SwingHelper.createHtmlViewer [HtmlPaneColors]
                        pane.setContentType("text/html"); // ERROR
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/EditorPaneTest.java:14: Error: Constructing an HTML JEditorPane directly can lead to subtle theming bugs; either set the editor kit directly (setEditorKit(UIUtil.getHTMLEditorKit())) or better yet use SwingHelper.createHtmlViewer [HtmlPaneColors]
                        JEditorPane pane = new JEditorPane(
                                           ^
                src/test/pkg/EditorPaneTest2.kt:7: Error: Constructing an HTML JEditorPane directly can lead to subtle theming bugs; either set the editor kit directly (setEditorKit(UIUtil.getHTMLEditorKit())) or better yet use SwingHelper.createHtmlViewer [HtmlPaneColors]
                            pane.setContentType("text/html") // ERROR
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/EditorPaneTest2.kt:12: Error: Constructing an HTML JEditorPane directly can lead to subtle theming bugs; either set the editor kit directly (setEditorKit(UIUtil.getHTMLEditorKit())) or better yet use SwingHelper.createHtmlViewer [HtmlPaneColors]
                             pane.contentType = "text/html" // ERROR
                             ~~~~~~~~~~~~~~~~
                src/test/pkg/EditorPaneTest2.kt:17: Error: Constructing an HTML JEditorPane directly can lead to subtle theming bugs; either set the editor kit directly (setEditorKit(UIUtil.getHTMLEditorKit())) or better yet use SwingHelper.createHtmlViewer [HtmlPaneColors]
                                JEditorPane(
                                ^
                src/test/pkg/EditorPaneTest2.kt:47: Error: Constructing an HTML JEditorPane directly can lead to subtle theming bugs; either set the editor kit directly (setEditorKit(UIUtil.getHTMLEditorKit())) or better yet use SwingHelper.createHtmlViewer [HtmlPaneColors]
                                it.contentType = "text/html" // ERROR
                                ~~~~~~~~~~~~~~
                6 errors, 0 warnings
                """
            )
    }
}
