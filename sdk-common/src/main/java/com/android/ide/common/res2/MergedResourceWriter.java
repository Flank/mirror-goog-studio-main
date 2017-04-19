/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.ide.common.res2;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.RES_QUALIFIER_SEP;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.internal.PngException;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.utils.FileUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A {@link MergeWriter} for assets, using {@link ResourceItem}. Also takes care of compiling
 * resources and stripping data binding from layout files.
 */
public class MergedResourceWriter extends MergeWriter<ResourceItem> {

    @NonNull
    private final ResourcePreprocessor mPreprocessor;

    /**
     * If non-null, points to a File that we should write public.txt to
     */
    private final File mPublicFile;

    @Nullable
    private MergingLog mMergingLog;

    private DocumentBuilderFactory mFactory;

    /**
     * Compiler for resources
     */
    @NonNull
    private final QueueableResourceCompiler mResourceCompiler;

    /**
     * map of XML values files to write after parsing all the files. the key is the qualifier.
     */
    private ListMultimap<String, ResourceItem> mValuesResMap;

    /**
     * Set of qualifier that had a previously written resource now gone. This is to keep a list of
     * values files that must be written out even with no touched or updated resources, in case one
     * or more resources were removed.
     */
    private Set<String> mQualifierWithDeletedValues;

    /**
     * Futures we are waiting for...
     */
    @NonNull
    private final ConcurrentLinkedDeque<Future<File>> mCompiling;

    /**
     * Temporary directory to use while writing merged resources.
     */
    @NonNull
    private final File mTemporaryDirectory;

    /**
     * File where {@link #mCompiledFileMap} is read from and where its contents are written.
     */
    @NonNull
    private final File mCompiledFileMapFile;

    @Nullable private final SingleFileProcessor dataBindingExpressionRemover;

    @Nullable private final File dataBindingLayoutOutputFolder;

    /**
     * Maps resource files to their compiled files. Used to compiled resources that no longer
     * exist.
     */
    private final Properties mCompiledFileMap;

    @NonNull
    private final ConcurrentLinkedQueue<CompileResourceRequest> mCompileResourceRequests =
            new ConcurrentLinkedQueue<>();

    /**
     * A {@link MergeWriter} for resources, using {@link ResourceItem}. Also takes care of compiling
     * resources and stripping data binding from layout files.
     *
     * @param rootFolder merged resources directory to write to (e.g. {@code
     *     intermediates/res/merged/debug})
     * @param publicFile File that we should write public.txt to
     * @param blameLogFolder folder containing merging log
     * @param preprocessor preprocessor for merged resources, such as vector drawable rendering
     * @param resourceCompiler resource compiler, i.e. AAPT
     * @param temporaryDirectory temporary directory for intermediate merged files
     * @param dataBindingExpressionRemover removes data binding expressions from layout files
     * @param dataBindingLayoutOutputFolder original layout files passed to the data binding task
     */
    public MergedResourceWriter(
            @NonNull File rootFolder,
            @Nullable File publicFile,
            @Nullable File blameLogFolder,
            @NonNull ResourcePreprocessor preprocessor,
            @NonNull QueueableResourceCompiler resourceCompiler,
            @NonNull File temporaryDirectory,
            @Nullable SingleFileProcessor dataBindingExpressionRemover,
            @Nullable File dataBindingLayoutOutputFolder) {
        super(rootFolder);
        Preconditions.checkState(
                (dataBindingExpressionRemover == null) == (dataBindingLayoutOutputFolder == null),
                "Both dataBindingExpressionRemover and dataBindingLayoutOutputFolder need to be "
                        + "either null or non null");

        mResourceCompiler = resourceCompiler;
        mPublicFile = publicFile;
        mMergingLog = blameLogFolder != null ? new MergingLog(blameLogFolder) : null;
        mPreprocessor = preprocessor;
        mCompiling = new ConcurrentLinkedDeque<>();
        mTemporaryDirectory = temporaryDirectory;
        this.dataBindingExpressionRemover = dataBindingExpressionRemover;
        this.dataBindingLayoutOutputFolder = dataBindingLayoutOutputFolder;

        mCompiledFileMapFile = new File(temporaryDirectory, "compile-file-map.properties");
        mCompiledFileMap = new Properties();
        if (mCompiledFileMapFile.exists()) {
            try (FileReader fr = new FileReader(mCompiledFileMapFile)) {
                mCompiledFileMap.load(fr);
            } catch (IOException e) {
                /*
                 * If we can't load the map, then we proceed without one. This means that
                 * we won't be able to delete compiled resource files if the original ones
                 * are deleted.
                 */
            }
        }
    }

