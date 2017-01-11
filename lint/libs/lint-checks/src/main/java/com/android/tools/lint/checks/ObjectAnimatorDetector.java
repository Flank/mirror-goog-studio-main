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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildTypeContainer;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.detector.api.TypeEvaluator;
import com.google.common.collect.Sets;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Looks for issues around ObjectAnimator usages
 */
public class ObjectAnimatorDetector extends Detector implements JavaPsiScanner {
    public static final String KEEP_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "Keep";

    private static final Implementation IMPLEMENTATION = new Implementation(
            ObjectAnimatorDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /** Missing @Keep */
    public static final Issue MISSING_KEEP = Issue.create(
            "AnimatorKeep",
            "Missing @Keep for Animated Properties",

            "When you use property animators, properties can be accessed via reflection. "
                    + "Those methods should be annotated with @Keep to ensure that during "
                    + "release builds, the methods are not potentially treated as unused "
                    + "and removed, or treated as internal only and get renamed to something "
                    + "shorter.\n"
                    + "\n"
                    + "This check will also flag other potential reflection problems it "
                    + "encounters, such as a missing property, wrong argument types, etc.",

            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Incorrect ObjectAnimator binding */
    public static final Issue BROKEN_PROPERTY = Issue.create(
            "ObjectAnimatorBinding",
            "Incorrect ObjectAnimator Property",

            "This check cross references properties referenced by String from `ObjectAnimator` "
                    + "and `PropertyValuesHolder` method calls and ensures that the corresponding "
                    + "setter methods exist and have the right signatures.",

            Category.CORRECTNESS,
            4,
            Severity.ERROR,
            IMPLEMENTATION);

    /**
     * Multiple properties might all point back to the same setter; we don't want to
     * highlight these more than once (duplicate warnings etc) so keep track of them here
     */
    private Set<PsiElement> mAlreadyWarned;

    /** Constructs a new {@link ObjectAnimatorDetector} */
    public ObjectAnimatorDetector() {
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "ofInt",
                "ofArgb",
                "ofFloat",
                "ofMultiInt",
                "ofMultiFloat",
                "ofObject",
                "ofPropertyValuesHolder");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiMethodCallExpression call, @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.isMemberInClass(method, "android.animation.ObjectAnimator") &&
                !(method.getName().equals("ofPropertyValuesHolder")
                    && evaluator.isMemberInClass(method, "android.animation.ValueAnimator"))) {
            return;
        }

        PsiExpression[] expressions = call.getArgumentList().getExpressions();
        if (expressions.length < 2) {
            return;
        }

        PsiType type = TypeEvaluator.evaluate(context, expressions[0]);
        if (!(type instanceof PsiClassType)) {
            return;
        }
        PsiClass targetClass = ((PsiClassType) type).resolve();
        if (targetClass == null) {
            return;
        }

        String methodName = method.getName();
        if (methodName.equals("ofPropertyValuesHolder")) {
            if (!evaluator.isMemberInClass(method, "android.animation.ObjectAnimator")) {
                // *Don't* match ValueAnimator.ofPropertyValuesHolder(); for that method,
                // arg0 is another PropertyValuesHolder, *not* the target object!
                return;
            }

            // Try to find the corresponding property value holder initializations
            // and validate each one
            checkPropertyValueHolders(context, targetClass, expressions);
        } else {
            // If "ObjectAnimator#ofObject", look for the type evaluator type in
            // argument at index 2 (third argument)
            String expectedType = getExpectedType(context, call, 2);
            if (expectedType != null) {
                checkProperty(context, expressions[1], targetClass, expectedType);
            }
        }
    }

