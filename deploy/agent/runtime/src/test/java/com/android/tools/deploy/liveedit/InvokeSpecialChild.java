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
package com.android.tools.deploy.liveedit;

public class InvokeSpecialChild extends InvokeSpecialParent {

    @Override
    protected int getHash() {
        throw new IllegalStateException("This should never be called");
    }

    @Override
    int getArrayValue(int[] array, int index) {
        throw new IllegalStateException("This should never be called");
    }

    public int callSuperGetHash() {
        return super.getHash();
    }

    public int callgetArrayValue(int[] array, int index) {
        return super.getArrayValue(array, index);
    }
}
