/*
 * Copyright (C) 2021 The Android Open Source Project
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

import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

public class ViewDebug {
    @SuppressWarnings("unused") // invoked by reflection
    public static AutoCloseable startRenderingCommandsCapture(
            View tree, Executor executor, Callable<OutputStream> output) {
        View.AttachInfo attachInfo = tree.mAttachInfo;
        if (attachInfo == null) {
            throw new IllegalArgumentException("Given view isn't attached");
        }
        attachInfo
                .getRenderer()
                .setPictureCaptureCallback(
                        picture ->
                                executor.execute(
                                        () -> {
                                            try {
                                                output.call().write(picture.getBytes());
                                            } catch (Exception e) {
                                                throw new RuntimeException(e);
                                            }
                                        }));
        return () -> attachInfo.getRenderer().setPictureCaptureCallback(null);
    }
}
