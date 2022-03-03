/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.view;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

public class ViewTreeObserver {
    @Nullable private Runnable callback = null;

    public void registerFrameCommitCallback(@NonNull Runnable callback) {
        this.callback = callback;
    }

    public boolean unregisterFrameCommitCallback(@NonNull Runnable callback) {
        if (this.callback == callback) {
            this.callback = null;
            return true;
        }
        return false;
    }

    @VisibleForTesting // Test only
    public void fireFrameCommit() {
        if (callback != null) {
            callback.run();
        }
    }
}
