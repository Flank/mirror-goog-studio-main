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
package com.android.tools.lint.detector.api;

import static com.android.SdkConstants.ANDROID_PKG;
import static com.android.SdkConstants.ANDROID_PKG_PREFIX;
import static com.android.SdkConstants.CLASS_CONTEXT;
import static com.android.SdkConstants.CLASS_FRAGMENT;
import static com.android.SdkConstants.CLASS_RESOURCES;
import static com.android.SdkConstants.CLASS_V4_FRAGMENT;
import static com.android.SdkConstants.CLS_TYPED_ARRAY;
import static com.android.SdkConstants.R_CLASS;
import static com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX;
import static com.android.tools.lint.client.api.AndroidPlatformAnnotations.isPlatformAnnotation;
import static com.android.tools.lint.client.api.AndroidPlatformAnnotations.toAndroidxAnnotation;
import static com.android.tools.lint.detector.api.Lint.getMethodName;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.support.AndroidxName;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.ResourceReference;
import com.google.common.collect.ImmutableMap;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import kotlin.collections.CollectionsKt;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.asJava.elements.KtLightMethod;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UIfExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UastCallKind;
import org.jetbrains.uast.UastFacade;
import org.jetbrains.uast.java.UnknownJavaExpression;

/** Evaluates constant expressions */
public class ResourceEvaluator {

    /**
     * Marker ResourceType used to signify that an expression is of type {@code @ColorInt}, which
     * isn't actually a ResourceType but one we want to specifically compare with. We're using
     * {@link ResourceType#PUBLIC} because that one won't appear in the R class (and ResourceType is
     * an enum we can't just create new constants for.)
     */
    public static final ResourceType COLOR_INT_MARKER_TYPE = ResourceType.PUBLIC;
    /**
     * Marker ResourceType used to signify that an expression is of type {@code @Px}, which isn't
     * actually a ResourceType but one we want to specifically compare with. We're using {@link
     * ResourceType#SAMPLE_DATA} because that one doesn't have a corresponding {@code *Res} constant
     * (and ResourceType is an enum we can't just create new constants for.)
     */
    public static final ResourceType DIMENSION_MARKER_TYPE = ResourceType.SAMPLE_DATA;

    /** Marker for @Dimension(unit = SP); see {@link #DIMENSION_MARKER_TYPE} */
    public static final ResourceType DIMENSION_SP_MARKER_TYPE = ResourceType.OVERLAYABLE;

    /** Marker for @Dimension(unit = DP); see {@link #DIMENSION_MARKER_TYPE} */
    public static final ResourceType DIMENSION_DP_MARKER_TYPE = ResourceType.AAPT;

    /** The marker types respresenting dimensions */
    public static final ResourceType[] DIMENSION_MARKERS =
            new ResourceType[] {
                DIMENSION_MARKER_TYPE, DIMENSION_SP_MARKER_TYPE, DIMENSION_DP_MARKER_TYPE
            };

    /** Marker type used to signify *all* resource types (for the annotation `AnyRes`) */
    public static final ResourceType ANY_RES_MARKER_TYPE = ResourceType.MACRO;

