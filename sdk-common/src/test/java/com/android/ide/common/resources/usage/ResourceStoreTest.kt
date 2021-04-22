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

package com.android.ide.common.resources.usage

import com.android.ide.common.resources.usage.ResourceUsageModel.Resource
import com.android.resources.ResourceType
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test

class ResourceStoreTest {
    private fun checkSerialization(store: ResourceStore, includeValues: Boolean) {
        val serialized = ResourceStore.serialize(store, includeValues)
        val deserialized = ResourceStore.deserialize(serialized)
        if (includeValues) {
            assertThat(ResourceStore.serialize(deserialized, includeValues)).isEqualTo(serialized)
            assertThat(deserialized.dumpConfig()).isEqualTo(store.dumpConfig())
            assertThat(deserialized.dumpKeepResources()).isEqualTo(store.dumpKeepResources())
            assertThat(deserialized.dumpReferences()).isEqualTo(store.dumpReferences())
            assertThat(deserialized.dumpResourceModel()).isEqualTo(store.dumpResourceModel())
        }
    }

    private fun checkSerialization(store: ResourceStore) {
        checkSerialization(store, true)
        checkSerialization(store, false)
    }

    @Test
    fun testBasic() {
        val model = ResourceUsageModel()
        model.addResource(ResourceType.STRING, "app_name", null)
        assertThat(model.serialize(true)).isEqualTo("string[app_name(E)];;;;")
        checkSerialization(model.mResourceStore)
    }

    @Test
    fun testSimple() {
        val store = ResourceStore(supportMultipackages = false)
        val appName = store.addResource(Resource(null, ResourceType.STRING, "app_name", -1))
        val hello = store.addResource(Resource(null, ResourceType.STRING, "hello_world", 0x7f030000))
        val layout = store.addResource(Resource(null, ResourceType.LAYOUT, "activity_main", 0x7f070000))
        val included = store.addResource(Resource(null, ResourceType.LAYOUT, "included", -1))
        store.addResource(Resource(null, ResourceType.DIMEN, "dim", -1))
        appName.isDeclared = true
        layout.addReference(included)
        layout.addReference(hello)
        layout.isReachable = true
        appName.isPublic = true
        store.recordDiscardToolAttribute("dimen/d*")
        store.recordKeepToolAttribute("layout/included,string/*")
        assertThat(ResourceStore.serialize(store, true)).isEqualTo(
            "dimen[dim(E)],layout[activity_main(R,7f070000),included(E)],string[app_name(DP),hello_world(E,7f030000)];1^2^4;layout/included,string/*;dimen/d*;"
        )
        checkSerialization(store)
    }

    @Test
    fun testMultiplePackages() {
        val store = ResourceStore(supportMultipackages = true)
        val appName = store.addResource(Resource("test.pkg", ResourceType.STRING, "app_name", -1))
        val hello = store.addResource(Resource("test.pkg", ResourceType.STRING, "hello_world", 0x7f030000))
        val layout = store.addResource(Resource(null, ResourceType.LAYOUT, "activity_main", 0x7f070000))
        val included = store.addResource(Resource("test.pkg.sub", ResourceType.LAYOUT, "included", -1))
        store.addResource(Resource(null, ResourceType.DIMEN, "dim", -1))
        layout.addReference(included)
        layout.addReference(hello)
        layout.isReachable = true
        appName.isPublic = true
        store.recordDiscardToolAttribute("dimen/d*")
        store.recordKeepToolAttribute("layout/included,string/*")
        assertThat(ResourceStore.serialize(store, true)).isEqualTo(
            "P;dimen[dim(E)],layout[activity_main(R,,7f070000),included(E,test.pkg.sub)],string[app_name(P,test.pkg),hello_world(E,test.pkg,7f030000)];1^2^4;layout/included,string/*;dimen/d*;"
        )
        checkSerialization(store)
    }

    @Test
    fun testMerge() {
        val store1 = ResourceStore.deserialize("dimen[dim(E)],layout[activity_main(R,7f070000),included(E)],string[app_name(DP),hello_world(E,7f030000)];1^2^4;layout/included,string/*;dimen/d*;")
        val store2 = ResourceStore.deserialize("string[app_name2(E)];;;;")
        val store3 = ResourceStore.deserialize("dimen[dim(E)],layout[activity_main(DR,7f070000),included(E)],string[hello_world(E,7f030000)];;;string/s*;")

        store1.merge(store2)
        store1.merge(store3)
        val merged = ResourceStore.serialize(store1)
        assertThat(merged).isEqualTo("dimen[dim(E)],layout[activity_main(U,7f070000),included(E)],string[app_name2(E),app_name(DP),hello_world(E,7f030000)];1^2^5;layout/included,string/*;dimen/d*,string/s*;")
        // Additional merges should be no-ops: flags, locations etc should be skipped if already there
        store1.merge(store3)
        store1.merge(store2)
        val merged2 = ResourceStore.serialize(store1)
        assertThat(merged2).isEqualTo(merged)
    }

    @Test
    fun testTypes() {
        for (type in ResourceType.values()) {
            val store = ResourceStore(supportMultipackages = true)
            try {
                store.addResource(Resource("test.pkg", type, type.name, -1))
                val serialized = ResourceStore.serialize(store, false)
                ResourceStore.deserialize(serialized)
            } catch (e: Throwable) {
                fail("Failed deserializing resource type $type")
            }
        }

        // 185057616: UnusedResourceDetector fails on 7.0.0-alpha14
        ResourceStore.deserialize(
            "P;dimen[name1_ref(E),name0_ref(E),name0(E,test.pkg)],sample[name1(E,test.pkg)];2^1,3^0;;;"
        )
    }
}
