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

package com.activity;

/**
 * A minimal activity useful for verifying basic transport test framework functionality.
 *
 * <p>A host-side test should trigger the [.printName] function remotely and check that the class's
 * name was echoed to the logs.
 */
@SuppressWarnings("unused") // Class accessed by reflection
public final class SimpleActivity extends TransportTestActivity {
    public SimpleActivity() {
        super("SimpleActivity");
    }

    public void printName() {
        System.out.println(getLocalClassName());
    }
}