    public static final AndroidxName COLOR_INT_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "ColorInt");
    public static final AndroidxName PX_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "Px");
    public static final AndroidxName DIMENSION_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "Dimension");
    public static final String RES_SUFFIX = "Res";

    public static final AndroidxName ANIMATOR_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "AnimatorRes");
    public static final AndroidxName ANIM_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "AnimRes");
    public static final AndroidxName ANY_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "AnyRes");
    public static final AndroidxName ARRAY_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "ArrayRes");
    public static final AndroidxName BOOL_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "BoolRes");
    public static final AndroidxName COLOR_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "ColorRes");
    public static final AndroidxName ATTR_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "AttrRes");
    public static final AndroidxName DIMEN_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "DimenRes");
    public static final AndroidxName DRAWABLE_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "DrawableRes");
    public static final AndroidxName FONT_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "FontRes");
    public static final AndroidxName FRACTION_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "FractionRes");
    public static final AndroidxName ID_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "IdRes");
    public static final AndroidxName INTEGER_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "IntegerRes");
    public static final AndroidxName INTERPOLATOR_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "InterpolatorRes");
    public static final AndroidxName LAYOUT_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "LayoutRes");
    public static final AndroidxName MENU_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "MenuRes");
    public static final AndroidxName PLURALS_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "PluralsRes");
    public static final AndroidxName RAW_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "RawRes");
    public static final AndroidxName STRING_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "StringRes");
    public static final AndroidxName STYLEABLE_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "StyleableRes");
    public static final AndroidxName STYLE_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "StyleRes");
    public static final AndroidxName TRANSITION_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "TransitionRes");
    public static final AndroidxName XML_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "XmlRes");

    private final JavaEvaluator evaluator;
    public static final AndroidxName NAVIGATION_RES_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "NavigationRes");

    private static final ImmutableMap<String, ResourceType> TYPE_FROM_ANNOTATION_SIGNATURE =
            ImmutableMap.<String, ResourceType>builder()
                    .put(ANIMATOR_RES_ANNOTATION.oldName(), ResourceType.ANIMATOR)
                    .put(ANIMATOR_RES_ANNOTATION.newName(), ResourceType.ANIMATOR)
                    .put(ANIM_RES_ANNOTATION.oldName(), ResourceType.ANIM)
                    .put(ANIM_RES_ANNOTATION.newName(), ResourceType.ANIM)
                    .put(ARRAY_RES_ANNOTATION.oldName(), ResourceType.ARRAY)
                    .put(ARRAY_RES_ANNOTATION.newName(), ResourceType.ARRAY)
                    .put(ATTR_RES_ANNOTATION.oldName(), ResourceType.ATTR)
                    .put(ATTR_RES_ANNOTATION.newName(), ResourceType.ATTR)
                    .put(BOOL_RES_ANNOTATION.oldName(), ResourceType.BOOL)
                    .put(BOOL_RES_ANNOTATION.newName(), ResourceType.BOOL)
                    .put(COLOR_RES_ANNOTATION.oldName(), ResourceType.COLOR)
                    .put(COLOR_RES_ANNOTATION.newName(), ResourceType.COLOR)
                    .put(DIMEN_RES_ANNOTATION.oldName(), ResourceType.DIMEN)
                    .put(DIMEN_RES_ANNOTATION.newName(), ResourceType.DIMEN)
                    .put(DRAWABLE_RES_ANNOTATION.oldName(), ResourceType.DRAWABLE)
                    .put(DRAWABLE_RES_ANNOTATION.newName(), ResourceType.DRAWABLE)
                    .put(FONT_RES_ANNOTATION.oldName(), ResourceType.FONT)
                    .put(FONT_RES_ANNOTATION.newName(), ResourceType.FONT)
                    .put(FRACTION_RES_ANNOTATION.oldName(), ResourceType.FRACTION)
                    .put(FRACTION_RES_ANNOTATION.newName(), ResourceType.FRACTION)
                    .put(ID_RES_ANNOTATION.oldName(), ResourceType.ID)
                    .put(ID_RES_ANNOTATION.newName(), ResourceType.ID)
                    .put(INTEGER_RES_ANNOTATION.oldName(), ResourceType.INTEGER)
                    .put(INTEGER_RES_ANNOTATION.newName(), ResourceType.INTEGER)
                    .put(INTERPOLATOR_RES_ANNOTATION.oldName(), ResourceType.INTERPOLATOR)
                    .put(INTERPOLATOR_RES_ANNOTATION.newName(), ResourceType.INTERPOLATOR)
                    .put(LAYOUT_RES_ANNOTATION.oldName(), ResourceType.LAYOUT)
                    .put(LAYOUT_RES_ANNOTATION.newName(), ResourceType.LAYOUT)
                    .put(MENU_RES_ANNOTATION.oldName(), ResourceType.MENU)
                    .put(MENU_RES_ANNOTATION.newName(), ResourceType.MENU)
                    .put(NAVIGATION_RES_ANNOTATION.oldName(), ResourceType.NAVIGATION)
                    .put(NAVIGATION_RES_ANNOTATION.newName(), ResourceType.NAVIGATION)
                    .put(PLURALS_RES_ANNOTATION.oldName(), ResourceType.PLURALS)
                    .put(PLURALS_RES_ANNOTATION.newName(), ResourceType.PLURALS)
                    .put(RAW_RES_ANNOTATION.oldName(), ResourceType.RAW)
                    .put(RAW_RES_ANNOTATION.newName(), ResourceType.RAW)
                    .put(STRING_RES_ANNOTATION.oldName(), ResourceType.STRING)
                    .put(STRING_RES_ANNOTATION.newName(), ResourceType.STRING)
                    .put(STYLEABLE_RES_ANNOTATION.oldName(), ResourceType.STYLEABLE)
                    .put(STYLEABLE_RES_ANNOTATION.newName(), ResourceType.STYLEABLE)
                    .put(STYLE_RES_ANNOTATION.oldName(), ResourceType.STYLE)
                    .put(STYLE_RES_ANNOTATION.newName(), ResourceType.STYLE)
                    .put(TRANSITION_RES_ANNOTATION.oldName(), ResourceType.TRANSITION)
                    .put(TRANSITION_RES_ANNOTATION.newName(), ResourceType.TRANSITION)
                    .put(XML_RES_ANNOTATION.oldName(), ResourceType.XML)
                    .put(XML_RES_ANNOTATION.newName(), ResourceType.XML)
                    .build();

    private boolean allowDereference = true;

    /**
     * Creates a new resource evaluator
     *
     * @param evaluator the evaluator to use to resolve annotations references, if any
     */
    public ResourceEvaluator(@Nullable JavaEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * Whether we allow dereferencing resources when computing constants; e.g. if we ask for the
     * resource for {@code x} when the code is {@code x = getString(R.string.name)}, if {@code
     * allowDereference} is true we'll return R.string.name, otherwise we'll return null.
     *
     * @return this for constructor chaining
     */
    public ResourceEvaluator allowDereference(boolean allow) {
        allowDereference = allow;
        return this;
    }

    /**
     * Evaluates the given node and returns the resource reference (type and name) it points to, if
     * any
     *
     * @param evaluator the evaluator to use to look up annotations
     * @param element the node to compute the constant value for
     * @return the corresponding resource url (type and name)
     */
    @Nullable
    public static ResourceUrl getResource(
            @Nullable JavaEvaluator evaluator, @NonNull PsiElement element) {
        return new ResourceEvaluator(evaluator).getResource(element);
    }

    /**
     * Evaluates the given node and returns the resource reference (type and name) it points to, if
     * any
     *
     * @param evaluator the evaluator to use to look up annotations
     * @param element the node to compute the constant value for
     * @return the corresponding resource url (type and name)
     */
    @Nullable
    public static ResourceUrl getResource(
            @NonNull JavaEvaluator evaluator, @NonNull UElement element) {
        return new ResourceEvaluator(evaluator).getResource(element);
    }

    /**
     * Evaluates the given node and returns the resource types implied by the given element, if any.
     *
     * @param evaluator the evaluator to use to look up annotations
     * @param element the node to compute the constant value for
     * @return the corresponding resource types
     */
    @Nullable
    public static EnumSet<ResourceType> getResourceTypes(
            @Nullable JavaEvaluator evaluator, @NonNull PsiElement element) {
        return new ResourceEvaluator(evaluator).getResourceTypes(element);
    }

    /**
     * Evaluates the given node and returns the resource types implied by the given element, if any.
     *
     * @param evaluator the evaluator to use to look up annotations
     * @param element the node to compute the constant value for
     * @return the corresponding resource types
     */
    @Nullable
    public static EnumSet<ResourceType> getResourceTypes(
            @Nullable JavaEvaluator evaluator, @NonNull UElement element) {
        return new ResourceEvaluator(evaluator).getResourceTypes(element);
    }

    /**
     * Evaluates the given node and returns the resource reference (type and name) it points to, if
     * any
     *
     * @param element the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    @Nullable
    public ResourceUrl getResource(@Nullable UElement element) {
        if (element == null) {
            return null;
        }

        if (element instanceof UIfExpression) {
            UIfExpression expression = (UIfExpression) element;
            Object known = ConstantEvaluator.evaluate(null, expression.getCondition());
            if (known == Boolean.TRUE && expression.getThenExpression() != null) {
                return getResource(expression.getThenExpression());
            } else if (known == Boolean.FALSE && expression.getElseExpression() != null) {
                return getResource(expression.getElseExpression());
            }
        } else if (element instanceof UParenthesizedExpression) {
            UParenthesizedExpression parenthesizedExpression = (UParenthesizedExpression) element;
            return getResource(parenthesizedExpression.getExpression());
        } else if (element instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) element;
            PsiMethod function = call.resolve();
            PsiClass containingClass = PsiTreeUtil.getParentOfType(function, PsiClass.class);
            if (function != null && containingClass != null) {
                String qualifiedName = containingClass.getQualifiedName();
                String name = getMethodName(call);
                if ((CLASS_RESOURCES.equals(qualifiedName)
                                || CLASS_CONTEXT.equals(qualifiedName)
                                || CLASS_FRAGMENT.equals(qualifiedName)
                                || CLASS_V4_FRAGMENT.isEquals(qualifiedName)
                                || CLS_TYPED_ARRAY.equals(qualifiedName))
                        && name != null
                        && name.startsWith("get")) {
                    List<UExpression> args = call.getValueArguments();
                    if (!args.isEmpty()) {
                        return getResource(args.get(0));
                    }
                }
            }
        } else if (allowDereference && element instanceof UQualifiedReferenceExpression) {
            UExpression selector = ((UQualifiedReferenceExpression) element).getSelector();
            if (selector instanceof UCallExpression) {
                ResourceUrl url = getResource(selector);
                if (url != null) {
                    return url;
                }
            }
        }

        if (element instanceof UReferenceExpression) {
            ResourceUrl url = getResourceConstant(element);
            if (url != null) {
                return url;
            }
            PsiElement resolved = ((UReferenceExpression) element).resolve();
            if (resolved instanceof PsiVariable) {
                PsiVariable variable = (PsiVariable) resolved;
                UElement lastAssignment = UastLintUtils.findLastAssignment(variable, element);

                if (lastAssignment != null) {
                    return getResource(lastAssignment);
                }

                return null;
            }
        }

        return null;
    }

    /**
     * Evaluates the given node and returns the resource reference (type and name) it points to, if
     * any
     *
     * @param element the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    @Nullable
    public ResourceUrl getResource(@Nullable PsiElement element) {
        if (element == null) {
            return null;
        }
        if (element instanceof PsiConditionalExpression) {
            PsiConditionalExpression expression = (PsiConditionalExpression) element;
            Object known = ConstantEvaluator.evaluate(null, expression.getCondition());
            if (known == Boolean.TRUE && expression.getThenExpression() != null) {
                return getResource(expression.getThenExpression());
            } else if (known == Boolean.FALSE && expression.getElseExpression() != null) {
                return getResource(expression.getElseExpression());
            }
        } else if (element instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression) element;
            return getResource(parenthesizedExpression.getExpression());
        } else if (element instanceof PsiMethodCallExpression && allowDereference) {
            PsiMethodCallExpression call = (PsiMethodCallExpression) element;
            PsiReferenceExpression expression = call.getMethodExpression();
            PsiMethod method = call.resolveMethod();
            if (method != null && method.getContainingClass() != null) {
                String qualifiedName = method.getContainingClass().getQualifiedName();
                String name = expression.getReferenceName();
                if ((CLASS_RESOURCES.equals(qualifiedName)
                                || CLASS_CONTEXT.equals(qualifiedName)
                                || CLASS_FRAGMENT.equals(qualifiedName)
                                || CLASS_V4_FRAGMENT.isEquals(qualifiedName)
                                || CLS_TYPED_ARRAY.equals(qualifiedName))
                        && name != null
                        && name.startsWith("get")) {
                    PsiExpression[] args = call.getArgumentList().getExpressions();
                    if (args.length > 0) {
                        return getResource(args[0]);
                    }
                }
            }
        } else if (element instanceof PsiReference) {
            ResourceUrl url = getResourceConstant(element);
            if (url != null) {
                return url;
            }
            PsiElement resolved = ((PsiReference) element).resolve();
            if (resolved instanceof PsiField) {
                url = getResourceConstant(resolved);
                if (url != null) {
                    return url;
                }
                PsiField field = (PsiField) resolved;
                if (field.getInitializer() != null) {
                    return getResource(field.getInitializer());
                }
                return null;
            } else if (resolved instanceof PsiLocalVariable) {
                PsiLocalVariable variable = (PsiLocalVariable) resolved;
                PsiExpression last = ConstantEvaluator.findLastAssignment(element, variable);
                if (last != null) {
                    return getResource(last);
                }
            }
        }

        return null;
    }

    /**
     * Evaluates the given node and returns the resource types applicable to the node, if any.
     *
     * @param element the element to compute the types for
     * @return the corresponding resource types
     */
    @Nullable
    public EnumSet<ResourceType> getResourceTypes(@Nullable UElement element) {
        if (element == null) {
            return null;
        }
        if (element instanceof UIfExpression) {
            UIfExpression expression = (UIfExpression) element;
            Object known = ConstantEvaluator.evaluate(null, expression.getCondition());
            if (known == Boolean.TRUE && expression.getThenExpression() != null) {
                return getResourceTypes(expression.getThenExpression());
            } else if (known == Boolean.FALSE && expression.getElseExpression() != null) {
                return getResourceTypes(expression.getElseExpression());
            } else {
                EnumSet<ResourceType> left = getResourceTypes(expression.getThenExpression());
                EnumSet<ResourceType> right = getResourceTypes(expression.getElseExpression());
                if (left == null) {
                    return right;
                } else if (right == null) {
                    return left;
                } else {
                    EnumSet<ResourceType> copy = EnumSet.copyOf(left);
                    copy.addAll(right);
                    return copy;
                }
            }
        } else if (element instanceof UParenthesizedExpression) {
            UParenthesizedExpression parenthesizedExpression = (UParenthesizedExpression) element;
            return getResourceTypes(parenthesizedExpression.getExpression());
        } else if ((element instanceof UQualifiedReferenceExpression)
                || element instanceof UCallExpression) {
            UElement probablyCallExpression = element;
            if (element instanceof UQualifiedReferenceExpression) {
                UQualifiedReferenceExpression qualifiedExpression =
                        (UQualifiedReferenceExpression) element;
                probablyCallExpression = qualifiedExpression.getSelector();
            }
            if ((probablyCallExpression instanceof UCallExpression)) {
                UCallExpression call = (UCallExpression) probablyCallExpression;
                PsiMethod method = call.resolve();
                if (method != null) {
                    EnumSet<ResourceType> types = getTypesFromAnnotations(method);
                    if (types != null) {
                        return types;
                    }
                } else if (call.getKind() == UastCallKind.NESTED_ARRAY_INITIALIZER) {
                    EnumSet<ResourceType> types = EnumSet.noneOf(ResourceType.class);
                    for (UExpression argument : call.getValueArguments()) {
                        EnumSet<ResourceType> resourceTypes = getResourceTypes(argument);
                        if (resourceTypes != null && !resourceTypes.isEmpty()) {
                            types.addAll(resourceTypes);
                        }
                    }
                    if (!types.isEmpty()) {
                        return types;
                    }
                }
            }
        } else if (element instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) element).getExpressions();
            return getResourceTypes(CollectionsKt.lastOrNull(expressions));
        }

        if (element instanceof UReferenceExpression) {
            ResourceUrl url = getResourceConstant(element);
            if (url != null) {
                return EnumSet.of(url.type);
            }

            PsiElement resolved = ((UReferenceExpression) element).resolve();

            if (resolved instanceof PsiModifierListOwner) {
                EnumSet<ResourceType> types =
                        getTypesFromAnnotations((PsiModifierListOwner) resolved);
                if (types != null && !types.isEmpty()) {
                    return types;
                }
            }

            if (resolved instanceof PsiVariable) {
                PsiVariable variable = (PsiVariable) resolved;
                UElement lastAssignment = UastLintUtils.findLastAssignment(variable, element);

                if (lastAssignment != null) {
                    return getResourceTypes(lastAssignment);
                }

                return null;
            }
        }

        return null;
    }

    /**
     * Evaluates the given node and returns the resource types applicable to the node, if any.
     *
     * @param element the element to compute the types for
     * @return the corresponding resource types
     */
    @Nullable
    public EnumSet<ResourceType> getResourceTypes(@Nullable PsiElement element) {
        if (element == null) {
            return null;
        }
        if (element instanceof PsiConditionalExpression) {
            PsiConditionalExpression expression = (PsiConditionalExpression) element;
            Object known = ConstantEvaluator.evaluate(null, expression.getCondition());
            if (known == Boolean.TRUE && expression.getThenExpression() != null) {
                return getResourceTypes(expression.getThenExpression());
            } else if (known == Boolean.FALSE && expression.getElseExpression() != null) {
                return getResourceTypes(expression.getElseExpression());
            } else {
                EnumSet<ResourceType> left = getResourceTypes(expression.getThenExpression());
                EnumSet<ResourceType> right = getResourceTypes(expression.getElseExpression());
                if (left == null) {
                    return right;
                } else if (right == null) {
                    return left;
                } else {
                    EnumSet<ResourceType> copy = EnumSet.copyOf(left);
                    copy.addAll(right);
                    return copy;
                }
            }
        } else if (element instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression) element;
            return getResourceTypes(parenthesizedExpression.getExpression());
        } else if (element instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression call = (PsiMethodCallExpression) element;
            PsiMethod method = call.resolveMethod();
            if (method != null) {
                EnumSet<ResourceType> types = getTypesFromAnnotations(method);
                if (types != null) {
                    return types;
                }
            }
        } else if (element instanceof PsiReference) {
            ResourceUrl url = getResourceConstant(element);
            if (url != null) {
                return EnumSet.of(url.type);
            }
            PsiElement resolved = ((PsiReference) element).resolve();
            if (resolved instanceof PsiModifierListOwner) {
                EnumSet<ResourceType> types =
                        getTypesFromAnnotations((PsiModifierListOwner) resolved);
                if (types != null && !types.isEmpty()) {
                    return types;
                }
            }

            if (resolved instanceof PsiField) {
                url = getResourceConstant(resolved);
                if (url != null) {
                    return EnumSet.of(url.type);
                }
                PsiField field = (PsiField) resolved;
                if (field.getInitializer() != null) {
                    return getResourceTypes(field.getInitializer());
                }
                return null;
            } else if (resolved instanceof PsiParameter) {
                return getTypesFromAnnotations((PsiParameter) resolved);
            } else if (resolved instanceof PsiLocalVariable) {
                PsiLocalVariable variable = (PsiLocalVariable) resolved;
                PsiExpression last = ConstantEvaluator.findLastAssignment(element, variable);
                if (last != null) {
                    return getResourceTypes(last);
                }
            }
        }

        return null;
    }

    @Nullable
    private EnumSet<ResourceType> getTypesFromAnnotations(PsiModifierListOwner owner) {
        if (evaluator == null) {
            return null;
        }
        return getTypesFromAnnotations(evaluator, owner);
    }

    @Nullable
    public static EnumSet<ResourceType> getTypesFromAnnotations(
            @NonNull JavaEvaluator evaluator, @NonNull PsiModifierListOwner owner) {
        List<UAnnotation> annotations = evaluator.getAnnotations(owner, true, null);
        EnumSet<ResourceType> typeAnnotations = getTypesFromAnnotations(annotations);
        if ((typeAnnotations == null || typeAnnotations.isEmpty())
                && owner instanceof KtLightMethod) {
            PsiElement origin = LightClassUtilsKt.getUnwrapped(owner);
            if (origin instanceof KtProperty) {
                List<UAnnotation> newAnnotations = null;
                List<KtAnnotationEntry> entries = ((KtProperty) origin).getAnnotationEntries();
                for (KtAnnotationEntry entry : entries) {
                    UAnnotation converted =
                            (UAnnotation)
                                    UastFacade.INSTANCE.convertElement(
                                            entry, null, UAnnotation.class);
                    if (newAnnotations == null) {
                        newAnnotations = new ArrayList<>();
                    }
                    newAnnotations.add(converted);
                }

                if (newAnnotations != null) {
                    return getTypesFromAnnotations(newAnnotations);
                }
            }
        }
        return typeAnnotations;
    }

    @Nullable
    public static EnumSet<ResourceType> getTypesFromAnnotations(
            @NonNull List<UAnnotation> annotations) {
        EnumSet<ResourceType> resources = null;
        for (UAnnotation annotation : annotations) {
            ResourceType type = getTypeFromAnnotation(annotation);
            if (type != null) {
                if (type == ANY_RES_MARKER_TYPE) {
                    return getAnyRes();
                } else if (resources == null) {
                    resources = EnumSet.of(type);
                } else {
                    resources.add(type);
                }
            }
        }

        return resources;
    }

    @Nullable
    public static EnumSet<ResourceType> getTypesFromAnnotationList(
            @NonNull List<AnnotationInfo> annotations) {
        EnumSet<ResourceType> resources = null;
        for (AnnotationInfo annotation : annotations) {
            ResourceType type = getTypeFromAnnotation(annotation.getAnnotation());
            if (type != null) {
                if (type == ANY_RES_MARKER_TYPE) {
                    return getAnyRes();
                } else if (resources == null) {
                    resources = EnumSet.of(type);
                } else {
                    resources.add(type);
                }
            }
        }

        return resources;
    }

    @Nullable
    public static ResourceType getTypeFromAnnotation(@Nullable String signature) {
        return getTypeFromAnnotation(null, signature);
    }

    @Nullable
    public static ResourceType getTypeFromAnnotation(@NonNull UAnnotation annotation) {
        return getTypeFromAnnotation(annotation, annotation.getQualifiedName());
    }

    @Nullable
    public static ResourceType getTypeFromAnnotation(
            @Nullable UAnnotation annotation, @Nullable String signature) {
        if (signature == null) {
            return null;
        } else if (isPlatformAnnotation(signature)) {
            signature = toAndroidxAnnotation(signature);
        }
        if (COLOR_INT_ANNOTATION.isEquals(signature)) {
            return COLOR_INT_MARKER_TYPE;
        } else if (PX_ANNOTATION.isEquals(signature)) {
            return DIMENSION_MARKER_TYPE;
        } else if (DIMENSION_ANNOTATION.isEquals(signature)) {
            if (annotation != null) {
                UExpression unit = annotation.findAttributeValue("unit");
                if (unit instanceof UReferenceExpression) {
                    String name = null;
                    PsiElement resolved = ((UReferenceExpression) unit).resolve();
                    if (resolved instanceof PsiNamedElement) {
                        name = ((PsiNamedElement) resolved).getName();
                    } else {
                        UElement leaf = UastLintUtilsKt.findSelector(unit);
                        if (leaf instanceof USimpleNameReferenceExpression) {
                            name = ((USimpleNameReferenceExpression) leaf).getIdentifier();
                        }
                    }
                    if ("DP".equals(name)) {
                        return DIMENSION_DP_MARKER_TYPE;
                    } else if ("SP".equals(name)) {
                        return DIMENSION_SP_MARKER_TYPE;
                    } else if ("PX".equals(name)) {
                        return DIMENSION_MARKER_TYPE;
                    } else {
                        // If we can't resolve the symbol, don't just assume it's a @Px since that
                        // can lead to
                        // problems like issue 216139975
                        return null;
                    }
                } else if (unit instanceof ULiteralExpression) {
                    return getDimensionFromIntValue(((ULiteralExpression) unit).getValue());
                } else if (unit != null
                        && unit.getSourcePsi() instanceof PsiLiteralExpression) { // bytecode
                    PsiLiteralExpression psi = (PsiLiteralExpression) unit.getSourcePsi();
                    return getDimensionFromIntValue(psi.getValue());
                } else if (unit instanceof UnknownJavaExpression) {
                    // some other PSI problem
                    return null;
                }
            }
            return DIMENSION_MARKER_TYPE;
        } else {
            if (ANY_RES_ANNOTATION.isEquals(signature)) {
                return ANY_RES_MARKER_TYPE;
            } else {
                return getTypeFromAnnotationSignature(signature);
            }
        }
    }

    @Nullable
    private static ResourceType getDimensionFromIntValue(Object value) {
        if (value instanceof Integer) {
            switch ((Integer) value) {
                case 0:
                    return DIMENSION_DP_MARKER_TYPE;
                case 1:
                    return DIMENSION_MARKER_TYPE;
                case 2:
                    return DIMENSION_SP_MARKER_TYPE;
                default:
                    // If it's some other number, it must be a future-added new unit
                    // type that this
                    // version of lint doesn't understand; don't process this as a
                    // resource type
                    // since it can lead to incorrect comparisons
                    return null;
            }
        } else {
            return null;
        }
    }

    @Nullable
    public static ResourceType getTypeFromAnnotationSignature(@NonNull String signature) {
        return TYPE_FROM_ANNOTATION_SIGNATURE.get(signature);
    }

    /** Returns a resource URL based on the field reference in the code */
    @Nullable
    public static ResourceUrl getResourceConstant(@NonNull PsiElement node) {
        // R.type.name
        if (node instanceof PsiReferenceExpression) {
            PsiReferenceExpression expression = (PsiReferenceExpression) node;
            if (expression.getQualifier() instanceof PsiReferenceExpression) {
                PsiReferenceExpression select = (PsiReferenceExpression) expression.getQualifier();
                if (select.getQualifier() instanceof PsiReferenceExpression) {
                    PsiReferenceExpression reference =
                            (PsiReferenceExpression) select.getQualifier();
                    if (R_CLASS.equals(reference.getReferenceName())) {
                        String typeName = select.getReferenceName();
                        String name = expression.getReferenceName();

                        ResourceType type = ResourceType.fromClassName(typeName);
                        if (type != null && name != null) {
                            boolean isFramework =
                                    reference.getQualifier() instanceof PsiReferenceExpression
                                            && ANDROID_PKG.equals(
                                                    ((PsiReferenceExpression)
                                                                    reference.getQualifier())
                                                            .getReferenceName());

                            return ResourceUrl.create(type, name, isFramework);
                        }
                    }
                }
            }
        } else if (node instanceof PsiField) {
            PsiField field = (PsiField) node;
            PsiClass typeClass = field.getContainingClass();
            if (typeClass != null) {
                PsiClass rClass = typeClass.getContainingClass();
                if (rClass != null && R_CLASS.equals(rClass.getName())) {
                    String name = field.getName();
                    ResourceType type = ResourceType.fromClassName(typeClass.getName());
                    if (type != null && name != null) {
                        String qualifiedName = rClass.getQualifiedName();
                        boolean isFramework =
                                qualifiedName != null
                                        && qualifiedName.startsWith(ANDROID_PKG_PREFIX);
                        return ResourceUrl.create(type, name, isFramework);
                    }
                }
            }
        }
        return null;
    }

    /** Returns a resource URL based on the field reference in the code */
    @Nullable
    public static ResourceUrl getResourceConstant(@NonNull UElement node) {
        ResourceReference reference = ResourceReference.get(node);
        if (reference == null || reference.getHeuristic()) {
            return null;
        }

        String name = reference.getName();
        ResourceType type = reference.getType();
        boolean isFramework = reference.getPackage().equals("android");

        return ResourceUrl.create(type, name, isFramework);
    }

    /** The set of all "real" resource types. */
    public static EnumSet<ResourceType> getAnyRes() {
        EnumSet<ResourceType> types = EnumSet.allOf(ResourceType.class);
        types.remove(COLOR_INT_MARKER_TYPE);
        types.remove(DIMENSION_MARKER_TYPE);
        types.remove(DIMENSION_SP_MARKER_TYPE);
        types.remove(DIMENSION_DP_MARKER_TYPE);
        types.remove(ANY_RES_MARKER_TYPE);
        return types;
    }
}
