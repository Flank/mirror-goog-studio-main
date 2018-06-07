/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.profiler.support.event;


/**
 * Wrapper class for InputConnect. This class accepts the currently set InputConnection and fowards
 * calls to it. Upon setComposingText, sendKeyEvent, and commitText the class will in addition call
 * to sendKeyboardEvent via RPC to perfd.
 */
public class InputConnectionWrapper extends android.view.inputmethod.InputConnectionWrapper {

    private native void sendKeyboardEvent(String keycode);

    public InputConnectionWrapper() {
        // Setting mutable to true so the EventProfiler can reset / null out the InputConnection
        // when input is no longer active. This prevents an InputConnection object leak.
        super(null, true);
    }

    @Override
    public boolean setComposingText(CharSequence charSequence, int i) {
        sendKeyboardEvent(charSequence.toString());
        return super.setComposingText(charSequence, i);
    }

    @Override
    public boolean commitText(CharSequence charSequence, int i) {
        sendKeyboardEvent(charSequence.toString());
        return super.commitText(charSequence, i);
    }

    @Override
    public void closeConnection() {
        super.closeConnection();
        setTarget(null);
    }
}
