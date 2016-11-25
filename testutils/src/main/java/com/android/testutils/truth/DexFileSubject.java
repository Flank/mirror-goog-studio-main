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

package com.android.testutils.truth;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import java.io.File;
import java.io.IOException;

public class DexFileSubject extends Subject<DexFileSubject, File> implements DexSubject {

    public static final SubjectFactory<DexFileSubject, File> FACTORY =
            new SubjectFactory<DexFileSubject, File>() {
                @Override
                public DexFileSubject getSubject(@NonNull FailureStrategy fs,
                        @Nullable File that) {
                    return new DexFileSubject(fs, that);
                }
            };


    public DexFileSubject(@NonNull FailureStrategy fs, @Nullable File that) {
        super(fs, that);
    }

    private DexBackedDexFileSubject delegate;

    @NonNull
    private DexBackedDexFileSubject getDelegate() throws IOException {
        if (delegate == null) {
            delegate = DexBackedDexFileSubject.FACTORY.getSubject(
                    failureStrategy,
                    DexUtils.loadDex(getSubject()));
        }
        return delegate;
    }


    @Override
    public IndirectSubject<DexClassSubject> containsClass(@NonNull String className)
            throws IOException {
        return getDelegate().containsClass(className);
    }

    @Override
    public void containsClasses(@NonNull String... classNames) throws IOException {
        getDelegate().containsClasses(classNames);
    }
}
