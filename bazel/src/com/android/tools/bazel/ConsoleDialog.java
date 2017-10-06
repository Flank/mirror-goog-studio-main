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
package com.android.tools.bazel;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

/**
 * A generic dialog that exposes a writer to for the user to add content to. This content
 * is displayed in a scrollable text area.
 */
public class ConsoleDialog extends DialogWrapper {

    private JTextArea myText;
    private final PrintWriter myWriter;
    private final String myTitle;

    public ConsoleDialog(@Nullable Project project, String title) {
        super(project, true, DialogWrapper.IdeModalityType.MODELESS);
        myTitle = title;
        myWriter = new PrintWriter(new JTextAreaWriter());
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder(new EtchedBorder(), myTitle));

        // create the middle panel components

        myText = new JTextArea(16, 58);
        myText.setEditable(false);

        JScrollPane scroll = new JBScrollPane(myText);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    public PrintWriter getWriter() {
        return myWriter;
    }

    class JTextAreaWriter extends Writer {
        public void write(char[] cbuf, int off, int len) throws IOException {
            myText.append(new String(cbuf, off, len));
        }

        @Override
        public void flush() throws IOException {
        }

        @Override
        public void close() throws IOException {
        }
    }
}
