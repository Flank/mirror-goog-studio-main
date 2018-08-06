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
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import javax.swing.tree.MutableTreeNode;

public class ArchiveTreeStructure {

    //this is a list of extensions we're likely to find inside APK files (or AIA bundles or AARs)
    //that are to be treated as internal archives that are browsable in APK analyzer
    private static final List<String> INNER_ZIP_EXTENSIONS =
            ImmutableList.of(".zip", ".apk", ".jar");

    private static final FileVisitor<Path> fileVisitor =
            new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    dir.toFile().deleteOnExit();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    file.toFile().deleteOnExit();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            };

    @NonNull
    public static ArchiveNode create(@NonNull Archive archive) throws IOException {
        return createWorker(archive, "");
    }

    @NonNull
    private static ArchiveNode createWorker(@NonNull Archive archive, @NonNull String pathPrefix)
            throws IOException {
        Path contentRoot = archive.getContentRoot();
        ArchiveTreeNode rootNode =
                new ArchiveTreeNode(new ArchiveEntry(archive, contentRoot, pathPrefix));

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
                        // The entry is a "zip" type entry that need to be extracted into a temporary
                        // directory to allow for recursive extraction if needed, so we extract the
                        // entry into a temp file, and wrap it with a new Archive instance.
                        //
                        // Note: Temporary files are deleted using the "deleteOnExit" mechanism, it should
                        //       be more deterministic and reliable than that.
                        if (tempFolder == null) {
                            tempFolder =
                                    Files.createTempDirectory(
                                            archive.getPath().getFileName().toString());
                        }
                        Path tempFile =
                                tempFolder.resolve(contentRoot.relativize(childPath).toString());
                        Files.createDirectories(tempFile.getParent());
                        Files.copy(childPath, tempFile);
                        Archive tempArchive = Archives.openInnerZip(tempFile);

                        // Create inner tree for temp archive file
                        ArchiveTreeNode newArchiveNode =
                                (ArchiveTreeNode)
                                        createWorker(
                                                tempArchive, pathPrefix + childPath.toString());

                        // Create root node for temp archive file, and append children
                        childNode =
                                new ArchiveTreeNode(
                                        new InnerArchiveEntry(
                                                archive, childPath, pathPrefix, tempArchive));

                        for (ArchiveNode archiveNodeChild : newArchiveNode.getChildren()) {
                            childNode.add((MutableTreeNode) archiveNodeChild);
                        }
                    } else {
                        childNode =
                                new ArchiveTreeNode(
                                        new ArchiveEntry(archive, childPath, pathPrefix));
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
        Map<String, Long> rawFileSizes;
        if (root.getData() instanceof InnerArchiveEntry) {
            rawFileSizes =
                    calculator.getRawSizePerFile(
                            ((InnerArchiveEntry) root.getData())
                                    .asArchiveEntry()
                                    .getArchive()
                                    .getPath());
        } else {
            rawFileSizes = calculator.getRawSizePerFile(root.getData().getArchive().getPath());
        }

        // first set the file sizes for all child nodes
        ArchiveTreeStream.preOrderStream(root)
                .forEach(
                        node -> {
                            ArchiveEntry data = node.getData();
                            if (node != root
                                    && data.getPath().getFileName() != null
                                    && node.getData() instanceof InnerArchiveEntry) {
                                updateRawFileSizes(node, calculator);
                            }
                            Long rawFileSize = rawFileSizes.get(data.getPath().toString());
                            if (rawFileSize != null) {
                                data.setRawFileSize(rawFileSize);
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
        Map<String, Long> downloadFileSizes;
        if (root.getData() instanceof InnerArchiveEntry) {
            downloadFileSizes =
                    calculator.getDownloadSizePerFile(
                            ((InnerArchiveEntry) root.getData())
                                    .asArchiveEntry()
                                    .getArchive()
                                    .getPath());
        } else {
            downloadFileSizes =
                    calculator.getDownloadSizePerFile(root.getData().getArchive().getPath());
        }

        // first set the file sizes for all child nodes
        ArchiveTreeStream.preOrderStream(root)
                .forEach(
                        node -> {
                            ArchiveEntry data = node.getData();
                            if (node != root
                                    && data.getPath().getFileName() != null
                                    && node.getData() instanceof InnerArchiveEntry) {
                                updateDownloadFileSizes(node, calculator);
                            }
                            Long downloadFileSize =
                                    downloadFileSizes.get(data.getPath().toString());
                            if (downloadFileSize != null) {
                                data.setDownloadFileSize(downloadFileSize);
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