    /*
     * Used in tests.
     */
    public static MergedResourceWriter createWriterWithoutPngCruncher(
            @NonNull File rootFolder,
            @Nullable File publicFile,
            @Nullable File blameLogFolder,
            @NonNull ResourcePreprocessor preprocessor,
            @NonNull File temporaryDirectory) {
        return new MergedResourceWriter(
                rootFolder,
                publicFile,
                blameLogFolder,
                preprocessor,
                QueueableResourceCompiler.NONE,
                temporaryDirectory,
                null,
                null);
    }

    @Override
    public void start(@NonNull DocumentBuilderFactory factory) throws ConsumerException {
        super.start(factory);
        mValuesResMap = ArrayListMultimap.create();
        mQualifierWithDeletedValues = Sets.newHashSet();
        mFactory = factory;
    }

    @Override
    public void end() throws ConsumerException {
        // Make sure all PNGs are generated first.
        super.end();
        // now perform all the databinding, PNG crunching (AAPT1) and resources compilation (AAPT2).
        try {
            mResourceCompiler.start();
            File tmpDir = new File(mTemporaryDirectory, "stripped.dir");
            try {
                FileUtils.cleanOutputDir(tmpDir);
            } catch (IOException e) {
                throw new ConsumerException(e);
            }

            while (!mCompileResourceRequests.isEmpty()) {
                CompileResourceRequest request = mCompileResourceRequests.poll();
                try {
                    ListenableFuture<File> result;

                    if (mMergingLog != null) {
                        mMergingLog.logCopy(
                                request.getInput(), mResourceCompiler.compileOutputFor(request));
                    }

                    if (dataBindingExpressionRemover != null
                            && request.getFolderName().startsWith("layout")
                            && request.getInput().getName().endsWith(".xml")) {

                        // Try to strip the layout. If stripping modified the file (there was data
                        // binding in the layout), copy the *original* file to the intermediate
                        // data-binding-layout-in folder and compile the *stripped* layout into
                        // merged resources folder. Otherwise, compile into merged resources folder
                        // normally.

                        File strippedLayoutFolder = new File(tmpDir, request.getFolderName());
                        File strippedLayout =
                                new File(strippedLayoutFolder, request.getInput().getName());

                        boolean removedDataBinding =
                                dataBindingExpressionRemover.processSingleFile(
                                        request.getInput(), strippedLayout);

                        if (removedDataBinding) {
                            File databindingLayoutDir =
                                    new File(
                                            dataBindingLayoutOutputFolder, request.getFolderName());

                            FileUtils.mkdirs(databindingLayoutDir);
                            FileUtils.copyFileToDirectory(request.getInput(), databindingLayoutDir);

                            if (mMergingLog != null) {
                                mMergingLog.logCopy(
                                        request.getInput(),
                                        new File(
                                                databindingLayoutDir,
                                                request.getInput().getName()));
                            }

                            result =
                                    mResourceCompiler.compile(
                                            new CompileResourceRequest(
                                                    strippedLayout,
                                                    request.getOutput(),
                                                    request.getFolderName()));
                        } else {
                            result = mResourceCompiler.compile(request);
                        }
                    } else {
                        result = mResourceCompiler.compile(request);
                    }

                    // adding to the mCompiling seems unnecessary at this point, the end() call will
                    // take care of waiting for all requests to be processed.
                    mCompiling.add(result);
                    result.addListener(
                            () -> {
                                try {
                                    File outFile = result.get();
                                    mCompiledFileMap.put(
                                            request.getInput().getAbsolutePath(),
                                            outFile.getAbsolutePath());
                                } catch (Exception e) {
                                    /*
                                     * We will detect any exceptions (or generate them during copy)
                                     * asynchronously, so we need to be careful to report them back.
                                     * Because end() will wait for all futures and report any
                                     * failures, we will register a new future that will throw the
                                     * exception when we fail. This ensures that end() will throw
                                     * the exception.
                                     */
                                    SettableFuture<File> failureSimulator = SettableFuture.create();
                                    failureSimulator.setException(e);
                                    mCompiling.add(failureSimulator);
                                }
                            },
                            MoreExecutors.sameThreadExecutor());
                } catch(PngException | IOException e) {
                    throw MergingException.wrapException(e).withFile(request.getInput()).build();
                }
            }
            mResourceCompiler.end();

        } catch (Exception e) {
            throw new ConsumerException(e);
        }
        // now wait for all PNGs to be actually crunched.This seems to only be necessary to
        // propagate exception at this point. There should be a simpler way to do this.
        try {
            Future<File> first;
            while ((first = mCompiling.pollFirst()) != null) {
                first.get();
            }

        } catch (InterruptedException|ExecutionException e) {
            throw new ConsumerException(e);
        }

        if (mMergingLog != null) {
            try {
                mMergingLog.write();
            } catch (IOException e) {
                throw new ConsumerException(e);
            }
            mMergingLog = null;
        }

        mValuesResMap = null;
        mQualifierWithDeletedValues = null;
        mFactory = null;

        try (FileWriter fw = new FileWriter(mCompiledFileMapFile)) {
            mCompiledFileMap.store(fw, null);
        } catch (IOException e) {
            throw new ConsumerException(e);
        }
    }

