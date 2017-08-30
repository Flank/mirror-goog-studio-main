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

package com.android.builder.core;

import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.process.JavaProcessInfo;
import com.android.ide.common.process.ProcessException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DesugarProcessBuilderTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testTooManyPathArgs() throws IOException, ProcessException {
        int INPUTS = DesugarProcessBuilder.MAX_PATH_ARGS_FOR_WINDOWS;
        Map<Path, Path> inToOut = new HashMap<>(INPUTS);
        List<Path> classpath = new ArrayList<>(INPUTS);
        List<Path> bootClasspath = new ArrayList<>(INPUTS);
        for (int i = 0; i < INPUTS; i++) {
            inToOut.put(Paths.get(Integer.toString(i)), Paths.get(Integer.toString(i)));
            classpath.add(Paths.get(Integer.toString(i)));
            bootClasspath.add(Paths.get(Integer.toString(i)));
        }

        DesugarProcessBuilder builder =
                new DesugarProcessBuilder(
                        Paths.get(""),
                        false,
                        inToOut,
                        classpath,
                        bootClasspath,
                        10,
                        tmp.getRoot().toPath());
        JavaProcessInfo windowsProc = builder.build(true);
        assertThat(windowsProc.getArgs()).hasSize(1);

        JavaProcessInfo nonWinProc = builder.build(false);
        assertThat(nonWinProc.getArgs()).hasSize(INPUTS * 8 + 4);
    }

    @Test
    public void testFewPathArgsOnWindows() throws IOException, ProcessException {
        int INPUTS = DesugarProcessBuilder.MAX_PATH_ARGS_FOR_WINDOWS / 4;
        Map<Path, Path> inToOut = new HashMap<>(INPUTS);
        List<Path> classpath = new ArrayList<>(INPUTS);
        List<Path> bootClasspath = new ArrayList<>(INPUTS);
        for (int i = 0; i < INPUTS; i++) {
            inToOut.put(Paths.get(Integer.toString(i)), Paths.get(Integer.toString(i)));
            classpath.add(Paths.get(Integer.toString(i)));
            bootClasspath.add(Paths.get(Integer.toString(i)));
        }

        DesugarProcessBuilder builder =
                new DesugarProcessBuilder(
                        Paths.get(""),
                        false,
                        inToOut,
                        classpath,
                        bootClasspath,
                        10,
                        tmp.getRoot().toPath());
        JavaProcessInfo info = builder.build(true);
        assertThat(info.getArgs()).hasSize(INPUTS * 8 + 4);
    }
}
