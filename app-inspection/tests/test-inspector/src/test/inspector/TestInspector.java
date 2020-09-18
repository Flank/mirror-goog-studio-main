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
import androidx.annotation.Nullable;
import androidx.inspection.Connection;
import androidx.inspection.Inspector;
import androidx.inspection.InspectorEnvironment;
import java.util.Arrays;
import test.inspector.api.TestInspectorApi;

/**
 * A basic inspector, but also a base class for other test inspectors.
 *
 * <p>It logs when certain events happen, which tests can assert against, but otherwise no-ops.
 * Various {@code handle} methods are exposed to child classes that want to override any behavior.
 */
public class TestInspector extends Inspector {
    @Nullable protected final InspectorEnvironment environment;

    TestInspector(@NonNull Connection connection) {
        this(connection, null);
    }

    TestInspector(@NonNull Connection connection, @Nullable InspectorEnvironment environment) {
        super(connection);
        this.environment = environment;
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
        commandCallback.reply(handleReceiveCommand(bytes));
    }

    /**
     * Child classes can override to handle a passed in a byte array command.
     *
     * <p>Overrides should feel free to call {@code return super.handleReceiveCommand(...)} at the
     * end of their method if they don't care about the return value.
     */
    @NonNull
    protected byte[] handleReceiveCommand(@NonNull byte[] bytes) {
        return TestInspectorApi.Reply.SUCCESS.toByteArray();
    }

    /**
     * A helper method which replaces {@link Class#forName(String)} as a workaround for that method
     * not working in tests.
     */
    @SuppressWarnings("rawtypes")
    protected Class<?> forName(@NonNull String className) throws ClassNotFoundException {
        // NOTE: Normally, a user would call Class.forName, which works on Android, but isn't
        // currently working in our test environment. If we can fix it, we can delete this method
        // and replace calls to it with Class.forName directly.
        if (environment == null) {
            throw new IllegalStateException(
                    "To use forName, construct TestInspector with an InspectorEnvironment");
        }

        Class<?> foundInstance = null;
        for (Class instance : environment.artTooling().findInstances(Class.class)) {
            if (instance != null && instance.getName().equals(className)) {
                if (foundInstance == null) {
                    foundInstance = instance;
                } else {
                    throw new IllegalStateException("Found multiple instances of " + className);
                }
            }
        }

        if (foundInstance != null) {
            return foundInstance;
        } else {
            throw new ClassNotFoundException();
        }
    }
}
