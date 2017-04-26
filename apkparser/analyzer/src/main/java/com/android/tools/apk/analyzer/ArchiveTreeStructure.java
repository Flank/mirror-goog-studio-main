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

package com.android.tools.apk.analyzer;

import com.android.annotations.NonNull;
import com.android.tools.apk.analyzer.internal.ArchiveTreeNode;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import javax.swing.tree.MutableTreeNode;

public class ArchiveTreeStructure {

    //this is a list of extensions we're likely to find inside APK files (or AIA bundles or AARs)
    //that are to be treated as internal archives that are browsable in APK analyzer
    private static final List<String> INNER_ZIP_EXTENSIONS =
            ImmutableList.of(".zip", ".apk", ".jar");

    private static final FileVisitor<Path> fileVisitor =
            new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    dir.toFile().deleteOnExit();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    file.toFile().deleteOnExit();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                        throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                        throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            };

    public static ArchiveNode create(@NonNull Archive archive) throws IOException {
        Path contentRoot = archive.getContentRoot();
        ArchiveTreeNode rootNode = new ArchiveTreeNode(new ArchiveEntry(archive, contentRoot));

        Stack<ArchiveTreeNode> stack = new Stack<>();
        stack.push(rootNode);

        Path tempFolder = null;

        while (!stack.isEmpty()) {
            ArchiveTreeNode node = stack.pop();
            Path path = node.getData().getPath();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path childPath : stream) {
                    ArchiveTreeNode childNode;
                    if (INNER_ZIP_EXTENSIONS
                            .stream()
                            .anyMatch(ext -> childPath.getFileName().toString().endsWith(ext))) {
                        if (tempFolder == null) {
                            tempFolder =
                                    Files.createTempDirectory(
                                            archive.getPath().getFileName().toString());
                        }
                        Path tempFile =
                                tempFolder.resolve(contentRoot.relativize(childPath).toString());
                        Files.createDirectories(tempFile.getParent());
                        Files.copy(childPath, tempFile);
                        Archive tempArchive = Archives.open(tempFile);
                        ArchiveTreeNode newArchiveNode = (ArchiveTreeNode) create(tempArchive);
                        childNode = new ArchiveTreeNode(new ArchiveEntry(tempArchive, childPath));
                        for (ArchiveNode archiveNodeChild : newArchiveNode.getChildren()) {
                            childNode.add((MutableTreeNode) archiveNodeChild);
                        }
                    } else {
                        childNode = new ArchiveTreeNode(new ArchiveEntry(archive, childPath));
                        if (Files.isDirectory(childPath)) {
                            stack.push(childNode);
                        }
                    }

                    node.add(childNode);
                }
            }
        }

        if (tempFolder != null) {
            Files.walkFileTree(tempFolder, fileVisitor);
        }

        return rootNode;
    }

    public static void updateRawFileSizes(
            @NonNull ArchiveNode root, @NonNull ApkSizeCalculator calculator) {
        Map<String, Long> rawFileSizes =
                calculator.getRawSizePerFile(root.getData().getArchive().getPath());

        // first set the file sizes for all child nodes
        ArchiveTreeStream.preOrderStream(root)
                .forEach(
                        node -> {
                            ArchiveEntry data = node.getData();
                            if (node != root
                                    && data.getPath().getFileName() != null
                                    && INNER_ZIP_EXTENSIONS
                                            .stream()
                                            .anyMatch(
                                                    ext ->
                                                            data.getPath()
                                                                    .getFileName()
                                                                    .toString()
                                                                    .endsWith(ext))) {
                                updateRawFileSizes(node, calculator);
                            } else {
                                Long rawFileSize = rawFileSizes.get(data.getPath().toString());
                                if (rawFileSize != null) {
                                    data.setRawFileSize(rawFileSize);
                                }
                            }
                        });

        // now set the directory entries to be the size of all their children
        ArchiveTreeStream.postOrderStream(root)
                .forEach(
                        node -> {
                            ArchiveEntry data = node.getData();
                            if (data.getRawFileSize() < 0 && node.getChildCount() > 0) {
                                Long sizeOfAllChildren =
                                        node.getChildren()
                                                .stream()
                                                .map(n -> n.getData().getRawFileSize())
                                                .reduce(0L, Long::sum);
                                data.setRawFileSize(sizeOfAllChildren);
                            }
                        });
    }

    public static void updateDownloadFileSizes(
            @NonNull ArchiveNode root, @NonNull ApkSizeCalculator calculator) {
        Map<String, Long> downloadFileSizes =
                calculator.getDownloadSizePerFile(root.getData().getArchive().getPath());

        // first set the file sizes for all child nodes
        ArchiveTreeStream.preOrderStream(root)
                .forEach(
                        node -> {
                            ArchiveEntry data = node.getData();
                            if (node != root
                                    && data.getPath().getFileName() != null
                                    && INNER_ZIP_EXTENSIONS
                                            .stream()
                                            .anyMatch(
                                                    ext ->
                                                            data.getPath()
                                                                    .getFileName()
                                                                    .toString()
                                                                    .endsWith(ext))) {
                                updateDownloadFileSizes(node, calculator);
                            } else {
                                Long downloadFileSize =
                                        downloadFileSizes.get(data.getPath().toString());
                                if (downloadFileSize != null) {
                                    data.setDownloadFileSize(downloadFileSize);
                                }
                            }
                        });

        // now set the directory entries to be the size of all their children
        ArchiveTreeStream.postOrderStream(root)
                .forEach(
                        node -> {
                            ArchiveEntry data = node.getData();
                            if (data.getDownloadFileSize() < 0 && node.getChildCount() > 0) {
                                Long sizeOfAllChildren =
                                        node.getChildren()
                                                .stream()
                                                .map(n -> n.getData().getDownloadFileSize())
                                                .reduce(0L, Long::sum);
                                data.setDownloadFileSize(sizeOfAllChildren);
                            }
                        });
    }

    public static void sort(
            @NonNull ArchiveNode root, @NonNull Comparator<ArchiveNode> comparator) {
        assert root instanceof ArchiveTreeNode;
        sort((ArchiveTreeNode) root, comparator);
    }

    private static void sort(
            @NonNull ArchiveTreeNode root, @NonNull Comparator<ArchiveNode> comparator) {
        List<ArchiveNode> children = new ArrayList<>(root.getChildren());
        children.sort(comparator);

        root.removeAllChildren();
        for (ArchiveNode child : children) {
            root.add((ArchiveTreeNode) child);
            sort(child, comparator);
        }
    }
}
