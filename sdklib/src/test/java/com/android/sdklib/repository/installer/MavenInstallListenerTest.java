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

package com.android.sdklib.repository.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.repository.Revision;
import com.android.repository.api.ConstantSourceProvider;
import com.android.repository.api.Installer;
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Uninstaller;
import com.android.repository.impl.installer.BasicInstallerFactory;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeInstallListenerFactory;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeProgressRunner;
import com.android.repository.testframework.FakeSettingsController;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.file.InMemoryFileSystems;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

/** Tests for {@link MavenInstallListener} */
public class MavenInstallListenerTest {

    private static final String POM_1_2_3_CONTENTS =
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                    + "  <modelVersion>4.0.0</modelVersion>\n"
                    + "  <groupId>com.android.group1</groupId>\n"
                    + "  <artifactId>artifact1</artifactId>\n"
                    + "  <version>1.2.3</version>\n"
                    + "  <packaging>pom</packaging>\n"
                    + "  <name>test package 1 version 1.2.3</name>\n"
                    + "</project>";

    private static final String POM_1_0_0_CONTENTS =
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                    + "  <modelVersion>4.0.0</modelVersion>\n"
                    + "  <groupId>com.android.group1</groupId>\n"
                    + "  <artifactId>artifact1</artifactId>\n"
                    + "  <version>1.0.0</version>\n"
                    + "  <packaging>pom</packaging>\n"
                    + "  <name>test package 1 version 1.2.3</name>\n"
                    + "</project>";

    private final Path root = InMemoryFileSystems.createInMemoryFileSystemAndFolder("repo");

