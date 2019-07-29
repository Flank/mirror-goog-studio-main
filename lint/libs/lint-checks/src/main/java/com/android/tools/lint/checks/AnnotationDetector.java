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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.FQCN_SUPPRESS_LINT;
import static com.android.SdkConstants.INT_DEF_ANNOTATION;
import static com.android.SdkConstants.LONG_DEF_ANNOTATION;
import static com.android.SdkConstants.STRING_DEF_ANNOTATION;
import static com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX;
import static com.android.SdkConstants.TYPE_DEF_FLAG_ATTRIBUTE;
import static com.android.tools.lint.client.api.JavaEvaluatorKt.TYPE_DOUBLE;
import static com.android.tools.lint.client.api.JavaEvaluatorKt.TYPE_FLOAT;
import static com.android.tools.lint.client.api.JavaEvaluatorKt.TYPE_INT;
import static com.android.tools.lint.client.api.JavaEvaluatorKt.TYPE_LONG;
import static com.android.tools.lint.client.api.JavaEvaluatorKt.TYPE_SHORT;
import static com.android.tools.lint.client.api.JavaEvaluatorKt.TYPE_STRING;
import static com.android.tools.lint.detector.api.Lint.getAutoBoxedType;
import static com.android.tools.lint.detector.api.ResourceEvaluator.COLOR_INT_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.DIMENSION_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.PX_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.RES_SUFFIX;
import static com.android.tools.lint.detector.api.UastLintUtils.getAnnotationBooleanValue;
import static com.android.tools.lint.detector.api.UastLintUtils.getAnnotationStringValue;
import static com.android.tools.lint.detector.api.UastLintUtils.getAnnotationStringValues;
import static com.android.tools.lint.detector.api.UastLintUtils.getDoubleAttribute;
import static com.android.tools.lint.detector.api.UastLintUtils.getLongAttribute;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.support.AndroidxName;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.ExternalReferenceExpression;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UastLintUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kotlin.collections.CollectionsKt;
import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UDeclaration;
import org.jetbrains.uast.UDeclarationsExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UIdentifier;
import org.jetbrains.uast.UIfExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.ULocalVariable;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UNamedExpression;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.USwitchClauseExpression;
import org.jetbrains.uast.USwitchExpression;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.java.JavaUAnnotation;
import org.jetbrains.uast.java.JavaUTypeCastExpression;
import org.jetbrains.uast.kotlin.KotlinUSwitchExpression;
import org.jetbrains.uast.util.UastExpressionUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

