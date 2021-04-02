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
package com.android.tools.layoutinspector

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.tools.idea.layoutinspector.proto.SkiaParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.fail

private const val COMPONENT_PATH = "tools/base/dynamic-layout-inspector/skia"
private const val TEST_DATA_PATH = "$COMPONENT_PATH/testData"
private const val DIFF_THRESHOLD = 0.01

class SkiaParserTest {
    companion object {
        init {
            System.loadLibrary("skiaparser-test")
        }
    }

    private external fun generateBoxes(): ByteArray?

    @Test
    fun testBoxes() {
        val response = SkiaParser.InspectorView.parseFrom(generateBoxes())
        val tree = LayoutInspectorUtils.buildTree(response, mapOf(), { false }, mapOf())!!
        assertTreeIdsEqual(
            Node(
                1, listOf(
                    Node(1),
                    Node(
                        4, listOf(
                            Node(4)
                        )
                    )
                )
            ), tree
        )
        assertImagesSimilar(tree, 1, 0, "skiaTest_testBoxes_1.png")
        assertImagesSimilar(tree, 4, 0, "skiaTest_testBoxes_4.png")
    }

    private external fun generateImage(): ByteArray?

    @Test
    fun testImage() {
        val response = SkiaParser.InspectorView.parseFrom(generateImage())
        val tree = LayoutInspectorUtils.buildTree(response, mapOf(), { false }, mapOf())!!
        assertTreeIdsEqual(
            Node(
                1, listOf(
                    Node(1),
                )
            ), tree
        )
        assertImagesSimilar(tree, 1, 0, "skiaTest_testImage.png")
    }

    private external fun generateTransformedViews(): ByteArray?

    @Test
    fun testTransformation() {
        val response = SkiaParser.InspectorView.parseFrom(generateTransformedViews())
        val tree = LayoutInspectorUtils.buildTree(response, mapOf(), { false }, mapOf())!!
        assertTreeIdsEqual(
            Node(
                1, listOf(
                    Node(1),
                    Node(
                        2, listOf(
                            Node(2),
                            Node(
                                3, listOf(
                                    Node(3)
                                )
                            ),
                            Node(
                                4, listOf(
                                    Node(4)
                                )
                            )
                        )
                    ),
                    Node(1)
                )
            ), tree
        )

        assertImagesSimilar(tree, 1, 0, "skiaTest_testTransformation_1a.png")
        assertImagesSimilar(tree, 2, 0, "skiaTest_testTransformation_2.png")
        assertImagesSimilar(tree, 3, 0, "skiaTest_testTransformation_3.png")
        assertImagesSimilar(tree, 4, 0, "skiaTest_testTransformation_4.png")
        assertImagesSimilar(tree, 1, 1, "skiaTest_testTransformation_1b.png")
    }

    private external fun generateRealWorldExample(filename: String): ByteArray?

    // Unlike the previous tests, this one uses a pre-serialized SKP that came from Android.
    // This lets us test a more realistic scenario (notably including text: our build of skia
    // doesn't include the ability to load fonts other than as serialized within SKPs, so we can't
    // generate text in tests ourselves), at the cost of having to regenerate the sample (and
    // probably the golden images etc.) whenever we update the skia version such that the serialized
    // SKP is no longer supported.
    // The current SKP comes from the "Motion Layout / Constraint Layout" sample project,
    // "Complex Motion Example (3/4)".
    @Test
    fun testRealWorld() {
        val response = SkiaParser.InspectorView.parseFrom(generateRealWorldExample(
            TestUtils.getWorkspaceRoot().resolve("$TEST_DATA_PATH/realWorldExample.skp")
                .toString()))
        val tree = LayoutInspectorUtils.buildTree(response, mapOf(), { false }, mapOf())!!

        assertImagesSimilar(tree, 82, 0, "skiaTest_testRealWorld_82.png")
        assertImagesSimilar(tree, 83, 0, "skiaTest_testRealWorld_83.png")
        assertImagesSimilar(tree, 84, 0, "skiaTest_testRealWorld_84.png")
        assertImagesSimilar(tree, 81, 0, "skiaTest_testRealWorld_81.png")
        assertImagesSimilar(tree, 86, 0, "skiaTest_testRealWorld_86.png")
        assertThat(findImage(85, AtomicInteger(0), tree)).isNull()
        assertImagesSimilar(tree, 87, 0, "skiaTest_testRealWorld_87.png")
        assertImagesSimilar(tree, 80, 0, "skiaTest_testRealWorld_80.png")
        assertImagesSimilar(tree, 73, 0, "skiaTest_testRealWorld_73.png")
    }

    private external fun generateBoxesData(): ByteArray?

    @Test
    fun testMaxSupportedVersion() {
        val skp = generateBoxesData()!!
        val versionMap = LayoutInspectorUtils.loadSkiaParserVersionMap(
            TestUtils.getWorkspaceRoot().resolve("$COMPONENT_PATH/files/version-map.xml"))
        assertThat(LayoutInspectorUtils.getSkpVersion(skp))
            .isEqualTo(versionMap.servers.mapNotNull { it.skpEnd }.max())
    }

    private fun assertImagesSimilar(root: SkiaViewNode, id: Long, instance: Int, fileName: String) {
        ImageDiffUtil.assertImageSimilar(
            TestUtils.getWorkspaceRoot().resolve("$TEST_DATA_PATH/$fileName"),
            findImage(id, AtomicInteger(instance), root) ?: fail("No image for id $id"),
            DIFF_THRESHOLD
        )
    }

    private class Node(val id: Long, val children: List<Node> = listOf())

    private fun assertTreeIdsEqual(expected: Node, actual: SkiaViewNode) {
        assertEquals(expected.id, actual.id)
        assertEquals(expected.children.size, actual.children.size)
        assertEquals(expected.children.isEmpty(), actual.image != null)
        expected.children.zip(actual.children).forEach { (e, a) -> assertTreeIdsEqual(e, a) }
    }

    private fun findImage(id: Long, instance: AtomicInteger, node: SkiaViewNode): BufferedImage? {
        if (id == node.id && node.image != null) {
            if (instance.get() == 0) {
                return node.image as? BufferedImage
            }
            instance.decrementAndGet()
        }
        return node.children.mapNotNull { findImage(id, instance, it) }.firstOrNull()
    }
}
