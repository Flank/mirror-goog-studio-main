/*
 * Copyright (C) 2018 The Android Open Source Project
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
package app;

public class ClinitTarget {

    // The original clinit sets the counter to 2. However, since it was never
    // loaded before the swap, the counter should never get past 1.
    static {
        TestActivity.incrementCounter();
        TestActivity.incrementCounter();
    }

    public String getStatus() {
        return "ClinitTarget NOT SWAPPED";
    }
}
