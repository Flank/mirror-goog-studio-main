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

package test.inspector;

import androidx.annotation.NonNull;
import androidx.inspection.Connection;

/**
 * An inspector that replies to any bytes it receives in {@link #onReceiveCommand(byte[],
 * CommandCallback)} with an event containing those bytes except in reverse.
 */
public final class ReverseEchoInspector extends TestInspector {

    ReverseEchoInspector(@NonNull Connection connection) {
        super(connection);
    }

    @NonNull
    @Override
    protected byte[] handleReceiveCommand(@NonNull byte[] bytes) {
        byte[] reversed = new byte[bytes.length];
        for (int i = 0; i < bytes.length; ++i) {
            reversed[i] = bytes[bytes.length - i - 1];
        }
        getConnection().sendEvent(reversed);
        return super.handleReceiveCommand(bytes);
    }
}
