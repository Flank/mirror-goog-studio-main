/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ide.common.resources;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.resources.ResourceType;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MergeResourceWriterWithCompilerTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private Map<String, ResourceMergerItem> mResourceItems;

    private ResourcePreprocessor mEmptyPreprocessor;

    private ResourceCompilationService mSimpleCompiler;

    @Before
    public final void before() throws Exception {
        mEmptyPreprocessor =
                new ResourcePreprocessor() {
                    @NonNull
                    @Override
                    public Collection<File> getFilesToBeGenerated(@NonNull File original) {
                        return Collections.emptySet();
                    }

                    @Override
                    public void generateFile(@NonNull File toBeGenerated, @NonNull File original) {}
                };

        mSimpleCompiler =
                new ResourceCompilationService() {
                    @Override
                    public void submitCompile(@NonNull CompileResourceRequest request)
                            throws IOException {
                        File outputPath = compileOutputFor(request);
                        Files.copy(request.getInputFile(), outputPath);
                    }

                    @Override
                    public void close() {}

                    @NonNull
                    @Override
                    public File compileOutputFor(@NonNull CompileResourceRequest request) {
                        return new File(
                                request.getOutputDirectory(),
                                request.getInputFile().getName() + "-c");
                    }
                };

        createSourceResourcesFiles();
    }

    /**
     * Creates the source resources to merge:
     *
     * <pre>
     * raw
     *   + f1.txt ("foo")
     * </pre>
     */
    private void createSourceResourcesFiles() throws Exception {
        File resourceDir = mTemporaryFolder.newFolder();
        File rawRes = new File(resourceDir, "raw");
        rawRes.mkdir();
        File f1 = new File(rawRes, "f1.txt");
        Files.asCharSink(f1, Charsets.UTF_8).write("foo");

        ResourceMergerItem f1Item =
                new ResourceMergerItem("f1.txt", null, ResourceType.RAW, null, null, null);
        ResourceFile f1File = new ResourceFile(f1, f1Item, new FolderConfiguration());
        f1Item.setSourceFile(f1File);

        File f2 = new File(rawRes, "f2.xml");
        Files.asCharSink(f2, Charsets.UTF_8).write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");

        ResourceMergerItem f2Item =
                new ResourceMergerItem("f2.xml", null, ResourceType.RAW, null, null, null);
        ResourceFile f2File = new ResourceFile(f2, f2Item, new FolderConfiguration());
        f2Item.setSourceFile(f2File);

        mResourceItems = new HashMap<>();
        mResourceItems.put("f1.txt", f1Item);
        mResourceItems.put("f2.xml", f2Item);
    }

    @Test
    public void addAndDeleteFileTxt() throws Exception {
        addAndDeleteFile("f1.txt");
    }

    @Test
    public void addAndDeleteFileXml() throws Exception {
        addAndDeleteFile("f2.xml");
    }

    private WorkerExecutorFacade facade =
            new WorkerExecutorFacade() {

                @Override
                public void submit(Class<? extends Runnable> actionClass, Serializable parameter) {
                    submit(
                            actionClass,
                            new Configuration(
                                    parameter,
                                    IsolationMode.NONE,
                                    ImmutableList.of(),
                                    ImmutableList.of()));
                }

                @Override
                public void submit(
                        @NonNull Class<? extends Runnable> actionClass,
                        @NonNull Configuration configuration) {
                    Runnable action;
                    try {
                        action =
                                actionClass
                                        .getConstructor(configuration.getParameter().getClass())
                                        .newInstance(configuration.getParameter());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    action.run();
                }

                @Override
                public void await() {}

                @Override
                public void close() {}
            };

    public void addAndDeleteFile(@NonNull String name) throws Exception {
        File root = mTemporaryFolder.newFolder();
        File tmpFolder = mTemporaryFolder.newFolder();

        MergedResourceWriter writer =
                new MergedResourceWriter(
                        facade,
                        root,
                        null,
                        null,
                        mEmptyPreprocessor,
                        mSimpleCompiler,
                        tmpFolder,
                        null,
                        null,
                        false,
                        false);

        /*
         * Add the file.
         */

        writer.start(DocumentBuilderFactory.newInstance());
        mResourceItems.get(name).setTouched();
        writer.addItem(mResourceItems.get(name));
        writer.postWriteAction();
        writer.end();

        File f1Compiled = new File(root, name + "-c");
        assertTrue(f1Compiled.exists());


        /*
         * Remove the file.
         */
        writer =
                new MergedResourceWriter(
                        facade,
                        root,
                        null,
                        null,
                        mEmptyPreprocessor,
                        mSimpleCompiler,
                        tmpFolder,
                        null,
                        null,
                        false,
                        false);

        mResourceItems.get(name).setRemoved();
        writer.start(DocumentBuilderFactory.newInstance());
        writer.removeItem(mResourceItems.get(name), null);
        writer.postWriteAction();
        writer.end();

        assertFalse(f1Compiled.exists());
    }
}
