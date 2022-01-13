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

package com.android.repository.impl.local;

import static com.google.common.truth.Truth.assertThat;

import com.android.repository.Revision;
import com.android.repository.api.Dependency;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Repository;
import com.android.repository.impl.manager.LocalRepoLoader;
import com.android.repository.impl.manager.LocalRepoLoaderImpl;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.GenericFactory;
import com.android.repository.impl.meta.LocalPackageImpl;
import com.android.repository.impl.meta.RevisionType;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.testutils.file.InMemoryFileSystems;
import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import junit.framework.TestCase;

/**
 * Tests for {@link LocalRepoLoaderImpl}.
 */
public class LocalRepoTest extends TestCase {

    // Test that we can parse a basic package.
    public void testParseGeneric() throws Exception {
        Path sdkRoot =
                InMemoryFileSystems.createInMemoryFileSystemAndFolder("repo/random").getParent();
        Files.write(
                sdkRoot.resolve("random/package.xml"),
                ("<repo:repository\n"
                                + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                                + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                                + "\n"
                                + "    <license type=\"text\" id=\"license1\">\n"
                                + "        This is the license\n"
                                + "        for this platform.\n"
                                + "    </license>\n"
                                + "\n"
                                + "    <localPackage path=\"random\">\n"
                                + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                                + "        <revision>\n"
                                + "            <major>3</major>\n"
                                + "        </revision>\n"
                                + "        <display-name>The first Android platform ever</display-name>\n"
                                + "        <uses-license ref=\"license1\"/>\n"
                                + "        <dependencies>\n"
                                + "            <dependency path=\"tools\">\n"
                                + "                <min-revision>\n"
                                + "                    <major>2</major>\n"
                                + "                    <micro>1</micro>\n"
                                + "                </min-revision>\n"
                                + "            </dependency>\n"
                                + "        </dependencies>\n"
                                + "    </localPackage>\n"
                                + "</repo:repository>")
                        .getBytes(StandardCharsets.UTF_8));

        RepoManager manager = RepoManager.create();
        LocalRepoLoader localLoader = new LocalRepoLoaderImpl(sdkRoot, manager, null);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        LocalPackage p = localLoader.getPackages(progress).get("random");
        progress.assertNoErrorsOrWarnings();
        assertEquals(new Revision(3), p.getVersion());
        assertEquals("This is the license for this platform.", p.getLicense().getValue());
        assertTrue(p.getTypeDetails() instanceof TypeDetails.GenericType);
        assertEquals("The first Android platform ever", p.getDisplayName());
        // TODO: validate package in more detail
    }

    // Test writing a package out to xml
    public void testMarshalGeneric() {
        RepoManager manager = new RepoManagerImpl();

        CommonFactory factory = RepoManager.getCommonModule().createLatestFactory();
        GenericFactory genericFactory = RepoManager.getGenericModule().createLatestFactory();
        Repository repo = factory.createRepositoryType();
        LocalPackageImpl p = factory.createLocalPackage();
        License license = factory.createLicenseType("some license text", "license1");
        p.setLicense(license);
        p.setPath("mypackage;path");
        p.setVersion(new Revision(1, 2));
        p.setDisplayName("package name");
        p.setTypeDetails((TypeDetails) genericFactory.createGenericDetailsType());
        Dependency dep = factory.createDependencyType();
        dep.setPath("depId1");
        RevisionType r = factory.createRevisionType(new Revision(1, 2, 3));
        dep.setMinRevision(r);
        p.addDependency(dep);
        dep = factory.createDependencyType();
        dep.setPath("depId2");
        p.addDependency(dep);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        repo.setLocalPackage(p);
        repo.getLicense().add(license);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        SchemaModuleUtil.marshal(
                RepoManager.getCommonModule().createLatestFactory().generateRepository(repo),
                ImmutableSet.of(RepoManager.getGenericModule()), output,
                manager.getResourceResolver(progress), progress);
        progress.assertNoErrorsOrWarnings();

        assertThat(output.toString())
                .isEqualTo(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><ns4:repository "
                                + "xmlns:ns2=\"http://schemas.android.com/repository/android/generic/01\" "
                                + "xmlns:ns3=\"http://schemas.android.com/repository/android/generic/02\" "
                                + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/02\">"
                                + "<license id=\"license1\" type=\"text\">some license text</license>"
                                + "<localPackage path=\"mypackage;path\">"
                                + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                                + "xsi:type=\"ns3:genericDetailsType\"/>"
                                + "<revision><major>1</major><minor>2</minor></revision>"
                                + "<display-name>package name</display-name>"
                                + "<uses-license ref=\"license1\"/>"
                                + "<dependencies>"
                                + "<dependency path=\"depId1\"><min-revision><major>1</major>"
                                + "<minor>2</minor><micro>3</micro></min-revision></dependency>"
                                + "<dependency path=\"depId2\"/></dependencies>"
                                + "</localPackage></ns4:repository>");
    }

    // Test that a package in an inconsistent location gives a warning.
    public void testWrongPath() throws Exception {
        Path sdkRoot =
                InMemoryFileSystems.createInMemoryFileSystemAndFolder("repo/bogus").getParent();
        Files.write(
                sdkRoot.resolve("bogus/package.xml"),
                ("<repo:repository\n"
                                + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                                + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                                + "\n"
                                + "    <localPackage path=\"random\">\n"
                                + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                                + "        <revision>\n"
                                + "            <major>3</major>\n"
                                + "        </revision>\n"
                                + "        <display-name>The first Android platform ever</display-name>\n"
                                + "    </localPackage>\n"
                                + "</repo:repository>")
                        .getBytes(StandardCharsets.UTF_8));

        RepoManager manager = RepoManager.create();
        LocalRepoLoader localLoader = new LocalRepoLoaderImpl(sdkRoot, manager, null);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        LocalPackage p = localLoader.getPackages(progress).get("random");
        assertEquals(new Revision(3), p.getVersion());
        assertFalse(progress.getWarnings().isEmpty());
    }

    // Test that a package in an inconsistent location is overridden by one in the right place
    public void testDuplicate() throws Exception {
        Path sdkRoot =
                InMemoryFileSystems.createInMemoryFileSystemAndFolder("repo/bogus").getParent();
        Files.createDirectory(sdkRoot.resolve("random"));
        Files.write(
                sdkRoot.resolve("bogus/package.xml"),
                ("<repo:repository\n"
                                + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                                + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                                + "\n"
                                + "    <localPackage path=\"random\">\n"
                                + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                                + "        <revision>\n"
                                + "            <major>1</major>\n"
                                + "        </revision>\n"
                                + "        <display-name>The first Android platform ever</display-name>\n"
                                + "    </localPackage>\n"
                                + "</repo:repository>")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(
                sdkRoot.resolve("random/package.xml"),
                ("<repo:repository\n"
                                + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                                + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                                + "\n"
                                + "    <localPackage path=\"random\">\n"
                                + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                                + "        <revision>\n"
                                + "            <major>3</major>\n"
                                + "        </revision>\n"
                                + "        <display-name>The first Android platform ever</display-name>\n"
                                + "    </localPackage>\n"
                                + "</repo:repository>")
                        .getBytes(StandardCharsets.UTF_8));

        RepoManager manager = RepoManager.create();
        LocalRepoLoader localLoader = new LocalRepoLoaderImpl(sdkRoot, manager, null);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        LocalPackage p = localLoader.getPackages(progress).get("random");
        assertEquals(new Revision(3), p.getVersion());
        assertFalse(progress.getWarnings().isEmpty());
    }

    // todo: test strictness
}
