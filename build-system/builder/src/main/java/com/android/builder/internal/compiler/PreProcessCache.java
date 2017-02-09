/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.internal.compiler;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Immutable;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.repository.Revision;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

abstract class PreProcessCache<T extends PreProcessCache.Key> {

    private static final String NODE_ITEMS = "items";
    private static final String NODE_ITEM = "item";
    private static final String NODE_DEX = "dex";
    private static final String ATTR_VERSION = "version";
    private static final String ATTR_JAR = "jar";
    private static final String ATTR_DEX = "dex";
    private static final String ATTR_SHA1 = "sha1";
    private static final String ATTR_REVISION = "revision";

    private static final String XML_VERSION = "2";

    interface BaseItem {
        @NonNull
        File getSourceFile();

        @NonNull
        List<File> getOutputFiles();

        @Nullable
        HashCode getSourceHash();

        boolean areOutputFilesPresent();

    }

    /**
     * Items representing jar/dex files that have been processed during a build.
     */
    @Immutable
    protected static class Item implements BaseItem {
        @NonNull
        private final File mSourceFile;
        @NonNull
        private final List<File> mOutputFiles;
        @NonNull
        private final CountDownLatch mLatch;

        Item(
                @NonNull File sourceFile,
                @NonNull List<File> outputFiles,
                @NonNull CountDownLatch latch) {
            mSourceFile = sourceFile;
            mOutputFiles = Lists.newArrayList(outputFiles);
            mLatch = latch;
        }

        Item(
                @NonNull File sourceFile,
                @NonNull CountDownLatch latch) {
            mSourceFile = sourceFile;
            mOutputFiles = Lists.newArrayList();
            mLatch = latch;
        }

        @Override
        @NonNull
        public File getSourceFile() {
            return mSourceFile;
        }

        @Override
        @NonNull
        public List<File> getOutputFiles() {
            return mOutputFiles;
        }

        @Nullable
        @Override
        public HashCode getSourceHash() {
            return null;
        }

        @NonNull
        CountDownLatch getLatch() {
            return mLatch;
        }

        @Override
        public boolean areOutputFilesPresent() {
            boolean filesOk = !mOutputFiles.isEmpty();
            for (File outputFile : mOutputFiles) {
                filesOk &= outputFile.isFile();
            }
            return filesOk;
        }

        @Override
        public String toString() {
            return "Item{" +
                    "mOutputFiles=" + mOutputFiles +
                    ", mSourceFile=" + mSourceFile +
                    '}';
        }
    }

    /**
     * Items representing jar/dex files that have been processed in a previous build, then were
     * stored in a cache file and then reloaded during the current build.
     */
    @Immutable
    private static class StoredItem implements BaseItem {
        @NonNull
        private final File mSourceFile;
        @NonNull
        private final List<File> mOutputFiles;
        @NonNull
        private final HashCode mSourceHash;

        StoredItem(
                @NonNull File sourceFile,
                @NonNull List<File> outputFiles,
                @NonNull HashCode sourceHash) {
            mSourceFile = sourceFile;
            mOutputFiles = Lists.newArrayList(outputFiles);
            mSourceHash = sourceHash;
        }

        @Override
        @NonNull
        public File getSourceFile() {
            return mSourceFile;
        }

        @Override
        @NonNull
        public List<File> getOutputFiles() {
            return mOutputFiles;
        }

        @Override
        @NonNull
        public HashCode getSourceHash() {
            return mSourceHash;
        }

        @Override
        public boolean areOutputFilesPresent() {
            boolean filesOk = !mOutputFiles.isEmpty();
            for (File outputFile : mOutputFiles) {
                filesOk &= outputFile.isFile();
            }
            return filesOk;
        }

        @Override
        public String toString() {
            return "StoredItem{" +
                    "mSourceFile=" + mSourceFile +
                    ", mOutputFiles=" + mOutputFiles +
                    ", mSourceHash=" + mSourceHash +
                    '}';
        }
    }

    /**
     * Key to store Item/StoredItem in maps.
     * The key contains the element that are used for the dex call:
     * - source file
     * - build tools revision
     * - jumbo mode
     */
    @Immutable
    protected static class Key {
        @NonNull
        private final File mSourceFile;
        @NonNull
        private final Revision mBuildToolsRevision;

        public static Key of(@NonNull File sourceFile, @NonNull Revision buildToolsRevision) {
            return new Key(sourceFile, buildToolsRevision);
        }

        Key(@NonNull File sourceFile, @NonNull Revision buildToolsRevision) {
            mSourceFile = sourceFile;
            mBuildToolsRevision = buildToolsRevision;
        }

        @NonNull
        public Revision getBuildToolsRevision() {
            return mBuildToolsRevision;
        }

        @NonNull
        public File getSourceFile() {
            return mSourceFile;
        }

        @SuppressWarnings("RedundantIfStatement")
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }

            Key key = (Key) o;

