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

package android.view;

import android.graphics.HardwareRenderer;
import android.os.Handler;
import android.widget.RootLinearLayout;
import androidx.annotation.NonNull;

/**
 * This class is included for testing of LayoutInspectorService.
 *
 * <p>The class SkiaQWorkaround will look for a "mAttachInfo" field on the root view that the
 * LayoutInspectorService is gathering information about. SkiaQWorkaround will then look for the 2
 * fields mHandler and mThreadedRenderer.
 *
 * <p>This class is used in connection with the made up layout {@link RootLinearLayout}.
 */
public class AttachInfo {
    private final Handler mHandler;
    private final HardwareRenderer mThreadedRenderer;

    public AttachInfo(@NonNull Handler handler, @NonNull HardwareRenderer renderer) {
        mHandler = handler;
        mThreadedRenderer = renderer;
    }
}
