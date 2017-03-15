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

package com.android.builder.dexing;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.PathUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A collector for runtime annotated classes.
 *
 * <p>Provides {@link #collectClasses(Collection)} which given a collection of jars and directories
 * returns the set of classes that should be kept according to the predicate.
 *
 * <p>The predicate to determine whether to keep the class is injected in the constructor.
 *
 * <p>In legacy multidex, by default all classes annotated with a runtime-retention annotation are
 * kept in the main dex, to avoid issues with reflection.
 *
 * <p>See <a href="http://b.android.com/78144">Issue 78144</a>.
 */
public class RuntimeAnnotatedClassCollector {

    @NonNull private final WaitableExecutor<List<String>> waitableExecutor;
    @NonNull private final Predicate<byte[]> keepClassPredicate;

    public RuntimeAnnotatedClassCollector(@NonNull Predicate<byte[]> keepClassPredicate)
            throws InterruptedException {
        this.keepClassPredicate = keepClassPredicate;
        this.waitableExecutor = WaitableExecutor.useGlobalSharedThreadPool();
    }

    @NonNull
    private static List<String> kept(
            @NonNull Path input, @NonNull Predicate<byte[]> keepClassPredicate) throws IOException {
        if (Files.isDirectory(input)) {
            return keptFromDir(input, keepClassPredicate);
        } else if (Files.isRegularFile(input)) {
            return keptFromJar(input, keepClassPredicate);
        }
        throw new IOException("Could not open input file or dir: " + input);
    }

    @NonNull
    private static List<String> keptFromDir(
            @NonNull Path inputDir, @NonNull Predicate<byte[]> keepClassPredicate)
            throws IOException {
        List<String> classes = new ArrayList<>();
        Files.walkFileTree(
                inputDir,
                EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        if (!isClass(file.getFileName().toString())) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (!keepClassPredicate.test(Files.readAllBytes(file))) {
                            return FileVisitResult.CONTINUE;
                        }
                        classes.add(PathUtils.toSystemIndependentPath(inputDir.relativize(file)));
                        return FileVisitResult.CONTINUE;
                    }
                });
        return classes;
    }

    @NonNull
    private static List<String> keptFromJar(
            @NonNull Path inputJar, @NonNull Predicate<byte[]> keepClassPredicate)
            throws IOException {
        List<String> classes = new ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(inputJar))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = entry.getName();
                if (!isClass(name)) {
                    continue;
                }
                if (!keepClassPredicate.test(ByteStreams.toByteArray(zipInputStream))) {
                    continue;
                }
                classes.add(name);
            }
        }
        return classes;
    }

    private static boolean isClass(@NonNull String name) {
        return com.google.common.io.Files.getFileExtension(name)
                .equalsIgnoreCase(SdkConstants.EXT_CLASS);
    }

    /**
     * Returns the set of paths to classes that are kept by the keepClassPredicate.
     *
     * @param inputs jars and directories to scan for .class files.
     * @return set of paths to classes (e.g. {@code com/example/Foo.class}) that match the keep
     *     class predicate
     * @throws RuntimeException if there is an error reading any of the inputs.
     */
    @NonNull
    public Set<String> collectClasses(@NonNull Collection<Path> inputs)
            throws InterruptedException {
        for (Path input : inputs) {
            waitableExecutor.execute(() -> kept(input, keepClassPredicate));
        }
        List<List<String>> result = waitableExecutor.waitForTasksWithQuickFail(true);

        return ImmutableSet.copyOf(
                result.stream().flatMap(Collection::stream).collect(Collectors.toSet()));
    }
}