    @Nullable
    private static String getExpectedType(
            @NonNull JavaContext context,
            @NonNull PsiMethodCallExpression method,
            int evaluatorIndex) {
        String methodName = method.getMethodExpression().getReferenceName();

        if (methodName == null) {
            return null;
        }

        switch (methodName) {
            case "ofArgb":
            case "ofInt" : return "int";
            case "ofFloat" : return "float";
            case "ofMultiInt" : return "int[]";
            case "ofMultiFloat" : return "float[]";
            case "ofKeyframe" : return "android.animation.Keyframe";
            case "ofObject" : {
                PsiExpression[] args = method.getArgumentList().getExpressions();
                if (args.length > evaluatorIndex) {
                    PsiType evaluatorType = TypeEvaluator.evaluate(context, args[evaluatorIndex]);
                    if (evaluatorType != null) {
                        String typeName = evaluatorType.getCanonicalText();
                        if ("android.animation.FloatEvaluator".equals(typeName)) {
                            return "float";
                        } else if ("android.animation.FloatArrayEvaluator".equals(typeName)) {
                            return "float[]";
                        } else if ("android.animation.IntEvaluator".equals(typeName)
                                || "android.animation.ArgbEvaluator".equals(typeName)) {
                            return "int";
                        } else if ("android.animation.IntArrayEvaluator".equals(typeName)) {
                            return "int[]";
                        } else if ("android.animation.PointFEvaluator".equals(typeName)) {
                            return "android.graphics.PointF";
                        }
                    }
                }
            }
        }

        return null;
    }

    private void checkPropertyValueHolders(
            @NonNull JavaContext context,
            @NonNull PsiClass targetClass,
            @NonNull PsiExpression[] expressions) {
        for (int i = 1; i < expressions.length; i++) { // expressions[0] is the target class
            PsiExpression arg = expressions[i];
            // Find last assignment for each argument; this should be generic
            // infrastructure.
            PsiMethodCallExpression holder = findHolderConstruction(context, arg);
            if (holder != null) {
                PsiExpression[] args = holder.getArgumentList().getExpressions();
                if (args.length >= 2) {
                    // If "PropertyValueHolder#ofObject", look for the type evaluator type in
                    // argument at index 1 (second argument)
                    String expectedType = getExpectedType(context, holder, 1);
                    if (expectedType != null) {
                        checkProperty(context, args[0], targetClass, expectedType);
                    }
                }
            }
        }
    }

