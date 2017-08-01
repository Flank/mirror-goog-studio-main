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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.ErrorReporter;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.blame.Message;
import java.util.List;

/** Error reporter that does not processes the messages. Also, it ignores handling the issues. */
public class NoOpErrorReporter extends ErrorReporter {

    private static final SyncIssue FAKE_ISSUE =
            new SyncIssue() {
                @Override
                public int getSeverity() {
                    return 0;
                }

                @Override
                public int getType() {
                    return 0;
                }

                @Nullable
                @Override
                public String getData() {
                    return null;
                }

                @NonNull
                @Override
                public String getMessage() {
                    return "";
                }

                @Nullable
                @Override
                public List<String> getMultiLineMessage() {
                    return null;
                }
            };

    public NoOpErrorReporter() {
        super(EvaluationMode.IDE);
    }

    @Override
    public void receiveMessage(@NonNull Message message) {
        // do nothing
    }

    @NonNull
    @Override
    public SyncIssue handleIssue(
            @Nullable String data, int type, int severity, @NonNull String msg) {
        return FAKE_ISSUE;
    }

    @Override
    public boolean hasSyncIssue(int type) {
        return false;
    }
}
