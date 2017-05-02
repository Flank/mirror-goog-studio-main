/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.incremental;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.internal.incremental.InstantRunBuildContext.Build;
import com.android.builder.Version;
import com.android.sdklib.AndroidVersion;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Tests for the {@link InstantRunBuildContext} */
public class InstantRunBuildContextTest {

    private static final InstantRunBuildContext.BuildIdAllocator idAllocator = System::nanoTime;

    @Test
    public void testTaskDurationRecording() throws ParserConfigurationException {
        InstantRunBuildContext buildContext = new InstantRunBuildContext(idAllocator, true);
        buildContext.startRecording(InstantRunBuildContext.TaskType.VERIFIER);
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertThat(buildContext.stopRecording(InstantRunBuildContext.TaskType.VERIFIER))
                .isAtLeast(1L);
        assertThat(buildContext.getBuildId())
                .isNotEqualTo(new InstantRunBuildContext(true).getBuildId());
    }

    @Test
    public void testPersistenceFromCleanState() throws ParserConfigurationException {
        InstantRunBuildContext buildContext = new InstantRunBuildContext(idAllocator, true);
        buildContext.setApiLevel(new AndroidVersion(23, null), null);
        String persistedState = buildContext.toXml();
        assertThat(persistedState).isNotEmpty();
        assertThat(persistedState).contains(InstantRunBuildContext.ATTR_TIMESTAMP);
    }

    @Test
    public void testFormatPresence() throws ParserConfigurationException {
        InstantRunBuildContext buildContext = new InstantRunBuildContext(idAllocator, true);
        buildContext.setApiLevel(new AndroidVersion(23, null), null);
        String persistedState = buildContext.toXml();
        assertThat(persistedState).isNotEmpty();
        assertThat(persistedState)
                .contains(
                        InstantRunBuildContext.ATTR_FORMAT
                                + "=\""
                                + InstantRunBuildContext.CURRENT_FORMAT
                                + "\"");
    }

    @Test
    public void testDuplicateEntries() throws ParserConfigurationException, IOException {
        InstantRunBuildContext context = new InstantRunBuildContext(idAllocator, true);
        context.setApiLevel(new AndroidVersion(21, null), null);
        context.addChangedFile(
                FileType.SPLIT, new File("/tmp/dependencies.apk"));
        context.addChangedFile(
                FileType.SPLIT, new File("/tmp/dependencies.apk"));
        context.close();
        Build build = context.getPreviousBuilds().iterator().next();
        assertThat(build.getArtifacts()).hasSize(1);
        assertThat(build.getArtifacts().get(0).getType()).isEqualTo(
                FileType.SPLIT);
    }

    @Test
    public void testLoadingFromCleanState()
            throws ParserConfigurationException, SAXException, IOException {
        InstantRunBuildContext buildContext = new InstantRunBuildContext(idAllocator, true);
        buildContext.setApiLevel(new AndroidVersion(23, null), null);
        File file = new File("/path/to/non/existing/file");
        buildContext.loadFromXmlFile(file);
        assertThat(buildContext.getBuildId()).isAtLeast(1L);
        assertThat(buildContext.getVerifierResult())
                .isEqualTo(InstantRunVerifierStatus.INITIAL_BUILD);
    }

    @Test
    public void testLoadingFromADifferentPluginVersion() throws Exception {
        String xml;
        {
            InstantRunBuildContext context = new InstantRunBuildContext(idAllocator, true);
            context.setApiLevel(new AndroidVersion(23, null), null);
            context.addChangedFile(
                    FileType.MAIN, new File("/tmp/main.apk"));
            context.close();
            assertThat(context.getPreviousBuilds()).isNotEmpty();
            xml = context.toXml();
        }
        xml = xml.replace(Version.ANDROID_GRADLE_PLUGIN_VERSION, "Other");
        {
            InstantRunBuildContext context = new InstantRunBuildContext(idAllocator, true);
            context.setApiLevel(new AndroidVersion(23, null), null);
            context.loadFromXml(xml);
            assertThat(context.getVerifierResult())
                    .isEqualTo(InstantRunVerifierStatus.INITIAL_BUILD);
            assertThat(context.getPreviousBuilds()).isEmpty();
        }
    }

