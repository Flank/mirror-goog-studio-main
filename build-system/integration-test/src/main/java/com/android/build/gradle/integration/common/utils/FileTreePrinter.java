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

package com.android.build.gradle.integration.common.utils;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class FileTreePrinter extends TestWatcher {

    @Override
    protected void failed(Throwable failure, Description description) {
        for (Path repo : GradleTestProject.getLocalRepositories()) {
            System.out.println("--- Local Repo " + repo.toString());
            try {
                Files.walkFileTree(repo, new PrintFileTree(System.out));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            System.out.println("---");
        }
    }

    private static final class PrintFileTree extends SimpleFileVisitor<Path> {
        private final PrintStream out;
        private int depth = 0;

        public PrintFileTree(PrintStream out) {
            this.out = out;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
            printName(dir);
            depth++;
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            printName(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            depth--;
            return FileVisitResult.CONTINUE;
        }

        private void printName(Path path) {
            for (int i = 0; i < depth; i++) {
                System.out.print(' ');
            }
            out.println(path.getFileName().toString());
        }
    }
}
