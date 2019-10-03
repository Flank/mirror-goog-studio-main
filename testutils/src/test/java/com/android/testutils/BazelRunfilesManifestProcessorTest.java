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

package com.android.testutils;

import static com.android.testutils.AssumeUtil.assumeNotWindows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.testutils.filesystemdiff.FileSystemEntry;
import com.android.testutils.filesystemdiff.SymbolicLinkDefinition;
import com.android.testutils.filesystemdiff.SymbolicLinkEntry;
import com.android.testutils.filesystemdiff.TreeBuilder;
import com.android.testutils.filesystemdiff.TreeDifferenceEngineTest;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BazelRunfilesManifestProcessorTest {

    private static TreeDifferenceEngineTest.FileSystem createEmptyFileSystem() throws IOException {
        return TreeDifferenceEngineTest.createEmptyFileSystem();
    }

    private static TreeDifferenceEngineTest.FileSystem createTestFileSystem() throws IOException {
        return TreeDifferenceEngineTest.createTestFileSystem();
    }

    private static List<SymbolicLinkDefinition> createLinkDefs(Path sourcePath) {
        List<SymbolicLinkDefinition> links = new ArrayList<>();
        try {
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Path relPath = sourcePath.relativize(file);
                    // Bazel runfiles manifest don't currently support spaces in paths
                    if (!file.toString().contains(" ") && !relPath.toString().contains(" ")) {
                        links.add(new SymbolicLinkDefinition(relPath, file));
                    }
                    return super.visitFile(file, attrs);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return links;
    }

    private static String createLinkDefsContents(List<SymbolicLinkDefinition> links) {
        StringBuilder sb = new StringBuilder();
        for (SymbolicLinkDefinition x : links) {
            sb.append(String.format("%s %s\n", x.getPath(), x.getTarget()));
        }
        return sb.toString();
    }

    private static List<SymbolicLinkDefinition> runProcessor(
            TreeDifferenceEngineTest.FileSystem sourceFileSystem,
            TreeDifferenceEngineTest.FileSystem destinationFileSystem) {
        assumeTrue("Platform must support symbolic links, or test must be run as Admin on Windows",
                destinationFileSystem.supportsSymbolicLinks());

        List<SymbolicLinkDefinition> links = createLinkDefs(sourceFileSystem.getPath());
        String linksContent = createLinkDefsContents(links);

        if (destinationFileSystem.exists("MANIFEST")) {
            destinationFileSystem.delete("MANIFEST");
        }
        Path manifestFile = destinationFileSystem.createFile("MANIFEST", linksContent);

        HashMap<String, String> env = new HashMap<>();
        env.put(BazelRunfilesManifestProcessor.TEST_SRCDIR_ENV,
                destinationFileSystem.getPath().toString());
        env.put(BazelRunfilesManifestProcessor.RUNFILES_MANIFEST_FILE_ENV, manifestFile.toString());

        BazelRunfilesManifestProcessor.setUpRunfiles(env);
        return links;
    }

    private static void assertSameFileSystems(TreeDifferenceEngineTest.FileSystem destFileSystem,
            List<SymbolicLinkDefinition> links) throws IOException {

        assertSameFileSystems(
                TreeBuilder.buildFromSymbolicLinkDefinitions(destFileSystem.getPath(), links),
                TreeBuilder.buildFromFileSystem(destFileSystem.getPath()),
                destFileSystem.getPath().resolve("MANIFEST"));

        for (SymbolicLinkDefinition x : links) {
            Path destinationPath = destFileSystem.getPath().resolve(x.getPath());
            assertTrue(Files.exists(destinationPath));
            assertTrue(Files.isSymbolicLink(destinationPath));
        }
    }

    private static void assertSameFileSystems(
            FileSystemEntry expected, FileSystemEntry actual, Path manifest) throws IOException {

        assertEquals(expected.getKind(), actual.getKind());
        if (expected instanceof SymbolicLinkEntry) {
            assertEquals(((SymbolicLinkEntry) expected).getTarget(),
                    ((SymbolicLinkEntry) actual).getTarget());
        }

        List<FileSystemEntry> expectedChildren = expected.getChildEntries().stream()
                .sorted((o1, o2) -> o1.getName().compareTo(o2.getName()))
                .collect(Collectors.toList());

        List<FileSystemEntry> actualChildren = actual.getChildEntries().stream()
                .filter(o -> !o.getPath().equals(manifest))
                .sorted((o1, o2) -> o1.getName().compareTo(o2.getName()))
                .collect(Collectors.toList());

        assertEquals(expectedChildren.size(), actualChildren.size());

        for (int i = 0; i < expectedChildren.size(); i++) {
            assertSameFileSystems(expectedChildren.get(i), actualChildren.get(i), manifest);
        }
    }

    @Parameterized.Parameter
    public TreeDifferenceEngineTest.FileSystem mSourceFileSystem;

    @Parameterized.Parameter(1)
    public TreeDifferenceEngineTest.FileSystem mDestinationFileSystem;

    @Parameterized.Parameters
    public static ImmutableList<Object[]> getParameters() throws IOException {
        return ImmutableList.of(
                new Object[]{createEmptyFileSystem(), createEmptyFileSystem()},
                new Object[]{createEmptyFileSystem(), createTestFileSystem()},
                new Object[]{createTestFileSystem(), createEmptyFileSystem()},
                new Object[]{createTestFileSystem(), createTestFileSystem()});
    }

    @After
    public void after() throws Exception {
        if (mSourceFileSystem != null) {
            mSourceFileSystem.close();
        }
        if (mDestinationFileSystem != null) {
            mDestinationFileSystem.close();
        }
    }

    @Test
    public void creatingFileSystemShouldWork() throws Exception {
        assumeNotWindows();
        List<SymbolicLinkDefinition> links =
                runProcessor(mSourceFileSystem, mDestinationFileSystem);
        assertSameFileSystems(mDestinationFileSystem, links);
    }
}
