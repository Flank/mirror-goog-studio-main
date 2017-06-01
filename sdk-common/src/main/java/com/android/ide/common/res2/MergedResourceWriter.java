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
import com.android.ide.common.workers.WorkerExecutorFacade;
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
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
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
public class MergedResourceWriter
        extends MergeWriter<ResourceItem, MergedResourceWriter.FileGenerationParameters> {

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

    @Nullable private final File resourceShrinkerOutputFolder;

    private final boolean pseudoLocalesEnabled;

    private final boolean crunchPng;

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
     * @param blameLog merging log for rewriting error messages
     * @param preprocessor preprocessor for merged resources, such as vector drawable rendering
     * @param resourceCompiler resource compiler, i.e. AAPT
     * @param temporaryDirectory temporary directory for intermediate merged files
     * @param dataBindingExpressionRemover removes data binding expressions from layout files
     * @param dataBindingLayoutOutputFolder for layout files passed to the data binding task
     * @param resourceShrinkerOutputFolder for saved uncompiled resources for the resource shrinking
     *     transform
     * @param pseudoLocalesEnabled generate resources for pseudo-locales (en-XA and ar-XB)
     * @param crunchPng should we crunch PNG files
     */
    public MergedResourceWriter(
            @NonNull WorkerExecutorFacade<FileGenerationParameters> workerExecutor,
            @NonNull File rootFolder,
            @Nullable File publicFile,
            @Nullable MergingLog blameLog,
            @NonNull ResourcePreprocessor preprocessor,
            @NonNull QueueableResourceCompiler resourceCompiler,
            @NonNull File temporaryDirectory,
            @Nullable SingleFileProcessor dataBindingExpressionRemover,
            @Nullable File dataBindingLayoutOutputFolder,
            @Nullable File resourceShrinkerOutputFolder,
            boolean pseudoLocalesEnabled,
            boolean crunchPng) {
        super(rootFolder, workerExecutor);
        Preconditions.checkState(
                (dataBindingExpressionRemover == null) == (dataBindingLayoutOutputFolder == null),
                "Both dataBindingExpressionRemover and dataBindingLayoutOutputFolder need to be "
                        + "either null or non null");

        mResourceCompiler = resourceCompiler;
        mPublicFile = publicFile;
        mMergingLog = blameLog;
        mPreprocessor = preprocessor;
        mCompiling = new ConcurrentLinkedDeque<>();
        mTemporaryDirectory = temporaryDirectory;
        this.dataBindingExpressionRemover = dataBindingExpressionRemover;
        this.dataBindingLayoutOutputFolder = dataBindingLayoutOutputFolder;
        this.resourceShrinkerOutputFolder = resourceShrinkerOutputFolder;
        this.pseudoLocalesEnabled = pseudoLocalesEnabled;
        this.crunchPng = crunchPng;

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
                new WorkerExecutorFacade<FileGenerationParameters>() {
                    @Override
                    public void submit(FileGenerationParameters parameter) {
                        new FileGenerationWorkAction(parameter).run();
                    }

                    @Override
                    public void await() {}
                },
                rootFolder,
                publicFile,
                blameLogFolder != null ? new MergingLog(blameLogFolder) : null,
                preprocessor,
                QueueableResourceCompiler.NONE,
                temporaryDirectory,
                null,
                null,
                null,
                false,
                false);
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
        Map<Future, String> outstandingRequests = new HashMap<>();
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
                    Future<File> result;
                    File fileToCompile = request.getInput();

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
                                mMergingLog.logCopy(request.getInput(), strippedLayout);

                                mMergingLog.logCopy(
                                        request.getInput(),
                                        new File(
                                                databindingLayoutDir,
                                                request.getInput().getName()));
                            }

                            fileToCompile = strippedLayout;
                        }
                    }

                    // If we are going to shrink resources, the resource shrinker needs to have the
                    // final merged uncompiled file.
                    if (resourceShrinkerOutputFolder != null) {
                        File typeDir =
                                new File(resourceShrinkerOutputFolder, request.getFolderName());
                        FileUtils.mkdirs(typeDir);
                        FileUtils.copyFileToDirectory(fileToCompile, typeDir);
                    }

                    result =
                            mResourceCompiler.compile(
                                    new CompileResourceRequest(
                                            fileToCompile,
                                            request.getOutput(),
                                            request.getFolderName(),
                                            pseudoLocalesEnabled,
                                            crunchPng));

                    outstandingRequests.put(result, request.getInput().getAbsolutePath());

                    // adding to the mCompiling seems unnecessary at this point, the end() call will
                    // take care of waiting for all requests to be processed.
                    mCompiling.add(result);

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
                File outFile = first.get();
                mCompiledFileMap.put(outstandingRequests.get(first), outFile.getAbsolutePath());
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
                File file = item.getFile();
                String folderName = getFolderName(item);

                // TODO : make this also a request and use multi-threading for generation.
                if (type == DataFile.FileType.GENERATED_FILES) {
                    try {
                        FileGenerationParameters workItem =
                                new FileGenerationParameters(item, mPreprocessor);
                        if (workItem.resourceItem.getSource() != null) {
                            getExecutor().submit(workItem);
                        }
                    } catch (Exception e) {
                        throw new ConsumerException(e, item.getSource().getFile());
                    }
                }

                // enlist a new crunching request.
                mCompileResourceRequests.add(
                        new CompileResourceRequest(file, getRootFolder(), folderName));
            }
        }
    }

    public static class FileGenerationParameters implements Serializable {
        public final ResourceItem resourceItem;
        public final ResourcePreprocessor resourcePreprocessor;

        private FileGenerationParameters(
                ResourceItem resourceItem, ResourcePreprocessor resourcePreprocessor) {
            this.resourceItem = resourceItem;
            this.resourcePreprocessor = resourcePreprocessor;
        }
    }

    public static class FileGenerationWorkAction implements Runnable {

        private final FileGenerationParameters workItem;

        public FileGenerationWorkAction(FileGenerationParameters workItem) {
            this.workItem = workItem;
        }

        @Override
        public void run() {
            try {
                workItem.resourcePreprocessor.generateFile(
                        workItem.resourceItem.getFile(),
                        workItem.resourceItem.getSource().getFile());
            } catch (Exception e) {
                throw new RuntimeException(
                        "Error while processing "
                                + workItem.resourceItem.getSource().getFile()
                                + " : "
                                + e.getMessage(),
                        e);
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

                    CompileResourceRequest request =
                            new CompileResourceRequest(
                                    outFile,
                                    getRootFolder(),
                                    folderName,
                                    pseudoLocalesEnabled,
                                    crunchPng);

                    // If we are going to shrink resources, the resource shrinker needs to have the
                    // final merged uncompiled file.
                    if (resourceShrinkerOutputFolder != null) {
                        File typeDir = new File(resourceShrinkerOutputFolder, folderName);
                        FileUtils.mkdirs(typeDir);
                        FileUtils.copyFileToDirectory(outFile, typeDir);
                    }

                    if (blame != null) {
                        mMergingLog.logSource(
                                new SourceFile(mResourceCompiler.compileOutputFor(request)), blame);

                        mMergingLog.logSource(new SourceFile(outFile), blame);
                    }

                    mResourceCompiler.compile(request).get();


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

            if (resourceShrinkerOutputFolder != null) {
                removeOutFile(
                        FileUtils.join(
                                resourceShrinkerOutputFolder, folderName, folderName + DOT_XML));
            }

            // Remove the intermediate (compiled) values file.
            removeOutFile(
                    mResourceCompiler.compileOutputFor(
                            new CompileResourceRequest(
                                    FileUtils.join(
                                            getRootFolder(), folderName, folderName + DOT_XML),
                                    getRootFolder(),
                                    folderName)));
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
            return mResourceCompiler.compileOutputFor(
                    new CompileResourceRequest(file, getRootFolder(), getFolderName(resourceItem)));
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

    private void removeFileFromResourceShrinkerOutputFolder(@NonNull ResourceItem resourceItem) {
        File originalFile = resourceItem.getFile();
        File resTypeDir =
                new File(resourceShrinkerOutputFolder, originalFile.getParentFile().getName());
        File toRemove = new File(resTypeDir, originalFile.getName());
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
        if (resourceShrinkerOutputFolder != null) {
            // The file was copied for the resource shrinking and needs to be removed from there.
            removeFileFromResourceShrinkerOutputFolder(resourceItem);
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