    @Test
    public void testInstallFirst() throws Exception {
        RepoManager mgr = new RepoManagerImpl();
        mgr.registerSchemaModule(AndroidSdkHandler.getCommonModule());
        mgr.registerSchemaModule(AndroidSdkHandler.getAddonModule());
        mgr.setLocalPath(root);
        FakeDownloader downloader = new FakeDownloader(root.getRoot().resolve("tmp"));
        URL repoUrl = new URL("http://example.com/sample.xml");

        // The repo we're going to download
        downloader.registerUrl(repoUrl,
                getClass().getResourceAsStream("testdata/remote_maven_repo.xml"));

        // Create the archive and register the URL
        URL archiveUrl = new URL("http://example.com/2/arch1");
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
        ZipOutputStream zos = new ZipOutputStream(baos);
        zos.putNextEntry(new ZipEntry("top-level/a"));
        zos.write("contents1".getBytes());
        zos.closeEntry();
        zos.putNextEntry(new ZipEntry("top-level/artifact1-1.2.3.pom"));
        zos.write(POM_1_2_3_CONTENTS.getBytes());
        zos.closeEntry();
        zos.close();
        ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
        downloader.registerUrl(archiveUrl, is);

        // Register a source provider to get the repo
        mgr.registerSourceProvider(new ConstantSourceProvider(repoUrl.toString(), "sample",
                ImmutableList.of(AndroidSdkHandler.getAddonModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.loadSynchronously(
                RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                new FakeSettingsController(false));

        runner.getProgressIndicator().assertNoErrorsOrWarnings();

        RepositoryPackages pkgs = mgr.getPackages();

        RemotePackage p = pkgs.getRemotePackages()
                .get("m2repository;com;android;group1;artifact1;1.2.3");
        // Install
        BasicInstallerFactory factory = new BasicInstallerFactory();
        factory.setListenerFactory(new FakeInstallListenerFactory(new MavenInstallListener()));
        Installer installer = factory.createInstaller(p, mgr, downloader);
        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        installer.prepare(progress.createSubProgress(0.5));
        installer.complete(progress.createSubProgress(1));
        progress.assertNoErrorsOrWarnings();

        Path artifactRoot = root.resolve("m2repository/com/android/group1/artifact1");
        Path mavenMetadata = artifactRoot.resolve("maven-metadata.xml");
        MavenInstallListener.MavenMetadata metadata =
                MavenInstallListener.unmarshal(
                        mavenMetadata, MavenInstallListener.MavenMetadata.class, progress);

        assertEquals("artifact1", metadata.artifactId);
        assertEquals("com.android.group1", metadata.groupId);
        assertEquals("1.2.3", metadata.versioning.release);
        assertEquals(ImmutableList.of("1.2.3"), metadata.versioning.versions.version);

        List<Path> contents =
                Files.list(root.resolve("m2repository/com/android/group1/artifact1/1.2.3"))
                        .collect(Collectors.toList());

        // Ensure it was installed on the filesystem
        assertEquals(
                ImmutableList.of(
                        root.resolve("m2repository/com/android/group1/artifact1/1.2.3/a"),
                        root.resolve(
                                "m2repository/com/android/group1/artifact1/1.2.3/artifact1-1.2.3.pom"),
                        root.resolve(
                                "m2repository/com/android/group1/artifact1/1.2.3/package.xml")),
                contents);

        // Reload
        mgr.loadSynchronously(
                0,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                new FakeSettingsController(false));

        // Ensure it was recognized as a package.
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(1, locals.size());
        assertTrue(locals.containsKey("m2repository;com;android;group1;artifact1;1.2.3"));
        LocalPackage newPkg = locals.get("m2repository;com;android;group1;artifact1;1.2.3");
        assertEquals("maven package", newPkg.getDisplayName());
        assertEquals(new Revision(3), newPkg.getVersion());

    }

    @Test
    public void testInstallAdditional() throws Exception {
        InMemoryFileSystems.recordExistingFile(
                root.resolve("m2repository/com/android/group1/artifact1/maven-metadata.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<metadata>\n"
                        + "  <groupId>com.android.group1</groupId>\n"
                        + "  <artifactId>artifact1</artifactId>\n"
                        + "  <release>1.0.0</release>\n"
                        + "  <versioning>\n"
                        + "    <versions>\n"
                        + "      <version>1.0.0</version>\n"
                        + "    </versions>\n"
                        + "    <lastUpdated>20151006162600</lastUpdated>\n"
                        + "  </versioning>\n"
                        + "</metadata>\n");
        InMemoryFileSystems.recordExistingFile(
                root.resolve("m2repository/com/android/group1/artifact1/1.0.0/artifact1-1.0.0.pom"),
                POM_1_0_0_CONTENTS);
        RepoManager mgr = new RepoManagerImpl();
        mgr.registerSchemaModule(AndroidSdkHandler.getCommonModule());
        mgr.registerSchemaModule(AndroidSdkHandler.getAddonModule());
        mgr.setLocalPath(root);
        FakeDownloader downloader = new FakeDownloader(root.getRoot().resolve("tmp"));
        URL repoUrl = new URL("http://example.com/sample.xml");

        // The repo we're going to download
        downloader.registerUrl(repoUrl,
                getClass().getResourceAsStream("testdata/remote_maven_repo.xml"));

        // Create the archive and register the URL
        URL archiveUrl = new URL("http://example.com/2/arch1");
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
        ZipOutputStream zos = new ZipOutputStream(baos);
        zos.putNextEntry(new ZipEntry("top-level/a"));
        zos.write("contents1".getBytes());
        zos.closeEntry();
        zos.putNextEntry(new ZipEntry("top-level/artifact1-1.2.3.pom"));
        zos.write(POM_1_2_3_CONTENTS.getBytes());
        zos.closeEntry();
        zos.close();
        ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
        downloader.registerUrl(archiveUrl, is);

        // Register a source provider to get the repo
        mgr.registerSourceProvider(new ConstantSourceProvider(repoUrl.toString(), "sample",
                ImmutableList.of(AndroidSdkHandler.getAddonModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.loadSynchronously(
                RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                new FakeSettingsController(false));

        runner.getProgressIndicator().assertNoErrorsOrWarnings();

        RepositoryPackages pkgs = mgr.getPackages();

        RemotePackage remotePackage = pkgs.getRemotePackages()
                .get("m2repository;com;android;group1;artifact1;1.2.3");

        // Install
        InstallerFactory factory = new BasicInstallerFactory();
        factory.setListenerFactory(new FakeInstallListenerFactory(new MavenInstallListener()));
        Installer installer = factory.createInstaller(remotePackage, mgr, downloader);
        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        installer.prepare(progress.createSubProgress(0.5));
        installer.complete(progress.createSubProgress(1));
        progress.assertNoErrorsOrWarnings();

        Path artifactRoot = root.resolve("m2repository/com/android/group1/artifact1");
        Path mavenMetadata = artifactRoot.resolve("maven-metadata.xml");
        MavenInstallListener.MavenMetadata metadata =
                MavenInstallListener.unmarshal(
                        mavenMetadata, MavenInstallListener.MavenMetadata.class, progress);
        progress.assertNoErrorsOrWarnings();
        assertEquals("artifact1", metadata.artifactId);
        assertEquals("com.android.group1", metadata.groupId);
        assertEquals("1.2.3", metadata.versioning.release);
        assertEquals(ImmutableList.of("1.0.0", "1.2.3"), metadata.versioning.versions.version);

        List<Path> contents =
                Files.list(root.resolve("m2repository/com/android/group1/artifact1/1.2.3"))
                        .collect(Collectors.toList());

        // Ensure it was installed on the filesystem
        assertEquals(
                ImmutableList.of(
                        root.resolve("m2repository/com/android/group1/artifact1/1.2.3/a"),
                        root.resolve(
                                "m2repository/com/android/group1/artifact1/1.2.3/artifact1-1.2.3.pom"),
                        root.resolve(
                                "m2repository/com/android/group1/artifact1/1.2.3/package.xml")),
                contents);
        // Reload
        mgr.loadSynchronously(
                0,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                new FakeSettingsController(false));

        // Ensure it was recognized as a package.
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(1, locals.size());
        assertTrue(locals.containsKey("m2repository;com;android;group1;artifact1;1.2.3"));
        LocalPackage newPkg = locals.get("m2repository;com;android;group1;artifact1;1.2.3");
        assertEquals("maven package", newPkg.getDisplayName());
        assertEquals(new Revision(3), newPkg.getVersion());
    }

    @Test
    public void testRemove() {
        InMemoryFileSystems.recordExistingFile(
                root.resolve("m2repository/com/android/group1/artifact1/1.2.3/package.xml"),
                "<repo:sdk-addon\n"
                        + "        xmlns:repo=\"http://schemas.android.com/sdk/android/repo/addon2/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "\n"
                        + "    <localPackage path=\"m2repository;com;android;group1;artifact1;1.2.3\">\n"
                        + "        <type-details xsi:type=\"repo:extraDetailsType\">\n"
                        + "            <vendor>\n"
                        + "                <id>cyclop</id>\n"
                        + "                <display>The big bus</display>\n"
                        + "            </vendor>\n"
                        + "        </type-details>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>A Maven artifact</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:sdk-addon>");
        InMemoryFileSystems.recordExistingFile(
                root.resolve("m2repository/com/android/group1/artifact1/1.2.3/artifact1-1.2.3.pom"),
                POM_1_2_3_CONTENTS);
        InMemoryFileSystems.recordExistingFile(
                root.resolve("m2repository/com/android/group1/artifact1/1.0.0/package.xml"),
                "<repo:sdk-addon\n"
                        + "        xmlns:repo=\"http://schemas.android.com/sdk/android/repo/addon2/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "\n"
                        + "    <localPackage path=\"m2repository;com;android;group1;artifact1;1.0.0\">\n"
                        + "        <type-details xsi:type=\"repo:extraDetailsType\">\n"
                        + "            <vendor>\n"
                        + "                <id>cyclop</id>\n"
                        + "                <display>The big bus</display>\n"
                        + "            </vendor>\n"
                        + "        </type-details>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>Another Maven artifact</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:sdk-addon>");
        InMemoryFileSystems.recordExistingFile(
                root.resolve("m2repository/com/android/group1/artifact1/1.0.0/artifact1-1.0.0.pom"),
                POM_1_0_0_CONTENTS);

        Path metadataPath =
                root.resolve("m2repository/com/android/group1/artifact1/maven-metadata.xml");
        InMemoryFileSystems.recordExistingFile(
                metadataPath,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<metadata>\n"
                        + "  <groupId>com.android.group1</groupId>\n"
                        + "  <artifactId>artifact1</artifactId>\n"
                        + "  <release>1.2.3</release>\n"
                        + "  <versioning>\n"
                        + "    <versions>\n"
                        + "      <version>1.2.3</version>\n"
                        + "      <version>1.0.0</version>\n"
                        + "    </versions>\n"
                        + "    <lastUpdated>20151006162600</lastUpdated>\n"
                        + "  </versioning>\n"
                        + "</metadata>\n");

        RepoManager mgr = new RepoManagerImpl();
        mgr.setLocalPath(root);
        mgr.registerSchemaModule(AndroidSdkHandler.getCommonModule());
        mgr.registerSchemaModule(AndroidSdkHandler.getAddonModule());

        FakeProgressRunner runner = new FakeProgressRunner();
        FakeDownloader downloader = new FakeDownloader(root.getRoot().resolve("tmp"));
        // Reload
        mgr.loadSynchronously(
                0,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                new FakeSettingsController(false));
        runner.getProgressIndicator().assertNoErrorsOrWarnings();

        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(2, locals.size());
        LocalPackage p = locals.get("m2repository;com;android;group1;artifact1;1.2.3");
        assertNotNull(p);
        InstallerFactory factory = new BasicInstallerFactory();
        factory.setListenerFactory(new FakeInstallListenerFactory(new MavenInstallListener()));
        Uninstaller uninstaller = factory.createUninstaller(p, mgr);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        uninstaller.prepare(progress);
        uninstaller.complete(progress);
        progress.assertNoErrorsOrWarnings();
        MavenInstallListener.MavenMetadata metadata =
                MavenInstallListener.unmarshal(
                        metadataPath, MavenInstallListener.MavenMetadata.class, progress);
        progress.assertNoErrorsOrWarnings();
        assertNotNull(metadata);
        assertEquals(ImmutableList.of("1.0.0"), metadata.versioning.versions.version);
        assertEquals("1.0.0", metadata.versioning.release);
    }

    @Test
    public void testRemoveAll() {
        InMemoryFileSystems.recordExistingFile(
                root.resolve("m2repository/com/android/group1/artifact1/1.2.3/package.xml"),
                "<repo:sdk-addon\n"
                        + "        xmlns:repo=\"http://schemas.android.com/sdk/android/repo/addon2/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "\n"
                        + "    <localPackage path=\"m2repository;com;android;group1;artifact1;1.2.3\">\n"
                        + "        <type-details xsi:type=\"repo:extraDetailsType\">\n"
                        + "            <vendor>\n"
                        + "                <id>cyclop</id>\n"
                        + "                <display>The big bus</display>\n"
                        + "            </vendor>\n"
                        + "        </type-details>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>A Maven artifact</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:sdk-addon>");
        InMemoryFileSystems.recordExistingFile(
                root.resolve("m2repository/com/android/group1/artifact1/1.2.3/artifact1-1.2.3.pom"),
                POM_1_2_3_CONTENTS);

        Path metadataPath =
                root.resolve("m2repository/com/android/group1/artifact1/maven-metadata.xml");
        InMemoryFileSystems.recordExistingFile(
                metadataPath,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<metadata>\n"
                        + "  <groupId>com.android.group1</groupId>\n"
                        + "  <artifactId>artifact1</artifactId>\n"
                        + "  <release>1.2.3</release>\n"
                        + "  <versioning>\n"
                        + "    <versions>\n"
                        + "      <version>1.2.3</version>\n"
                        + "    </versions>\n"
                        + "    <lastUpdated>20151006162600</lastUpdated>\n"
                        + "  </versioning>\n"
                        + "</metadata>\n");

        RepoManager mgr = new RepoManagerImpl();
        mgr.setLocalPath(root);
        mgr.registerSchemaModule(AndroidSdkHandler.getCommonModule());
        mgr.registerSchemaModule(AndroidSdkHandler.getAddonModule());

        FakeProgressRunner runner = new FakeProgressRunner();
        FakeDownloader downloader = new FakeDownloader(root.getRoot().resolve("tmp"));
        // Reload
        mgr.loadSynchronously(
                0,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                new FakeSettingsController(false));
        runner.getProgressIndicator().assertNoErrorsOrWarnings();

        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(1, locals.size());
        LocalPackage p = locals.get("m2repository;com;android;group1;artifact1;1.2.3");
        assertNotNull(p);
        InstallerFactory factory = new BasicInstallerFactory();
        factory.setListenerFactory(new FakeInstallListenerFactory(new MavenInstallListener()));
        Uninstaller uninstaller = factory.createUninstaller(p, mgr);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        uninstaller.prepare(progress);
        uninstaller.complete(progress);
        progress.assertNoErrorsOrWarnings();
        assertFalse(Files.exists(metadataPath));
    }
}
