/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.res.shrinker

import com.android.build.gradle.internal.res.shrinker.PossibleResourcesMarker.NO_MATCH
import com.android.build.gradle.internal.res.shrinker.PossibleResourcesMarker.convertFormatStringToRegexp
import com.android.ide.common.resources.usage.ResourceStore
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource
import com.android.resources.ResourceType
import com.google.common.collect.ImmutableList.of
import com.google.common.collect.ImmutableSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PossibleResourcesMarkerTest {

    @Test
    fun `mark resources based on prefix`() {
        assertEquals(of("my_draw_1_main", "my_draw_2_main", "my_draw_3_title", "my_draw_"),
            findReachableResources("my_draw_"))
    }

    @Test
    fun `mark resources based on formatting`() {
        assertEquals(of("my_draw_1_main", "my_draw_2_main"),
            findReachableResources("my_draw_%d_main"))

        assertEquals(of("my_draw_1_main", "my_draw_2_main", "my_draw_3_title"),
            findReachableResources("my_draw_%d%s"))

        assertEquals(of("another_my_draw_2"), findReachableResources("%smy_draw_%d"))
    }

    @Test
    fun `mark resources based on id value`() {
        assertEquals(of("my_draw_3_title"), findReachableResources("2130837506"))
        assertEquals(of("my_draw_3_title"),
            findReachableResources("android.resource://com.myapp/2130837506"))
    }

    @Test
    fun `mark web content resources`() {
        assertEquals(of("my_draw_1_main"),
            findReachableResources("drawable/my_draw_1_main.png", webContent = true))
        assertEquals(of("layout_my"),
            findReachableResources("file:///android_res/layout/layout_my.xml", webContent = true))
        assertEquals(of<String>(),
            findReachableResources("file:///android_res/layout/layout_my.xml", webContent = false))
    }

    @Test
    fun testFormatStringRegexp() {
        assertEquals(NO_MATCH, convertFormatStringToRegexp(""))
        assertEquals("\\Qfoo_\\E", convertFormatStringToRegexp("foo_"))
        assertEquals("\\Qfoo\\E.*\\Q_\\E.*\\Qend\\E", convertFormatStringToRegexp("foo%s_%1\$send"))
        assertEquals("\\Qescape!.()\\E", convertFormatStringToRegexp("escape!.()"))
        assertEquals(NO_MATCH, convertFormatStringToRegexp("%c%c%c%d"))
        assertEquals(NO_MATCH, convertFormatStringToRegexp("%d%s"))
        assertEquals(NO_MATCH, convertFormatStringToRegexp("%s%s"))
        assertEquals(NO_MATCH, convertFormatStringToRegexp("%d_%d"))
        assertEquals(NO_MATCH, convertFormatStringToRegexp("%s%s%s%s"))
        assertEquals(NO_MATCH, convertFormatStringToRegexp("%s_%s_%s"))
        assertEquals(NO_MATCH, convertFormatStringToRegexp("%.0f%s"))
        assertEquals(".*\\Qabc\\E", convertFormatStringToRegexp("%sabc"))
        assertTrue("abc".matches(convertFormatStringToRegexp("%sabc").toRegex()))
        assertTrue("somethingabc".matches(convertFormatStringToRegexp("%sabc").toRegex()))
        assertEquals("\\Qa\\E\\p{Digit}+.*", convertFormatStringToRegexp("a%d%s"))
        assertTrue("a52hello".matches(convertFormatStringToRegexp("a%d%s").toRegex()))
        assertEquals("\\Qprefix\\E0*\\p{Digit}+\\Qsuffix\\E", convertFormatStringToRegexp("prefix%05dsuffix"))
        assertTrue("prefix012345suffix".matches(convertFormatStringToRegexp("prefix%05dsuffix").toRegex()))
        assertEquals("\\p{Digit}+\\Qk\\E", convertFormatStringToRegexp("%dk"))
        assertTrue("1234k".matches(convertFormatStringToRegexp("%dk").toRegex()))
        assertFalse("ic_shield_dark".matches(convertFormatStringToRegexp("%dk").toRegex()))
        assertTrue("foo_".matches(convertFormatStringToRegexp("foo_").toRegex()))
        assertTrue("fooA_BBend".matches(convertFormatStringToRegexp("foo%s_%1\$send").toRegex()))
        assertFalse("A_BBend".matches(convertFormatStringToRegexp("foo%s_%1\$send").toRegex()))
        // Comprehensive test
        val p = "prefix%s%%%n%c%x%d%o%b%h%f%e%a%g%C%X%5B%E%A%G"
        var s = String.format(p, "", 'c', 1, 1, 1, true, 1, 1f, 1f, 1f, 1f, 'c', 1, 1, 1f, 1f, 1f)
        s = s.replace("\r\n".toRegex(), "\n")
        assertTrue(s.matches(convertFormatStringToRegexp(p).toRegex()))
    }

    private fun createResourceModel(): ResourceStore {
        val model = ResourceStore()
        model.addResource(Resource(ResourceType.LAYOUT, "layout_my", 0x7f010000))
        model.addResource(Resource(ResourceType.DRAWABLE, "my_draw_1_main", 0x7f020000))
        model.addResource(Resource(ResourceType.DRAWABLE, "my_draw_2_main", 0x7f020001))
        model.addResource(Resource(ResourceType.DRAWABLE, "my_draw_3_title", 0x7f020002))
        model.addResource(Resource(ResourceType.DRAWABLE, "my_draw_", 0x7f020003))
        model.addResource(Resource(ResourceType.DRAWABLE, "another_my_draw_2", 0x7f020004))
        return model
    }

    private fun findReachableResources(string: String, webContent: Boolean = false): List<String> {
        val model = createResourceModel()
        PossibleResourcesMarker(NoDebugReporter, model, ImmutableSet.of(string), webContent)
            .markPossibleResourcesReachable()
        return model.resources
            .filter { it.isReachable }
            .map { it.name }
    }
}
