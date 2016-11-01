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

import static com.android.tools.lint.psi.EcjPsiManager.getTypeName;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.psi.javadoc.PsiDocComment;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.eclipse.jdt.internal.compiler.ast.TypeParameter;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeVariableBinding;

class EcjPsiTypeParameter extends EcjPsiSourceElement implements PsiTypeParameter {

    private final TypeParameter mDeclaration;

    private EcjPsiReferenceList mExtendsList;

    private String mQualifiedName;

    private EcjPsiModifierList mModifierList;

    EcjPsiTypeParameter(@NonNull EcjPsiManager manager, @NonNull TypeParameter declaration) {
        super(manager, declaration);
        mDeclaration = declaration;
        if (declaration.binding != null && declaration.binding.compoundName != null) {
            mQualifiedName = getTypeName(declaration.binding);
        }

        mManager.registerElement(declaration.binding, this);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitTypeParameter(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    void setExtendsList(EcjPsiReferenceList extendsList) {
        mExtendsList = extendsList;
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isAnnotationType() {
        return false;
    }

    @Override
    public boolean isEnum() {
        return false;
    }

    @Override
    public PsiIdentifier getNameIdentifier() {
        return null;
    }

    @Override
    public String getName() {
        return new String(mDeclaration.name);
    }

    @Nullable
    @Override
    public PsiTypeParameterListOwner getOwner() {
        return (PsiTypeParameterListOwner)getParent().getParent();
    }

    @Override
    public int getIndex() {
        int index = 0;
        EcjPsiSourceElement curr = mParent.mFirstChild;
        while (curr != null) {
            if (curr == this) {
                return index;
            } else if (curr instanceof EcjPsiTypeParameter) {
                index++;
            }
            curr = curr.mNextSibling;
        }
        return -1;
    }

    @Override
    @NonNull
    public PsiElement[] getChildren() {
        return PsiElement.EMPTY_ARRAY;
    }

    @Override
    @NonNull
    public PsiReferenceList getExtendsList() {
        return mExtendsList;
    }

    @Override
    @NonNull
    public PsiClassType[] getImplementsListTypes() {
        return PsiClassType.EMPTY_ARRAY;
    }

    @Override
    @NonNull
    public PsiField[] getFields() {
        return PsiField.EMPTY_ARRAY;
    }

    @Override
    @NonNull
    public PsiMethod[] getMethods() {
        return PsiMethod.EMPTY_ARRAY;
    }

    @NonNull
    @Override
    public PsiAnnotation[] getAnnotations() {
        return PsiAnnotation.EMPTY_ARRAY;
    }

    @NonNull
    @Override
    public PsiAnnotation[] getApplicableAnnotations() {
        return PsiAnnotation.EMPTY_ARRAY;
    }

    @Nullable
    @Override
    public PsiAnnotation findAnnotation(@NonNull String s) {
        return null;
    }

    @Override
    public PsiTypeParameterList getTypeParameterList() {
        return null;
    }

    @Override
    public boolean hasTypeParameters() {
        return false;
    }

    @Override
    public PsiElement getScope() {
        return getParent().getParent();
    }

    @Override
    @NonNull
    public PsiMethod[] getConstructors() {
        return PsiMethod.EMPTY_ARRAY;
    }

    @Override
    public PsiDocComment getDocComment() {
        return null;
    }

    @Override
    @NonNull
    public PsiClass[] getInnerClasses() {
        return PsiClass.EMPTY_ARRAY;
    }

    @Override
    @NonNull
    public PsiField[] getAllFields() {
        return PsiField.EMPTY_ARRAY;
    }

    @Override
    @NonNull
    public PsiMethod[] getAllMethods() {
        return PsiMethod.EMPTY_ARRAY;
    }

    @Override
    @NonNull
    public PsiClass[] getAllInnerClasses() {
        return PsiClass.EMPTY_ARRAY;
    }

    @Override
    @NonNull
    public PsiClassInitializer[] getInitializers() {
        return PsiClassInitializer.EMPTY_ARRAY;
    }

    @Override
    @NonNull
    public PsiTypeParameter[] getTypeParameters() {
        return PsiTypeParameter.EMPTY_ARRAY;
    }

    @Override
    public PsiClass getSuperClass() {
        if (mDeclaration.bounds != null && mDeclaration.bounds.length > 0) {
            return mManager.findClass(mDeclaration.bounds[0]);
        }
        return null;
    }

    @Override
    public PsiClass[] getInterfaces() {
        return PsiClass.EMPTY_ARRAY;
    }

    @Override
    @NonNull
    public PsiClass[] getSupers() {
        if (mDeclaration.bounds != null && mDeclaration.bounds.length > 0) {
            List<PsiClass> classes = Lists.newArrayListWithCapacity(mDeclaration.bounds.length);
            for (TypeReference ref : mDeclaration.bounds) {
                PsiClass cls = mManager.findClass(ref);
                if (cls != null) {
                    classes.add(cls);
                }
            }
            return classes.toArray(PsiClass.EMPTY_ARRAY);
        }
        return PsiClass.EMPTY_ARRAY;
    }

    @Override
    public boolean hasModifierProperty(@NonNull String name) {
        return false;
    }

    @Override
    public PsiJavaToken getLBrace() {
        return null;
    }

    @Override
    public PsiJavaToken getRBrace() {
        return null;
    }

    @Override
    @NonNull
    public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
        return Collections.emptyList();
    }

    @Override
    public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
        return null;
    }

    @Override
    @NonNull
    public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
        return PsiMethod.EMPTY_ARRAY;
    }

