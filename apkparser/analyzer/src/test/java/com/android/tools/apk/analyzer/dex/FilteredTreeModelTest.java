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
package com.android.tools.apk.analyzer.dex;

import static com.android.tools.apk.analyzer.dex.DexFiles.getDexFile;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.testutils.TestResources;
import com.android.tools.apk.analyzer.FilteredTreeModel;
import com.android.tools.apk.analyzer.dex.tree.DexElementNode;
import com.android.tools.apk.analyzer.dex.tree.DexPackageNode;
import com.android.tools.proguard.ProguardMap;
import com.android.tools.proguard.ProguardSeedsMap;
import com.android.tools.proguard.ProguardUsagesMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import javax.swing.tree.TreeModel;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.junit.Test;

public class FilteredTreeModelTest {

    @Test
    public void fieldsAndMethodReferenceTree() throws IOException {
        DexBackedDexFile dexFile = getTestDexFile("Test2.dex");
        DexPackageNode packageTreeNode =
                new PackageTreeCreator(null, false).constructPackageTree(dexFile);

        DexViewFilters options = new DexViewFilters();
        options.setShowFields(true);
        options.setShowMethods(true);
        options.setShowReferencedNodes(true);

        FilteredTreeModel filteredTreeModel = new FilteredTreeModel<>(packageTreeNode, options);

        StringBuffer sb = new StringBuffer(100);
        dumpTree(sb, filteredTreeModel, packageTreeNode, 0);
        assertEquals(
                "root: 6,11\n"
                        + "  java: 0,4\n"
                        + "    lang: 0,3\n"
                        + "      Integer: 0,1\n"
                        + "        java.lang.Integer valueOf(int): 0,1\n"
                        + "      Object: 0,1\n"
                        + "        <init>(): 0,1\n"
                        + "      Boolean: 0,1\n"
                        + "        java.lang.Boolean valueOf(boolean): 0,1\n"
                        + "      annotation: 0,0\n"
                        + "        RetentionPolicy: 0,0\n"
                        + "          java.lang.annotation.RetentionPolicy RUNTIME: 0,0\n"
                        + "    util: 0,1\n"
                        + "      Collections: 0,1\n"
                        + "        java.util.List emptyList(): 0,1\n"
                        + "  Test2: 3,3\n"
                        + "    <init>(): 1,1\n"
                        + "    java.lang.Integer get(): 1,1\n"
                        + "    java.util.List getList(): 1,1\n"
                        + "    a aClassField: 0,0\n"
                        + "    int aField: 0,0\n"
                        + "  TestSubclass: 2,3\n"
                        + "    <init>(): 1,1\n"
                        + "    java.util.List getAnotherList(): 1,1\n"
                        + "    java.util.List getList(): 0,1\n"
                        + "  a: 1,1\n"
                        + "    <init>(): 1,1\n"
                        + "  SomeAnnotation: 0,0\n",
                sb.toString());
    }

    @Test
    public void fieldsOnlyReferenceTree() throws IOException {
        DexBackedDexFile dexFile = getTestDexFile("Test2.dex");
        DexPackageNode packageTreeNode =
                new PackageTreeCreator(null, false).constructPackageTree(dexFile);
        DexViewFilters options = new DexViewFilters();
        options.setShowFields(true);
        options.setShowMethods(false);
        options.setShowReferencedNodes(true);
        FilteredTreeModel filteredTreeModel = new FilteredTreeModel<>(packageTreeNode, options);

        StringBuffer sb = new StringBuffer(100);
        dumpTree(sb, filteredTreeModel, packageTreeNode, 0);
        assertEquals(
                "root: 6,11\n"
                        + "  java: 0,4\n"
                        + "    lang: 0,3\n"
                        + "      Integer: 0,1\n"
                        + "      Object: 0,1\n"
                        + "      Boolean: 0,1\n"
                        + "      annotation: 0,0\n"
                        + "        RetentionPolicy: 0,0\n"
                        + "          java.lang.annotation.RetentionPolicy RUNTIME: 0,0\n"
                        + "    util: 0,1\n"
                        + "      Collections: 0,1\n"
                        + "  Test2: 3,3\n"
                        + "    a aClassField: 0,0\n"
                        + "    int aField: 0,0\n"
                        + "  TestSubclass: 2,3\n"
                        + "  a: 1,1\n"
                        + "  SomeAnnotation: 0,0\n",
                sb.toString());
    }

