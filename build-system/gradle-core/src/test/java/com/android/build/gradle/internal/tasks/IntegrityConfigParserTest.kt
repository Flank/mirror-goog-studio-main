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
import com.android.bundle.AppIntegrityConfigOuterClass.DebuggerCheck
import com.android.bundle.AppIntegrityConfigOuterClass.EmulatorCheck
import com.android.bundle.AppIntegrityConfigOuterClass.InstallerCheck

import com.android.bundle.AppIntegrityConfigOuterClass.LicenseCheck
import com.android.bundle.AppIntegrityConfigOuterClass.Policy
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

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
        .setDebuggerCheck(DebuggerCheck.newBuilder().setEnabled(true))
        .setEmulatorCheck(EmulatorCheck.newBuilder().setEnabled(true))
        .build()

    private val disabledConfigXML = """<IntegrityConfig enabled="false"/>"""
    private val disabledConfigProto =
        AppIntegrityConfig.newBuilder(defaultConfigProto).setEnabled(false).build()

    private val customConfigXML = """
        <IntegrityConfig>
            <LicenseCheck>
                <Policy action="DISABLE"/>
            </LicenseCheck>
            <InstallerCheck>
                <Policy action="WARN_THEN_DISABLE"/>
                <AdditionalInstallSource>com.amazon.vending</AdditionalInstallSource>
            </InstallerCheck>
            <DebuggerCheck enabled="false"/>
            <EmulatorCheck enabled="false"/>
        </IntegrityConfig>
        """
    private val customConfigProto = AppIntegrityConfig.newBuilder(defaultConfigProto)
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
        ).setDebuggerCheck(
            DebuggerCheck.newBuilder().setEnabled(false)
        ).setEmulatorCheck(
            AppIntegrityConfigOuterClass.EmulatorCheck.newBuilder().setEnabled(false)
        ).build()

    lateinit var documentBuilder: DocumentBuilder

    @Before
    fun setUp() {
        documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
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
        val disabledConfigDoc = documentBuilder.parse(InputSource(StringReader(disabledConfigXML)))

        val parser = IntegrityConfigParser(disabledConfigDoc)
        val disabledEnabledConfigProto = parser.parseConfig()

        assertEquals(disabledConfigProto, disabledEnabledConfigProto)
    }

    @Test
    fun testParseCustomConfig() {
        val customConfigDoc = documentBuilder.parse(InputSource(StringReader(customConfigXML)))

        val parser = IntegrityConfigParser(customConfigDoc)
        val parsedCustomConfigProto = parser.parseConfig()

        assertEquals(customConfigProto, parsedCustomConfigProto)

    }
}
