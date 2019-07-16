/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.apk.analyzer.internal.rewriters;

import com.android.annotations.NonNull;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.rewriter.FieldReferenceRewriter;
import org.jf.dexlib2.rewriter.Rewriters;

public abstract class FieldReferenceWithNameRewriter extends FieldReferenceRewriter {

    public FieldReferenceWithNameRewriter(@NonNull Rewriters rewriters) {
        super(rewriters);
    }

    public abstract String rewriteName(FieldReference fieldReference);

    @NonNull
    @Override
    public FieldReference rewrite(@NonNull FieldReference fieldReference) {
        return new RewrittenFieldReferenceWithName(fieldReference);
    }

    protected class RewrittenFieldReferenceWithName extends RewrittenFieldReference {

        public RewrittenFieldReferenceWithName(FieldReference fieldReference) {
            super(fieldReference);
        }

        @Override
        @NonNull
        public String getName() {
            return rewriteName(fieldReference);
        }
    }
}
