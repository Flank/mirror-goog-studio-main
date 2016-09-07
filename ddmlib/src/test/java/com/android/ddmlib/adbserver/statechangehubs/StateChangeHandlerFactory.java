/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.ddmlib.adbserver.statechangehubs;

/**
 * Base factory interface that defines handler results.
 */
public interface StateChangeHandlerFactory {

    class HandlerResult {

        // Decide if the executing thread should terminate the connection and exit the handler
        // task.
        public boolean mShouldContinue = true;

        public HandlerResult(boolean shouldContinue) {
            mShouldContinue = shouldContinue;
        }
    }
}
