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

package android.graphics;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import java.nio.Buffer;

@SuppressWarnings("unused")
public final class Bitmap {

    private final int mWidth;
    private final int mHeight;
    private final Config config;

    @VisibleForTesting public final byte[] bytes;

    private Bitmap(int width, int height, Config config) {
        mWidth = width;
        mHeight = height;
        bytes = new byte[mWidth * mHeight];
        this.config = config;
    }

    public enum Config {
        RGB_565,
        ARGB_8888
    }

    @NonNull
    public static Bitmap createBitmap(int width, int height, Config config) {
        return new Bitmap(width, height, config);
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getByteCount() {
        return bytes.length;
    }

    public Config getConfig() {
        return config;
    }

    public void copyPixelsToBuffer(@NonNull Buffer buffer) {
        // Ignore the inspection, as this call is only used in tests. Leave space for the actual
        // header (BitmapUtils.BITMAP_HEADER_SIZE)
        //noinspection SuspiciousSystemArraycopy
        System.arraycopy(bytes, 0, buffer.array(), 9, bytes.length);
    }
}
