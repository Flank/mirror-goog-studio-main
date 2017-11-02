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

package com.android.sdklib.devices

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StorageTest {

  @Test
  fun testGetSize() {
    val storageNoUnits = Storage(1025)
    assertThat(storageNoUnits.size).isEqualTo(1025)
    assertThat(storageNoUnits.getSizeAsUnit(Storage.Unit.B)).isEqualTo(1025)
    assertThat(storageNoUnits.getSizeAsUnit(Storage.Unit.KiB)).isEqualTo(1)

    val storageMbytes = Storage(35, Storage.Unit.MiB)
    assertThat(storageMbytes.size).isEqualTo(35 * 1024L * 1024L)
    assertThat(storageMbytes.getSizeAsUnit(Storage.Unit.KiB)).isEqualTo(35 * 1024L)
    assertThat(storageMbytes.getSizeAsUnit(Storage.Unit.MiB)).isEqualTo(35)

    assertThat(storageMbytes.getPreciseSizeAsUnit(Storage.Unit.B)).isWithin(0.001).of(35.0 * 1024 * 1024)
    assertThat(storageMbytes.getPreciseSizeAsUnit(Storage.Unit.KiB)).isWithin(0.001).of(35.0 * 1024)
    assertThat(storageMbytes.getPreciseSizeAsUnit(Storage.Unit.MiB)).isWithin(0.0001).of(35.0)
    assertThat(storageMbytes.getPreciseSizeAsUnit(Storage.Unit.GiB)).isWithin(0.0001).of(35.0 / 1024)
  }

  @Test
  fun testAppropriateUnits() {
    assertThat(Storage(2048, Storage.Unit.KiB).appropriateUnits).isEqualTo(Storage.Unit.MiB)
    assertThat(Storage(2049, Storage.Unit.KiB).appropriateUnits).isEqualTo(Storage.Unit.KiB)
  }

  @Test
  fun testEquals() {
    val storageNoUnits = Storage(1025 * 1024L)
    val StorageBytes = Storage(1025 * 1024L, Storage.Unit.B)
    val StorageKbytes = Storage(1025, Storage.Unit.KiB)
    val StorageDecimal = Storage((1025 * 1000).toLong())

    assertThat(storageNoUnits).isEqualTo(StorageBytes)
    assertThat(StorageBytes).isEqualTo(StorageKbytes)
    assertThat(StorageKbytes).isNotEqualTo(StorageDecimal)
  }

  @Test
  fun testLessThan() {
    val small = Storage(123, Storage.Unit.MiB)
    val medium = Storage(124 * 1024, Storage.Unit.KiB)
    val large = Storage(125, Storage.Unit.MiB)

    assertThat(small.lessThan(medium)).isTrue()
    assertThat(medium.lessThan(large)).isTrue()

    assertThat(small.lessThan(small)).isFalse()
    assertThat(medium.lessThan(small)).isFalse()
    assertThat(large.lessThan(medium)).isFalse()
  }

  @Test
  fun testToString() {
    assertThat(Storage(2048, Storage.Unit.KiB).toString()).isEqualTo("2 MB")
    assertThat(Storage(2049, Storage.Unit.KiB).toString()).isEqualTo("2049 KB")
    assertThat(Storage(2000, Storage.Unit.MiB).toString()).isEqualTo("2000 MB")
    assertThat(Storage(2000 * 1024L, Storage.Unit.MiB).toString()).isEqualTo("2000 GB")
  }

  @Test
  fun testUnit() {
    assertThat(Storage.Unit.getEnum("B")).isEqualTo(Storage.Unit.B)
    assertThat(Storage.Unit.getEnum("KiB")).isEqualTo(Storage.Unit.KiB)
    assertThat(Storage.Unit.getEnum("MiB")).isEqualTo(Storage.Unit.MiB)
    assertThat(Storage.Unit.getEnum("GiB")).isEqualTo(Storage.Unit.GiB)
    assertThat(Storage.Unit.getEnum("TiB")).isEqualTo(Storage.Unit.TiB)
    assertThat(Storage.Unit.getEnum("ZiB")).isEqualTo(null)

    assertThat(Storage.Unit.B.displayValue).isEqualTo("B")
    assertThat(Storage.Unit.KiB.displayValue).isEqualTo("KB")
    assertThat(Storage.Unit.MiB.displayValue).isEqualTo("MB")
    assertThat(Storage.Unit.GiB.displayValue).isEqualTo("GB")
    assertThat(Storage.Unit.TiB.displayValue).isEqualTo("TB")

    assertThat(Storage.Unit.B.toString()).isEqualTo("B")
    assertThat(Storage.Unit.KiB.toString()).isEqualTo("KiB")
    assertThat(Storage.Unit.MiB.toString()).isEqualTo("MiB")
    assertThat(Storage.Unit.GiB.toString()).isEqualTo("GiB")
    assertThat(Storage.Unit.TiB.toString()).isEqualTo("TiB")

    assertThat(Storage.Unit.B.numberOfBytes).isEqualTo(1)
    assertThat(Storage.Unit.KiB.numberOfBytes).isEqualTo(1024L)
    assertThat(Storage.Unit.MiB.numberOfBytes).isEqualTo(1024L * 1024L)
    assertThat(Storage.Unit.GiB.numberOfBytes).isEqualTo(1024L * 1024L * 1024L)
    assertThat(Storage.Unit.TiB.numberOfBytes).isEqualTo(1024L * 1024L * 1024L * 1024L)
  }
}
