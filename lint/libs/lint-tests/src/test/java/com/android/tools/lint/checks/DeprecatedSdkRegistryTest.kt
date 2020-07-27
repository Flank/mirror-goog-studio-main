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
    fun testBasic() {
        @Language("XML")
        val xml =
            """
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

    fun testHostileNetwork() {
        @Language("HTML")
        val xml =
            """
                <!DOCTYPE html>
                <html lang=en>
                  <meta charset=utf-8>
                  <meta name=viewport content="initial-scale=1, minimum-scale=1, width=device-width">
                  <title>Error 404 (Not Found)!!1</title>
                  <style>
                    *{margin:0;padding:0}html,code{font:15px/22px arial,sans-serif}html{background:#fff;color:#222;padding:15px}body{margin:7% auto 0;max-width:390px;min-height:180px;padding:30px 0 15px}* > body{background:url(//www.google.com/images/errors/robot.png) 100% 5px no-repeat;padding-right:205px}p{margin:11px 0 22px;overflow:hidden}ins{color:#777;text-decoration:none}a img{border:0}@media screen and (max-width:772px){body{background:none;margin-top:0;max-width:none;padding-right:0}}#logo{background:url(//www.google.com/images/logos/errorpage/error_logo-150x54.png) no-repeat;margin-left:-5px}@media only screen and (min-resolution:192dpi){#logo{background:url(//www.google.com/images/logos/errorpage/error_logo-150x54-2x.png) no-repeat 0% 0%/100% 100%;-moz-border-image:url(//www.google.com/images/logos/errorpage/error_logo-150x54-2x.png) 0}}@media only screen and (-webkit-min-device-pixel-ratio:2){#logo{background:url(//www.google.com/images/logos/errorpage/error_logo-150x54-2x.png) no-repeat;-webkit-background-size:100% 100%}}#logo{display:inline-block;height:54px;width:150px}
                  </style>
                  <a href=//www.google.com/><span id=logo aria-label=Google></span></a>
                  <p><b>404.</b> <ins>That’s an error.</ins>
                  <p>  <ins>That’s all we know.</ins>
        """
        val lookup = StubDeprecatedSdkRegistry(
            mapOf("" to xml.trimIndent())
        )

        val coordinate1 = parseCoordinateString("foo.bar:baz:1.0.0")!!
        assertNull(lookup.getVersionInfo(coordinate1))

        val coordinate2 = parseCoordinateString("com.example.ads.thirdparty:example:7.1.5")!!
        val info = lookup.getVersionInfo(coordinate2)
        assertNull(info)
    }
}