/** Checks annotations to make sure they are valid */
public class AnnotationDetector extends Detector implements SourceCodeScanner {
    public static final String GMS_HIDE_ANNOTATION = "com.google.android.gms.common.internal.Hide";
    public static final AndroidxName CHECK_RESULT_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "CheckResult");
    public static final AndroidxName INT_RANGE_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "IntRange");
    public static final AndroidxName FLOAT_RANGE_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "FloatRange");
    public static final AndroidxName SIZE_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "Size");
    public static final AndroidxName PERMISSION_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX.oldName(), "RequiresPermission");
    public static final AndroidxName UI_THREAD_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "UiThread");
    public static final AndroidxName MAIN_THREAD_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "MainThread");
    public static final AndroidxName WORKER_THREAD_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "WorkerThread");
    public static final AndroidxName BINDER_THREAD_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "BinderThread");
    public static final AndroidxName ANY_THREAD_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "AnyThread");
    public static final AndroidxName RESTRICT_TO_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "RestrictTo");
    public static final AndroidxName VISIBLE_FOR_TESTING_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "VisibleForTesting");
    public static final AndroidxName PERMISSION_ANNOTATION_READ =
            AndroidxName.of(PERMISSION_ANNOTATION, "Read");
    public static final AndroidxName PERMISSION_ANNOTATION_WRITE =
            AndroidxName.of(PERMISSION_ANNOTATION, "Write");

    // TODO: Add analysis to enforce this annotation:
    public static final AndroidxName HALF_FLOAT_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "HalfFloat");

    public static final String THREAD_SUFFIX = "Thread";
    public static final String ATTR_SUGGEST = "suggest";
    public static final String ATTR_TO = "to";
    public static final String ATTR_FROM = "from";
    public static final String ATTR_FROM_INCLUSIVE = "fromInclusive";
    public static final String ATTR_TO_INCLUSIVE = "toInclusive";
    public static final String ATTR_MULTIPLE = "multiple";
    public static final String ATTR_MIN = "min";
    public static final String ATTR_MAX = "max";
    public static final String ATTR_ALL_OF = "allOf";
    public static final String ATTR_ANY_OF = "anyOf";
    public static final String ATTR_CONDITIONAL = "conditional";

    public static final String SECURITY_EXCEPTION = "java.lang.SecurityException";

    public static final String FINDBUGS_ANNOTATIONS_CHECK_RETURN_VALUE =
            "edu.umd.cs.findbugs.annotations.CheckReturnValue";
    public static final String JAVAX_ANNOTATION_CHECK_RETURN_VALUE =
            "javax.annotation.CheckReturnValue";
    public static final String ERRORPRONE_CAN_IGNORE_RETURN_VALUE =
            "com.google.errorprone.annotations.CanIgnoreReturnValue";
    public static final String GUAVA_VISIBLE_FOR_TESTING =
            "com.google.common.annotations.VisibleForTesting";

    public static final Implementation IMPLEMENTATION =
            new Implementation(AnnotationDetector.class, Scope.JAVA_FILE_SCOPE);

    /** Placing SuppressLint on a local variable doesn't work for class-file based checks */
    public static final Issue INSIDE_METHOD =
            Issue.create(
                    "LocalSuppress",
                    "@SuppressLint on invalid element",
                    "The `@SuppressAnnotation` is used to suppress Lint warnings in Java files. However, "
                            + "while many lint checks analyzes the Java source code, where they can find "
                            + "annotations on (for example) local variables, some checks are analyzing the "
                            + "`.class` files. And in class files, annotations only appear on classes, fields "
                            + "and methods. Annotations placed on local variables disappear. If you attempt "
                            + "to suppress a lint error for a class-file based lint check, the suppress "
                            + "annotation not work. You must move the annotation out to the surrounding method.",
                    Category.CORRECTNESS,
                    3,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /** Incorrectly using a support annotation */
    @SuppressWarnings("WeakerAccess")
    public static final Issue ANNOTATION_USAGE =
            Issue.create(
                    "SupportAnnotationUsage",
                    "Incorrect support annotation usage",
                    "This lint check makes sure that the support annotations (such as "
                            + "`@IntDef` and `@ColorInt`) are used correctly. For example, it's an "
                            + "error to specify an `@IntRange` where the `from` value is higher than "
                            + "the `to` value.",
                    Category.CORRECTNESS,
                    2,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /** IntDef annotations should be unique */
    public static final Issue UNIQUE =
            Issue.create(
                            "UniqueConstants",
                            "Overlapping Enumeration Constants",
                            "The `@IntDef` annotation allows you to "
                                    + "create a light-weight \"enum\" or type definition. However, it's possible to "
                                    + "accidentally specify the same value for two or more of the values, which can "
                                    + "lead to hard-to-detect bugs. This check looks for this scenario and flags any "
                                    + "repeated constants.\n"
                                    + "\n"
                                    + "In some cases, the repeated constant is intentional (for example, renaming a "
                                    + "constant to a more intuitive name, and leaving the old name in place for "
                                    + "compatibility purposes).  In that case, simply suppress this check by adding a "
                                    + "`@SuppressLint(\"UniqueConstants\")` annotation.",
                            Category.CORRECTNESS,
                            3,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    /** Flags should typically be specified as bit shifts */
    public static final Issue FLAG_STYLE =
            Issue.create(
                    "ShiftFlags",
                    "Dangerous Flag Constant Declaration",
                    "When defining multiple constants for use in flags, the recommended style is "
                            + "to use the form `1 << 2`, `1 << 3`, `1 << 4` and so on to ensure that the "
                            + "constants are unique and non-overlapping.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** All IntDef constants should be included in switch */
    public static final Issue SWITCH_TYPE_DEF =
            Issue.create(
                            "SwitchIntDef",
                            "Missing @IntDef in Switch",
                            "This check warns if a `switch` statement does not explicitly include all "
                                    + "the values declared by the typedef `@IntDef` declaration.",
                            Category.CORRECTNESS,
                            3,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    /** Constructs a new {@link AnnotationDetector} check */
    public AnnotationDetector() {}

    // ---- implements SourceCodeScanner ----

    /**
     * Set of fields we've already warned about {@link #FLAG_STYLE} for; these can be referenced
     * multiple times, so we should only flag them once
     */
    private Set<PsiElement> mWarnedFlags;

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>(2);
        types.add(UAnnotation.class);
        types.add(USwitchExpression.class);
        return types;
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new AnnotationChecker(context);
    }

    private class AnnotationChecker extends UElementHandler {
        private final JavaContext mContext;

        public AnnotationChecker(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitAnnotation(@NonNull UAnnotation annotation) {
            String type = annotation.getQualifiedName();
            if (type == null || type.startsWith("java.lang.")) {
                return;
            }

            if (FQCN_SUPPRESS_LINT.equals(type)) {
                UElement parent = annotation.getUastParent();
                if (parent == null) {
                    return;
                }
                // Only flag local variables and parameters (not classes, fields and methods)
                if (!(parent instanceof UDeclarationsExpression
                        || parent instanceof ULocalVariable
                        || parent instanceof UParameter)) {
                    return;
                }
                List<UNamedExpression> attributes = annotation.getAttributeValues();
                if (attributes.size() == 1) {
                    UNamedExpression attribute = attributes.get(0);
                    UExpression value = attribute.getExpression();
                    if (value instanceof ULiteralExpression) {
                        Object v = ((ULiteralExpression) value).getValue();
                        if (v instanceof String) {
                            String id = (String) v;
                            checkSuppressLint(annotation, id);
                        }
                    } else if (UastExpressionUtils.isArrayInitializer(value)) {
                        for (UExpression ex : ((UCallExpression) value).getValueArguments()) {
                            if (ex instanceof ULiteralExpression) {
                                Object v = ((ULiteralExpression) ex).getValue();
                                if (v instanceof String) {
                                    String id = (String) v;
                                    if (!checkSuppressLint(annotation, id)) {
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (SUPPORT_ANNOTATIONS_PREFIX.isPrefix(type)) {
                if (CHECK_RESULT_ANNOTATION.isEquals(type)) {
                    // Check that the return type of this method is not void!
                    if (annotation.getUastParent() instanceof UMethod) {
                        UMethod method = (UMethod) annotation.getUastParent();
                        if (!method.isConstructor()
                                && PsiType.VOID.equals(method.getReturnType())) {
                            mContext.report(
                                    ANNOTATION_USAGE,
                                    annotation,
                                    mContext.getLocation(annotation),
                                    "@CheckResult should not be specified on `void` methods");
                        }
                    }
                } else if (INT_RANGE_ANNOTATION.isEquals(type)
                        || FLOAT_RANGE_ANNOTATION.isEquals(type)) {
                    // Check that the annotated element's type is int or long.
                    // Also make sure that from <= to.
                    boolean invalid;
                    if (INT_RANGE_ANNOTATION.isEquals(type)) {
                        checkTargetType(annotation, TYPE_INT, TYPE_LONG, true);

                        long from =
                                getLongAttribute(mContext, annotation, ATTR_FROM, Long.MIN_VALUE);
                        long to = getLongAttribute(mContext, annotation, ATTR_TO, Long.MAX_VALUE);
                        invalid = from > to;
                    } else {
                        checkTargetType(annotation, TYPE_FLOAT, TYPE_DOUBLE, true);

                        double from =
                                getDoubleAttribute(
                                        mContext, annotation, ATTR_FROM, Double.NEGATIVE_INFINITY);
                        double to =
                                getDoubleAttribute(
                                        mContext, annotation, ATTR_TO, Double.POSITIVE_INFINITY);
                        invalid = from > to;
                    }
                    if (invalid) {
                        mContext.report(
                                ANNOTATION_USAGE,
                                annotation,
                                mContext.getLocation(annotation),
                                "Invalid range: the `from` attribute must be less than "
                                        + "the `to` attribute");
                    }
                } else if (SIZE_ANNOTATION.isEquals(type)) {
                    // Check that the annotated element's type is an array, or a collection
                    // (or at least not an int or long; if so, suggest IntRange)
                    // Make sure the size and the modulo is not negative.
                    int unset = -42;
                    long exact = getLongAttribute(mContext, annotation, ATTR_VALUE, unset);
                    long min = getLongAttribute(mContext, annotation, ATTR_MIN, Long.MIN_VALUE);
                    long max = getLongAttribute(mContext, annotation, ATTR_MAX, Long.MAX_VALUE);
                    long multiple = getLongAttribute(mContext, annotation, ATTR_MULTIPLE, 1);
                    if (min > max) {
                        mContext.report(
                                ANNOTATION_USAGE,
                                annotation,
                                mContext.getLocation(annotation),
                                "Invalid size range: the `min` attribute must be less than "
                                        + "the `max` attribute");
                    } else if (multiple < 1) {
                        mContext.report(
                                ANNOTATION_USAGE,
                                annotation,
                                mContext.getLocation(annotation),
                                "The size multiple must be at least 1");

                    } else if (exact < 0 && exact != unset || min < 0 && min != Long.MIN_VALUE) {
                        mContext.report(
                                ANNOTATION_USAGE,
                                annotation,
                                mContext.getLocation(annotation),
                                "The size can't be negative");
                    }
                } else if (COLOR_INT_ANNOTATION.isEquals(type)) {
                    // Check that ColorInt applies to the right type
                    checkTargetType(annotation, TYPE_INT, TYPE_LONG, true);
                } else if (DIMENSION_ANNOTATION.isEquals(type) || (PX_ANNOTATION.isEquals(type))) {
                    // Check that @Dimension and @Px applies to the right type
                    checkTargetType(annotation, TYPE_INT, TYPE_LONG, TYPE_FLOAT, TYPE_DOUBLE, true);
                } else if (INT_DEF_ANNOTATION.isEquals(type)
                        || LONG_DEF_ANNOTATION.isEquals(type)) {
                    // Make sure IntDef constants are unique
                    ensureUniqueValues(annotation);
                } else if (PERMISSION_ANNOTATION.isEquals(type)
                        || PERMISSION_ANNOTATION_READ.isEquals(type)
                        || PERMISSION_ANNOTATION_WRITE.isEquals(type)) {
                    // Check that if there are no arguments, this is specified on a parameter,
                    // and conversely, on methods and fields there is a valid argument.
                    if (annotation.getUastParent() instanceof UMethod) {
                        String value = getAnnotationStringValue(annotation, ATTR_VALUE);
                        String[] anyOf = getAnnotationStringValues(annotation, ATTR_ANY_OF);
                        String[] allOf = getAnnotationStringValues(annotation, ATTR_ALL_OF);

                        int set = 0;
                        //noinspection VariableNotUsedInsideIf
                        if (value != null) {
                            set++;
                        }
                        //noinspection VariableNotUsedInsideIf
                        if (allOf != null) {
                            set++;
                        }
                        //noinspection VariableNotUsedInsideIf
                        if (anyOf != null) {
                            set++;
                        }

                        if (set == 0) {
                            mContext.report(
                                    ANNOTATION_USAGE,
                                    annotation,
                                    mContext.getLocation(annotation),
                                    "For methods, permission annotation should specify one "
                                            + "of `value`, `anyOf` or `allOf`");
                        } else if (set > 1) {
                            mContext.report(
                                    ANNOTATION_USAGE,
                                    annotation,
                                    mContext.getLocation(annotation),
                                    "Only specify one of `value`, `anyOf` or `allOf`");
                        }
                    }
                } else if (HALF_FLOAT_ANNOTATION.isEquals(type)) {
                    // Check that half floats are on shorts
                    checkTargetType(annotation, TYPE_SHORT, null, true);
                } else if (type.endsWith(RES_SUFFIX)) {
                    // Check that resource type annotations are on ints
                    checkTargetType(annotation, TYPE_INT, TYPE_LONG, true);
                } else if (RESTRICT_TO_ANNOTATION.isEquals(type)) {
                    UExpression attributeValue = annotation.findDeclaredAttributeValue(ATTR_VALUE);
                    if (attributeValue == null) {
                        attributeValue = annotation.findDeclaredAttributeValue(null);
                    }
                    if (attributeValue == null) {
                        mContext.report(
                                ANNOTATION_USAGE,
                                annotation,
                                mContext.getLocation(annotation),
                                "Restrict to what? Expected at least one `RestrictTo.Scope` arguments.");
                    } else {
                        String values = attributeValue.asSourceString();
                        if (values.contains("SUBCLASSES")
                                && annotation.getUastParent() instanceof UClass) {
                            mContext.report(
                                    ANNOTATION_USAGE,
                                    annotation,
                                    mContext.getLocation(annotation),
                                    "`RestrictTo.Scope.SUBCLASSES` should only be specified on methods and fields");
                        }
                    }
                }
            } else {
                // Look for typedefs (and make sure they're specified on the right type)
                PsiElement resolved = annotation.resolve();
                if (resolved != null) {
                    PsiClass cls = (PsiClass) resolved;
                    if (cls.isAnnotationType() && cls.getModifierList() != null) {
                        for (PsiAnnotation a :
                                mContext.getEvaluator().getAllAnnotations(cls, false)) {
                            String name = a.getQualifiedName();
                            if (INT_DEF_ANNOTATION.isEquals(name)) {
                                checkTargetType(annotation, TYPE_INT, TYPE_LONG, true);
                            } else if (LONG_DEF_ANNOTATION.isEquals(name)) {
                                checkTargetType(annotation, TYPE_LONG, null, true);
                            } else if (STRING_DEF_ANNOTATION.isEquals(type)) {
                                checkTargetType(annotation, TYPE_STRING, null, true);
                            }
                        }
                    }
                }
            }
        }

        private void checkTargetType(
                @NonNull UAnnotation node, @NonNull String type, boolean allowCollection) {
            checkTargetType(node, type, null, null, null, allowCollection);
        }

        private void checkTargetType(
                @NonNull UAnnotation node,
                @NonNull String type1,
                @Nullable String type2,
                boolean allowCollection) {
            checkTargetType(node, type1, type2, null, null, allowCollection);
        }

        private void checkTargetType(
                @NonNull UAnnotation node,
                @NonNull String type1,
                @Nullable String type2,
                @Nullable String type3,
                @Nullable String type4,
                boolean allowCollection) {
            UElement parent = node.getUastParent();
            PsiType type;

            if (parent instanceof UDeclarationsExpression) {
                List<UDeclaration> elements = ((UDeclarationsExpression) parent).getDeclarations();
                if (!elements.isEmpty()) {
                    UDeclaration element = elements.get(0);
                    if (element instanceof ULocalVariable) {
                        type = ((ULocalVariable) element).getType();
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            } else if (parent instanceof UMethod) {
                UMethod method = (UMethod) parent;
                type =
                        method.isConstructor()
                                ? mContext.getEvaluator().getClassType(method.getContainingClass())
                                : method.getReturnType();
            } else if (parent instanceof UVariable) {
                // Field or local variable or parameter
                UVariable variable = (UVariable) parent;
                if (variable.getTypeReference() == null) {
                    // Uh oh.
                    // https://youtrack.jetbrains.com/issue/KT-20172
                    return;
                }
                type = variable.getType();
            } else {
                return;
            }
            if (type == null) {
                return;
            }

            if (allowCollection) {
                if (type instanceof PsiArrayType) {
                    // For example, int[]
                    type = type.getDeepComponentType();
                } else if (type instanceof PsiClassType) {
                    // For example, List<Integer>
                    PsiClassType classType = (PsiClassType) type;
                    if (classType.getParameters().length == 1) {
                        PsiClass resolved = classType.resolve();
                        if (resolved != null
                                && mContext.getEvaluator()
                                        .implementsInterface(
                                                resolved, "java.util.Collection", false)) {
                            type = classType.getParameters()[0];
                        }
                    }
                }
            }

            if (!type.isValid()) {
                return;
            }

            String typeName = type.getCanonicalText();
            if (typeName.equals("error.NonExistentClass")) {
                // Type not found. Not awesome.
                // https://youtrack.jetbrains.com/issue/KT-20172
                return;
            }

            if (!(typeName.equals(type1)
                    || typeName.equals(type2)
                    || typeName.equals(type3)
                    || typeName.equals(type4))) {
                // Autoboxing? You can put @DrawableRes on a java.lang.Integer for example
                if (typeName.equals(getAutoBoxedType(type1))
                        || type2 != null && typeName.equals(getAutoBoxedType(type2))
                        || type3 != null && typeName.equals(getAutoBoxedType(type3))
                        || type4 != null && typeName.equals(getAutoBoxedType(type4))) {
                    return;
                }

                String expectedTypes;
                if (type4 != null) {
                    expectedTypes = type1 + ", " + type2 + ", " + type3 + ", or " + type4;
                } else if (type3 != null) {
                    expectedTypes = type1 + ", " + type2 + ", or " + type3;
                } else if (type2 != null) {
                    expectedTypes = type1 + " or " + type2;
                } else {
                    expectedTypes = type1;
                }
                if (typeName.equals(TYPE_STRING)) {
                    typeName = "String";
                }
                String message =
                        String.format(
                                "This annotation does not apply for type %1$s; expected %2$s",
                                typeName, expectedTypes);
                Location location = mContext.getLocation(node);
                mContext.report(ANNOTATION_USAGE, node, location, message);
            }
        }

        @Override
        public void visitSwitchExpression(@NonNull USwitchExpression switchExpression) {
            UExpression condition = switchExpression.getExpression();
            if (condition != null && PsiType.INT.equals(condition.getExpressionType())) {
                UAnnotation annotation = findIntDefAnnotation(condition);
                if (annotation != null) {
                    UExpression value = annotation.findAttributeValue(ATTR_VALUE);
                    if (value == null) {
                        value = annotation.findAttributeValue(null);
                    }

                    if (value != null && UastExpressionUtils.isArrayInitializer(value)) {
                        List<UExpression> allowedValues =
                                ((UCallExpression) value).getValueArguments();
                        switchExpression.accept(new SwitchChecker(switchExpression, allowedValues));
                    }
                }
            }
        }

        /**
         * Searches for the corresponding @IntDef annotation definition associated with a given node
         */
        @Nullable
        private UAnnotation findIntDefAnnotation(@NonNull UExpression expression) {
            if (expression instanceof UReferenceExpression) {
                PsiElement resolved = ((UReferenceExpression) expression).resolve();

                if (resolved instanceof PsiModifierListOwner) {
                    PsiAnnotation[] annotations =
                            mContext.getEvaluator()
                                    .getAllAnnotations((PsiModifierListOwner) resolved, true);
                    PsiAnnotation[] relevantAnnotations =
                            filterRelevantAnnotations(mContext.getEvaluator(), annotations);
                    UAnnotation annotation =
                            TypedefDetector.Companion.findIntDef(
                                    JavaUAnnotation.wrap(relevantAnnotations));
                    if (annotation != null) {
                        return annotation;
                    }
                }

                if (resolved instanceof PsiLocalVariable) {
                    PsiLocalVariable variable = (PsiLocalVariable) resolved;
                    UExpression lastAssignment =
                            UastLintUtils.findLastAssignment(variable, expression);

                    if (lastAssignment != null) {
                        return findIntDefAnnotation(lastAssignment);
                    }
                }
            } else if (expression instanceof UCallExpression) {
                PsiMethod method = ((UCallExpression) expression).resolve();
                if (method != null) {
                    PsiAnnotation[] annotations =
                            mContext.getEvaluator().getAllAnnotations(method, true);
                    PsiAnnotation[] relevantAnnotations =
                            filterRelevantAnnotations(mContext.getEvaluator(), annotations);
                    List<UAnnotation> uAnnotations = JavaUAnnotation.wrap(relevantAnnotations);
                    UAnnotation annotation = TypedefDetector.Companion.findIntDef(uAnnotations);
                    if (annotation != null) {
                        return annotation;
                    }
                }
            } else if (expression instanceof UIfExpression) {
                UIfExpression ifExpression = (UIfExpression) expression;
                if (ifExpression.getThenExpression() != null) {
                    UAnnotation result = findIntDefAnnotation(ifExpression.getThenExpression());
                    if (result != null) {
                        return result;
                    }
                }
                if (ifExpression.getElseExpression() != null) {
                    UAnnotation result = findIntDefAnnotation(ifExpression.getElseExpression());
                    if (result != null) {
                        return result;
                    }
                }
            } else if (expression instanceof JavaUTypeCastExpression) {
                return findIntDefAnnotation(((JavaUTypeCastExpression) expression).getOperand());

            } else if (expression instanceof UParenthesizedExpression) {
                return findIntDefAnnotation(
                        ((UParenthesizedExpression) expression).getExpression());
            }

            return null;
        }

        @Nullable
        private Integer getConstantValue(@NonNull PsiField intDefConstantRef) {
            Object constant = intDefConstantRef.computeConstantValue();
            if (constant instanceof Number) {
                return ((Number) constant).intValue();
            }

            return null;
        }

        private void ensureUniqueValues(@NonNull UAnnotation node) {
            UExpression value = node.findDeclaredAttributeValue(ATTR_VALUE);
            if (value == null) {
                value = node.findDeclaredAttributeValue(null);
            }
            if (value == null) {
                return;
            }

            if (!(UastExpressionUtils.isArrayInitializer(value))) {
                return;
            }

            List<UExpression> initializers = ((UCallExpression) value).getValueArguments();
            Map<Number, Integer> valueToIndex =
                    Maps.newHashMapWithExpectedSize(initializers.size());

            boolean flag = getAnnotationBooleanValue(node, TYPE_DEF_FLAG_ATTRIBUTE) == Boolean.TRUE;
            if (flag) {
                ensureUsingFlagStyle(initializers);
            }

            ConstantEvaluator constantEvaluator = new ConstantEvaluator();
            for (int index = 0; index < initializers.size(); index++) {
                UExpression expression = initializers.get(index);
                Object o = constantEvaluator.evaluate(expression);
                if (o instanceof Number) {
                    Number number = (Number) o;
                    if (valueToIndex.containsKey(number)) {
                        @SuppressWarnings("UnnecessaryLocalVariable")
                        Number repeatedValue = number;

                        Location location;
                        String message;
                        int prevIndex = valueToIndex.get(number);
                        UExpression prevConstant = initializers.get(prevIndex);
                        message =
                                String.format(
                                        "Constants `%1$s` and `%2$s` specify the same exact "
                                                + "value (%3$s); this is usually a cut & paste or "
                                                + "merge error",
                                        expression.asSourceString(),
                                        prevConstant.asSourceString(),
                                        repeatedValue.toString());
                        location = mContext.getLocation(expression);
                        Location secondary = mContext.getLocation(prevConstant);
                        secondary.setMessage("Previous same value");
                        location.setSecondary(secondary);
                        UElement scope = getAnnotationScope(node);
                        mContext.report(UNIQUE, scope, location, message);
                        break;
                    }
                    valueToIndex.put(number, index);
                }
            }
        }

        private void ensureUsingFlagStyle(@NonNull List<UExpression> constants) {
            if (constants.size() < 3) {
                return;
            }

            for (UExpression constant : constants) {
                if (constant instanceof UReferenceExpression) {
                    PsiElement resolved = ((UReferenceExpression) constant).resolve();
                    // Don't try to check complied code.
                    if (!(resolved instanceof PsiCompiledElement) && resolved instanceof PsiField) {
                        PsiExpression initializer = ((PsiField) resolved).getInitializer();
                        if (initializer instanceof PsiLiteral) {
                            PsiLiteral literal = (PsiLiteral) initializer;
                            Object o = literal.getValue();
                            if (!(o instanceof Number)) {
                                continue;
                            }
                            long value = ((Number) o).longValue();
                            // Allow -1, 0 and 1. You can write 1 as "1 << 0" but IntelliJ for
                            // example warns that that's a redundant shift.
                            if (Math.abs(value) <= 1) {
                                continue;
                            }
                            // Only warn if we're setting a specific bit
                            if (Long.bitCount(value) != 1) {
                                continue;
                            }
                            int shift = Long.numberOfTrailingZeros(value);
                            if (mWarnedFlags == null) {
                                mWarnedFlags = Sets.newHashSet();
                            }
                            if (!mWarnedFlags.add(resolved)) {
                                return;
                            }
                            String message =
                                    String.format(
                                            "Consider declaring this constant using 1 << %1$d instead",
                                            shift);
                            String replace =
                                    String.format(
                                            Locale.ROOT,
                                            "1%s << %d",
                                            o instanceof Long ? "L" : "",
                                            shift);
                            LintFix fix =
                                    fix().replace()
                                            .sharedName("Change declaration to <<")
                                            .with(replace)
                                            .autoFix()
                                            .build();
                            Location location = mContext.getLocation(initializer);
                            mContext.report(FLAG_STYLE, initializer, location, message, fix);
                        }
                    }
                }
            }
        }

        private boolean checkSuppressLint(@NonNull UAnnotation node, @NonNull String id) {
            IssueRegistry registry = mContext.getDriver().getRegistry();
            Issue issue = registry.getIssue(id);
            // Special-case the ApiDetector issue, since it does both source file analysis
            // only on field references, and class file analysis on the rest, so we allow
            // annotations outside of methods only on fields
            if (issue != null && !issue.getImplementation().getScope().contains(Scope.JAVA_FILE)
                    || issue == ApiDetector.UNSUPPORTED) {
                // This issue doesn't have AST access: annotations are not
                // available for local variables or parameters
                UElement scope = getAnnotationScope(node);
                mContext.report(
                        INSIDE_METHOD,
                        scope,
                        mContext.getLocation(node),
                        String.format(
                                "The `@SuppressLint` annotation cannot be used on a local "
                                        + "variable with the lint check '%1$s': move out to the "
                                        + "surrounding method",
                                id));
                return false;
            }

            return true;
        }

        private class SwitchChecker extends AbstractUastVisitor {

            private final USwitchExpression mSwitchExpression;
            private final List<UExpression> mAllowedValues;
            private final List<Object> mFields;
            private final List<Integer> mSeenValues;

            private boolean mReported = false;

            private SwitchChecker(
                    USwitchExpression switchExpression, List<UExpression> allowedValues) {
                mSwitchExpression = switchExpression;
                mAllowedValues = allowedValues;

                mFields = Lists.newArrayListWithCapacity(allowedValues.size());
                for (UExpression allowedValue : allowedValues) {
                    if (allowedValue instanceof ExternalReferenceExpression) {
                        ExternalReferenceExpression externalRef =
                                (ExternalReferenceExpression) allowedValue;

                        PsiElement resolved = UastLintUtils.resolve(externalRef, switchExpression);

                        if (resolved instanceof PsiField) {
                            mFields.add(resolved);
                        }
                    } else if (allowedValue instanceof UReferenceExpression) {
                        PsiElement resolved = ((UReferenceExpression) allowedValue).resolve();
                        if (resolved != null) {
                            mFields.add(resolved);
                        }
                    } else if (allowedValue instanceof ULiteralExpression) {
                        mFields.add(allowedValue);
                    }
                }

                mSeenValues = Lists.newArrayListWithCapacity(allowedValues.size());
            }

            @Override
            public boolean visitSwitchClauseExpression(USwitchClauseExpression node) {
                if (mReported) {
                    return true;
                }

                if (mAllowedValues == null) {
                    return true;
                }

                List<UExpression> caseValues = node.getCaseValues();
                if (caseValues.isEmpty()) {
                    // We had an else clause: don't report any as missing
                    mFields.clear();
                    return true;
                }

                for (UExpression caseValue : caseValues) {
                    if (caseValue instanceof ULiteralExpression) {
                        // Report warnings if you specify hardcoded constants.
                        // It's the wrong thing to do.
                        List<String> list = computeFieldNames(mSwitchExpression, mAllowedValues);
                        // Keep error message in sync with {@link #getMissingCases}
                        String message =
                                "Don't use a constant here; expected one of: "
                                        + displayConstants(list);
                        mContext.report(
                                SWITCH_TYPE_DEF,
                                caseValue,
                                mContext.getLocation(caseValue),
                                message);
                        // Don't look for other missing typedef constants since you might
                        // have aliased with value
                        mReported = true;
                    } else if (caseValue
                            instanceof
                            UReferenceExpression) { // default case can have null expression
                        PsiElement resolved = ((UReferenceExpression) caseValue).resolve();
                        if (resolved == null) {
                            // If there are compilation issues (e.g. user is editing code) we
                            // can't be certain, so don't flag anything.
                            return true;
                        }
                        if (resolved instanceof PsiField) {
                            // We can't just do
                            //    fields.remove(resolved);
                            // since the fields list contains instances of potentially
                            // different types with different hash codes (due to the
                            // external annotations, which are not of the same type as
                            // for example the ECJ based ones.
                            //
                            // The equals method on external field class deliberately handles
                            // this (but it can't make its hash code match what
                            // the ECJ fields do, which is tied to the ECJ binding hash code.)
                            // So instead, manually check for equals. These lists tend to
                            // be very short anyway.
                            PsiField resolvedField = (PsiField) resolved;
                            boolean found = removeFieldFromList(mFields, resolvedField);
                            if (!found) {
                                // Look for local alias
                                UExpression initializer =
                                        mContext.getUastContext()
                                                .getInitializerBody(((PsiField) resolved));
                                if (initializer instanceof UReferenceExpression) {
                                    resolved = ((UReferenceExpression) initializer).resolve();
                                    if (resolved instanceof PsiField) {
                                        found = removeFieldFromList(mFields, (PsiField) resolved);
                                    }
                                }
                            }

                            if (found) {
                                Integer cv = getConstantValue((PsiField) resolved);
                                if (cv != null) {
                                    mSeenValues.add(cv);
                                }
                            } else {
                                List<String> list =
                                        computeFieldNames(mSwitchExpression, mAllowedValues);
                                // Keep error message in sync with {@link #getMissingCases}
                                String message =
                                        "Unexpected constant; expected one of: "
                                                + displayConstants(list);
                                LintFix fix = fix().data(list);
                                Location location = mContext.getNameLocation(caseValue);
                                mContext.report(SWITCH_TYPE_DEF, caseValue, location, message, fix);
                            }
                        }
                    }
                }
                return true;
            }

            @Override
            public void afterVisitSwitchExpression(USwitchExpression node) {
                reportMissingSwitchCases();
                super.afterVisitSwitchExpression(node);
            }

            private void reportMissingSwitchCases() {
                if (mReported) {
                    return;
                }

                if (mAllowedValues == null) {
                    return;
                }

                // Any missing switch constants? Before we flag them, look to see if any
                // of them have the same values: those can be omitted
                if (!mFields.isEmpty()) {
                    ListIterator<Object> iterator = mFields.listIterator();
                    while (iterator.hasNext()) {
                        Object next = iterator.next();
                        if (next instanceof PsiField) {
                            Integer cv = getConstantValue((PsiField) next);
                            if (mSeenValues.contains(cv)) {
                                iterator.remove();
                            }
                        }
                    }
                }

                if (!mFields.isEmpty()) {
                    List<String> list = computeFieldNames(mSwitchExpression, mFields);
                    // Keep error message in sync with {@link #getMissingCases}
                    LintFix fix = fix().data(list);
                    UIdentifier identifier = mSwitchExpression.getSwitchIdentifier();
                    Location location = mContext.getLocation(identifier);
                    // Workaround Kotlin UAST passing <error> instead of PsiKeyword as in Java
                    if (mSwitchExpression instanceof KotlinUSwitchExpression
                            && !"when".equals(identifier.getName())) {
                        PsiElement sourcePsi = mSwitchExpression.getSourcePsi();
                        if (sourcePsi != null) {
                            PsiElement keyword = sourcePsi.getFirstChild();
                            if (keyword != null) {
                                location = mContext.getLocation(keyword);
                            }
                        }
                    }
                    String message =
                            "Switch statement on an `int` with known associated constant "
                                    + "missing case "
                                    + displayConstants(list);
                    mContext.report(SWITCH_TYPE_DEF, mSwitchExpression, location, message, fix);
                }
            }
        }
    }

    @NonNull
    private static String displayConstants(List<String> list) {
        return CollectionsKt.joinToString(
                list,
                ", ", // separator
                "", // prefix
                "", // postfix
                -1, // limited
                "", // truncated
                s -> {
                    int index = s.lastIndexOf('.');
                    if (index != -1) {
                        int classIndex = s.lastIndexOf('.', index - 1);
                        if (classIndex != -1) {
                            return "`" + s.substring(classIndex + 1) + "`";
                        }
                    }
                    return "`" + s + "`";
                });
    }

    private static List<String> computeFieldNames(
            @NonNull USwitchExpression node, Iterable<?> allowedValues) {

        List<String> list = Lists.newArrayList();
        for (Object o : allowedValues) {
            if (o instanceof ExternalReferenceExpression) {
                ExternalReferenceExpression externalRef = (ExternalReferenceExpression) o;
                PsiElement resolved = UastLintUtils.resolve(externalRef, node);
                if (resolved != null) {
                    o = resolved;
                }
            } else if (o instanceof PsiReferenceExpression) {
                PsiReferenceExpression ref = (PsiReferenceExpression) o;
                PsiElement resolved = ref.resolve();
                if (resolved != null) {
                    o = resolved;
                } else {
                    String referenceName = ref.getReferenceName();
                    if (referenceName != null) {
                        list.add(referenceName);
                    }
                    continue;
                }
            } else if (o instanceof PsiLiteral) {
                list.add((String) ((PsiLiteral) o).getValue());
                continue;
            } else if (o instanceof UReferenceExpression) {
                UReferenceExpression ref = (UReferenceExpression) o;
                PsiElement resolved = ref.resolve();
                if (resolved == null) {
                    String resolvedName = ref.getResolvedName();
                    if (resolvedName != null) {
                        list.add(resolvedName);
                    }
                    continue;
                }
                o = resolved;
            }

            if (o instanceof PsiField) {
                PsiField field = (PsiField) o;
                // Only include class name if necessary
                String name = field.getName();
                UClass clz = UastUtils.getParentOfType(node, UClass.class, true);
                if (clz != null) {
                    PsiClass containingClass = field.getContainingClass();
                    if (containingClass != null && !containingClass.equals(clz.getPsi())) {
                        name = containingClass.getQualifiedName() + '.' + field.getName();
                    }
                }
                list.add(name);
            }
        }
        Collections.sort(list);
        return list;
    }

    /**
     * Returns the node to use as the scope for the given annotation node. You can't annotate an
     * annotation itself (with {@code @SuppressLint}), but you should be able to place an annotation
     * next to it, as a sibling, to only suppress the error on this annotated element, not the whole
     * surrounding class.
     */
    @NonNull
    private static UElement getAnnotationScope(@NonNull UAnnotation node) {
        UElement scope = UastUtils.getParentOfType(node, UAnnotation.class, true);
        if (scope == null) {
            scope = node;
        }
        return scope;
    }

    private static boolean removeFieldFromList(
            @NonNull List<Object> fields, @NonNull PsiField resolvedField) {
        for (Object field : fields) {
            // We can't just call .equals here because the annotation
            // we are comparing against may be either a PsiFieldImpl
            // (for a local annotation) or a ClsFieldImpl (for an annotation
            // read from storage) or maybe even other PSI internal classes.
            // So compare by name and class instead.

            if (!(field instanceof PsiField)) {
                continue;
            }
            PsiField candidateField = (PsiField) field;
            if (candidateField.isEquivalentTo(resolvedField)) {
                return true;
            }
        }

        return false;
    }

    // Like JavaEvaluator#filterRelevantAnnotations, but hardcoded for the IntRange and
    // IntDef annotations since this check isn't a generalized annotation checker like the
    // others.
    @NonNull
    static PsiAnnotation[] filterRelevantAnnotations(
            @NonNull JavaEvaluator evaluator, @NonNull PsiAnnotation[] annotations) {
        List<PsiAnnotation> result = null;
        int length = annotations.length;
        if (length == 0) {
            return annotations;
        }
        for (PsiAnnotation annotation : annotations) {
            String signature = annotation.getQualifiedName();
            if (signature == null || signature.startsWith("java.")) {
                // @Override, @SuppressWarnings etc. Ignore
                continue;
            }

            if (SUPPORT_ANNOTATIONS_PREFIX.isPrefix(signature)
                    || signature.equals(GMS_HIDE_ANNOTATION)) {
                // Bail on the nullness annotations early since they're the most commonly
                // defined ones. They're not analyzed in lint yet.
                if (signature.endsWith(".Nullable") || signature.endsWith(".NonNull")) {
                    continue;
                }

                // Common case: there's just one annotation; no need to create a list copy
                if (length == 1) {
                    return annotations;
                }
                if (result == null) {
                    result = new ArrayList<>(2);
                }
                result.add(annotation);
            }

            // Special case @IntDef and @StringDef: These are used on annotations
            // themselves. For example, you create a new annotation named @foo.bar.Baz,
            // annotate it with @IntDef, and then use @foo.bar.Baz in your signatures.
            // Here we want to map from @foo.bar.Baz to the corresponding int def.
            // Don't need to compute this if performing @IntDef or @StringDef lookup
            PsiClass cls = null;
            PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
            if (ref != null) {
                PsiElement resolved = ref.resolve();
                if (resolved instanceof PsiClass) {
                    cls = (PsiClass) resolved;
                }
            } else {
                Project project = annotation.getProject();
                GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
                cls = JavaPsiFacade.getInstance(project).findClass(signature, scope);
            }
            if (cls == null || !cls.isAnnotationType()) {
                continue;
            }
            PsiAnnotation[] innerAnnotations = evaluator.getAllAnnotations(cls, false);
            for (int j = 0; j < innerAnnotations.length; j++) {
                PsiAnnotation inner = innerAnnotations[j];
                String a = inner.getQualifiedName();
                if (a == null || a.startsWith("java.")) {
                    // @Override, @SuppressWarnings etc. Ignore
                    continue;
                }
                if (INT_DEF_ANNOTATION.isEquals(a)
                        || LONG_DEF_ANNOTATION.isEquals(a)
                        || PERMISSION_ANNOTATION.isEquals(a)
                        || INT_RANGE_ANNOTATION.isEquals(a)
                        || STRING_DEF_ANNOTATION.isEquals(a)) {
                    if (length == 1 && j == innerAnnotations.length - 1 && result == null) {
                        return innerAnnotations;
                    }
                    if (result == null) {
                        result = new ArrayList<>(2);
                    }
                    result.add(inner);
                }
            }
        }

        return result != null
                ? result.toArray(PsiAnnotation.EMPTY_ARRAY)
                : PsiAnnotation.EMPTY_ARRAY;
    }
}
