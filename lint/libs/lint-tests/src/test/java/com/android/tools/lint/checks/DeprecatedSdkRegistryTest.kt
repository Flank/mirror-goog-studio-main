/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.ide.common.repository.GradleCoordinate.parseCoordinateString
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import java.io.File

private class StubDeprecatedSdkRegistry @JvmOverloads constructor(
    private val builtInData: Map<String, String> = emptyMap(),
    private val urls: Map<String, String> = emptyMap(),
    cacheDir: File? = null
) : DeprecatedSdkRegistry(cacheDir = cacheDir) {
    override fun readUrlData(url: String, timeout: Int): ByteArray? = urls[url]?.toByteArray()
    override fun error(throwable: Throwable, message: String?) = throw throwable
    override fun readDefaultData(relative: String) = builtInData[relative]?.byteInputStream()
}

class DeprecatedSdkRegistryTest : TestCase() {
    val x = """
<sdk_metadata>
 <library groupId="log4j" artifactId="log4j" recommended-version="1.2.17+" recommended-version-sha="5af35056b4d257e4b64b9e8069c0746e8b08629f">
  <versions from="1.2.14" to="1.2.16" status="deprecated" description="Deprecated due to ANR issue">
   <vulnerability description="Specifics and developer actions go here." cve="CVE-4313" />
  </versions>
  <versions from="1.2.4" to="1.2.13" status="insecure" description="Bad security bug CVE-4311" />
   <vulnerability description="Buffer overflow vulnerability in this version." cve="CVE-4311" />
  <versions to="1.2.0" status="obsolete" description="Library is obsolete." />
 </library>
</sdk_metadata>
        """
    fun testBasic() {
        @Language("XML")
        val xml = """
            <sdk_metadata>
             <library groupId="log4j" artifactId="log4j" recommended-version="1.2.17+" recommended-version-sha="5af35056b4d257e4b64b9e8069c0746e8b08629f">
              <versions from="1.2.14" to="1.2.16" status="deprecated" description="Deprecated due to ANR issue">
               <vulnerability description="Specifics and developer actions go here." cve="CVE-4313" />
              </versions>
              <versions from="1.2.4" to="1.2.13" status="insecure" description="Bad security bug CVE-4311" />
               <vulnerability description="Buffer overflow vulnerability in this version." cve="CVE-4311" />
              <versions to="1.2.0" status="obsolete" description="Library is obsolete." />
             </library>
             <library groupId="com.example.ads.thirdparty" artifactId="example" recommended-version="7.3.1">
              <versions from="7.1.0" to="7.2.1" status="deprecated" description="Deprecated due to ANR issue">
               <vulnerability description="Specifics and developer actions go here." />
              </versions>
              <versions to="7.0.0" status="deprecated" description="Deprecated due to ANR issue">
               <vulnerability description="Specifics and developer actions go here." />
              </versions>
              </library>
            </sdk_metadata>
        """
        val lookup = StubDeprecatedSdkRegistry(
            mapOf("" to xml.trimIndent())
        )

        val coordinate1 = parseCoordinateString("foo.bar:baz:1.0.0")!!
        assertNull(lookup.getVersionInfo(coordinate1))

        val coordinate2 = parseCoordinateString("com.example.ads.thirdparty:example:7.1.5")!!
        val info = lookup.getVersionInfo(coordinate2)
        assertNotNull(info)
        info!!

        assertEquals("Deprecated due to ANR issue", info.message)
        assertEquals("7.3.1", info.recommended)

        assertEquals("7.3.1", lookup.getRecommendedVersion(coordinate2)?.toString())
    }
}