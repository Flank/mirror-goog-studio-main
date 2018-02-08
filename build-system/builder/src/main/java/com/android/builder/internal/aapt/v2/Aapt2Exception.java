/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.builder.internal.aapt.v2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/** Exception thrown when an error occurs during {@code aapt2} processing. */
public class Aapt2Exception extends RuntimeException {
    private static final long serialVersionUID = 7034893190645766939L;

    @Nullable private final String output;

    /**
     * Creates a new exception.
     *
     * @param description a description of the exception
     */
    public Aapt2Exception(@NonNull String description) {
        super(description);
        output = null;
    }

    /**
     * Creates a new exception.
     *
     * @param description a description of the exception
     * @param output the error output of AAPT
     */
    public Aapt2Exception(@NonNull String description, @Nullable String output) {
        super(description);
        this.output = output;
    }

    /**
     * Creates a new exception.
     *
     * @param description a description of the exception
     * @param cause the cause of the exception
     */
    public Aapt2Exception(String description, @Nullable Throwable cause) {
        super(description, cause);
        this.output = null;
    }

    /**
     * Creates a new exception.
     *
     * @param description a description of the exception
     * @param output the error output of AAPT
     * @param cause the cause of the exception
     */
    public Aapt2Exception(
            @NonNull String description, @Nullable String output, @Nullable Throwable cause) {
        super(description, cause);
        this.output = output;
    }

    /**
     * The error output of AAPT2.
     *
     * <p>Null if not set.
     */
    @Nullable
    public String getOutput() {
        return output;
    }

    @Override
    public String getMessage() {
        if (output == null) {
            return super.getMessage();
        }
        return super.getMessage() + "\nOutput:  " + output;
    }
}
