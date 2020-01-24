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
import test.inspector.api.TestInspectorApi;

/**
 * A basic inspector, but also a base class for other test inspectors.
 *
 * <p>It logs when certain events happen, which tests can assert against, but otherwise no-ops.
 * Various {@code handle} methods are exposed to child classes that want to override any behavior.
 */
public class TestInspector extends Inspector {

    TestInspector(@NonNull Connection connection) {
        super(connection);
        System.out.println("TEST INSPECTOR CREATED");
    }

    @Override
    public final void onDispose() {
        handleDispose();
        System.out.println("TEST INSPECTOR DISPOSED");
    }

    protected void handleDispose() {}

    @Override
    public final void onReceiveCommand(
            @NonNull byte[] bytes, @NonNull Inspector.CommandCallback commandCallback) {
        System.out.println("TEST INSPECTOR COMMAND: " + Arrays.toString(bytes));
        handleReceiveCommand(bytes);
        commandCallback.reply(TestInspectorApi.Reply.SUCCESS.toByteArray());
    }

    protected void handleReceiveCommand(@NonNull byte[] bytes) {}
}
