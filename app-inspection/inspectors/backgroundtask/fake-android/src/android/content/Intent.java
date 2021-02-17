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

package android.content;

@SuppressWarnings("MethodMayBeStatic")
public class Intent {
    public static String EXTRA_ALARM_COUNT = "";

    public int getIntExtra(String name, int defaultValue) {
        throw new RuntimeException("");
    }

    public boolean filterEquals(Intent other) {
        throw new RuntimeException("");
    }

    public int filterHashCode() {
        throw new RuntimeException("");
    }
}
