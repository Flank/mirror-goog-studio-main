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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.tools.lint.client.api.JavaEvaluatorKt.TYPE_STRING;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.UastScanner;
import com.android.tools.lint.detector.api.Detector.XmlScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.XmlUtils;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Ensures that PreferenceActivity and its subclasses are never exported.
 */
public class PreferenceActivityDetector extends Detector
        implements XmlScanner, UastScanner {

    @SuppressWarnings("unchecked")
    public static final Implementation IMPLEMENTATION = new Implementation(
            PreferenceActivityDetector.class,
            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
            Scope.MANIFEST_SCOPE,
            Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ExportedPreferenceActivity",
            "PreferenceActivity should not be exported",
            "Fragment injection gives anyone who can send your PreferenceActivity an intent the "
                    + "ability to load any fragment, with any arguments, in your process.",
            Category.SECURITY,
            8,
            Severity.WARNING,
            IMPLEMENTATION)
            .addMoreInfo("http://securityintelligence.com/"
                    + "new-vulnerability-android-framework-fragment-injection");
    private static final String PREFERENCE_ACTIVITY = "android.preference.PreferenceActivity";
    private static final String IS_VALID_FRAGMENT = "isValidFragment";

    private final Map<String, Location.Handle> mExportedActivities =
            new HashMap<>();

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTIVITY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (SecurityDetector.getExported(element)) {
            String fqcn = LintUtils.resolveManifestName(element);
            if (fqcn.equals(PREFERENCE_ACTIVITY) &&
                    !context.getDriver().isSuppressed(context, ISSUE, element)) {
                String message = "`PreferenceActivity` should not be exported";
                context.report(ISSUE, element, context.getLocation(element), message);
            }
            mExportedActivities.put(fqcn, context.createLocationHandle(element));
        }
    }

    // ---- Implements UastScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(PREFERENCE_ACTIVITY);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (!context.getProject().getReportIssues()) {
            return;
        }
        JavaEvaluator evaluator = context.getEvaluator();
        String className = declaration.getQualifiedName();
        if (evaluator.extendsClass(declaration, PREFERENCE_ACTIVITY, false)
                && isExported(context, className)) {
            // Ignore the issue if we target an API greater than 19 and the class in
            // question specifically overrides isValidFragment() and thus knowingly white-lists
            // valid fragments.
            if (context.getMainProject().getTargetSdk() >= 19
                    && overridesIsValidFragment(evaluator, declaration)) {
                return;
            }

            String message = String.format(
                    "`PreferenceActivity` subclass `%1$s` should not be exported",
                    className);
            Location location;
            if (context.getScope().contains(Scope.MANIFEST)) {
                location = mExportedActivities.get(className).resolve();
            } else {
                // When linting incrementally just in the Java class, place the error on
                // the class itself rather than the export line in the manifest
                location = context.getNameLocation(declaration);
                message += " in the manifest";
            }
            context.report(ISSUE, declaration, location, message);
        }
    }

    private boolean isExported(@NonNull JavaContext context, @Nullable String className) {
        if (className == null) {
            return false;
        }

        // If analyzing manifest files directly, we've already recorded the available
        // activities
        if (context.getScope().contains(Scope.MANIFEST)) {
            return mExportedActivities.containsKey(className);
        }
        Project mainProject = context.getMainProject();

        Document mergedManifest = mainProject.getMergedManifest();
        if (mergedManifest == null ||
                mergedManifest.getDocumentElement() == null) {
            return false;
        }
        Element application = XmlUtils.getFirstSubTagByName(
                mergedManifest.getDocumentElement(), TAG_APPLICATION);
        if (application != null) {
            for (Element element : XmlUtils.getSubTags(application)) {
                if (TAG_ACTIVITY.equals(element.getTagName())) {
                    String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (className.endsWith(name)) {
                        String fqn = LintUtils.resolveManifestName(element);
                        if (fqn.equals(className)) {
                            return SecurityDetector.getExported(element);
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean overridesIsValidFragment(
            @NonNull JavaEvaluator evaluator,
            @NonNull PsiClass resolvedClass) {
        for (PsiMethod method : resolvedClass.findMethodsByName(IS_VALID_FRAGMENT, false)) {
            if (evaluator.parametersMatch(method, TYPE_STRING)) {
                return true;
            }
        }
        return false;
    }
}
