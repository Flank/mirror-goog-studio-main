/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import java.util.EnumSet;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Checks that byte order marks do not appear in resource names
 */
public class ByteOrderMarkDetector extends ResourceXmlDetector
        implements Detector.JavaPsiScanner, Detector.GradleScanner {

    /** Detects BOM characters in the middle of files */
    public static final Issue BOM = Issue.create(
            "ByteOrderMark",
            "Byte order mark inside files",
            "Lint will flag any byte-order-mark (BOM) characters it finds in the middle " +
            "of a file. Since we expect files to be encoded with UTF-8 (see the EnforceUTF8 " +
            "issue), the BOM characters are not necessary, and they are not handled correctly " +
            "by all tools. For example, if you have a BOM as part of a resource name in one " +
            "particular translation, that name will not be considered identical to the base " +
            "resource's name and the translation will not be used.",
            Category.I18N,
            8,
            Severity.FATAL,
            new Implementation(
              ByteOrderMarkDetector.class,
              // Applies to all text files
              EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE, Scope.JAVA_FILE, Scope.GRADLE_FILE,
                      Scope.PROPERTY_FILE, Scope.PROGUARD_FILE),
              Scope.RESOURCE_FILE_SCOPE,
              Scope.JAVA_FILE_SCOPE,
              Scope.MANIFEST_SCOPE,
              Scope.JAVA_FILE_SCOPE,
              Scope.GRADLE_SCOPE,
              Scope.PROPERTY_SCOPE,
              Scope.PROGUARD_SCOPE))
            .addMoreInfo("http://en.wikipedia.org/wiki/Byte_order_mark");

    /** Constructs a new {@link ByteOrderMarkDetector} */
    public ByteOrderMarkDetector() {
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        CharSequence source = context.getContents();
        if (source == null) {
            return;
        }
        int max = source.length();
        for (int i = 1; i < max; i++) {
            char c = source.charAt(i);
            if (c == '\uFEFF') {
                Location location = Location.create(context.file, source, i, i + 1);
                String message = "Found byte-order-mark in the middle of a file";

                if (context instanceof XmlContext) {
                    XmlContext xmlContext = (XmlContext)context;
                    Node leaf = xmlContext.getParser().findNodeAt(xmlContext, i);
                    if (leaf != null) {
                        xmlContext.report(BOM, leaf, location, message);
                        continue;
                    }
                } else if (context instanceof JavaContext) {
                    JavaContext javaContext = (JavaContext)context;
                    PsiJavaFile file = javaContext.getJavaFile();
                    if (file != null) {
                        PsiElement closest = javaContext.getParser().findElementAt(javaContext, i);
                        if (closest == null && file.getClasses().length > 0) {
                            closest = file.getClasses()[0];
                        }
                        if (closest != null) {
                            javaContext.report(BOM, closest, location, message);
                            continue;
                        }
                    }
                }

                // Report without surrounding scope node; no nearby @SuppressLint annotation
                context.report(BOM, location, message);
            }
        }
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // The work is done in beforeCheckFile()
    }

    @Nullable
    @Override
    public JavaElementVisitor createPsiVisitor(@NonNull JavaContext context) {
        // Java files: work is done in beforeCheckFile()
        return new JavaElementVisitor() { };
    }

    @Override
    public void run(@NonNull Context context) {
        // ProGuard files: work is done in beforeCheckFile()
    }
}