            if (!mBuildToolsRevision.equals(key.mBuildToolsRevision)) {
                return false;
            }
            if (!mSourceFile.equals(key.mSourceFile)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mSourceFile, mBuildToolsRevision);
        }
    }

    interface KeyFactory<T> {
        T of(@NonNull File sourceFile, @NonNull Revision revision, @NonNull NamedNodeMap attrMap);
    }

    @GuardedBy("this")
    private boolean mLoaded = false;

    @GuardedBy("this")
    private final Map<T, Item> mMap = Maps.newHashMap();
    @GuardedBy("this")
    private final Map<T, StoredItem> mStoredItems = Maps.newHashMap();

    @GuardedBy("this")
    private int mMisses = 0;
    @GuardedBy("this")
    private int mHits = 0;

    @NonNull
    protected abstract KeyFactory<T> getKeyFactory();

    /**
     * Loads the stored item. This can be called several times (per subproject), so only
     * the first call should do something.
     */
    public synchronized void load(@NonNull File itemStorage) {
        if (mLoaded) {
            return;
        }

        loadItems(itemStorage);

        mLoaded = true;
    }

    /**
     * Removes a cached item if exists and calls a {@link #getItem}.
     * This can happen when the generated files was removed by an external event.
     * @param itemKey the key of the item to re-generate
     */
    synchronized Pair<Item, Boolean> regenerateItem(@NonNull ILogger logger, @NonNull T itemKey) {
        mMap.remove(itemKey);
        return getItem(logger, itemKey);
    }

    /**
     * Returns an {@link Item} loaded from the cache. If no item can be found this, throws an
     * exception.
     *
     * @param itemKey the key of the item
     * @return a pair of item, boolean
     */
    synchronized Pair<Item, Boolean> getItem(@NonNull ILogger logger, @NonNull T itemKey) {

        File inputFile = itemKey.getSourceFile();
        if (inputFile.isDirectory()) {
            throw new RuntimeException(
                    String.format("Cache cannot handle folder : %s", inputFile.getAbsolutePath()));
        }

        // get the item
        Item item = mMap.get(itemKey);

        boolean newItem = false;

        if (item == null) {
            // check if we have a stored version.
            StoredItem storedItem = mStoredItems.get(itemKey);

            logger.verbose("StoredItem is %1$s", storedItem);

            if (storedItem != null) {
                // check the sha1 is still valid, and the pre-dex files are still there.
                HashCode hash = getHash(inputFile);
                logger.verbose("Hash for %1$s is %2$s", inputFile.getAbsolutePath(), hash);
                if (storedItem.areOutputFilesPresent() &&
                        storedItem.getSourceHash().equals(hash)) {

                    logger.verbose("Cached result for getItem(" + inputFile + "): "
                            + storedItem.getOutputFiles());
                    for (File f : storedItem.getOutputFiles()) {
                        logger.verbose(
                                String.format("%s l:%d ts:%d", f, f.length(), f.lastModified()));
                    }

                    // create an item where the outFile is the one stored since it
                    // represent the pre-dexed library already.
                    // Next time this lib needs to be pre-dexed, we'll use the item
                    // rather than the stored item, allowing us to not compute the sha1 again.
                    // Use a 0-count latch since there is nothing to do.
                    item = new Item(inputFile, storedItem.getOutputFiles(), new CountDownLatch(0));
                }
            }

            // if we didn't find a valid stored item, create a new one.
            if (item == null) {
                item = new Item(inputFile, new CountDownLatch(1));
                newItem = true;
            }
            mMap.put(itemKey, item);
        }

        return Pair.of(item, newItem);
    }

    @Nullable
    private static HashCode getHash(@NonNull File file) {
        try {
            return Files.hash(file, Hashing.sha1());
        } catch (IOException ignored) {
        }

        return null;
    }

    public synchronized void clear(@Nullable File itemStorage, @Nullable ILogger logger) throws
            IOException {
        if (!mMap.isEmpty()) {
            if (itemStorage != null) {
                saveItems(itemStorage);
            }

            if (logger != null) {
                logger.verbose("PREDEX CACHE HITS:   %1$d", mHits);
                logger.verbose("PREDEX CACHE MISSES: %1$d", mMisses);
            }
        }

        mMap.clear();
        mStoredItems.clear();
        mHits = 0;
        mMisses = 0;
        mLoaded = false;
    }

    private synchronized void loadItems(@NonNull File itemStorage) {
        if (!itemStorage.isFile()) {
            return;
        }

        try {
            Document document = XmlUtils.parseUtfXmlFile(itemStorage, true);

            // get the root node
            Node rootNode = document.getDocumentElement();
            if (rootNode == null || !NODE_ITEMS.equals(rootNode.getLocalName())) {
                return;
            }


            // check the version of the XML
            NamedNodeMap rootAttrMap = rootNode.getAttributes();
            Node versionAttr = rootAttrMap.getNamedItem(ATTR_VERSION);
            if (versionAttr == null || !XML_VERSION.equals(versionAttr.getNodeValue())) {
                return;
            }

            NodeList nodes = rootNode.getChildNodes();

            for (int i = 0, n = nodes.getLength(); i < n; i++) {
                Node node = nodes.item(i);

                if (node.getNodeType() != Node.ELEMENT_NODE ||
                        !NODE_ITEM.equals(node.getLocalName())) {
                    continue;
                }

                NamedNodeMap attrMap = node.getAttributes();

                File sourceFile = new File(attrMap.getNamedItem(ATTR_JAR).getNodeValue());
                Revision revision = Revision.parseRevision(attrMap.getNamedItem(
                        ATTR_REVISION).getNodeValue());

                List<File> outputFiles = Lists.newArrayList();
                NodeList dexNodes = node.getChildNodes();
                for (int j = 0, m = dexNodes.getLength(); j < m; j++) {
                    Node dexNode = dexNodes.item(j);

                    if (dexNode.getNodeType() != Node.ELEMENT_NODE ||
                            !NODE_DEX.equals(dexNode.getLocalName())) {
                        continue;
                    }

                    NamedNodeMap dexAttrMap = dexNode.getAttributes();
                    outputFiles.add(new File(dexAttrMap.getNamedItem(ATTR_DEX).getNodeValue()));
                }

                StoredItem item = new StoredItem(
                        sourceFile,
                        outputFiles,
                        HashCode.fromString(attrMap.getNamedItem(ATTR_SHA1).getNodeValue()));

                T key = getKeyFactory().of(sourceFile, revision, attrMap);

                mStoredItems.put(key, item);
            }
        } catch (Exception ignored) {
            // if we fail to read parts or any of the file, all it'll do is fail to reuse an
            // already pre-dexed library, so that's not a super big deal.
        }
    }

    private synchronized void saveItems(@NonNull File itemStorage) throws IOException {
        // write "compact" blob
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setIgnoringComments(true);
        DocumentBuilder builder;

        try {
            builder = factory.newDocumentBuilder();
            Document document = builder.newDocument();

            Node rootNode = document.createElement(NODE_ITEMS);
            document.appendChild(rootNode);

            // Set the version
            Attr attr = document.createAttribute(ATTR_VERSION);
            attr.setValue(XML_VERSION);
            rootNode.getAttributes().setNamedItem(attr);

            Set<T> keys = Sets.newHashSetWithExpectedSize(mMap.size() + mStoredItems.size());
            keys.addAll(mMap.keySet());
            keys.addAll(mStoredItems.keySet());

            for (T key : keys) {
                Item item = mMap.get(key);

                if (item != null) {

                    Node itemNode = createItemNode(document,
                            key,
                            item);
                    if (itemNode != null) {
                        rootNode.appendChild(itemNode);
                    }

                } else {
                    StoredItem storedItem = mStoredItems.get(key);
                    // check that the source file still exists in order to avoid
                    // storing libraries that are gone.
                    if (storedItem != null &&
                            storedItem.getSourceFile().isFile() &&
                            storedItem.areOutputFilesPresent()) {
                        Node itemNode = createItemNode(document,
                                key,
                                storedItem);
                        if (itemNode != null) {
                            rootNode.appendChild(itemNode);
                        }
                    }
                }
            }

            String content = XmlPrettyPrinter.prettyPrint(document, true);

            FileUtils.mkdirs(itemStorage.getParentFile());
            Files.write(content, itemStorage, Charsets.UTF_8);
        } catch (ParserConfigurationException ignored) {
        }
    }

    @Nullable
    protected Node createItemNode(
            @NonNull Document document,
            @NonNull T itemKey,
            @NonNull BaseItem item) throws IOException {
        if (!item.areOutputFilesPresent()) {
            return null;
        }

        Node itemNode = document.createElement(NODE_ITEM);

        Attr attr = document.createAttribute(ATTR_JAR);
        attr.setValue(item.getSourceFile().getPath());
        itemNode.getAttributes().setNamedItem(attr);

        attr = document.createAttribute(ATTR_REVISION);
        attr.setValue(itemKey.getBuildToolsRevision().toString());
        itemNode.getAttributes().setNamedItem(attr);

        HashCode hashCode = item.getSourceHash();
        if (hashCode == null) {
            try {
                hashCode = Files.hash(item.getSourceFile(), Hashing.sha1());
            } catch (IOException ex) {
                // If we can't compute the hash for whatever reason, simply skip this entry.
                return null;
            }
        }
        attr = document.createAttribute(ATTR_SHA1);
        attr.setValue(hashCode.toString());
        itemNode.getAttributes().setNamedItem(attr);

        for (File dexFile : item.getOutputFiles()) {

            Node dexNode = document.createElement(NODE_DEX);
            itemNode.appendChild(dexNode);

            attr = document.createAttribute(ATTR_DEX);
            attr.setValue(dexFile.getPath());
            dexNode.getAttributes().setNamedItem(attr);
        }

        return itemNode;
    }

    synchronized void incrementMisses() {
        mMisses++;
    }

    synchronized void incrementHits() {
        mHits++;
    }

    @VisibleForTesting
    /*package*/ synchronized int getMisses() {
        return mMisses;
    }

    @VisibleForTesting
    /*package*/ synchronized int getHits() {
        return mHits;
    }

}
