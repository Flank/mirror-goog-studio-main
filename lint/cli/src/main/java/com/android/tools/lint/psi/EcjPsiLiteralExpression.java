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

import static com.android.SdkConstants.VALUE_TRUE;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import org.eclipse.jdt.internal.compiler.ast.Literal;
import org.eclipse.jdt.internal.compiler.ast.NullLiteral;
import org.eclipse.jdt.internal.compiler.impl.BooleanConstant;
import org.eclipse.jdt.internal.compiler.impl.Constant;

class EcjPsiLiteralExpression extends EcjPsiExpression implements PsiLiteralExpression {
    private final Object mValue;
    private String mText;

    EcjPsiLiteralExpression(@NonNull EcjPsiManager manager,
            @NonNull Literal expression) {
        super(manager, expression);
        mValue = expression.constant;
        if (mValue == Constant.NotAConstant) {
            if (expression instanceof NullLiteral) {
                mText = "null";
            }
        } else if (mValue instanceof BooleanConstant) {
            mText = ((BooleanConstant) mValue).booleanValue()
                    ? VALUE_TRUE : SdkConstants.VALUE_FALSE;
        } else {
            char[] source = expression.source();
            if (source != null
                    // The char array seems to inline unicode escapes, drops quotes etc
                    // so it doesn't actually match the source code
                    && source.length == (expression.sourceEnd - expression.sourceStart)) {
                mText = new String(source);
            }
        }
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitLiteralExpression(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @Nullable
    @Override
    public Object getValue() {
        return EcjPsiManager.inlineConstants(mValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EcjPsiLiteralExpression that = (EcjPsiLiteralExpression) o;

        return mValue != null ? mValue.equals(that.mValue) : that.mValue == null;
    }

    @Override
    public int hashCode() {
        return mValue != null ? mValue.hashCode() : 0;
    }

    @Override
    public String getText() {
        // Cache since we often call getText() on literals to compare
        if (mText == null) {
            mText = super.getText();
        }

        return mText;
    }
}