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
package com.android.layoutinspector;

import com.android.layoutinspector.model.ClientWindow;
import com.android.layoutinspector.model.ViewNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.TimeUnit;

public class LayoutInspectorBridge {
    public static LayoutInspectorResult captureView(
            ClientWindow window, LayoutInspectorCaptureOptions options) {
        byte[] hierarchy = window.loadWindowData(20, TimeUnit.SECONDS);
        if (hierarchy == null) {
            return new LayoutInspectorResult(null, "Unexpected error: empty view hierarchy");
        }

        ViewNode root = ViewNode.parseFlatString(hierarchy);

        if (root == null) {
            return new LayoutInspectorResult(null, "Unable to parse view hierarchy");
        }

        //  Get the preview of the root node
        byte[] preview = window.loadViewImage(root, 10, TimeUnit.SECONDS);
        if (preview == null) {
            return new LayoutInspectorResult(null, "Unable to obtain preview image");
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
        ObjectOutputStream output = null;

        try {
            output = new ObjectOutputStream(bytes);
            output.writeUTF(options.toString());

            output.writeInt(hierarchy.length);
            output.write(hierarchy);

            output.writeInt(preview.length);
            output.write(preview);
        } catch (IOException e) {
            return new LayoutInspectorResult(
                    null, "Unexpected error while saving hierarchy snapshot: " + e);
        } finally {
            try {
                if (output != null) {
                    output.close();
                }
            } catch (IOException e) {
                return new LayoutInspectorResult(
                        null, "Unexpected error while closing hierarchy snapshot: " + e);
            }
        }

        return new LayoutInspectorResult(bytes.toByteArray(), "");
    }
}
