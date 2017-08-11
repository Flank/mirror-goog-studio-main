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
package com.android.tools.perflib.heap.ext

import com.android.testutils.TestResources
import com.android.tools.perflib.captures.MemoryMappedFileBuffer
import com.android.tools.perflib.heap.ClassInstance
import com.android.tools.perflib.heap.Instance
import com.android.tools.perflib.heap.Snapshot
import com.android.tools.perflib.heap.Type
import com.android.tools.proguard.ProguardMap
import com.google.common.truth.Truth
import org.junit.Test
import java.util.*
import java.util.Collections.emptyList

class NativeRegistryPostProcessorTest {

  @Test
  fun testNativeAllocationsCorrectlyAssigned() {
    val instances = HashMap<Instance, Long>()

    val file = TestResources.getFile(this.javaClass, "/o_bitmap_native_allocation.android-hprof")
    val snapshot = Snapshot.createSnapshot(MemoryMappedFileBuffer(file), ProguardMap(), emptyList<SnapshotPostProcessor>())

    val cleanerClass = snapshot.findClass(NativeRegistryPostProcessor.CLEANER_CLASS)
    val cleanerThunkClass = snapshot.findClass(NativeRegistryPostProcessor.CLEANER_THUNK_CLASS)
    val nativeRegistryClass = snapshot.findClass(NativeRegistryPostProcessor.NATIVE_REGISTRY_CLASS)
    Truth.assertThat(cleanerClass).isNotNull()
    Truth.assertThat(cleanerThunkClass).isNotNull()
    Truth.assertThat(nativeRegistryClass).isNotNull()

    // Logic here pretty much mimics NativeRegistryPostProcessor...
    for (instance in cleanerClass!!.instancesList) {
      val classInstance = instance as ClassInstance

      val referentField = classInstance.values.stream().filter { f -> "referent" == f.field.name }.findFirst()
      Truth.assertThat(referentField.isPresent).isTrue()

      val thunkField = classInstance.values.stream().filter { f -> "thunk" == f.field.name }.findFirst()
      Truth.assertThat(thunkField.isPresent).isTrue()

      val referentInstance = referentField.get().value as Instance
      val thunkInstance = thunkField.get().value as ClassInstance
      Truth.assertThat(referentInstance).isNotNull()
      Truth.assertThat(referentInstance.nativeSize).isEqualTo(0L)
      Truth.assertThat(thunkInstance).isNotNull()

      val nativeRegistryField = thunkInstance.values.stream().filter {
        f ->
        f.value is ClassInstance && (f.value as ClassInstance).classObj === nativeRegistryClass
      }.findFirst()

      if (!nativeRegistryField.isPresent) {
        continue
      }

      val nativeRegistryInstance = nativeRegistryField.get().value as ClassInstance
      val sizeField = nativeRegistryInstance.values.stream()
          .filter { f -> "size" == f.field.name && f.field.type == Type.LONG }.findFirst()
      Truth.assertThat(sizeField.isPresent).isTrue()

      (instances as java.util.Map<Instance, Long>).putIfAbsent(referentInstance, sizeField.get().value as Long)
    }

    val postProcessor = NativeRegistryPostProcessor()
    postProcessor.postProcess(snapshot)

    // Verify postprocessor results.
    Truth.assertThat(postProcessor.hasNativeAllocations).isTrue()
    for ((key, value) in instances) {
      Truth.assertThat(key.nativeSize).isEqualTo(value)
    }
  }
}
