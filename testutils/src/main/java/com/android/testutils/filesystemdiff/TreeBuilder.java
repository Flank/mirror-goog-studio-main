/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.testutils.filesystemdiff;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class TreeBuilder {
    public static FileSystemEntry buildFromFileSystem(Path rootPath) {
        FileSystemEntry root = mapPath(rootPath);
        if (root instanceof DirectoryEntry) {
            DirectoryEntry dirEntry = (DirectoryEntry) root;

            ForkJoinPool pool = new ForkJoinPool();
            pool.invoke(new TraverseDirectoryTask(dirEntry));
        }
        return root;
    }

    public static class TraverseDirectoryTask extends RecursiveTask<DirectoryEntry> {
        private DirectoryEntry mRoot;

        public TraverseDirectoryTask(DirectoryEntry root) {
            mRoot = root;
        }

        @Override
        protected DirectoryEntry compute() {
            try {
                try (DirectoryStream<Path> childPaths = Files.newDirectoryStream(mRoot.getPath())) {
                    List<TraverseDirectoryTask> tasks = new ArrayList<>();

                    for (Path childPath : childPaths) {
                        FileSystemEntry child = mapPath(childPath);
                        if (child instanceof DirectoryEntry) {
                            tasks.add(new TraverseDirectoryTask((DirectoryEntry)child));
                        } else {
                            mRoot.addChildEntry(child);
                        }
                    }

                    invokeAll(tasks);
                    tasks.forEach(x -> mRoot.addChildEntry(x.mRoot));
                }

                return mRoot;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static FileSystemEntry buildFromSymbolicLinkDefinitions(Path rootPath,
            Iterable<SymbolicLinkDefinition> symbolicLinks) {
        DirectoryEntry root = new DirectoryEntry(rootPath);

        Map<Path, DirectoryEntry> directories = new HashMap<>();
        directories.put(root.getPath(), root);

        for (SymbolicLinkDefinition link : symbolicLinks) {
            FileSystemEntry childEntry = new SymbolicLinkEntry(link.getPath(), link.getTarget());
            DirectoryEntry parentEntry = getOrCreateDirectoryEntry(root, directories,
                    link.getPath().getParent());
            parentEntry.addChildEntry(childEntry);
        }

        return root;
    }

    private static DirectoryEntry getOrCreateDirectoryEntry(
            DirectoryEntry root, Map<Path, DirectoryEntry> directories, Path directoryPath) {
        if (directoryPath == null) {
            return root;
        }

        DirectoryEntry entry = directories.get(directoryPath);
        if (entry != null) {
            return entry;
        }

        entry = new DirectoryEntry(directoryPath);
        directories.put(directoryPath, entry);

        DirectoryEntry parentEntry =
                getOrCreateDirectoryEntry(root, directories, directoryPath.getParent());
        parentEntry.addChildEntry(entry);
        return entry;
    }


    private static FileSystemEntry mapPath(Path path) {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (attributes.isSymbolicLink()) {
            try {
                return new SymbolicLinkEntry(path, Files.readSymbolicLink(path));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (attributes.isDirectory()) {
            return new DirectoryEntry(path);
        } else {
            return new FileEntry(path);
        }
    }
}
