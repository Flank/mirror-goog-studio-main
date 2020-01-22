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
import androidx.inspection.Inspector;
import java.util.Arrays;

/**
 * Basic inspector which prints the bytes it receives to the console and then sends back a unique
 * (but otherwise arbitrary) byte array as a response.
 */
public class TestInspector extends Inspector {

    TestInspector(@NonNull Connection connection) {
        super(connection);
        System.out.println("TEST INSPECTOR CREATED");
    }

    @Override
    public void onDispose() {
        System.out.println("TEST INSPECTOR DISPOSED");
    }

    @Override
    public void onReceiveCommand(
            @NonNull byte[] bytes, @NonNull Inspector.CommandCallback commandCallback) {
        System.out.println("TEST INSPECTOR " + Arrays.toString(bytes));
        getConnection().sendEvent(new byte[] {8, 92, 43});
        commandCallback.reply(bytes);
    }
}
