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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;
import java.util.ArrayList;
import java.util.List;

public class PatchSet {
    enum Status {
        Ok, // PatchSet is valid
        NoChanges, // The previous and current apk lists are identical.
        Invalid, // An unknown error occurred during generation of the patches.
        SizeThresholdExceeded // The patchSet would have been bigger than MAX_PATCHSET_SIZE.
    }

    public static final PatchSet NO_CHANGES = new PatchSet(Status.NoChanges);
    public static final PatchSet SIZE_THRESHOLD_EXCEEDED =
            new PatchSet(Status.SizeThresholdExceeded);
    public static final PatchSet INVALID = new PatchSet(Status.Invalid);

    private final Status status;
    private final List<Deploy.PatchInstruction> patches;

    private PatchSet(Status status) {
        this.status = status;
        this.patches = new ArrayList<>();
    }

    public PatchSet(List<Deploy.PatchInstruction> patches) {
        this.status = Status.Ok;
        this.patches = patches;
    }

    public Status getStatus() {
        return status;
    }

    public List<Deploy.PatchInstruction> getPatches() {
        return patches;
    }
}
