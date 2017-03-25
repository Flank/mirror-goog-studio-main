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
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class ArchiveTreeStructure {
    public static ArchiveNode create(@NonNull Archive archive) throws IOException {
        Path contentRoot = archive.getContentRoot();
        ArchiveTreeNode rootNode = new ArchiveTreeNode(new ArchiveEntry(contentRoot));

        Stack<ArchiveTreeNode> stack = new Stack<>();
        stack.push(rootNode);

        while (!stack.isEmpty()) {
            ArchiveTreeNode node = stack.pop();
            Path path = node.getData().getPath();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path childPath : stream) {
                    ArchiveTreeNode childNode = new ArchiveTreeNode(new ArchiveEntry(childPath));
                    node.add(childNode);
                    if (Files.isDirectory(childPath)) {
                        stack.push(childNode);
                    }
                }
            }
        }

        return rootNode;
    }

    public static void updateRawFileSizes(
            @NonNull ArchiveNode root, @NonNull ApkSizeCalculator calculator) {
        Map<String, Long> rawFileSizes = calculator.getRawSizePerFile();

        // first set the file sizes for all child nodes
        ArchiveTreeStream.preOrderStream(root)
                .forEach(
                        node -> {
                            ArchiveEntry data = node.getData();
                            Long rawFileSize = rawFileSizes.get(data.getPath().toString());
                            data.setRawFileSize(rawFileSize == null ? 0 : rawFileSize);
                        });

        // now set the directory entries to be the size of all their children
        ArchiveTreeStream.postOrderStream(root)
                .forEach(
                        node -> {
                            ArchiveEntry data = node.getData();
                            if (Files.isDirectory(data.getPath())) {
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
        Map<String, Long> downloadFileSizes = calculator.getDownloadSizePerFile();

        // first set the file sizes for all child nodes
        ArchiveTreeStream.preOrderStream(root)
                .forEach(
                        node -> {
                            ArchiveEntry data = node.getData();
                            Long downloadFileSize =
                                    downloadFileSizes.get(data.getPath().toString());
                            data.setDownloadFileSize(
                                    downloadFileSize == null ? 0 : downloadFileSize);
                        });

        // now set the directory entries to be the size of all their children
        ArchiveTreeStream.postOrderStream(root)
                .forEach(
                        node -> {
                            ArchiveEntry data = node.getData();
                            if (Files.isDirectory(data.getPath())) {
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
