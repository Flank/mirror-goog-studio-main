/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.agent.app.inspection;

import androidx.annotation.NonNull;
import com.android.tools.agent.app.inspection.InspectorContext.CrashListener;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Simple class that redirects every interaction with inspector to the inspector's thread. */
public final class InspectorBridge {
    private final Executor mInspectorExecutor = Executors.newSingleThreadScheduledExecutor();
    private final InspectorContext mInspectorContext;

    private InspectorBridge(InspectorContext context) {
        mInspectorContext = context;
    }

    /**
     * Instantiate inspector itself. This is an async operation, {@code callback} will be called at
     * the end passing @{code null} if operation was successful or error message if it failed.
     */
    public void initializeInspector(
            @NonNull String dexPath, long nativePtr, @NonNull Consumer<String> callback) {
        mInspectorExecutor.execute(
                () -> {
                    String maybeErrorMsg =
                            mInspectorContext.initializeInspector(dexPath, nativePtr);
                    callback.accept(maybeErrorMsg);
                });
    }

    public String getProject() {
        return mInspectorContext.getProject();
    }

    public void sendCommand(int commandId, @NonNull byte[] rawCommand) {
        mInspectorExecutor.execute(() -> mInspectorContext.sendCommand(commandId, rawCommand));
    }

    public void cancelCommand(int commandId) {
        mInspectorExecutor.execute(() -> mInspectorContext.cancelCommand(commandId));
    }

    public void disposeInspector() {
        mInspectorExecutor.execute(mInspectorContext::disposeInspector);
    }

    public static InspectorBridge create(
            @NonNull String inspectorId,
            @NonNull String project,
            @NonNull CrashListener crashListener) {
        InspectorContext context = new InspectorContext(inspectorId, project, crashListener);
        return new InspectorBridge(context);
    }
}