    @Override
    public PsiField findFieldByName(String name, boolean checkBases) {
        return null;
    }

    @Override
    @NonNull
    public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
        return PsiMethod.EMPTY_ARRAY;
    }

    @Override
    @NonNull
    public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
        return Collections.emptyList();
    }

    @Override
    @NonNull
    public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
        return Collections.emptyList();
    }

    @Override
    public PsiClass findInnerClassByName(String name, boolean checkBases) {
        return null;
    }

    @Nullable
    @Override
    public String getQualifiedName() {
        return mQualifiedName;
    }

    @Nullable
    @Override
    public PsiClass getContainingClass() {
        return mParent instanceof PsiClass ? (PsiClass) mParent : null;
    }

    @Nullable
    @Override
    public PsiModifierList getModifierList() {
        return mModifierList;
    }

    public void setModifierList(@Nullable EcjPsiModifierList modifierList) {
        mModifierList = modifierList;
    }

    @Nullable
    @Override
    public PsiReferenceList getImplementsList() {
        return null;
    }

    @NonNull
    @Override
    public PsiClassType[] getExtendsListTypes() {
        return mExtendsList != null ? mExtendsList.getReferencedTypes() : PsiClassType.EMPTY_ARRAY;
    }

    @NonNull
    @Override
    public PsiClassType[] getSuperTypes() {
        return mManager.getClassTypes(getSupers());
    }

    @Override
    public boolean isInheritor(@NonNull PsiClass baseClass, boolean checkDeep) {
        String qualifiedName = baseClass.getQualifiedName();
        return qualifiedName != null && new EcjPsiJavaEvaluator(mManager)
                .inheritsFrom(this, qualifiedName, false);
    }

    @Override
    public boolean isInheritorDeep(PsiClass baseClass, @Nullable PsiClass classToByPass) {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public boolean isDeprecated() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        TypeParameter typeDeclaration = (TypeParameter) mNativeNode;
        TypeVariableBinding binding = typeDeclaration.binding;
        TypeBinding otherBinding;
        if (o instanceof EcjPsiTypeParameter) {
            TypeParameter otherTypeDeclaration =
                    (TypeParameter) (((EcjPsiTypeParameter) o).getNativeNode());
            assert otherTypeDeclaration != null;
            otherBinding = otherTypeDeclaration.binding;
            if (binding == null || otherBinding == null) {
                return typeDeclaration.equals(otherTypeDeclaration);
            }
            return binding.equals(otherBinding);
        } else if (o instanceof EcjPsiBinaryClass) {
            otherBinding = (ReferenceBinding) (((EcjPsiBinaryClass) o).getBinding());
            return binding != null && otherBinding != null && binding.equals(otherBinding);
        }

        return false;
    }

    @Override
    public int hashCode() {
        TypeVariableBinding binding = ((TypeParameter) mNativeNode).binding;
        return binding != null ? binding.hashCode() : 0;
    }

    @Nullable
    public ReferenceBinding getBinding() {
        if (mNativeNode instanceof TypeParameter) {
            return ((TypeParameter) mNativeNode).binding;
        }

        return null;
    }
}
