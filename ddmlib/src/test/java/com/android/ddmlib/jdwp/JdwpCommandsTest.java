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
package com.android.ddmlib.jdwp;

import static com.google.common.truth.Truth.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import junit.framework.TestCase;

public class JdwpCommandsTest extends TestCase {

    public void testCommandSetToString() throws Exception {
        List<Field> commandSetFields =
                getStaticFields()
                        .filter(field -> field.getName().startsWith("SET_"))
                        .collect(Collectors.toList());

        // Just to make sure enumerating fields did work as expected
        assertThat(commandSetFields.size()).isGreaterThan(10);

        // Known command sets
        for (Field field : commandSetFields) {
            String value = JdwpCommands.commandSetToString(field.getInt(null));
            assertThat(value).isEqualTo(field.getName());
        }

        // Unknown command sets
        assertThat(JdwpCommands.commandSetToString(233)).isEqualTo("SET_E9");
        assertThat(JdwpCommands.commandSetToString(2333)).isEqualTo("SET_91D");
    }

    public void testCommandToString() throws Exception {
        List<Field> commandFields =
                getStaticFields()
                        .filter(field -> field.getName().startsWith("CMD_"))
                        .collect(Collectors.toList());

        // Just to make sure enumerating fields did work as expected
        assertThat(commandFields.size()).isGreaterThan(50);

        // Known commands
        for (Field commandField : commandFields) {
            Field commandSetField = getCommandSetFieldFromCommandField(commandField);
            assertThat(commandSetField).isNotNull();

            String commandString =
                    JdwpCommands.commandToString(
                            commandSetField.getInt(null), commandField.getInt(null));
            assertThat(commandString).isEqualTo(commandField.getName());
        }

        // Unknown commands
        assertThat(JdwpCommands.commandToString(233, 1)).isEqualTo("CMD_E9_01");
        assertThat(JdwpCommands.commandToString(2333, 5)).isEqualTo("CMD_91D_05");
    }

    private static Field getCommandSetFieldFromCommandField(Field commandField) {
        // Commands constants are formatted as "CMD_" + command set + "_" + command
        int start = commandField.getName().indexOf('_');
        assertThat(start).isGreaterThan(0);

        int end = commandField.getName().indexOf('_', start + 1);
        assertThat(end).isGreaterThan(start);

        String commandSet = "SET_" + commandField.getName().substring(start + 1, end);
        return getStaticFields()
                .filter(field -> field.getName().equals(commandSet))
                .findFirst()
                .orElse(null);
    }

    private static Stream<Field> getStaticFields() {
        return Arrays.stream(JdwpCommands.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()));
    }
}
