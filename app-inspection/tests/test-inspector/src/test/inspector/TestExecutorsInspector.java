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
import androidx.inspection.InspectorEnvironment;
import java.util.Arrays;
import test.inspector.api.TestExecutorsApi;
import test.inspector.api.TestExecutorsApi.Command;

public class TestExecutorsInspector extends TestInspector {
    TestExecutorsInspector(
            @NonNull Connection connection, @NonNull InspectorEnvironment environment) {
        super(connection, environment);
    }

    @NonNull
    @Override
    protected byte[] handleReceiveCommand(@NonNull byte[] bytes) {
        for (Command command : Command.values()) {
            if (Arrays.equals(command.toByteArray(), bytes)) {
                switch (command) {
                    case COMPLETE_ON_PRIMARY_EXECUTOR:
                        environment.executors().primary().execute(new CompleteRunnable());
                        break;
                    case FAIL_ON_PRIMARY_EXECUTOR:
                        environment.executors().primary().execute(new CrashRunnable());
                        break;
                    case COMPLETE_ON_HANDLER:
                        environment.executors().handler().post(new CompleteRunnable());
                        break;
                    case FAIL_ON_HANDLER:
                        environment.executors().handler().post(new CrashRunnable());
                        break;
                    case COMPLETE_ON_IO:
                        environment.executors().io().execute(new CompleteRunnable());
                        break;
                    case FAIL_ON_IO:
                        environment.executors().io().execute(new CrashRunnable());
                        break;
                }
                break;
            }
        }
        return new byte[0];
    }

    private class CompleteRunnable implements Runnable {
        @Override
        public void run() {
            getConnection().sendEvent(TestExecutorsApi.Event.COMPLETED.toByteArray());
        }
    }

    private static class CrashRunnable implements Runnable {
        @Override
        public void run() {
            throw new RuntimeException("This is an inspector exception.");
        }
    }
}
