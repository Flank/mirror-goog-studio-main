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

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public class TreeDifferenceEngine {
    public static Script computeEditScript(FileSystemEntry root1, FileSystemEntry root2) {
        Script result = new Script();
        computeWorker(result, root1, root2, root1, root2);
        return result;
    }

    private static void computeWorker(Script script, FileSystemEntry leftRoot,
            FileSystemEntry rightRoot, FileSystemEntry left, FileSystemEntry right) {
        assert left == leftRoot || left.getPath().getFileName().equals(right.getPath().getFileName());

        if (!compareEntries(left, right)) {
            script.addDeleteEntry(left);
            script.addCreateEntry(right, mapToLeftEntry(leftRoot, rightRoot, right));
            return;
        }

        Map<String, FileSystemEntry> leftChildren = buildChildMap(left);
        Map<String, FileSystemEntry> rightChildren = buildChildMap(right);

        for (FileSystemEntry leftChild : leftChildren.values()) {
            FileSystemEntry rightChild = rightChildren.get(leftChild.getName());
            if (rightChild != null) {
                // If child with same name exist on both sides, recurse
                computeWorker(script, leftRoot, rightRoot, leftChild, rightChild);
            }
            else {
                // left child does not exist on the right, delete it
                script.addDeleteEntry(leftChild);
            }
        }

        // For all entries on the right not in the left, add create action
        for (FileSystemEntry rightChild : rightChildren.values()) {
            if (!leftChildren.containsKey(rightChild.getName())) {
                script.addCreateEntry(rightChild, mapToLeftEntry(leftRoot, rightRoot, rightChild));
            }
        }
    }

    private static FileSystemEntry mapToLeftEntry(FileSystemEntry leftRoot,
            FileSystemEntry rightRoot,
            FileSystemEntry right) {
        Path relPath = rightRoot.getPath().relativize(right.getPath());
        Path leftPath = leftRoot.getPath().resolve(relPath);
        switch(right.getKind()) {
            case Directory:
                return new DirectoryEntry(leftPath);
            case SymbolicLink:
                return new SymbolicLinkEntry(leftPath, ((SymbolicLinkEntry)right).getTarget());
            case File:
                return new FileEntry(leftPath);
            default:
                throw new RuntimeException("Invalid enum value");
        }
    }

    private static boolean compareEntries(FileSystemEntry left, FileSystemEntry right) {
        if (left.getKind() != right.getKind()) {
            return false;
        }

        if (left instanceof SymbolicLinkEntry) {
            SymbolicLinkEntry leftEntry = (SymbolicLinkEntry) left;
            SymbolicLinkEntry rightEntry = (SymbolicLinkEntry) right;
            if (!leftEntry.getTarget().equals(rightEntry.getTarget())) {
                return false;
            }
        }

        return true;
    }

    private static Map<String, FileSystemEntry> buildChildMap(FileSystemEntry parentEntry) {
        TreeMap<String, FileSystemEntry> result = new TreeMap<>();
        for (FileSystemEntry x : parentEntry.getChildEntries()) {
            result.put(x.getName(), x);
        }
        return result;
    }
}