    @Test
    public void testLoadingFromPreviousState()
            throws IOException, ParserConfigurationException, SAXException {
        File tmpFile = createMarkedBuildInfo();

        InstantRunBuildContext newContext = new InstantRunBuildContext(idAllocator, true);
        newContext.setApiLevel(new AndroidVersion(23, null), null);

        newContext.loadFromXmlFile(tmpFile);
        String xml = newContext.toXml();
        assertThat(xml).contains(InstantRunBuildContext.ATTR_TIMESTAMP);
    }

    @Test
    public void testPersistingAndLoadingPastBuilds()
            throws IOException, ParserConfigurationException, SAXException {
        InstantRunBuildContext buildContext = new InstantRunBuildContext(idAllocator, true);
        buildContext.setApiLevel(new AndroidVersion(23, null), null);
        buildContext.setSecretToken(12345L);
        File buildInfo = createBuildInfo(buildContext);
        buildContext = new InstantRunBuildContext(idAllocator, true);
        buildContext.setApiLevel(new AndroidVersion(23, null), null);
        buildContext.loadFromXmlFile(buildInfo);
        assertThat(buildContext.getPreviousBuilds()).hasSize(1);
        saveBuildInfo(buildContext, buildInfo);

        buildContext = new InstantRunBuildContext(idAllocator, true);
        buildContext.setApiLevel(new AndroidVersion(23, null), null);
        buildContext.loadFromXmlFile(buildInfo);
        assertThat(buildContext.getSecretToken()).isEqualTo(12345L);
        assertThat(buildContext.getPreviousBuilds()).hasSize(2);
    }

    @Test
    public void testXmlFormat() throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext first = new InstantRunBuildContext(idAllocator, true);
        first.setApiLevel(new AndroidVersion(23, null), null);
        first.setDensity("xxxhdpi");
        first.addChangedFile(FileType.MAIN, new File("main.apk"));
        first.addChangedFile(FileType.SPLIT, new File("split.apk"));
        String buildInfo = first.toXml();

        InstantRunBuildContext second = new InstantRunBuildContext(idAllocator, true);
        second.setApiLevel(new AndroidVersion(23, null), null);
        second.setDensity("xhdpi");
        second.loadFromXml(buildInfo);
        second.addChangedFile(FileType.SPLIT, new File("other.apk"));
        second.addChangedFile(FileType.RELOAD_DEX, new File("reload.dex"));
        buildInfo = second.toXml();

        Document document = XmlUtils.parseDocument(buildInfo, false);
        Element instantRun = (Element) document.getFirstChild();
        assertThat(instantRun.getTagName()).isEqualTo("instant-run");
        assertThat(instantRun.getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                .isEqualTo(String.valueOf(second.getBuildId()));
        assertThat(instantRun.getAttribute(InstantRunBuildContext.ATTR_DENSITY)).isEqualTo("xhdpi");

        // check the most recent build (called second) records :
        List<Element> secondArtifacts =
                getElementsByName(instantRun, InstantRunBuildContext.TAG_ARTIFACT);
        assertThat(secondArtifacts).hasSize(2);
        assertThat(secondArtifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_TYPE))
                .isEqualTo("SPLIT");
        assertThat(secondArtifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .endsWith("other.apk");
        assertThat(secondArtifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_TYPE))
                .isEqualTo("RELOAD_DEX");
        assertThat(secondArtifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .endsWith("reload.dex");

        boolean foundFirst = false;
        NodeList childNodes = instantRun.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (item.getNodeName().equals(InstantRunBuildContext.TAG_BUILD)) {
                // there should be one build child with first build references.
                foundFirst = true;
                assertThat(((Element) item).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                        .isEqualTo(String.valueOf(first.getBuildId()));
                List<Element> firstArtifacts =
                        getElementsByName(item, InstantRunBuildContext.TAG_ARTIFACT);
                assertThat(firstArtifacts).hasSize(2);
                assertThat(firstArtifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_TYPE))
                        .isEqualTo("SPLIT_MAIN");
                assertThat(firstArtifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                        .endsWith("main.apk");
                assertThat(firstArtifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_TYPE))
                        .isEqualTo("SPLIT");
                assertThat(firstArtifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                        .endsWith("split.apk");
            }
        }
        assertThat(foundFirst).isTrue();
    }