    @Test
    public void definedOnlyReferenceTree() throws IOException {
        DexBackedDexFile dexFile = getTestDexFile("Test2.dex");
        DexPackageNode packageTreeNode =
                new PackageTreeCreator(null, false).constructPackageTree(dexFile);
        DexViewFilters options = new DexViewFilters();
        options.setShowFields(true);
        options.setShowMethods(true);
        options.setShowReferencedNodes(false);
        FilteredTreeModel filteredTreeModel = new FilteredTreeModel<>(packageTreeNode, options);

        StringBuffer sb = new StringBuffer(100);
        dumpTree(sb, filteredTreeModel, packageTreeNode, 0);
        assertEquals(
                "root: 6,11\n"
                        + "  Test2: 3,3\n"
                        + "    <init>(): 1,1\n"
                        + "    java.lang.Integer get(): 1,1\n"
                        + "    java.util.List getList(): 1,1\n"
                        + "    a aClassField: 0,0\n"
                        + "    int aField: 0,0\n"
                        + "  TestSubclass: 2,3\n"
                        + "    <init>(): 1,1\n"
                        + "    java.util.List getAnotherList(): 1,1\n"
                        + "  a: 1,1\n"
                        + "    <init>(): 1,1\n"
                        + "  SomeAnnotation: 0,0\n",
                sb.toString());
    }

    @Test
    public void methodsOnlyReferenceTree() throws IOException {
        DexBackedDexFile dexFile = getTestDexFile("Test2.dex");
        DexPackageNode packageTreeNode =
                new PackageTreeCreator(null, false).constructPackageTree(dexFile);
        DexViewFilters options = new DexViewFilters();
        options.setShowFields(false);
        options.setShowMethods(true);
        options.setShowReferencedNodes(true);
        FilteredTreeModel filteredTreeModel = new FilteredTreeModel<>(packageTreeNode, options);

        StringBuffer sb = new StringBuffer(100);
        dumpTree(sb, filteredTreeModel, packageTreeNode, 0);
        assertEquals(
                "root: 6,11\n"
                        + "  java: 0,4\n"
                        + "    lang: 0,3\n"
                        + "      Integer: 0,1\n"
                        + "        java.lang.Integer valueOf(int): 0,1\n"
                        + "      Object: 0,1\n"
                        + "        <init>(): 0,1\n"
                        + "      Boolean: 0,1\n"
                        + "        java.lang.Boolean valueOf(boolean): 0,1\n"
                        + "      annotation: 0,0\n"
                        + "        RetentionPolicy: 0,0\n"
                        + "    util: 0,1\n"
                        + "      Collections: 0,1\n"
                        + "        java.util.List emptyList(): 0,1\n"
                        + "  Test2: 3,3\n"
                        + "    <init>(): 1,1\n"
                        + "    java.lang.Integer get(): 1,1\n"
                        + "    java.util.List getList(): 1,1\n"
                        + "  TestSubclass: 2,3\n"
                        + "    <init>(): 1,1\n"
                        + "    java.util.List getAnotherList(): 1,1\n"
                        + "    java.util.List getList(): 0,1\n"
                        + "  a: 1,1\n"
                        + "    <init>(): 1,1\n"
                        + "  SomeAnnotation: 0,0\n",
                sb.toString());
    }