    @Nullable
    private static PsiMethodCallExpression findHolderConstruction(@NonNull JavaContext context,
            @Nullable PsiExpression arg) {
        if (arg == null) {
            return null;
        }
        if (arg instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression callExpression = (PsiMethodCallExpression) arg;
            if (isHolderConstructionMethod(context, callExpression)) {
                return callExpression;
            }
            // else: look inside the method and see if it's a method which trivially returns
            // an instance?
        } else if (arg instanceof PsiReferenceExpression) {
            // Variable reference? Field reference? etc.
            PsiElement resolved = ((PsiReferenceExpression) arg).resolve();
            if (resolved instanceof PsiVariable) {
                PsiVariable variable = (PsiVariable) resolved;
                PsiExpression initializer = variable.getInitializer();
                if (initializer != null) {
                    PsiMethodCallExpression holder = findHolderConstruction(context, initializer);
                    if (holder != null) {
                        return holder;
                    }
                }

                if (!(variable instanceof PsiField)) {
                    PsiStatement statement = PsiTreeUtil.getParentOfType(arg, PsiStatement.class,
                            false);
                    if (statement != null) {
                        PsiStatement prev = PsiTreeUtil.getPrevSiblingOfType(statement,
                                PsiStatement.class);
                        String targetName = variable.getName();
                        if (targetName == null) {
                            return null;
                        }
                        while (prev != null) {
                            if (prev instanceof PsiDeclarationStatement) {
                                for (PsiElement element : ((PsiDeclarationStatement) prev)
                                        .getDeclaredElements()) {
                                    if (variable.equals(element)) {
                                        return findHolderConstruction(context,
                                                variable.getInitializer());
                                    }
                                }
                            } else if (prev instanceof PsiExpressionStatement) {
                                PsiExpression expression = ((PsiExpressionStatement) prev)
                                        .getExpression();
                                if (expression instanceof PsiAssignmentExpression) {
                                    PsiAssignmentExpression assign
                                            = (PsiAssignmentExpression) expression;
                                    PsiExpression lhs = assign.getLExpression();
                                    if (lhs instanceof PsiReferenceExpression) {
                                        PsiReferenceExpression reference =
                                                (PsiReferenceExpression) lhs;
                                        if (targetName.equals(reference.getReferenceName()) &&
                                                reference.getQualifier() == null) {
                                            return findHolderConstruction(context,
                                                    assign.getRExpression());
                                        }
                                    }
                                }
                            }
                            prev = PsiTreeUtil.getPrevSiblingOfType(prev,
                                    PsiStatement.class);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isHolderConstructionMethod(@NonNull JavaContext context,
            @NonNull PsiMethodCallExpression callExpression) {
        String referenceName = callExpression.getMethodExpression().getReferenceName();
        if (referenceName != null && referenceName.startsWith("of")) {
            PsiMethod resolved = callExpression.resolveMethod();
            if (resolved != null && context.getEvaluator().isMemberInClass(resolved,
                    "android.animation.PropertyValuesHolder")) {
                return true;
            }
        }

        return false;
    }

    private void checkProperty(
            @NonNull JavaContext context,
            @NonNull PsiExpression propertyNameExpression,
            @NonNull PsiClass targetClass,
            @NonNull String expectedType) {
        Object property = ConstantEvaluator.evaluate(context, propertyNameExpression);
        if (!(property instanceof String)) {
            return;
        }
        String propertyName = (String) property;

        String qualifiedName = targetClass.getQualifiedName();
        if (qualifiedName == null || qualifiedName.indexOf('.') == -1) { // resolve error?
            return;
        }

        String methodName = getMethodName("set", propertyName);
        PsiMethod[] methods = targetClass.findMethodsByName(methodName, true);

        PsiMethod bestMethod = null;
        boolean isExactMatch = false;

        for (PsiMethod m : methods) {
            if (m.getParameterList().getParametersCount() == 1) {
                if (bestMethod == null) {
                    bestMethod = m;
                }
                if (context.getEvaluator().parametersMatch(m, expectedType)) {
                    bestMethod = m;
                    isExactMatch = true;
                    break;
                }
            } else if (bestMethod == null) {
                bestMethod = m;
            }
        }

        if (bestMethod == null) {
            report(context, BROKEN_PROPERTY, propertyNameExpression, null,
                    String.format("Could not find property setter method `%1$s` on `%2$s`",
                            methodName, qualifiedName));
            return;
        }

        if (!isExactMatch) {
            report(context, BROKEN_PROPERTY, propertyNameExpression, bestMethod,
                    String.format("The setter for this property does not match the "
                                    + "expected signature (`public void %1$s(%2$s arg`)",
                            methodName, expectedType));
        } else if (context.getEvaluator().isStatic(bestMethod)) {
            report(context, BROKEN_PROPERTY, propertyNameExpression, bestMethod,
                    String.format("The setter for this property (%1$s.%2$s) should not be static",
                            qualifiedName, methodName));
        } else {
            PsiModifierListOwner owner = bestMethod;
            while (owner != null) {
                PsiModifierList modifierList = owner.getModifierList();
                if (modifierList != null) {
                    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                        if (KEEP_ANNOTATION.equals(annotation.getQualifiedName())) {
                            return;
                        }
                    }
                }
                owner = PsiTreeUtil.getParentOfType(owner, PsiModifierListOwner.class, true);
            }

            // Only flag these warnings if minifyEnabled is true in at least one
            // variant?
            if (!isMinifying(context)) {
                return;
            }

            report(context, MISSING_KEEP, propertyNameExpression, bestMethod, ""
                  // Keep in sync with isAddKeepErrorMessage below
                  + "This method is accessed from an ObjectAnimator so it should be "
                  + "annotated with `@Keep` to ensure that it is not discarded or "
                  + "renamed in release builds");
        }
    }

    private void report(
            @NonNull JavaContext context,
            @NonNull Issue issue,
            @NonNull PsiExpression propertyNameExpression,
            @Nullable PsiMethod method,
            @NonNull String message) {
        boolean reportOnMethod = issue == MISSING_KEEP && method != null;

        // No need to report @Keep issues in third party libraries
        if (reportOnMethod && method instanceof PsiCompiledElement) {
            return;
        }

        PsiElement locationNode = reportOnMethod ? method : propertyNameExpression;

        if (mAlreadyWarned != null && mAlreadyWarned.contains(locationNode)) {
            return;
        } else if (mAlreadyWarned == null) {
            mAlreadyWarned = Sets.newIdentityHashSet();
        }
        mAlreadyWarned.add(locationNode);

        Location methodLocation = null;
        if (method != null) {
            methodLocation = method.getNameIdentifier() != null
                    ? context.getRangeLocation(method.getNameIdentifier(), 0, method.getParameterList(), 0)
                    : context.getNameLocation(method);
        }

        Location location = reportOnMethod ? methodLocation : context.getNameLocation(locationNode);
        if (reportOnMethod) {
            Location secondary = context.getLocation(propertyNameExpression);
            location.setSecondary(secondary);
            secondary.setMessage("ObjectAnimator usage here");

            // In the same compilation unit, don't show the error on the reference,
            // but in other files (where you may not spot the problem), highlight it.
            if (Objects.equals(propertyNameExpression.getContainingFile(),
                    method.getContainingFile())) {
                // Same compilation unit: we don't need to show (in the IDE) the secondary
                // location since we're drawing attention to the keep issue)
                secondary.setVisible(false);
            } else {
                //noinspection ConstantConditions
                assert issue == MISSING_KEEP;
                String secondaryMessage = String.format("The method referenced here (%1$s) has "
                                + "not been annotated with `@Keep` which means it could be "
                                + "discarded or renamed in release builds",
                        method.getName());

                // If on the other hand we're in a separate compilation unit, we should
                // draw attention to the problem
                if (location == Location.NONE) {
                    // When running within the IDE in single file scope the IDE just creates
                    // none-locations for items in other files; in this case make this
                    // the primary locations instead
                    location = secondary;
                    message = secondaryMessage;
                } else {
                    secondary.setMessage(secondaryMessage);
                }
            }
        } else if (methodLocation != null) {
            location = location.withSecondary(methodLocation, "Property setter here");
        }
        context.report(issue, method, location, message);
    }

    /**
     * Returns true if the error message (which should have been produced by this detector)
     * corresponds to the method listed <b>on</b> a property method that it's missing a
     * Keep annotation. Used by IDE quickfixes to only add keep annotations on the actual
     * keep method, not on the error message (with the same inspection id) shown on the
     * object animator.
     *
     * @param message    the original message produced by lint
     * @param textFormat the format it's been provided in
     * @return true if this is a message on a property method missing {@code @Keep}
     */
    @SuppressWarnings("unused") // Referenced from IDE
    public static boolean isAddKeepErrorMessage(@NonNull String message,
        @NonNull TextFormat textFormat) {
        message = textFormat.convertTo(message, TextFormat.RAW);
        return message.contains("This method is accessed from an ObjectAnimator so");
    }

    // Copy of PropertyValuesHolder#getMethodName - copy to ensure lint & platform agree
    private static String getMethodName(String prefix, String propertyName) {
        //noinspection SizeReplaceableByIsEmpty
        if (propertyName == null || propertyName.length() == 0) {
            // shouldn't get here
            return prefix;
        }
        char firstLetter = Character.toUpperCase(propertyName.charAt(0));
        String theRest = propertyName.substring(1);
        return prefix + firstLetter + theRest;
    }

    @SuppressWarnings("SpellCheckingInspection")
    private static boolean isMinifying(@NonNull JavaContext context) {
        Project project = context.getMainProject();
        if (!project.isGradleProject()) {
            // Not a Gradle project: assume project may be using ProGuard/other shrinking
            return true;
        }

        AndroidProject model = project.getGradleProjectModel();
        if (model != null) {
            for (BuildTypeContainer buildTypeContainer : model.getBuildTypes()) {
                if (buildTypeContainer.getBuildType().isMinifyEnabled()) {
                    return true;
                }
            }
        } else {
            // No model? Err on the side of caution.
            return true;
        }

        return false;
    }
}
