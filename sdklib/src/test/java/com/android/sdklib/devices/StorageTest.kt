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
  fun testLargestReasonableUnits() {
    assertThat(Storage(2048, Storage.Unit.KiB).largestReasonableUnits).isEqualTo(Storage.Unit.MiB)
    assertThat(Storage(2049, Storage.Unit.KiB).largestReasonableUnits).isEqualTo(Storage.Unit.MiB)
    assertThat(Storage(500).largestReasonableUnits).isEqualTo(Storage.Unit.B)
    assertThat(Storage(500, Storage.Unit.GiB).largestReasonableUnits).isEqualTo(Storage.Unit.GiB)
    assertThat(Storage(1025, Storage.Unit.GiB).largestReasonableUnits).isEqualTo(Storage.Unit.TiB)
  }

  @Test
  fun testEquals() {
    val storageNoUnits = Storage(1025 * 1024L)
    val storageBytes = Storage(1025 * 1024L, Storage.Unit.B)
    val storageKbytes = Storage(1025, Storage.Unit.KiB)
    val storageDecimal = Storage(1025 * 1000L)

    assertThat(storageNoUnits).isEqualTo(storageBytes)
    assertThat(storageBytes).isEqualTo(storageKbytes)
    assertThat(storageKbytes).isNotEqualTo(storageDecimal)
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
  fun getStorageFromString() {
    assertThat(Storage.getStorageFromString("4")).isEqualTo(Storage(4, Storage.Unit.MiB))
    assertThat(Storage.getStorageFromString("5B")).isEqualTo(Storage(5, Storage.Unit.B))
    assertThat(Storage.getStorageFromString("5 B")).isEqualTo(Storage(5, Storage.Unit.B))
    assertThat(Storage.getStorageFromString("6 KB")).isEqualTo(Storage(6, Storage.Unit.KiB))
    assertThat(Storage.getStorageFromString("7MB")).isEqualTo(Storage(7, Storage.Unit.MiB))
    assertThat(Storage.getStorageFromString("2048   M")).isEqualTo(Storage(2, Storage.Unit.GiB))
    assertThat(Storage.getStorageFromString("8G")).isEqualTo(Storage(8, Storage.Unit.GiB))
    assertThat(Storage.getStorageFromString("9T")).isEqualTo(Storage(9, Storage.Unit.TiB))

    assertThat(Storage.getStorageFromString("")).isNull()
    assertThat(Storage.getStorageFromString("blah")).isNull()
    assertThat(Storage.getStorageFromString("2m")).isNull()
    assertThat(Storage.getStorageFromString("2 M B")).isNull()
    assertThat(Storage.getStorageFromString("2M B")).isNull()
    assertThat(Storage.getStorageFromString("1.5M")).isNull()
    assertThat(Storage.getStorageFromString("8KiB")).isNull()
  }

  @Test
  fun testToString() {
    assertThat(Storage(1234, Storage.Unit.B).toString()).isEqualTo("1234 B")
    assertThat(Storage(2048, Storage.Unit.KiB).toString()).isEqualTo("2 MB")
    assertThat(Storage(2049, Storage.Unit.KiB).toString()).isEqualTo("2049 KB")
    assertThat(Storage(2000, Storage.Unit.MiB).toString()).isEqualTo("2000 MB")
    assertThat(Storage(2000 * 1024L, Storage.Unit.MiB).toString()).isEqualTo("2000 GB")
    assertThat(Storage(2048 * 1024L, Storage.Unit.MiB).toString()).isEqualTo("2 TB")
  }

  @Test
  fun testToIniString() {
    assertThat(Storage(1234, Storage.Unit.B).toIniString()).isEqualTo("1234B")
    assertThat(Storage(2048, Storage.Unit.KiB).toIniString()).isEqualTo("2M")
    assertThat(Storage(2049, Storage.Unit.KiB).toIniString()).isEqualTo("2049K")
    assertThat(Storage(2000, Storage.Unit.MiB).toIniString()).isEqualTo("2000M")
    assertThat(Storage(2000 * 1024L, Storage.Unit.MiB).toIniString()).isEqualTo("2000G")
    assertThat(Storage(2048 * 1024L, Storage.Unit.MiB).toIniString()).isEqualTo("2T")
  }

  @Test
  fun testUnit() {
    assertThat(Storage.Unit.getEnum("B")).isEqualTo(Storage.Unit.B)
    assertThat(Storage.Unit.getEnum("KiB")).isEqualTo(Storage.Unit.KiB)
    assertThat(Storage.Unit.getEnum("MiB")).isEqualTo(Storage.Unit.MiB)
    assertThat(Storage.Unit.getEnum("GiB")).isEqualTo(Storage.Unit.GiB)
    assertThat(Storage.Unit.getEnum("TiB")).isEqualTo(Storage.Unit.TiB)
    assertThat(Storage.Unit.getEnum("")).isEqualTo(null)
    assertThat(Storage.Unit.getEnum(" ")).isEqualTo(null)
    assertThat(Storage.Unit.getEnum("ZiB")).isEqualTo(null)

    assertThat(Storage.Unit.getEnum('B')).isEqualTo(Storage.Unit.B)
    assertThat(Storage.Unit.getEnum('K')).isEqualTo(Storage.Unit.KiB)
    assertThat(Storage.Unit.getEnum('M')).isEqualTo(Storage.Unit.MiB)
    assertThat(Storage.Unit.getEnum('G')).isEqualTo(Storage.Unit.GiB)
    assertThat(Storage.Unit.getEnum('T')).isEqualTo(Storage.Unit.TiB)
    assertThat(Storage.Unit.getEnum(' ')).isEqualTo(null)
    assertThat(Storage.Unit.getEnum('m')).isEqualTo(null)
    assertThat(Storage.Unit.getEnum('Z')).isEqualTo(null)

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