    @Test
    public void removedNodesReferenceTree() throws IOException, ParseException {
        DexBackedDexFile dexFile = getTestDexFile("Test2.dex");
        Path mapPath = TestResources.getFile("/Test2_mapping.txt").toPath();
        ProguardMap map = new ProguardMap();
        map.readFromReader(Files.newBufferedReader(mapPath));

        mapPath = TestResources.getFile("/Test2_seeds.txt").toPath();
        ProguardSeedsMap seedsMap = ProguardSeedsMap.parse(Files.newBufferedReader(mapPath));

        mapPath = TestResources.getFile("/Test2_usage.txt").toPath();
        ProguardUsagesMap usageMap = ProguardUsagesMap.parse(Files.newBufferedReader(mapPath));

        ProguardMappings proguardMappings = new ProguardMappings(map, seedsMap, usageMap);
        DexPackageNode packageTreeNode =
                new PackageTreeCreator(proguardMappings, true).constructPackageTree(dexFile);
        DexViewFilters options = new DexViewFilters();
        options.setShowFields(true);
        options.setShowMethods(true);
        options.setShowReferencedNodes(true);
        options.setShowRemovedNodes(false);
        FilteredTreeModel filteredTreeModel = new FilteredTreeModel(packageTreeNode, options);

        StringBuffer sb = new StringBuffer(100);
        dumpTree(sb, filteredTreeModel, packageTreeNode, 0);
        assertEquals(
                "root: 6,11\n"
                        + "  java: 0,4\n"
                        + "    lang: 0,3\n"
                        + "      Integer: 0,1\n"
                        + "        java.lang.Integer valueOf(int): 0,1\n"
                        + "      Object: 0,1\n"
                        + "        <init>(): 0,1\n"
                        + "      Boolean: 0,1\n"
                        + "        java.lang.Boolean valueOf(boolean): 0,1\n"
                        + "      annotation: 0,0\n"
                        + "        RetentionPolicy: 0,0\n"
                        + "          java.lang.annotation.RetentionPolicy RUNTIME: 0,0\n"
                        + "    util: 0,1\n"
                        + "      Collections: 0,1\n"
                        + "        java.util.List emptyList(): 0,1\n"
                        + "  Test2: 3,3\n"
                        + "    <init>(): 1,1\n"
                        + "    java.lang.Integer get(): 1,1\n"
                        + "    java.util.List getList(): 1,1\n"
                        + "    AnotherClass aClassField: 0,0\n"
                        + "    int aField: 0,0\n"
                        + "  TestSubclass: 2,3\n"
                        + "    <init>(): 1,1\n"
                        + "    java.util.List getAnotherList(): 1,1\n"
                        + "    java.util.List getList(): 0,1\n"
                        + "  AnotherClass: 1,1\n"
                        + "    <init>(): 1,1\n"
                        + "  SomeAnnotation: 0,0\n",
                sb.toString());

        options.setShowRemovedNodes(true);
        sb.setLength(0);
        dumpTree(sb, filteredTreeModel, packageTreeNode, 0);
        assertEquals(
                "root: 6,11\n"
                        + "  java: 0,4\n"
                        + "    lang: 0,3\n"
                        + "      Integer: 0,1\n"
                        + "        java.lang.Integer valueOf(int): 0,1\n"
                        + "      Object: 0,1\n"
                        + "        <init>(): 0,1\n"
                        + "      Boolean: 0,1\n"
                        + "        java.lang.Boolean valueOf(boolean): 0,1\n"
                        + "      annotation: 0,0\n"
                        + "        RetentionPolicy: 0,0\n"
                        + "          java.lang.annotation.RetentionPolicy RUNTIME: 0,0\n"
                        + "    util: 0,1\n"
                        + "      Collections: 0,1\n"
                        + "        java.util.List emptyList(): 0,1\n"
                        + "  Test2: 3,3\n"
                        + "    <init>(): 1,1\n"
                        + "    java.lang.Integer get(): 1,1\n"
                        + "    java.util.List getList(): 1,1\n"
                        + "    AnotherClass aClassField: 0,0\n"
                        + "    int aField: 0,0\n"
                        + "  TestSubclass: 2,3\n"
                        + "    <init>(): 1,1\n"
                        + "    java.util.List getAnotherList(): 1,1\n"
                        + "    java.util.List getList(): 0,1\n"
                        + "  AnotherClass: 1,1\n"
                        + "    <init>(): 1,1\n"
                        + "    AnotherClass(int,TestSubclass): 0,0\n"
                        + "  SomeAnnotation: 0,0\n"
                        + "  RemovedSubclass: 0,0\n",
                sb.toString());
    }

    @NonNull
    private static DexBackedDexFile getTestDexFile(String filename) throws IOException {
        Path dexPath = TestResources.getFile("/" + filename).toPath();
        return getDexFile(Files.readAllBytes(dexPath));
    }

    private static void dumpTree(
            StringBuffer sb, @NonNull TreeModel model, DexElementNode node, int depth) {
        for (int i = 0; i < depth * 2; i++) {
            sb.append(' ');
        }
        sb.append(node.getName());
        sb.append(": ");
        sb.append(node.getMethodDefinitionsCount());
        sb.append(',');
        sb.append(node.getMethodReferencesCount());
        sb.append('\n');

        int count = model.getChildCount(node);
        for (int i = 0; i < count; i++) {
            dumpTree(sb, model, (DexElementNode) model.getChild(node, i), depth + 1);
        }
    }
}