    @Override
    public boolean ignoreItemInMerge(ResourceItem item) {
        return item.getIgnoredFromDiskMerge();
    }
    
    @Override
    public void addItem(@NonNull final ResourceItem item) throws ConsumerException {
        final ResourceFile.FileType type = item.getSourceType();

        if (type == ResourceFile.FileType.XML_VALUES) {
            // this is a resource for the values files

            // just add the node to write to the map based on the qualifier.
            // We'll figure out later if the files needs to be written or (not)
            mValuesResMap.put(item.getQualifiers(), item);
        } else {
            checkState(item.getSource() != null);
            // This is a single value file or a set of generated files. Only write it if the state
            // is TOUCHED.
            if (item.isTouched()) {
                getExecutor()
                        .execute(
                                () -> {
                                    File file = item.getFile();
                                    String folderName = getFolderName(item);

                                    // TODO : make this also a request and use multi-threading for generation.
                                    if (type == DataFile.FileType.GENERATED_FILES) {
                                        try {
                                            mPreprocessor.generateFile(
                                                    file, item.getSource().getFile());
                                        } catch (Exception e) {
                                            throw new ConsumerException(
                                                    e, item.getSource().getFile());
                                        }
                                    }

                                    // enlist a new crunching request.
                                    mCompileResourceRequests.add(
                                            new CompileResourceRequest(
                                                    file, getRootFolder(), folderName));
                                    return null;
                                });
            }
        }
    }

