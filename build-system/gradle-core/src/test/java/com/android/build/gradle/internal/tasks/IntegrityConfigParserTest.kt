/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.bundle.AppIntegrityConfigOuterClass
import com.android.bundle.AppIntegrityConfigOuterClass.AppIntegrityConfig
import com.android.bundle.AppIntegrityConfigOuterClass.EmulatorCheck
import com.android.bundle.AppIntegrityConfigOuterClass.InstallerCheck
import com.android.bundle.AppIntegrityConfigOuterClass.LicenseCheck
import com.android.bundle.AppIntegrityConfigOuterClass.Policy
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IntegrityConfigParserTest {

    private val defaultConfigXML = "<IntegrityConfig/>"
    private val defaultConfigProto = AppIntegrityConfig.newBuilder()
        .setEnabled(true)
        .setLicenseCheck(
            LicenseCheck.newBuilder().setEnabled(false).setPolicy(
                Policy.newBuilder().setAction(Policy.Action.WARN)
            )
        )
        .setInstallerCheck(
            InstallerCheck.newBuilder().setEnabled(true).setPolicy(
                Policy.newBuilder().setAction(Policy.Action.WARN)
            )
        )
        .setEmulatorCheck(EmulatorCheck.newBuilder().setEnabled(true))
        .build()

    lateinit var documentBuilder: DocumentBuilder

    @Before
    fun setUp() {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        documentBuilder = factory.newDocumentBuilder()
    }

    @Test
    fun testParseDefaultConfig() {
        val defaultConfigDoc = documentBuilder.parse(InputSource(StringReader(defaultConfigXML)))

        val parser = IntegrityConfigParser(defaultConfigDoc)
        val parsedDefaultConfigProto = parser.parseConfig()

        parsedDefaultConfigProto.licenseCheck.isInitialized
        assertEquals(defaultConfigProto, parsedDefaultConfigProto)
    }

    @Test
    fun testParseDisabledConfig() {
        val disabledConfigXML = """<IntegrityConfig enabled="false"/>"""
        val disabledConfigDoc = documentBuilder.parse(InputSource(StringReader(disabledConfigXML)))
        val disabledConfigProto =
            AppIntegrityConfig.newBuilder(defaultConfigProto).setEnabled(false).build()

        val parser = IntegrityConfigParser(disabledConfigDoc)
        val disabledEnabledConfigProto = parser.parseConfig()

        assertEquals(disabledConfigProto, disabledEnabledConfigProto)
    }

    @Test
    fun testParseCustomConfig_allElements() {
        val customConfigXML = """
        <IntegrityConfig>
            <LicenseCheck>
                <Policy action="DISABLE"/>
            </LicenseCheck>
            <InstallerCheck>
                <Policy action="WARN_THEN_DISABLE"/>
                <AdditionalInstallSource>com.amazon.vending</AdditionalInstallSource>
            </InstallerCheck>
            <EmulatorCheck enabled="false"/>
        </IntegrityConfig>
        """
        val customConfigProto = AppIntegrityConfig.newBuilder(defaultConfigProto)
            .setEnabled(true)
            .setLicenseCheck(
                LicenseCheck.newBuilder()
                    .setEnabled(true)
                    .setPolicy(Policy.newBuilder().setAction(Policy.Action.DISABLE))
            )
            .setInstallerCheck(
                AppIntegrityConfigOuterClass.InstallerCheck.newBuilder()
                    .setEnabled(true)
                    .setPolicy(Policy.newBuilder().setAction(Policy.Action.WARN_THEN_DISABLE))
                    .addAdditionalInstallSource("com.amazon.vending")
            ).setEmulatorCheck(
                AppIntegrityConfigOuterClass.EmulatorCheck.newBuilder().setEnabled(false)
            ).build()
        val customConfigDoc = documentBuilder.parse(InputSource(StringReader(customConfigXML)))

        val parser = IntegrityConfigParser(customConfigDoc)
        val parsedCustomConfigProto = parser.parseConfig()

        assertEquals(customConfigProto, parsedCustomConfigProto)
    }

    @Test
    fun testParseCustomConfig_withMissingElements() {
        val customConfigXML =
            """<IntegrityConfig>
                    <EmulatorCheck enabled="false"/>
               </IntegrityConfig>"""
        val customConfigProto = AppIntegrityConfig.newBuilder(defaultConfigProto)
            .setEnabled(true)
            .setLicenseCheck(
                LicenseCheck.newBuilder()
                    .setEnabled(false)
                    .setPolicy(Policy.newBuilder().setAction(Policy.Action.WARN))
            )
            .setInstallerCheck(
                AppIntegrityConfigOuterClass.InstallerCheck.newBuilder()
                    .setEnabled(true)
                    .setPolicy(Policy.newBuilder().setAction(Policy.Action.WARN))
            ).setEmulatorCheck(
                AppIntegrityConfigOuterClass.EmulatorCheck.newBuilder().setEnabled(false)
            ).build()
        val customConfigDoc = documentBuilder.parse(InputSource(StringReader(customConfigXML)))

        val parser = IntegrityConfigParser(customConfigDoc)
        val parsedCustomConfigProto = parser.parseConfig()

        assertEquals(customConfigProto, parsedCustomConfigProto)
    }

    @Test
    fun testParseCustomConfig_multipleInstallSources() {
        val customConfigXML = """
        <IntegrityConfig>
            <LicenseCheck>
                <Policy action="DISABLE"/>
            </LicenseCheck>
            <InstallerCheck>
                <Policy action="WARN_THEN_DISABLE"/>
                <AdditionalInstallSource>com.amazon.vending</AdditionalInstallSource>
                <AdditionalInstallSource>com.random.vending</AdditionalInstallSource>
            </InstallerCheck>
            <EmulatorCheck enabled="false"/>
        </IntegrityConfig>
        """
        val customConfigProto = AppIntegrityConfig.newBuilder(defaultConfigProto)
            .setEnabled(true)
            .setLicenseCheck(
                LicenseCheck.newBuilder()
                    .setEnabled(true)
                    .setPolicy(Policy.newBuilder().setAction(Policy.Action.DISABLE))
            )
            .setInstallerCheck(
                AppIntegrityConfigOuterClass.InstallerCheck.newBuilder()
                    .setEnabled(true)
                    .setPolicy(Policy.newBuilder().setAction(Policy.Action.WARN_THEN_DISABLE))
                    .addAdditionalInstallSource("com.amazon.vending")
                    .addAdditionalInstallSource("com.random.vending")
            ).setEmulatorCheck(
                AppIntegrityConfigOuterClass.EmulatorCheck.newBuilder().setEnabled(false)
            ).build()
        val customConfigDoc = documentBuilder.parse(InputSource(StringReader(customConfigXML)))

        val parser = IntegrityConfigParser(customConfigDoc)
        val parsedCustomConfigProto = parser.parseConfig()

        assertEquals(customConfigProto, parsedCustomConfigProto)
    }

    @Test
    fun testParseCustomConfig_unknownElement() {
        val customConfigXML = """
        <IntegrityConfig>
            <LicenseCheck>
                <Policy action="DISABLE"/>
            </LicenseCheck>
            <DebuggerCheck enabled="false"/>
            <EmulatorCheck enabled="false"/>
        </IntegrityConfig>
        """
        val customConfigDoc = documentBuilder.parse(InputSource(StringReader(customConfigXML)))

        val parser = IntegrityConfigParser(customConfigDoc)
        val exception =
            assertFailsWith<IntegrityConfigParser.InvalidIntegrityConfigException> { parser.parseConfig() }
        assertEquals(exception.message, "The IntegrityConfig xml provided is invalid.")
        assertTrue(exception.cause is SAXParseException)
        assertEquals(
            (exception.cause as SAXParseException).message,
            "cvc-complex-type.2.4.a: Invalid content was found starting with element 'DebuggerCheck'. One of '{EmulatorCheck, InstallerCheck}' is expected."
        )
    }

    @Test
    fun testParseCustomConfig_duplicateElement() {
        val customConfigXML = """
        <IntegrityConfig>
            <LicenseCheck>
                <Policy action="DISABLE"/>
            </LicenseCheck>
            <EmulatorCheck enabled="false"/>
            <EmulatorCheck enabled="false"/>
        </IntegrityConfig>
        """
        val customConfigDoc = documentBuilder.parse(InputSource(StringReader(customConfigXML)))

        val parser = IntegrityConfigParser(customConfigDoc)
        val exception =
            assertFailsWith<IntegrityConfigParser.InvalidIntegrityConfigException> { parser.parseConfig() }
        assertEquals(exception.message, "The IntegrityConfig xml provided is invalid.")
        assertTrue(exception.cause is SAXParseException)
        assertEquals(
            (exception.cause as SAXParseException).message,
            "cvc-complex-type.2.4.a: Invalid content was found starting with element 'EmulatorCheck'. One of '{InstallerCheck}' is expected."
        )
    }

    @Test
    fun testParseCustomConfig_unknownPolicy() {
        val customConfigXML = """
        <IntegrityConfig>
            <LicenseCheck>
                <Policy action="RANDOM"/>
            </LicenseCheck>
            <EmulatorCheck enabled="false"/>
        </IntegrityConfig>
        """
        val customConfigDoc = documentBuilder.parse(InputSource(StringReader(customConfigXML)))

        val parser = IntegrityConfigParser(customConfigDoc)
        val exception =
            assertFailsWith<IntegrityConfigParser.InvalidIntegrityConfigException> { parser.parseConfig() }
        assertEquals(exception.message, "The IntegrityConfig xml provided is invalid.")
        assertTrue(exception.cause is SAXParseException)
        assertEquals(
            (exception.cause as SAXParseException).message,
            "cvc-enumeration-valid: Value 'RANDOM' is not facet-valid with respect to enumeration '[DISABLE, WARN, WARN_THEN_DISABLE]'. It must be a value from the enumeration."
        )
    }
}