    @Test
    public void testArtifactsPersistence()
            throws IOException, ParserConfigurationException, SAXException {
        InstantRunBuildContext buildContext = new InstantRunBuildContext(idAllocator, true);
        buildContext.setApiLevel(new AndroidVersion(23, null), null);
        buildContext.addChangedFile(FileType.MAIN,
                new File("main.apk"));
        buildContext.addChangedFile(FileType.SPLIT,
                new File("split.apk"));
        String buildInfo = buildContext.toXml();

        // check xml format, the IDE depends on it.
        buildContext = new InstantRunBuildContext(idAllocator, true);
        buildContext.setApiLevel(new AndroidVersion(23, null), null);
        buildContext.loadFromXml(buildInfo);
        assertThat(buildContext.getPreviousBuilds()).hasSize(1);
        Build build = buildContext.getPreviousBuilds().iterator().next();

        assertThat(build.getArtifacts()).hasSize(2);
        assertThat(build.getArtifacts().get(0).getType()).isEqualTo(
                FileType.SPLIT_MAIN);
        assertThat(build.getArtifacts().get(1).getType()).isEqualTo(
                FileType.SPLIT);
    }

    @Test
    public void testOldReloadPurge()
            throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext initial = new InstantRunBuildContext(idAllocator, true);
        initial.setApiLevel(new AndroidVersion(23, null), null);
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split-0.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        InstantRunBuildContext first = new InstantRunBuildContext(idAllocator, true);
        first.setApiLevel(new AndroidVersion(23, null), null);
        first.loadFromXml(buildInfo);
        first.addChangedFile(FileType.RELOAD_DEX,
                new File("reload.dex"));
        first.setVerifierStatus(InstantRunVerifierStatus.COMPATIBLE);
        first.close();
        buildInfo = first.toXml();

        InstantRunBuildContext second = new InstantRunBuildContext(idAllocator, true);
        second.setApiLevel(new AndroidVersion(23, null), null);
        second.loadFromXml(buildInfo);
        second.addChangedFile(FileType.SPLIT, new File("split.apk"));
        second.setVerifierStatus(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);

        second.close();
        buildInfo = second.toXml();
        Document document = XmlUtils.parseDocument(buildInfo, false /* namespaceAware */);

        List<Element> builds =
                getElementsByName(document.getFirstChild(), InstantRunBuildContext.TAG_BUILD);
        // initial is never purged.
        assertThat(builds).hasSize(2);
        assertThat(builds.get(1).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                .isEqualTo(String.valueOf(second.getBuildId()));
    }

    @Test
    public void testMultipleReloadCollapse()
            throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext initial = new InstantRunBuildContext(idAllocator, true);
        initial.setApiLevel(new AndroidVersion(23, null), null);
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split-0.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        InstantRunBuildContext first = new InstantRunBuildContext(idAllocator, true);
        first.setApiLevel(new AndroidVersion(23, null), null);
        first.loadFromXml(buildInfo);
        first.addChangedFile(FileType.RELOAD_DEX,
                new File("reload.dex"));
        first.setVerifierStatus(InstantRunVerifierStatus.COMPATIBLE);
        first.close();
        buildInfo = first.toXml();

