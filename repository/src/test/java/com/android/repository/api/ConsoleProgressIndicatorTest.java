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
package com.android.repository.api;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Test;

/** Tests for {@link ConsoleProgressIndicator} */
public class ConsoleProgressIndicatorTest {
    @Test
    public void everything() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ConsoleProgressIndicator progressIndicator =
                new ConsoleProgressIndicator(new PrintStream(out), new PrintStream(err));

        progressIndicator.setText("foo");
        progressIndicator.setFraction(0.1);
        progressIndicator.setText("bar");
        progressIndicator.setFraction(0.2);
        progressIndicator.logInfo("Info msg");
        progressIndicator.setText("baz");
        progressIndicator.setFraction(0.3);
        progressIndicator.setSecondaryText("second");
        progressIndicator.logWarning("warning");
        progressIndicator.setText("");
        progressIndicator.setSecondaryText("");
        progressIndicator.setFraction(0.1);
        progressIndicator.logError("error");
        progressIndicator.setFraction(1);

        String expected =
                "foo                                                                             \r"
                        + "[===                                    ] 10% foo                               \r"
                        + "[===                                    ] 10% bar                               \r"
                        + "[=======                                ] 20% bar                               \r"
                        + "                                                                                \r"
                        + "Info: Info msg"
                        + System.lineSeparator()
                        + "[=======                                ] 20% bar                               \r"
                        + "[=======                                ] 20% baz                               \r"
                        + "[===========                            ] 30% baz                               \r"
                        + "[===========                            ] 30% baz second                        \r"
                        + "                                                                                \r"
                        + "[===========                            ] 30% baz second                        \r"
                        + "[===========                            ] 30%  second                           \r"
                        + "[===========                            ] 30%                                   \r"
                        + "[===                                    ] 10%                                   \r"
                        + "                                                                                \r"
                        + "[===                                    ] 10%                                   \r"
                        + "[=======================================] 100%                                  \r";
        assertEquals(expected, out.toString());
        assertEquals(
                "Warning: warning"
                        + System.lineSeparator()
                        + "Error: error"
                        + System.lineSeparator(),
                err.toString());
    }
}
