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

package com.android.manifmerger

import com.android.ide.common.blame.SourceFilePosition.UNKNOWN
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail

import com.android.manifmerger.DeepLink.DeepLinkException
import org.junit.Test

/** Testing [DeepLink].  */
class DeepLinkTest {

    @Test
    fun testSchemes() {
        var deepLink: DeepLink

        deepLink = DeepLink(UNKNOWN, "https://www.example.com", false)
        assertThat(deepLink.schemes).containsExactly("https")

        deepLink = DeepLink(UNKNOWN, "www.example.com", false)
        assertThat(deepLink.schemes).containsExactly("http", "https")

        deepLink = DeepLink(UNKNOWN, "file:///foo", false)
        assertThat(deepLink.schemes).containsExactly("file")

        deepLink = DeepLink(UNKNOWN, "file:/foo", false)
        assertThat(deepLink.schemes).containsExactly("file")

        deepLink = DeepLink(UNKNOWN, "file:/c:/foo", false)
        assertThat(deepLink.schemes).containsExactly("file")
    }

    @Test
    fun testSchemesExceptions() {

        try {
            DeepLink(UNKNOWN, "http.*://www.example.com", false)
            fail("Expecting DeepLinkException")
        } catch (e: DeepLinkException) {
            // should throw DeepLinkException because wildcards not allowed in scheme
        }

        try {
            DeepLink(UNKNOWN, "%http://www.example.com", false)
            fail("Expecting DeepLinkException")
        } catch (e: DeepLinkException) {
            // should throw DeepLinkException because of invalid URI syntax
        }

    }

    @Test
    fun testHost() {
        var deepLink: DeepLink

        deepLink = DeepLink(UNKNOWN, "https://foo:bar@www.example.com/baz", false)
        assertThat(deepLink.host).isEqualTo("www.example.com")

        deepLink = DeepLink(UNKNOWN, "www.example.com", false)
        assertThat(deepLink.host).isEqualTo("www.example.com")

        deepLink = DeepLink(UNKNOWN, "www.example.com/c:/foo", false)
        assertThat(deepLink.host).isEqualTo("www.example.com")

        deepLink = DeepLink(UNKNOWN, "\${applicationId}", false)
        assertThat(deepLink.host).isEqualTo("\${applicationId}")

        deepLink = DeepLink(UNKNOWN, ".*.example.com", false)
        assertThat(deepLink.host).isEqualTo("*.example.com")

        deepLink = DeepLink(UNKNOWN, "*.example.com", false)
        assertThat(deepLink.host).isNull()

        deepLink = DeepLink(UNKNOWN, "file:///foo", false)
        assertThat(deepLink.host).isNull()

        deepLink = DeepLink(UNKNOWN, "file:/foo", false)
        assertThat(deepLink.host).isNull()
    }

    @Test
    fun testHostExceptions() {

        try {
            DeepLink(UNKNOWN, "http://www.{placeholder}.com", false)
            fail("Expecting DeepLinkException")
        } catch (e: DeepLinkException) {
            // should throw DeepLinkException because host wildcards must be at beginning of host
        }

        try {
            DeepLink(UNKNOWN, "http://www.{.com", false)
            fail("Expecting DeepLinkException")
        } catch (e: DeepLinkException) {
            // should throw DeepLinkException because of invalid URI syntax
        }

    }

    @Test
    fun testPort() {
        var deepLink: DeepLink

        deepLink = DeepLink(UNKNOWN, "https://foo:bar@www.example.com:200/baz", false)
        assertThat(deepLink.port).isEqualTo(200)

        deepLink = DeepLink(UNKNOWN, "www.example.com:201", false)
        assertThat(deepLink.port).isEqualTo(201)

        deepLink = DeepLink(UNKNOWN, "www.example.com", false)
        assertThat(deepLink.port).isEqualTo(-1)

        deepLink = DeepLink(UNKNOWN, "www.example.com:foo", false)
        assertThat(deepLink.port).isEqualTo(-1)
    }

    @Test
    fun testPath() {
        var deepLink: DeepLink

        deepLink = DeepLink(UNKNOWN, "https://foo:bar@www.example.com/baz?query#fragment", false)
        assertThat(deepLink.path).isEqualTo("/baz")

        deepLink = DeepLink(UNKNOWN, "www.example.com", false)
        assertThat(deepLink.path).isEqualTo("/")

        deepLink = DeepLink(UNKNOWN, "www.example.com/c:/foo", false)
        assertThat(deepLink.path).isEqualTo("/c:/foo")

        deepLink = DeepLink(UNKNOWN, "/foo", false)
        assertThat(deepLink.path).isEqualTo("/foo")

        deepLink = DeepLink(UNKNOWN, "/c:/foo", false)
        assertThat(deepLink.path).isEqualTo("/c:/foo")

        deepLink = DeepLink(UNKNOWN, "file:///foo", false)
        assertThat(deepLink.path).isEqualTo("/foo")

        deepLink = DeepLink(UNKNOWN, "file:/foo", false)
        assertThat(deepLink.path).isEqualTo("/foo")

        deepLink = DeepLink(UNKNOWN, "file:/foo.*", false)
        assertThat(deepLink.path).isEqualTo("/foo.*")

        deepLink = DeepLink(UNKNOWN, "file:/foo{placeholder}", false)
        assertThat(deepLink.path).isEqualTo("/foo.*")

        deepLink = DeepLink(UNKNOWN, "file:/foo\${applicationId}", false)
        assertThat(deepLink.path).isEqualTo("/foo\${applicationId}")

        deepLink = DeepLink(UNKNOWN, "file:/{1}foo{2}", false)
        assertThat(deepLink.path).isEqualTo("/.*foo.*")
    }

    @Test
    fun testPathExceptions() {

        try {
            DeepLink(UNKNOWN, "http://www.example.com/{{nested}}", false)
            fail("Expecting DeepLinkException")
        } catch (e: DeepLinkException) {
            // should throw DeepLinkException because of invalid URI syntax
        }

        try {
            DeepLink(UNKNOWN, "http://www.example.com/hanging{", false)
            fail("Expecting DeepLinkException")
        } catch (e: DeepLinkException) {
            // should throw DeepLinkException because of invalid URI syntax
        }

        try {
            DeepLink(UNKNOWN, "http://www.example.com/nested/hanging{{}", false)
            fail("Expecting DeepLinkException")
        } catch (e: DeepLinkException) {
            // should throw DeepLinkException because of invalid URI syntax
        }

    }

    @Test
    fun testSourceFilePosition() {
        val deepLink: DeepLink

        deepLink = DeepLink(UNKNOWN, "http://www.example.com", false)
        assertThat(deepLink.sourceFilePosition).isEqualTo(UNKNOWN)
    }

    @Test
    fun testAutoVerify() {
        var deepLink: DeepLink

        deepLink = DeepLink(UNKNOWN, "http://www.example.com", false)
        assertThat(deepLink.isAutoVerify).isFalse()

        deepLink = DeepLink(UNKNOWN, "http://www.example.com", true)
        assertThat(deepLink.isAutoVerify).isTrue()
    }

    @Test
    fun testChooseEncoder() {
        val encoder = DeepLink.DeepLinkUri
                .chooseEncoder("http://www.example.com", 'w', 'x')
        assertThat(encoder).isEqualTo("wwwwx")
    }

    @Test
    fun testChooseEncoderExceptions() {
        try {
            DeepLink.DeepLinkUri.chooseEncoder("file:///foo", 'a', 'a')
            fail("Expecting IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // should throw IllegalArgumentException because char1 and char2 must be different
        }

    }
}
