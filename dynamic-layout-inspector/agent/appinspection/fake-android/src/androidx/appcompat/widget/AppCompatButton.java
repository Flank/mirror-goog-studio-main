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

package androidx.appcompat.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.Button;
import androidx.annotation.NonNull;

public class AppCompatButton extends Button {
    private final ColorStateList mSupportBackgroundTint;

    public AppCompatButton(
            Context context, CharSequence text, int backgroundColor, int supportBackgroundColor) {
        super(context, text, backgroundColor);
        mSupportBackgroundTint = ColorStateList.valueOf(supportBackgroundColor);
    }

    @NonNull
    public ColorStateList getSupportBackgroundTint() {
        return mSupportBackgroundTint;
    }
}
