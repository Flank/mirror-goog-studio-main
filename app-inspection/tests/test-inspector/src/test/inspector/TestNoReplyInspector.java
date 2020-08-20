/*
 * Copyright (C) 2020 The Android Open Source Project
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
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import test.inspector.api.NoReplyInspectorApi;
import test.inspector.api.TestInspectorApi;

/**
 * A broken inspector that "forgets" to call {@link CommandCallback#reply(byte[])}
 *
 * <p>In this case, app inspection should automatically send an error.
 */
public class TestNoReplyInspector extends Inspector {
    public TestNoReplyInspector(@NonNull Connection connection) {
        super(connection);
    }

    @Override
    public void onReceiveCommand(
            @NonNull byte[] bytes, @NonNull final CommandCallback commandCallback) {
        for (NoReplyInspectorApi.Command command : NoReplyInspectorApi.Command.values()) {
            if (Arrays.equals(command.toByteArray(), bytes)) {
                switch (command) {
                    case LOG_AND_NO_REPLY:
                        System.out.println("Command received");
                        // commandCallback.reply(...) would normally be called here
                        break;
                    case RUN_GC:
                        System.out.println("Collecting garbage");
                        Reference<Object> dummyReference = new WeakReference<>(new Object());
                        while (dummyReference.get() != null) {
                            Runtime.getRuntime().gc();
                        }
                        System.out.println("Garbage collected");
                        commandCallback.reply(TestInspectorApi.Reply.SUCCESS.toByteArray());
                        break;
                }
                break;
            }
        }
    }
}
