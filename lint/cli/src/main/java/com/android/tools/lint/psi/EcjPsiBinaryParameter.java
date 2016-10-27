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
import com.android.tools.lint.ExternalAnnotationRepository;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import java.util.Collection;
import java.util.List;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;

public class EcjPsiBinaryParameter extends EcjPsiBinaryElement implements PsiParameter,
        PsiModifierList {

    private boolean mVarArgs;
    private final int mIndex;
    private final EcjPsiBinaryMethod mMethod;

    public EcjPsiBinaryParameter(@NonNull EcjPsiManager manager,
            @Nullable TypeBinding binding, @NonNull EcjPsiBinaryMethod method,
            int index) {
        super(manager, binding);
        mMethod = method;
        mIndex = index;
    }

    @SuppressWarnings("SameParameterValue")
    void setVarArgs(boolean varArgs) {
        mVarArgs = varArgs;
    }

    @NonNull
    EcjPsiBinaryMethod getOwnerMethod() {
        return mMethod;
    }

    public int getIndex() {
        return mIndex;
    }

    @Override
    public boolean isVarArgs() {
        return mVarArgs;
    }

    @NonNull
    @Override
    public PsiType getType() {
        PsiType type = mManager.findType((TypeBinding) mBinding);
        if (type == null) {
            type = PsiType.NULL;
        }
        return type;
    }

    @Nullable
    @Override
    public PsiTypeElement getTypeElement() {
        return null;
    }

    @Nullable
    @Override
    public PsiExpression getInitializer() {
        return null;
    }

    @Override
    public boolean hasInitializer() {
        return false;
    }

    @Nullable
    @Override
    public Object computeConstantValue() {
        return null;
    }

    @Nullable
    @Override
    public PsiIdentifier getNameIdentifier() {
        return null;
    }

    @Nullable
    @Override
    public String getName() {
        return null;
    }

    // Modifier list inlined here

    @NonNull
    @Override
    public PsiModifierList getModifierList() {
        return this;
    }

    @Override
    public boolean hasModifierProperty(@NonNull @PsiModifier.ModifierConstant String s) {
        return hasExplicitModifier(s);
    }

    @Override
    public boolean hasExplicitModifier(@NonNull @PsiModifier.ModifierConstant String s) {
        return mBinding instanceof ReferenceBinding
                && EcjPsiModifierList .hasModifier(((ReferenceBinding) mBinding).modifiers, s);
    }

    @NonNull
    @Override
    public PsiAnnotation[] getAnnotations() {
        return getApplicableAnnotations();
    }

    @NonNull
    @Override
    public PsiAnnotation[] getApplicableAnnotations() {
        return findAnnotations(false);
    }

    @SuppressWarnings("SameParameterValue")
    private PsiAnnotation[] findAnnotations(boolean includeSuper) {
        List<PsiAnnotation> all = Lists.newArrayListWithExpectedSize(4);
        ExternalAnnotationRepository manager = mManager.getAnnotationRepository();

        MethodBinding binding = mMethod.getBinding();
        while (binding != null) {
            //noinspection VariableNotUsedInsideIf
            if (binding.declaringClass != null) { // prevent NPE in binding.getParameterAnnotations()
                AnnotationBinding[][] parameterAnnotations = binding.getParameterAnnotations();
                if (parameterAnnotations != null && mIndex < parameterAnnotations.length) {
                    AnnotationBinding[] annotations = parameterAnnotations[mIndex];
                    if (annotations != null && annotations.length > 0) {
                        for (AnnotationBinding annotation : annotations) {
                            if (annotation != null) {
                                all.add(new EcjPsiBinaryAnnotation(mManager, this, annotation));
                            }
                        }
                    }
                }
            }

            // Look for external annotations
            if (manager != null) {
                Collection<PsiAnnotation> external = manager.getParameterAnnotations(binding,
                        mIndex);
                if (external != null) {
                    all.addAll(external);
                }
            }

            if (!includeSuper) {
                break;
            }

            binding = EcjPsiManager.findSuperMethodBinding(binding, false, false);
            if (binding != null && binding.isPrivate()) {
                break;
            }
        }

        return EcjPsiManager.ensureUnique(all);
    }

    @Nullable
    @Override
    public PsiAnnotation findAnnotation(@NonNull String s) {
        for (PsiAnnotation annotation : getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (s.equals(qualifiedName)) {
                return annotation;
            }
        }
        return null;
    }
}
