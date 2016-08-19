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

import com.android.testutils.incremental.FileRecord;
import com.android.utils.FileUtils;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;

import java.io.IOException;

/**
 * Truth support for validating FileRecord.
 */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class FileRecordSubject extends Subject<FileRecordSubject, FileRecord> {
    public static final SubjectFactory<FileRecordSubject, FileRecord> FACTORY =
            new SubjectFactory<FileRecordSubject, FileRecord>() {
                @Override
                public FileRecordSubject getSubject(FailureStrategy fs, FileRecord that) {
                    return new FileRecordSubject(fs, that);
                }
            };

    public FileRecordSubject(FailureStrategy failureStrategy, FileRecord subject) {
        super(failureStrategy, subject);
    }

    public void hasChanged() throws IOException {
        if (FileUtils.sha1(getSubject().getFile()).equals(getSubject().getHash())) {
            fail("has changed");
        }
    }

    public void hasNotChanged() throws IOException {
        if (!FileUtils.sha1(getSubject().getFile()).equals(getSubject().getHash())) {
            fail("has not changed");
        }
    }
}
