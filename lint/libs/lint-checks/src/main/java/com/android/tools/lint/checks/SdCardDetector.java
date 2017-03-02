/*
 * Copyright (C) 2011 The Android Open Source Project
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
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastLiteralUtils;

/**
 * Looks for hardcoded references to /sdcard/.
 */
public class SdCardDetector extends Detector implements Detector.UastScanner {
    /** Hardcoded /sdcard/ references */
    public static final Issue ISSUE = Issue.create(
            "SdCardPath",
            "Hardcoded reference to `/sdcard`",

            "Your code should not reference the `/sdcard` path directly; instead use " +
            "`Environment.getExternalStorageDirectory().getPath()`.\n" +
            "\n" +
            "Similarly, do not reference the `/data/data/` path directly; it can vary " +
            "in multi-user scenarios. Instead, use " +
            "`Context.getFilesDir().getPath()`.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    SdCardDetector.class,
                    Scope.JAVA_FILE_SCOPE))
            .addMoreInfo(
            "http://developer.android.com/guide/topics/data/data-storage.html#filesExternal");

    /** Constructs a new {@link SdCardDetector} check */
    public SdCardDetector() {
    }


    // ---- Implements UastScanner ----

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(ULiteralExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new StringChecker(context);
    }

    private static class StringChecker extends UElementHandler {
        private final JavaContext context;

        public StringChecker(JavaContext context) {
            this.context = context;
        }

        @Override
        public void visitLiteralExpression(@NonNull ULiteralExpression node) {
            String s = UastLiteralUtils.getValueIfStringLiteral(node);
            if (s != null && !s.isEmpty()) {
                char c = s.charAt(0);
                if (c != '/' && c != 'f') {
                    return;
                }

                if (s.startsWith("/sdcard")
                        || s.startsWith("/mnt/sdcard/")
                        || s.startsWith("/system/media/sdcard")
                        || s.startsWith("file://sdcard/")
                        || s.startsWith("file:///sdcard/")) {
                    String message = "Do not hardcode \"/sdcard/\"; " +
                            "use `Environment.getExternalStorageDirectory().getPath()` instead";
                    Location location = context.getLocation(node);
                    context.report(ISSUE, node, location, message);
                } else if (s.startsWith("/data/data/")
                        || s.startsWith("/data/user/")) {
                    String message = "Do not hardcode \"`/data/`\"; " +
                            "use `Context.getFilesDir().getPath()` instead";
                    Location location = context.getLocation(node);
                    context.report(ISSUE, node, location, message);
                }
            }
        }
    }
}
