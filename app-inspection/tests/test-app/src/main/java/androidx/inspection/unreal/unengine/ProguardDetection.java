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

package androidx.inspection.unreal.unengine;

/**
 * Purposely empty, unused class that would be removed by proguard.
 *
 * <p>We use this class to detect if a target app was proguarded or not, because if it was, it
 * likely means we can't reliably inspect it, since library's methods used only by an inspector
 * would get removed. Instead, we'll need to tell users that app inspection isn't available for the
 * current app and that they should rebuild it again without proguarding to continue.
 */
class ProguardDetection {}
