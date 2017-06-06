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

package com.android.tools.aapt2;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.google.common.io.Closer;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MultipleLoadTest {

    private static final int CLASSLOADER_COUNT = 5;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void compilePng() throws Exception {
        URL[] urls = ((URLClassLoader) Aapt2Jni.class.getClassLoader()).getURLs();
        try (Closer closer = Closer.create()) {
            List<Method> methods = new ArrayList<>(CLASSLOADER_COUNT);
            Deque<Thread> threads = new ArrayDeque<>(CLASSLOADER_COUNT * 2);
            for (int i = 0; i < CLASSLOADER_COUNT; i++) {
                ClassLoader classLoader =
                        closer.register(new URLClassLoader(urls, System.class.getClassLoader()));
                Method run =
                        classLoader
                                .loadClass(MultipleLoadTest.class.getName())
                                .getDeclaredMethod("run", Path.class);
                methods.add(run);
                Runnable runnable =
                        () -> {
                            try {
                                Path dir = temporaryFolder.newFolder().toPath();
                                run.invoke(null, dir);

                            } catch (IOException
                                    | IllegalAccessException
                                    | InvocationTargetException e) {
                                throw new RuntimeException(e);
                            }
                        };
                threads.addFirst(new Thread(runnable));
                threads.addLast(new Thread(runnable));
            }
            assertThat(methods).containsNoDuplicates();

            for (Thread runnable : threads) {
                runnable.start();
            }
            for (Thread runnable : threads) {
                runnable.join();
            }
        }
    }

    @SuppressWarnings("unused") // Called by reflection above.
    public static void run(@NonNull Path dir) throws Exception {
        Aapt2Jni aapt2Jni =
                new Aapt2Jni(
                        (hashCode, creator) -> {
                            Path cache = dir.resolve("aapt2jni");
                            if (Files.exists(cache)) {
                                return cache;
                            }
                            Files.createDirectory(cache);
                            creator.create(cache);
                            return cache;
                        });

        Path drawable = dir.resolve("src").resolve("drawable");
        Path lena = drawable.resolve("lena.png");
        Aapt2TestFiles.writeLenaPng(lena);

        Path out = dir.resolve("out");
        Files.createDirectory(out);

        Aapt2Result result =
                aapt2Jni.compile(
                        Arrays.asList("-o", out.toString(), "--no-crunch", lena.toString()));

        assertEquals(0, result.getReturnCode());

        Path compiled = out.resolve(Aapt2RenamingConventions.compilationRename(lena.toFile()));
        assertThat(Files.isRegularFile(compiled)).isTrue();
    }
}
