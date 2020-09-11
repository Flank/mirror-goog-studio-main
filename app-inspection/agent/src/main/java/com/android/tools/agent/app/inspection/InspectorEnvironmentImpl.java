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

package com.android.tools.agent.app.inspection;

import androidx.annotation.NonNull;
import androidx.inspection.ArtTooling;
import androidx.inspection.InspectorEnvironment;
import androidx.inspection.InspectorExecutors;

class InspectorEnvironmentImpl implements InspectorEnvironment {
    private final InspectorExecutors mExecutors;

    private final ArtTooling mArtTooling;

    InspectorEnvironmentImpl(
            long mAppInspectionServicePtr,
            @NonNull String inspectorId,
            @NonNull InspectorExecutors executors) {
        mArtTooling = new ArtToolingImpl(mAppInspectionServicePtr, inspectorId);
        mExecutors = executors;
    }

    @NonNull
    @Override
    public InspectorExecutors executors() {
        return mExecutors;
    }

    @NonNull
    @Override
    public ArtTooling artTooling() {
        return mArtTooling;
    }
}

