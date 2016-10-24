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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.tree.IElementType;
import org.eclipse.jdt.internal.compiler.ast.Expression;

class EcjPsiAssignmentExpression extends EcjPsiExpression implements
        PsiAssignmentExpression {

    private PsiExpression mLhs;

    private PsiExpression mRhs;

    private IElementType mOperation;

    EcjPsiAssignmentExpression(@NonNull EcjPsiManager manager,
            @NonNull Expression expression) {
        super(manager, expression);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitAssignmentExpression(this);
        } else {
            visitor.visitElement(this);
        }
    }

    void setLhs(PsiExpression lhs) {
        mLhs = lhs;
    }

    void setRhs(PsiExpression rhs) {
        mRhs = rhs;
    }

    void setOperation(IElementType operation) {
        mOperation = operation;
    }

    @NonNull
    @Override
    public PsiExpression getLExpression() {
        return mLhs;
    }

    @Nullable
    @Override
    public PsiExpression getRExpression() {
        return mRhs;
    }

    @NonNull
    @Override
    public IElementType getOperationTokenType() {
        return mOperation;
    }

    @NonNull
    @Override
    public PsiJavaToken getOperationSign() {
        throw new UnimplementedLintPsiApiException();
    }
}
