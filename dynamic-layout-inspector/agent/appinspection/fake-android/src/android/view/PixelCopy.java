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

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import androidx.annotation.NonNull;

public class PixelCopy {
    public interface OnPixelCopyFinishedListener {
        void onPixelCopyFinished(int copyResult);
    }

    public static final int SUCCESS = 0;

    public static void request(
            @NonNull Surface source,
            @NonNull Rect srcRect,
            @NonNull Bitmap dest,
            @NonNull OnPixelCopyFinishedListener listener,
            @NonNull Handler listenerThread) {
        System.arraycopy(source.bitmapBytes, 0, dest.bytes, 0, source.bitmapBytes.length);
        listener.onPixelCopyFinished(0);
    }
}