        InstantRunBuildContext second = new InstantRunBuildContext(idAllocator, true);
        second.setApiLevel(new AndroidVersion(23, null), null);
        second.loadFromXml(buildInfo);
        second.addChangedFile(FileType.SPLIT, new File("split.apk"));
        second.setVerifierStatus(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);

        second.close();
        buildInfo = second.toXml();

        InstantRunBuildContext third = new InstantRunBuildContext(idAllocator, true);
        third.setApiLevel(new AndroidVersion(23, null), null);
        third.loadFromXml(buildInfo);
        third.addChangedFile(FileType.RESOURCES,
                new File("resources-debug.ap_"));
        third.addChangedFile(FileType.RELOAD_DEX, new File("reload.dex"));
        third.setVerifierStatus(InstantRunVerifierStatus.COMPATIBLE);

        third.close();
        buildInfo = third.toXml();

        InstantRunBuildContext fourth = new InstantRunBuildContext(idAllocator, true);
        fourth.setApiLevel(new AndroidVersion(23, null), null);
        fourth.loadFromXml(buildInfo);
        fourth.addChangedFile(FileType.RESOURCES,
                new File("resources-debug.ap_"));
        fourth.setVerifierStatus(InstantRunVerifierStatus.COMPATIBLE);
        fourth.close();
        buildInfo = fourth.toXml();

        Document document = XmlUtils.parseDocument(buildInfo, false /* namespaceAware */);

