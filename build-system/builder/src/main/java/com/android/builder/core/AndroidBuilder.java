/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.core;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.errors.EvalIssueReporter;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfo;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.android.repository.Revision;
import com.android.utils.ILogger;

/**
 * This is the main builder class. It is given all the data to process the build (such as {@link
 * DefaultProductFlavor}s, {@link DefaultBuildType} and dependencies) and use them when doing
 * specific build steps.
 *
 * <p>To use: create a builder with {@link #AndroidBuilder(ProcessExecutor, JavaProcessExecutor,
 * EvalIssueReporter, MessageReceiver, ILogger)}
 */
public class AndroidBuilder {

    /**
     * Minimal supported version of build tools.
     *
     * <p>ATTENTION: When changing this value, make sure to update the release notes
     * (https://developer.android.com/studio/releases/gradle-plugin).
     */
    public static final Revision MIN_BUILD_TOOLS_REV =
            Revision.parseRevision(SdkConstants.CURRENT_BUILD_TOOLS_VERSION);

    /**
     * Default version of build tools that will be used if the user does not specify.
     *
     * <p>ATTENTION: This is usually the same as the minimum build tools version, as documented in
     * {@code com.android.build.gradle.AndroidConfig#getBuildToolsVersion()} and {@code
     * com.android.build.api.dsl.extension.BuildProperties#getBuildToolsVersion()}, and in the
     * release notes (https://developer.android.com/studio/releases/gradle-plugin). If this version
     * is higher than the minimum version, make sure to update those places to document the new
     * behavior.
     */
    public static final Revision DEFAULT_BUILD_TOOLS_REVISION = MIN_BUILD_TOOLS_REV;

    @NonNull
    private final ILogger mLogger;

    @NonNull
    private final ProcessExecutor mProcessExecutor;
    @NonNull
    private final JavaProcessExecutor mJavaProcessExecutor;
    @NonNull private final EvalIssueReporter issueReporter;
    @NonNull private final MessageReceiver messageReceiver;

    /**
     * Creates an AndroidBuilder.
     *
     * <p><var>verboseExec</var> is needed on top of the ILogger due to remote exec tools not being
     * able to output info and verbose messages separately.
     *
     * @param logger the Logger
     */
    public AndroidBuilder(
            @NonNull ProcessExecutor processExecutor,
            @NonNull JavaProcessExecutor javaProcessExecutor,
            @NonNull EvalIssueReporter issueReporter,
            @NonNull MessageReceiver messageReceiver,
            @NonNull ILogger logger) {
        mProcessExecutor = checkNotNull(processExecutor);
        mJavaProcessExecutor = checkNotNull(javaProcessExecutor);
        this.issueReporter = checkNotNull(issueReporter);
        this.messageReceiver = messageReceiver;
        mLogger = checkNotNull(logger);
    }

    @NonNull
    public ILogger getLogger() {
        return mLogger;
    }

    @NonNull
    public EvalIssueReporter getIssueReporter() {
        return issueReporter;
    }

    @NonNull
    public MessageReceiver getMessageReceiver() {
        return messageReceiver;
    }

    @NonNull
    public ProcessExecutor getProcessExecutor() {
        return mProcessExecutor;
    }

    @NonNull
    public JavaProcessExecutor getJavaProcessExecutor() {
        return mJavaProcessExecutor;
    }

    @NonNull
    public ProcessResult executeProcess(@NonNull ProcessInfo processInfo,
            @NonNull ProcessOutputHandler handler) {
        return mProcessExecutor.execute(processInfo, handler);
    }
}
