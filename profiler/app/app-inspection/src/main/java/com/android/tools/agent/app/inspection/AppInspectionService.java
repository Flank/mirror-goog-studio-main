/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.agent.app.inspection;

/** This service controlls all app inspectors */
@SuppressWarnings("unused") // invoked via jni
public class AppInspectionService {
    private static AppInspectionService sInstance;

    public static AppInspectionService instance() {
        if (sInstance == null) {
            sInstance = new AppInspectionService();
        }
        return sInstance;
    }

    private AppInspectionService() {}

    @SuppressWarnings("unused") // invoked via jni
    public void onCommandStub() {
        sendServiceResponseStub();
    }

    private native void sendServiceResponseStub();
}