        List<Element> builds =
                getElementsByName(document.getFirstChild(), InstantRunBuildContext.TAG_BUILD);
        // first build should have been removed due to the coldswap presence.
        assertThat(builds).hasSize(4);
        assertThat(builds.get(1).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                .isEqualTo(String.valueOf(second.getBuildId()));
        assertThat(builds.get(2).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                .isEqualTo(String.valueOf(third.getBuildId()));
        assertThat(getElementsByName(builds.get(2), InstantRunBuildContext.TAG_ARTIFACT))
                .named("Superseded resources.ap_ artifact should be removed.")
                .hasSize(1);

    }

    @Test
    public void testOverlappingAndEmptyChanges()
            throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext initial = new InstantRunBuildContext(idAllocator, true);
        initial.setApiLevel(new AndroidVersion(25, null), null);
        initial.addChangedFile(FileType.MAIN, new File("/tmp/main.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split-0.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        InstantRunBuildContext first = new InstantRunBuildContext(idAllocator, true);
        first.setApiLevel(new AndroidVersion(25, null), null);
        first.loadFromXml(buildInfo);
        first.addChangedFile(FileType.SPLIT, new File("/tmp/split-1.apk"));
        first.addChangedFile(FileType.SPLIT, new File("/tmp/split-2.apk"));
        first.setVerifierStatus(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);
        first.close();
        buildInfo = first.toXml();

        InstantRunBuildContext second = new InstantRunBuildContext(idAllocator, true);
        second.setApiLevel(new AndroidVersion(25, null), null);
        second.loadFromXml(buildInfo);
        second.addChangedFile(FileType.SPLIT, new File("/tmp/split-2.apk"));
        second.setVerifierStatus(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);
        second.close();
        buildInfo = second.toXml();

        InstantRunBuildContext third = new InstantRunBuildContext(idAllocator, true);
        third.setApiLevel(new AndroidVersion(25, null), null);
        third.loadFromXml(buildInfo);
        third.addChangedFile(FileType.SPLIT, new File("/tmp/split-2.apk"));
        third.addChangedFile(FileType.SPLIT, new File("/tmp/split-3.apk"));
        third.setVerifierStatus(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);

        third.close();
        buildInfo = third.toXml();

        Document document = XmlUtils.parseDocument(buildInfo, false /* namespaceAware */);

        List<Element> builds =
                getElementsByName(document.getFirstChild(), InstantRunBuildContext.TAG_BUILD);
        // initial builds are never removed.
        assertThat(builds).hasSize(3);
        assertThat(builds.get(0).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                .isEqualTo(String.valueOf(initial.getBuildId()));
        List<Element> artifacts =
                getElementsByName(builds.get(0), InstantRunBuildContext.TAG_ARTIFACT);
        assertThat(artifacts).hasSize(2);
        // split-2 changes on first build is overlapped by third change.
        assertThat(artifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .isEqualTo(new File("/tmp/main.apk").getAbsolutePath());
        assertThat(artifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .isEqualTo(new File("/tmp/split-0.apk").getAbsolutePath());

        assertThat(builds.get(1).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                .isEqualTo(String.valueOf(first.getBuildId()));
        artifacts = getElementsByName(builds.get(0), InstantRunBuildContext.TAG_ARTIFACT);
        assertThat(artifacts).hasSize(2);
        // split-2 changes on first build is overlapped by third change.
        assertThat(artifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .isEqualTo(new File("/tmp/main.apk").getAbsolutePath());
        assertThat(artifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .isEqualTo(new File("/tmp/split-0.apk").getAbsolutePath());

        // second is removed.

        // third has not only split-main remaining.
        assertThat(builds.get(2).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                .isEqualTo(String.valueOf(third.getBuildId()));
        artifacts = getElementsByName(builds.get(2), InstantRunBuildContext.TAG_ARTIFACT);
        assertThat(artifacts).hasSize(2);
        // split-2 changes on first build is overlapped by third change.
        assertThat(artifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .isEqualTo(new File("/tmp/split-2.apk").getAbsolutePath());
        assertThat(artifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .isEqualTo(new File("/tmp/split-3.apk").getAbsolutePath());
    }

    @Test
    public void testTemporaryBuildProduction()
            throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext initial = new InstantRunBuildContext(idAllocator, true);
        initial.setApiLevel(new AndroidVersion(21, null), null);
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split-1.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split-2.apk"));
        String buildInfo = initial.toXml();

        InstantRunBuildContext first = new InstantRunBuildContext(idAllocator, true);
        first.setApiLevel(new AndroidVersion(21, null), null);
        first.loadFromXml(buildInfo);
        first.addChangedFile(FileType.RESOURCES, new File("/tmp/resources_ap"));
        first.close();
        String tmpBuildInfo = first.toXml(InstantRunBuildContext.PersistenceMode.TEMP_BUILD);

        InstantRunBuildContext fixed = new InstantRunBuildContext(idAllocator, true);
        fixed.setApiLevel(new AndroidVersion(21, null), null);
        fixed.loadFromXml(buildInfo);
        fixed.mergeFrom(tmpBuildInfo);
        fixed.addChangedFile(FileType.SPLIT, new File("/tmp/split-1.apk"));
        fixed.close();
        buildInfo = fixed.toXml();

        // now check we only have 2 builds...
        Document document = XmlUtils.parseDocument(buildInfo, false /* namespaceAware */);
        List<Element> builds =
                getElementsByName(document.getFirstChild(), InstantRunBuildContext.TAG_BUILD);
        // initial builds are never removed.
        // first build should have been removed due to the coldswap presence.
        assertThat(builds).hasSize(2);
        List<Element> artifacts =
                getElementsByName(builds.get(1), InstantRunBuildContext.TAG_ARTIFACT);
        assertThat(artifacts).hasSize(2);
    }


    @Test
    public void testX86InjectedArchitecture() {

        InstantRunBuildContext context = new InstantRunBuildContext(idAllocator, true);
        context.setApiLevel(new AndroidVersion(20, null), "x86");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.PRE_LOLLIPOP);

        context.setApiLevel(new AndroidVersion(21, null), "x86");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_APK);

        context.setApiLevel(new AndroidVersion(23, null), "x86");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_APK);

        context.setApiLevel(new AndroidVersion(21, null), "x86");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_APK);

        context.setApiLevel(new AndroidVersion(23, null), "x86");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_APK);

        context.setApiLevel(new AndroidVersion(21, null), "x86");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_APK);

        context.setApiLevel(new AndroidVersion(23, null), "x86");
        assertThat(context.getPatchingPolicy()).isEqualTo(InstantRunPatchingPolicy.MULTI_APK);
    }

    @Test
    public void testResourceRemovalWhenBuildingMainApp() throws Exception {
        InstantRunBuildContext context = new InstantRunBuildContext(idAllocator, true);
        context.setApiLevel(new AndroidVersion(19, null), null);

        context.addChangedFile(FileType.RESOURCES, new File("res.ap_"));
        String tempXml = context.toXml(InstantRunBuildContext.PersistenceMode.TEMP_BUILD);
        context.addChangedFile(FileType.MAIN, new File("debug.apk"));
        context.loadFromXml(tempXml);
        context.close();

        assertNotNull(context.getLastBuild());
        assertThat(context.getLastBuild().getArtifacts()).hasSize(1);
        assertThat(Iterables.getOnlyElement(context.getLastBuild().getArtifacts()).getType())
                .isEqualTo(FileType.MAIN);

    }

    @Test
    public void testFullAPKRequestWithSplits() throws Exception {
        InstantRunBuildContext initial = new InstantRunBuildContext(idAllocator, true);
        initial.setApiLevel(new AndroidVersion(25, null), null);

        // set the initial build.
        initial.addChangedFile(FileType.MAIN, new File("main.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split2.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split3.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        // re-add only the main apk.
        InstantRunBuildContext update = new InstantRunBuildContext(idAllocator, true);
        update.setApiLevel(new AndroidVersion(25, null), null);
        update.setVerifierStatus(InstantRunVerifierStatus.FULL_BUILD_REQUESTED);
        update.loadFromXml(buildInfo);
        update.addChangedFile(FileType.MAIN, new File("main.apk"));
        update.close();

        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(4);

        // now add only one split apk.
        update = new InstantRunBuildContext(idAllocator, true);
        update.setApiLevel(new AndroidVersion(25, null), null);
        update.setVerifierStatus(InstantRunVerifierStatus.FULL_BUILD_REQUESTED);
        update.loadFromXml(buildInfo);
        update.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        update.close();

        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(4);

        // and one of each type.
        update = new InstantRunBuildContext(idAllocator, true);
        update.setApiLevel(new AndroidVersion(25, null), null);
        update.setVerifierStatus(InstantRunVerifierStatus.FULL_BUILD_REQUESTED);
        update.loadFromXml(buildInfo);
        update.addChangedFile(FileType.MAIN, new File("main.apk"));
        update.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        update.close();

        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(4);

    }

    @Test
    public void testMainSplitReAddingWithSplitAPK() throws Exception {
        InstantRunBuildContext initial = new InstantRunBuildContext(idAllocator, true);
        initial.setApiLevel(new AndroidVersion(21, null), null);

        // set the initial build.
        initial.addChangedFile(FileType.MAIN, new File("main.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split2.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split3.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        // re-add only one of the split apk.
        InstantRunBuildContext update = new InstantRunBuildContext(idAllocator, true);
        update.setApiLevel(new AndroidVersion(21, null), null);
        update.setVerifierStatus(InstantRunVerifierStatus.METHOD_ADDED);
        update.loadFromXml(buildInfo);
        update.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        update.close();

        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(2);
        assertThat(
                        update.getLastBuild()
                                .getArtifacts()
                                .stream()
                                .map(InstantRunBuildContext.Artifact::getType)
                                .collect(Collectors.toList()))
                .containsExactlyElementsIn(ImmutableList.of(FileType.SPLIT_MAIN, FileType.SPLIT));
    }

    @Test
    public void testMainSplitNoReAddingWithAlreadyPresent() throws Exception {
        InstantRunBuildContext initial = new InstantRunBuildContext(idAllocator, true);
        initial.setApiLevel(new AndroidVersion(21, null), null);

        // set the initial build.
        initial.addChangedFile(FileType.MAIN, new File("main.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split2.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split3.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        // re-add only the main apk and a split
        InstantRunBuildContext update = new InstantRunBuildContext(idAllocator, true);
        update.setApiLevel(new AndroidVersion(21, null), null);
        update.setVerifierStatus(InstantRunVerifierStatus.METHOD_ADDED);
        update.loadFromXml(buildInfo);
        update.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        update.addChangedFile(FileType.MAIN, new File("main.apk"));
        update.close();

        // make sure SPLIT_MAIN is not added twice.
        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(2);
        assertThat(
                        update.getLastBuild()
                                .getArtifacts()
                                .stream()
                                .map(InstantRunBuildContext.Artifact::getType)
                                .collect(Collectors.toList()))
                .containsExactlyElementsIn(ImmutableList.of(FileType.SPLIT_MAIN, FileType.SPLIT));
    }

    @Test
    public void testMainSplitNoReAddingWithSplitAPK() throws Exception {
        InstantRunBuildContext initial = new InstantRunBuildContext(idAllocator, true);
        initial.setApiLevel(new AndroidVersion(25, null), null);

        // set the initial build.
        initial.addChangedFile(FileType.MAIN, new File("main.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split2.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split3.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        // re-add only one of the split apk.
        InstantRunBuildContext update = new InstantRunBuildContext(idAllocator, true);
        update.setApiLevel(new AndroidVersion(25, null), null);
        update.setVerifierStatus(InstantRunVerifierStatus.METHOD_ADDED);
        update.loadFromXml(buildInfo);
        update.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        update.close();

        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(1);
        assertThat(
                        update.getLastBuild()
                                .getArtifacts()
                                .stream()
                                .map(InstantRunBuildContext.Artifact::getType)
                                .collect(Collectors.toList()))
                .containsExactlyElementsIn(ImmutableList.of(FileType.SPLIT));
    }

    @Test
    public void testFullSplitNoFilter() throws Exception {
        InstantRunBuildContext initial = new InstantRunBuildContext(idAllocator, true);
        initial.addChangedFile(FileType.FULL_SPLIT, new File("fullSplit.apk"));
        initial.close();

        String buildInfo = initial.toXml();

        InstantRunBuildContext reloaded = new InstantRunBuildContext(idAllocator, true);
        reloaded.loadFromXml(buildInfo);
        assertThat(reloaded.getPreviousBuilds().size()).isEqualTo(1);
        assertThat(reloaded.getLastBuild().getArtifacts().size()).isEqualTo(1);
        assertThat(reloaded.getLastBuild().getArtifactForType(FileType.FULL_SPLIT)).isNotNull();
        InstantRunBuildContext.Artifact artifact = reloaded.getLastBuild().getArtifacts().get(0);
        assertThat(artifact.getType()).isEqualTo(FileType.FULL_SPLIT);
        assertThat(artifact.getLocation().getName()).isEqualTo("fullSplit.apk");
    }

    private static List<Element> getElementsByName(Node parent, String nodeName) {
        ImmutableList.Builder<Element> builder = ImmutableList.builder();
        NodeList childNodes = parent.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (item instanceof Element && item.getNodeName().equals(nodeName)) {
                builder.add((Element) item);
            }
        }
        return builder.build();
    }

    private static File createMarkedBuildInfo() throws IOException, ParserConfigurationException {
        InstantRunBuildContext originalContext = new InstantRunBuildContext(idAllocator, true);
        originalContext.setApiLevel(new AndroidVersion(23, null), null);
        return createBuildInfo(originalContext);
    }

    private static File createBuildInfo(InstantRunBuildContext context)
            throws IOException, ParserConfigurationException {
        File tmpFile = File.createTempFile("InstantRunBuildContext", "tmp");
        saveBuildInfo(context, tmpFile);
        tmpFile.deleteOnExit();
        return tmpFile;
    }

    private static void saveBuildInfo(InstantRunBuildContext context, File buildInfo)
            throws IOException, ParserConfigurationException {
        String xml = context.toXml();
        Files.write(xml, buildInfo, Charsets.UTF_8);
    }
}
