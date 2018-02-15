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
package com.android.layoutinspector.model

import org.junit.Assert.*

import javax.swing.tree.TreePath
import org.junit.Test

class ViewNodeTest {

    private val viewNodeFlatString: ByteArray
        get() {
            val text = ("myroot@191 cat:foo=4,4394 cat2:foo2=5,hello zoo=3,baz mID=3,god \n"
                    + "  node1@3232 cat:foo=8,[] happy cow:child=4,calf cow:foo=5,super mID=9,not-a-god \n"
                    + "  node2@222 noun:eg=10,alpha beta mID=11,maybe-a-god \n"
                    + "    node3@3333 mID=11,another-god cat:foo=19,this is a long text \n"
                    + "DONE.\n")
            return text.toByteArray()
        }

    @Test
    @Throws(Exception::class)
    fun testParseNodeTree() {
        val root = ViewNode.parseFlatString(viewNodeFlatString)
        assertEquals(root!!.id, "god")
        assertEquals(root.childCount.toLong(), 2)
        assertEquals(root.getChildAt(1).childCount.toLong(), 1)
    }

    @Test
    @Throws(Exception::class)
    fun testParseNodeAttrs() {
        var node = ViewNode.parseFlatString(viewNodeFlatString)!!.getChildAt(0)
        assertTrue(node.getProperty("cat:foo")!!.value!!.endsWith("happy"))
        assertEquals(node.getProperty("cow:child")!!.value, "calf")

        node = ViewNode.parseFlatString(viewNodeFlatString)!!.getChildAt(1).getChildAt(0)
        assertEquals(node.getProperty("cat:foo")!!.value, "this is a long text")
    }

    @Test
    @Throws(Exception::class)
    fun testPropertiesOrdering() {
        val root = ViewNode.parseFlatString(viewNodeFlatString)

        val prop1 = root!!.getChildAt(0).getProperty("cat:foo")
        val prop2 = root.getChildAt(1).getChildAt(0).getProperty("cat:foo")

        assertEquals(prop1, prop2)
        assertTrue(prop1!!.compareTo(prop2!!) == 0)

        // Test comparison between properties with null and non-null category values
        val prop3 = root.getProperty("zoo")
        assertEquals(prop3!!.compareTo(prop1).toLong(), -1)
        assertEquals(prop1.compareTo(prop3).toLong(), 1)
        assertNotEquals(prop1, prop3)

        // Test same non-null category values
        val prop4 = root.getChildAt(0).getProperty("cow:foo")
        val prop5 = root.getChildAt(0).getProperty("cow:child")
        assertNotEquals(prop4, prop5)
        assertEquals(prop4!!.compareTo(prop5!!).toLong(), 1)
        assertEquals(prop5.compareTo(prop4).toLong(), -1)

        // Test non-null categories ordering with the same property name
        assertNotEquals(prop1, prop4)
        assertEquals(prop1.compareTo(prop4).toLong(), -1)
        assertEquals(prop4.compareTo(prop1).toLong(), 1)
    }

    @Test
    @Throws(Exception::class)
    fun testViewNodeTableModel() {
        val node = ViewNode.parseFlatString(viewNodeFlatString)
        val model = ViewNodeTableModel()
        model.setNode(node!!)
        assertEquals(4, model.rowCount.toLong())
        // Arbitrarily take the second row for testing
        assertTrue(model.getValueAt(1, 0) is String)
        assertTrue(model.getValueAt(1, 1) is String)
        assertEquals("zoo", model.getValueAt(1, 0))
        assertEquals("baz", model.getValueAt(1, 1))
    }

    @Test
    @Throws(Exception::class)
    fun testViewNodeGroupProperties() {
        val node = ViewNode.parseFlatString(viewNodeFlatString)

        assertEquals(node!!.groupedProperties.size.toLong(), 3)
        assertEquals(node.groupedProperties["properties"]!!.size.toLong(), 2)
        assertEquals("4394", node.groupedProperties["cat"]!!.get(0).value)
        val property = node.groupedProperties["properties"]!!.get(0)
        assertEquals("zoo", property.name)
        assertEquals("baz", property.value)
    }

    @Test
    fun testGetPathNullRoot() {
        val node = ViewNode.parseFlatString(viewNodeFlatString)
        val child = node!!.getChildAt(1).getChildAt(0)

        val nodes = arrayOf(child.getParent()?.getParent()!!, child.getParent()!!, child)
        val path = TreePath(nodes)
        assertEquals(path, ViewNode.getPath(child))
    }

    @Test
    fun testGetPathWithParent() {
        val node = ViewNode.parseFlatString(viewNodeFlatString)
        val child = node!!.getChildAt(1).getChildAt(0)
        val root = child.getParent()

        val nodes = arrayOf(child.getParent()!!, child)
        val path = TreePath(nodes)
        assertEquals(path, ViewNode.getPathFromParent(child, root!!))
    }

    // root only affects the returned path if it's in the path.
    @Test
    fun testGetPathWithParentInvalid() {
        val node = ViewNode.parseFlatString(viewNodeFlatString)
        val child = node!!.getChildAt(1).getChildAt(0)
        val root = node.getChildAt(0)

        val nodes = arrayOf(child.getParent()!!.getParent()!!, child.getParent()!!, child)
        val path = TreePath(nodes)
        assertEquals(path, ViewNode.getPathFromParent(child, root))
    }
}
