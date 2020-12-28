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

package com.android.tools.agent.layoutinspector.testing;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.graphics.Canvas;
import android.graphics.Picture;
import androidx.annotation.NonNull;

/** This class is included for testing of LayoutInspectorService. */
public class TestCanvasFactory implements Picture.CanvasFactory {

    @Override
    public Canvas createCanvas(@NonNull Picture picture) {
        PictureCanvas canvas = mock(PictureCanvas.class);
        when(canvas.getPicture()).thenReturn(picture);
        return canvas;
    }

    public class PictureCanvas extends Canvas {
        public Picture getPicture() {
            return null;
        }
    }
}
