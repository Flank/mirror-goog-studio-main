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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;

import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.ElementValuePair;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.jetbrains.annotations.NotNull;

class EcjPsiBinaryNameValuePair extends EcjPsiBinaryElement implements PsiNameValuePair,
        PsiAnnotationMemberValue, PsiLiteral {

    private final ElementValuePair mPair;

    private final String mName;

    private PsiAnnotationMemberValue mValue;

    EcjPsiBinaryNameValuePair(@NonNull EcjPsiManager manager,
            @NonNull ElementValuePair pair) {
        super(manager, null);
        mPair = pair;
        mName = new String(pair.getName());
    }

    @Nullable
    @Override
    public PsiIdentifier getNameIdentifier() {
        return null;
    }

    @Nullable
    @Override
    public String getName() {
        return mName;
    }

    @Nullable
    @Override
    public String getLiteralValue() {
        // Uhm... we don't have this for binary elements
        return null;
    }

    @Nullable
    @Override
    public PsiAnnotationMemberValue getValue() {
        if (mValue == null) {
            mValue = createValue(mManager, mPair.getValue());
        }

        return mValue;
    }

    @NonNull
    private static PsiAnnotationMemberValue createValue(EcjPsiManager manager, @Nullable Object value) {
        if (value instanceof FieldBinding) {
            return new EcjPsiBinaryReferenceExpression(manager, (Binding) value);
        } else if (value instanceof Object[]) {
            return new EcjPsiBinaryArrayInitializerMemberValue(manager, (Object[]) value);
        } else {
            return new EcjPsiBinaryAnnotationMemberValue(manager, value);
        }
    }

    private static class EcjPsiBinaryArrayInitializerMemberValue extends EcjPsiBinaryElement
            implements PsiArrayInitializerMemberValue {
        private PsiAnnotationMemberValue[] mInitializers;

        public EcjPsiBinaryArrayInitializerMemberValue(EcjPsiManager manager, Object[] values) {
            super(manager, null);

            mInitializers = new PsiAnnotationMemberValue[values.length];
            for (int i = 0; i < values.length; i++) {
                mInitializers[i] = createValue(manager, values[i]);
            }
        }

        @NotNull
        @Override
        public PsiAnnotationMemberValue[] getInitializers() {
            return mInitializers;
        }
    }

    private static class EcjPsiBinaryReferenceExpression extends EcjPsiBinaryElement implements
            PsiReferenceExpression {

        EcjPsiBinaryReferenceExpression(@NonNull EcjPsiManager manager,
                @NotNull Binding binding) {
            super(manager, binding);
        }

        @Nullable
        @Override
        public PsiExpression getQualifierExpression() {
            return null;
        }

        @Nullable
        @Override
        public PsiType getType() {
            if (mBinding instanceof FieldBinding) {
                return mManager.findType(((FieldBinding)mBinding).type);
            }
            return null;
        }

        @Nullable
        @Override
        public PsiElement getReferenceNameElement() {
            return null;
        }

        @Nullable
        @Override
        public PsiReferenceParameterList getParameterList() {
            return null;
        }

        @NotNull
        @Override
        public PsiType[] getTypeParameters() {
            return new PsiType[0];
        }

        @Override
        public boolean isQualified() {
            return true;
        }

        @Override
        public String getQualifiedName() {
            ReferenceBinding typeBinding;
            if (mBinding instanceof FieldBinding) {
                typeBinding = ((FieldBinding)mBinding).declaringClass;
            } else if (mBinding instanceof MethodBinding) {
                typeBinding = ((MethodBinding)mBinding).declaringClass;
            } else {
                return getReferenceName();
            }
            PsiType type = mManager.findType(typeBinding);
            if (type instanceof PsiClassType) {
                PsiClass cls = ((PsiClassType) type).resolve();
                if (cls != null) {
                    String qualifiedName = cls.getQualifiedName();
                    if (qualifiedName != null) {
                        return qualifiedName + '.' + getReferenceName();
                    }
                }
            }

            return getReferenceName();
        }

        @Nullable
        @Override
        public PsiElement getQualifier() {
            return null;
        }

        @Nullable
        @Override
        public String getReferenceName() {
            return mBinding != null ? new String(mBinding.readableName()) : null;
        }

        @Override
        public PsiElement getElement() {
            return null;
        }

        @Override
        public TextRange getRangeInElement() {
            return null;
        }

        @Nullable
        @Override
        public PsiElement resolve() {
            return mManager.findElement(mBinding);
        }

        @NotNull
        @Override
        public String getCanonicalText() {
            return "";
        }

        @Override
        public boolean isSoft() {
            return false;
        }
    }
}