    @Override
    public void removeItem(@NonNull ResourceItem removedItem, @Nullable ResourceItem replacedBy)
            throws ConsumerException {
        ResourceFile.FileType removedType = removedItem.getSourceType();
        ResourceFile.FileType replacedType = replacedBy != null
                ? replacedBy.getSourceType()
                : null;

        switch (removedType) {
            case SINGLE_FILE: // Fall through.
            case GENERATED_FILES:
                if (replacedType == DataFile.FileType.SINGLE_FILE
                        || replacedType == DataFile.FileType.GENERATED_FILES) {

                    File removedFile = getResourceOutputFile(removedItem);
                    File replacedFile = getResourceOutputFile(replacedBy);
                    if (removedFile.equals(replacedFile)) {
                        /*
                         * There are two reasons to skip this: 1. we save an IO operation by
                         * deleting a file that will be overwritten. 2. if we did delete the file,
                         * we would have to be careful about concurrency to make sure we would be
                         * deleting the *old* file and not the overwritten version.
                         */
                        break;
                    }
                }
                removeOutFile(removedItem);
                break;
            case XML_VALUES:
                mQualifierWithDeletedValues.add(removedItem.getQualifiers());
                break;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    protected void postWriteAction() throws ConsumerException {

        /*
         * Create a temporary directory where merged XML files are placed before being processed
         * by the resource compiler.
         */
        File tmpDir = new File(mTemporaryDirectory, "merged.dir");
        try {
            FileUtils.cleanOutputDir(tmpDir);
        } catch (IOException e) {
            throw new ConsumerException(e);
        }

        // now write the values files.
        for (String key : mValuesResMap.keySet()) {
            // the key is the qualifier.

            // check if we have to write the file due to deleted values.
            // also remove it from that list anyway (to detect empty qualifiers later).
            boolean mustWriteFile = mQualifierWithDeletedValues.remove(key);

            // get the list of items to write
            List<ResourceItem> items = mValuesResMap.get(key);

            // now check if we really have to write it
            if (!mustWriteFile) {
                for (ResourceItem item : items) {
                    if (item.isTouched()) {
                        mustWriteFile = true;
                        break;
                    }
                }
            }

            if (mustWriteFile) {
                /*
                 * We will write the file to a temporary directory. If the folder name is "values",
                 * we will write the XML file to "<tmpdir>/values/values.xml". If the folder name
                 * is "values-XXX" we will write the XML file to
                 * "<tmpdir/values-XXX/values-XXX.xml".
                 *
                 * Then, we will issue a compile operation or copy the file if aapt does not require
                 * compilation of this file.
                 */
                try {
                    String folderName = key.isEmpty() ?
                            ResourceFolderType.VALUES.getName() :
                            ResourceFolderType.VALUES.getName() + RES_QUALIFIER_SEP + key;

                    File valuesFolder = new File(tmpDir, folderName);
                    // Name of the file is the same as the folder as AAPT gets confused with name
                    // collision when not normalizing folders name.
                    File outFile = new File(valuesFolder, folderName + DOT_XML);

                    FileUtils.mkdirs(valuesFolder);

                    DocumentBuilder builder = mFactory.newDocumentBuilder();
                    Document document = builder.newDocument();
                    final String publicTag = ResourceType.PUBLIC.getName();
                    List<Node> publicNodes = null;

                    Node rootNode = document.createElement(TAG_RESOURCES);
                    document.appendChild(rootNode);

                    Collections.sort(items);

                    for (ResourceItem item : items) {
                        Node nodeValue = item.getValue();
                        if (nodeValue != null && publicTag.equals(nodeValue.getNodeName())) {
                            if (publicNodes == null) {
                                publicNodes = Lists.newArrayList();
                            }
                            publicNodes.add(nodeValue);
                            continue;
                        }

                        // add a carriage return so that the nodes are not all on the same line.
                        // also add an indent of 4 spaces.
                        rootNode.appendChild(document.createTextNode("\n    "));

                        ResourceFile source = item.getSource();

                        Node adoptedNode = NodeUtils.adoptNode(document, nodeValue);
                        if (source != null) {
                            XmlUtils.attachSourceFile(
                                    adoptedNode, new SourceFile(source.getFile()));
                        }
                        rootNode.appendChild(adoptedNode);
                    }

                    // finish with a carriage return
                    rootNode.appendChild(document.createTextNode("\n"));

                    final String content;
                    Map<SourcePosition, SourceFilePosition> blame =
                            mMergingLog == null ? null : Maps.newLinkedHashMap();

                    if (blame != null) {
                        content = XmlUtils.toXml(document, blame);
                    } else {
                        content = XmlUtils.toXml(document);
                    }

                    Files.write(content, outFile, Charsets.UTF_8);

                    Future<File> f =
                            mResourceCompiler.compile(
                                    new CompileResourceRequest(
                                            outFile, getRootFolder(), folderName));
                    File copyOutput = f.get();

                    if (blame != null) {
                        mMergingLog.logSource(new SourceFile(copyOutput), blame);
                    }

                    if (publicNodes != null && mPublicFile != null) {
                        // Generate public.txt:
                        int size = publicNodes.size();
                        StringBuilder sb = new StringBuilder(size * 80);
                        for (Node node : publicNodes) {
                            if (node.getNodeType() == Node.ELEMENT_NODE) {
                                Element element = (Element) node;
                                String name = element.getAttribute(ATTR_NAME);
                                String type = element.getAttribute(ATTR_TYPE);
                                if (!name.isEmpty() && !type.isEmpty()) {
                                    String flattenedName = name.replace('.', '_');
                                    sb.append(type).append(' ').append(flattenedName).append('\n');
                                }
                            }
                        }
                        File parentFile = mPublicFile.getParentFile();
                        if (!parentFile.exists()) {
                            boolean mkdirs = parentFile.mkdirs();
                            if (!mkdirs) {
                                throw new IOException("Could not create " + parentFile);
                            }
                        }
                        String text = sb.toString();
                        Files.write(text, mPublicFile, Charsets.UTF_8);
                    }
                } catch (Exception e) {
                    throw new ConsumerException(e);
                }
            }
        }

        // now remove empty values files.
        for (String key : mQualifierWithDeletedValues) {
            String folderName = key != null && !key.isEmpty() ?
                    ResourceFolderType.VALUES.getName() + RES_QUALIFIER_SEP + key :
                    ResourceFolderType.VALUES.getName();

            removeOutFile(FileUtils.join(getRootFolder(), folderName, folderName + DOT_XML));
        }
    }

    /**
     * Obtains the where te merged resource is located.
     *
     * @param resourceItem the resource item
     * @return the file
     */
    @NonNull
    private File getResourceOutputFile(@NonNull ResourceItem resourceItem) {
        File file = resourceItem.getFile();
        String compiledFilePath = mCompiledFileMap.getProperty(file.getAbsolutePath());
        if (compiledFilePath != null) {
            return new File(compiledFilePath);
        } else {
            return FileUtils.join(getRootFolder(), getFolderName(resourceItem), file.getName());
        }
    }

    /**
     * Removes possibly existing layout file from the data binding output folder. If the original
     * file was a layout XML file it is possible that it contained data binding and was put into the
     * data binding layout output folder for data binding tasks to process.
     *
     * @param resourceItem the source item that could have created the file to remove
     */
    private void removeLayoutFileFromDataBindingOutputFolder(@NonNull ResourceItem resourceItem) {
        File originalFile = resourceItem.getFile();
        // Only files that come from layout folders and are XML files could have been stripped.
        if (!originalFile.getParentFile().getName().startsWith("layout")
                || !originalFile.getName().endsWith(".xml")) {
            return;
        }
        File layoutDir =
                new File(dataBindingLayoutOutputFolder, originalFile.getParentFile().getName());
        File toRemove = new File(layoutDir, originalFile.getName());
        removeOutFile(toRemove);
    }

    /**
     * Removes a file that already exists in the out res folder. This has to be a non value file.
     *
     * @param resourceItem the source item that created the file to remove, this item must have a
     *     file associated with it
     * @return true if success.
     */
    private boolean removeOutFile(ResourceItem resourceItem) {
        File fileToRemove = getResourceOutputFile(resourceItem);
        if (dataBindingExpressionRemover != null) {
            // The file could have possibly been a layout file with data binding.
            removeLayoutFileFromDataBindingOutputFolder(resourceItem);
        }
        return removeOutFile(fileToRemove);
    }

    /**
     * Removes a file from a folder based on a sub folder name and a filename
     *
     * @param fileToRemove the file to remove
     * @return true if success
     */
    private boolean removeOutFile(@NonNull File fileToRemove) {
        if (mMergingLog != null) {
            mMergingLog.logRemove(new SourceFile(fileToRemove));
        }

        return fileToRemove.delete();
    }

    /**
     * Calculates the right folder name give a resource item.
     *
     * @param resourceItem the resource item to calculate the folder name from.
     * @return a relative folder name
     */
    @NonNull
    private static String getFolderName(ResourceItem resourceItem) {
        ResourceType itemType = resourceItem.getType();
        String folderName = itemType.getName();
        String qualifiers = resourceItem.getQualifiers();
        if (!qualifiers.isEmpty()) {
            folderName = folderName + RES_QUALIFIER_SEP + qualifiers;
        }
        return folderName;
    }
}
