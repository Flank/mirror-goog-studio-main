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

package android.content.res

import android.os.LocaleList
import java.util.Locale

class Configuration(
    locale: Locale = Locale.getDefault(),
    @JvmField var fontScale: Float = 0f,
    @JvmField var mcc: Int = 0,
    @JvmField var mnc: Int = 0,
    @JvmField var screenLayout: Int = 0,
    @JvmField var colorMode: Int = 0,
    @JvmField var touchscreen: Int = 0,
    @JvmField var keyboard: Int = 0,
    @JvmField var keyboardHidden: Int = 0,
    @JvmField var hardKeyboardHidden: Int = 0,
    @JvmField var navigation: Int = 0,
    @JvmField var navigationHidden: Int = 0,
    @JvmField var uiMode: Int = 0,
    @JvmField var smallestScreenWidthDp: Int = 0,
    @JvmField var densityDpi: Int = 0,
    @JvmField var orientation: Int = 0,
    @JvmField var screenWidthDp: Int = 0,
    @JvmField var screenHeightDp: Int = 0,
) {

    val locales = LocaleList(locale)
}
