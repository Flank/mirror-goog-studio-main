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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
     * A helper method which wraps {@link InspectorEnvironment#findInstances(Class)} and lets you
     * search by class name instead.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected List<Class<?>> findInstances(@NonNull String className) {
        // NOTE: This method is quite a hack in its current state, although at least it's isolated
        // to test code only. On Android, Class.forName(className) works,  but for some reason,
        // in our fake environment, it does not. We work around this (until we can think of a
        // better way) by searching for ALL instances of Class, and then narrowing it down to the
        // instances which match the passed in className. It works for now, especially for test
        // code, but it would be nice if we can find a more correct way using ClassLoaders later.
        // If we could get Class.forName working, then we should just delete this function and
        // call environment.findInstances(Class.forName(...)) directly.

        if (environment == null) {
            throw new IllegalStateException(
                    "To use findInstances, construct TestInspector with an InspectorEnvironment");
        }
        List<Class> allClasses = environment.findInstances(Class.class);

        List<Class<?>> matchingClasses = new ArrayList<>();
        for (Class aClass : allClasses) {
            if (aClass != null && aClass.getName().equals(className)) {
                matchingClasses.add(aClass);
            }
        }

        List<Class<?>> instances = new ArrayList<>();
        for (Class<?> matchingClass : matchingClasses) {
            // For some reason we don't yet understand, we find two instances of many Class
            // entries, where one of them is not the real one. For now, we just iterate through all
            // of them, as only one actually finds anything.
            instances.addAll((List<Class<?>>) environment.findInstances(matchingClass));
        }
        return instances;
    }
}
