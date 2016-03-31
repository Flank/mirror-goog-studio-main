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

package com.android.tools.lint.psi;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.lint.ExternalAnnotationRepository;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.util.PsiTreeUtil;

import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class EcjPsiJavaEvaluator extends JavaEvaluator {
    private final EcjPsiManager mManager;

    public EcjPsiJavaEvaluator(@NonNull EcjPsiManager manager) {
        mManager = manager;
    }

    @Override
    public boolean extendsClass(
            @Nullable PsiClass cls,
            @NonNull String className,
            boolean strict) {
        ReferenceBinding binding;
        if (cls instanceof EcjPsiClass) {
            TypeDeclaration declaration = (TypeDeclaration) ((EcjPsiClass) cls).mNativeNode;
            binding = declaration.binding;
        } else if (cls instanceof EcjPsiBinaryClass) {
            binding = ((EcjPsiBinaryClass)cls).getTypeBinding();
        } else {
            return false;
        }
        if (strict) {
            binding = binding.superclass();
        }

        for (; binding != null; binding = binding.superclass()) {
            if (equalsCompound(className, binding.compoundName)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean implementsInterface(
            @NonNull PsiClass cls,
            @NonNull String interfaceName,
            boolean strict) {
        ReferenceBinding binding;
        if (cls instanceof EcjPsiClass) {
            TypeDeclaration declaration = (TypeDeclaration) ((EcjPsiClass) cls).mNativeNode;
            binding = declaration.binding;
        } else if (cls instanceof EcjPsiBinaryClass) {
            binding = ((EcjPsiBinaryClass)cls).getTypeBinding();
        } else {
            return false;
        }
        if (strict) {
            binding = binding.superclass();
        }
        return isInheritor(binding, interfaceName);
    }

    @Override
    public boolean inheritsFrom(
            @NonNull PsiClass cls,
            @NonNull String className,
            boolean strict) {
        return /*extendsClass(cls, className, strict) || */implementsInterface(cls, className, strict);
    }

    @VisibleForTesting
    static boolean equalsCompound(@NonNull String name, @NonNull char[][] compoundName) {
        int length = name.length();
        if (length == 0) {
            return false;
        }
        int index = 0;
        for (int i = 0, n = compoundName.length; i < n; i++) {
            char[] o = compoundName[i];
            //noinspection ForLoopReplaceableByForEach
            for (int j = 0, m = o.length; j < m; j++) {
                if (index == length) {
                    return false; // Don't allow prefix in a compound name
                }
                if (name.charAt(index) != o[j]
                        // Allow using . as an inner class separator whereas the
                        // symbol table will always use $
                        && !(o[j] == '$' && name.charAt(index) == '.')) {
                    return false;
                }
                index++;
            }
            if (i < n - 1) {
                if (index == length) {
                    return false;
                }
                if (name.charAt(index) != '.') {
                    return false;
                }
                index++;
                if (index == length) {
                    return false;
                }
            }
        }

        return index == length;
    }

    /** Checks whether the given class extends or implements a class with the given name */
    private static boolean isInheritor(@Nullable ReferenceBinding cls, @NonNull String name) {
        for (; cls != null; cls = cls.superclass()) {
            ReferenceBinding[] interfaces = cls.superInterfaces();
            for (ReferenceBinding binding : interfaces) {
                if (isInheritor(binding, name)) {
                    return true;
                }
            }

            if (equalsCompound(name, cls.compoundName)) {
                return true;
            }
        }

        return false;
    }

    @NonNull
    @Override
    public String getInternalName(@NonNull PsiClass psiClass) {
        ReferenceBinding binding = null;
        if (psiClass instanceof EcjPsiClass) {
            //noinspection ConstantConditions
            binding = ((TypeDeclaration) ((EcjPsiClass) psiClass).getNativeNode()).binding;
        } else if (psiClass instanceof EcjPsiBinaryClass) {
            Binding binaryBinding = ((EcjPsiBinaryClass) psiClass).getBinding();
            if (binaryBinding instanceof ReferenceBinding) {
                binding = (ReferenceBinding) binaryBinding;
            }
        }
        if (binding == null) {
            return super.getInternalName(psiClass);
        }

        return EcjPsiManager.getInternalName(binding.compoundName);
    }

    @NonNull
    @Override
    public String getInternalName(@NonNull PsiClassType psiClassType) {
        if (psiClassType instanceof EcjPsiClassType) {
            EcjPsiManager.getTypeName(((EcjPsiClassType)psiClassType).getBinding());
        }
        return super.getInternalName(psiClassType);
    }

    @Override
    @Nullable
    public PsiClass findClass(@NonNull String fullyQualifiedName) {
        return mManager.findClass(fullyQualifiedName);
    }

    @Nullable
    @Override
    public PsiClassType getClassType(@Nullable PsiClass psiClass) {
        if (psiClass != null) {
            return mManager.getClassType(psiClass);
        }
        return null;
    }

    @NonNull
    @Override
    public PsiAnnotation[] getAllAnnotations(@NonNull PsiModifierListOwner owner,
            boolean inHierarchy) {
        if (!inHierarchy) {
            return getDirectAnnotations(owner);
        }

        PsiModifierList modifierList = owner.getModifierList();
        if (modifierList == null) {
            return getDirectAnnotations(owner);
        }

        if (owner instanceof PsiMethod) {
            MethodBinding method;
            if (owner instanceof EcjPsiMethod) {
                EcjPsiMethod psiMethod = (EcjPsiMethod) owner;
                AbstractMethodDeclaration declaration = (AbstractMethodDeclaration) psiMethod.getNativeNode();
                assert declaration != null;
                method = declaration.binding;
            } else if (owner instanceof EcjPsiBinaryMethod) {
                method = ((EcjPsiBinaryMethod) owner).getBinding();
            } else {
                assert false : owner.getClass();
                return PsiAnnotation.EMPTY_ARRAY;
            }

            List<PsiAnnotation> all = Lists.newArrayListWithExpectedSize(2);
            ExternalAnnotationRepository manager = mManager.getAnnotationRepository();

            while (method != null) {
                if (method.declaringClass == null) {
                    // for example, for unresolved problem bindings
                    break;
                }
                AnnotationBinding[] annotations = method.getAnnotations();
                int count = annotations.length;
                if (count > 0) {
                    all = Lists.newArrayListWithExpectedSize(count);
                    for (AnnotationBinding annotation : annotations) {
                        if (annotation != null) {
                            all.add(new EcjPsiBinaryAnnotation(mManager, modifierList, annotation));
                        }
                    }
                }

                // Look for external annotations
                if (manager != null) {
                    Collection<PsiAnnotation> external = manager.getAnnotations(method);
                    if (external != null) {
                        all.addAll(external);
                    }
                }

                method = EcjPsiManager.findSuperMethodBinding(method, false, false);
            }

            return EcjPsiManager.ensureUnique(all);
        } else if (owner instanceof PsiClass) {
            ReferenceBinding cls;
            if (owner instanceof EcjPsiClass) {
                EcjPsiClass psiClass = (EcjPsiClass) owner;
                TypeDeclaration declaration = (TypeDeclaration) psiClass.getNativeNode();
                assert declaration != null;
                cls = declaration.binding;
            } else if (owner instanceof EcjPsiBinaryClass) {
                cls = ((EcjPsiBinaryClass) owner).getTypeBinding();
            } else {
                assert false : owner.getClass();
                return PsiAnnotation.EMPTY_ARRAY;
            }

            List<PsiAnnotation> all = Lists.newArrayListWithExpectedSize(2);
            ExternalAnnotationRepository manager = mManager.getAnnotationRepository();

            while (cls != null) {
                AnnotationBinding[] annotations = cls.getAnnotations();
                int count = annotations.length;
                if (count > 0) {
                    all = Lists.newArrayListWithExpectedSize(count);
                    for (AnnotationBinding annotation : annotations) {
                        if (annotation != null) {
                            all.add(new EcjPsiBinaryAnnotation(mManager, modifierList, annotation));
                        }
                    }
                }

                // Look for external annotations
                if (manager != null) {
                    Collection<PsiAnnotation> external = manager.getAnnotations(cls);
                    if (external != null) {
                        all.addAll(external);
                    }
                }

                cls = cls.superclass();
            }

            return EcjPsiManager.ensureUnique(all);
        } else if (owner instanceof PsiParameter) {
            MethodBinding method;
            int index;

            if (owner instanceof EcjPsiBinaryParameter) {
                EcjPsiBinaryParameter parameter = (EcjPsiBinaryParameter) owner;
                method = parameter.getOwnerMethod().getBinding();
                index = parameter.getIndex();
            } else if (owner instanceof EcjPsiParameter) {
                EcjPsiParameter parameter = (EcjPsiParameter) owner;
                if (parameter.getParent() instanceof PsiParameterList) {
                    EcjPsiMethod psiMethod = (EcjPsiMethod)PsiTreeUtil.getParentOfType(
                            parameter.getParent(), PsiMethod.class, true);
                    if (psiMethod == null) {
                        return getDirectAnnotations(owner);
                    }
                    index = ((PsiParameterList)parameter.getParent()).getParameterIndex(parameter);
                    AbstractMethodDeclaration declaration = (AbstractMethodDeclaration) psiMethod.getNativeNode();
                    assert declaration != null;
                    method = declaration.binding;
                } else {
                    // For each block, catch block
                    return getDirectAnnotations(owner);
                }
            } else {
                // Unexpected method type
                assert false : owner.getClass();
                return getDirectAnnotations(owner);
            }

            List<PsiAnnotation> all = Lists.newArrayListWithExpectedSize(2);
            ExternalAnnotationRepository manager = mManager.getAnnotationRepository();

            while (method != null) {
                if (method.declaringClass == null) {
                    // for example, for unresolved problem bindings
                    break;
                }
                AnnotationBinding[][] parameterAnnotations = method.getParameterAnnotations();
                if (parameterAnnotations != null && index < parameterAnnotations.length) {
                    AnnotationBinding[] annotations = parameterAnnotations[index];
                    int count = annotations.length;
                    if (count > 0) {
                        all = Lists.newArrayListWithExpectedSize(count);
                        for (AnnotationBinding annotation : annotations) {
                            if (annotation != null) {
                                all.add(new EcjPsiBinaryAnnotation(mManager, modifierList,
                                        annotation));
                            }
                        }
                    }
                }

                // Look for external annotations
                if (manager != null) {
                    Collection<PsiAnnotation> external = manager.getParameterAnnotations(method,
                            index);
                    if (external != null) {
                        all.addAll(external);
                    }
                }

                method = EcjPsiManager.findSuperMethodBinding(method, false, false);
            }

            return EcjPsiManager.ensureUnique(all);
        } else {
            // PsiField, PsiLocalVariable etc: no inheritance
            return getDirectAnnotations(owner);
        }
    }

    @NonNull
    private static PsiAnnotation[] getDirectAnnotations(@NonNull PsiModifierListOwner owner) {
        PsiModifierList modifierList = owner.getModifierList();
        if (modifierList != null) {
            return modifierList.getAnnotations();
        } else {
            return PsiAnnotation.EMPTY_ARRAY;
        }
    }

    @Nullable
    @Override
    public PsiAnnotation findAnnotationInHierarchy(@NonNull PsiModifierListOwner listOwner,
            @NonNull String... annotationNames) {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner,
            @NonNull String... annotationNames) {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public File getFile(@NonNull PsiFile file) {
        if (file instanceof EcjPsiJavaFile) {
            EcjPsiJavaFile javaFile = (EcjPsiJavaFile) file;
            return javaFile.getIoFile();
        }

        return null;
    }
}
