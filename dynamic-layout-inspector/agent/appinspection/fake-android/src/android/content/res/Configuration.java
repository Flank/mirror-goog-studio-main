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

package android.content.res;

import android.os.LocaleList;
import java.util.Locale;

public final class Configuration {
    private final LocaleList mLocales = new LocaleList(Locale.getDefault());
    public float fontScale = 0f;
    public int mcc = 0;
    public int mnc = 0;
    public int screenLayout = 0;
    public int colorMode = 0;
    public int touchscreen = 0;
    public int keyboard = 0;
    public int keyboardHidden = 0;
    public int hardKeyboardHidden = 0;
    public int navigation = 0;
    public int navigationHidden = 0;
    public int uiMode = 0;
    public int smallestScreenWidthDp = 0;
    public int densityDpi = 0;
    public int orientation = 0;
    public int screenWidthDp = 0;
    public int screenHeightDp = 0;

    public final LocaleList getLocales() {
        return mLocales;
    }
}
