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
import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import junit.framework.TestCase;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Tests for {@link LocalRepoLoaderImpl}.
 */
public class LocalRepoTest extends TestCase {

    // Test that we can parse a basic package.
    public void testParseGeneric() {
        MockFileOp mockFop = new MockFileOp();
        mockFop.recordExistingFolder("/repo/random");
        mockFop.recordExistingFile("/repo/random/package.xml",
                "<repo:repository\n"
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
                        + "</repo:repository>"
        );

        RepoManager manager = RepoManager.create();
        LocalRepoLoader localLoader =
                new LocalRepoLoaderImpl(mockFop.toPath("/repo"), manager, null);
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
    public void testMarshalGeneric() throws Exception {
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

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        dbf.setNamespaceAware(true);
        dbf.setSchema(SchemaModuleUtil.getSchema(ImmutableSet.of(RepoManager.getGenericModule()),
                SchemaModuleUtil.createResourceResolver(ImmutableSet.of(RepoManager.getCommonModule()),
                        progress),
                progress));
        progress.assertNoErrorsOrWarnings();
        DocumentBuilder db = dbf.newDocumentBuilder();

        db.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });

        Document doc = db.parse(new ByteArrayInputStream(output.toByteArray()));

        NodeList licences = doc.getElementsByTagName("license");
        assertEquals(1, licences.getLength());
        Element licenseNode = (Element) licences.item(0);
        assertEquals("license1", licenseNode.getAttribute("id"));
        assertEquals("some license text", licenseNode.getTextContent());
        Element packageNode = (Element) doc.getElementsByTagName("localPackage").item(0);
        assertEquals("mypackage;path", packageNode.getAttribute("path"));
        Element details = (Element) packageNode.getElementsByTagName("type-details").item(0);
        assertEquals("genericDetailsType", details.getSchemaTypeInfo().getTypeName());
        Element revision = (Element) packageNode.getElementsByTagName("revision").item(0);
        assertEquals("1", revision.getElementsByTagName("major").item(0).getTextContent());
        assertEquals("2", revision.getElementsByTagName("minor").item(0).getTextContent());
        assertEquals(
                "package name",
                packageNode.getElementsByTagName("display-name").item(0).getTextContent());
        Element usesLicense = (Element) packageNode.getElementsByTagName("uses-license").item(0);
        assertEquals("license1", usesLicense.getAttribute("ref"));
        Element dependencies = (Element) packageNode.getElementsByTagName("dependencies").item(0);
        Element dependency = (Element) dependencies.getElementsByTagName("dependency").item(0);
        assertEquals("depId1", dependency.getAttribute("path"));
        revision = (Element) dependency.getElementsByTagName("min-revision").item(0);
        assertEquals("1", revision.getElementsByTagName("major").item(0).getTextContent());
        assertEquals("2", revision.getElementsByTagName("minor").item(0).getTextContent());
        assertEquals("3", revision.getElementsByTagName("micro").item(0).getTextContent());
        dependency = (Element) dependencies.getElementsByTagName("dependency").item(1);
        assertEquals("depId2", dependency.getAttribute("path"));
    }

    // Test that a package in an inconsistent location gives a warning.
    public void testWrongPath() {
        MockFileOp mockFop = new MockFileOp();
        mockFop.recordExistingFile("/repo/bogus/package.xml",
                "<repo:repository\n"
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
                        + "</repo:repository>"
        );

        RepoManager manager = RepoManager.create();
        LocalRepoLoader localLoader =
                new LocalRepoLoaderImpl(mockFop.toPath("/repo"), manager, null);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        LocalPackage p = localLoader.getPackages(progress).get("random");
        assertEquals(new Revision(3), p.getVersion());
        assertFalse(progress.getWarnings().isEmpty());
    }

    // Test that a package in an inconsistent is overridden by one in the right place
    public void testDuplicate() {
        MockFileOp mockFop = new MockFileOp();
        mockFop.recordExistingFile("/repo/bogus/package.xml",
                "<repo:repository\n"
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
                        + "</repo:repository>"
        );
        mockFop.recordExistingFile("/repo/random/package.xml",
                "<repo:repository\n"
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
                        + "</repo:repository>"
        );

        RepoManager manager = RepoManager.create();
        LocalRepoLoader localLoader =
                new LocalRepoLoaderImpl(mockFop.toPath("/repo"), manager, null);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        LocalPackage p = localLoader.getPackages(progress).get("random");
        assertEquals(new Revision(3), p.getVersion());
        assertFalse(progress.getWarnings().isEmpty());
    }

    // todo: test strictness
}
