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
#include "utils/memory_map.h"

#include <gtest/gtest.h>
#include <sstream>  // for std::ostringstream
#include <string>
#include <vector>
#include "test/utils.h"

using std::string;
using std::vector;
using MemoryRegion = profiler::MemoryMap::MemoryRegion;

namespace profiler {

class MockProcfsMapFiles final : public ProcfsFiles {
 public:
  string GetMemoryMapFilePath(int32_t pid) const override {
    std::ostringstream os;
    os << "memory_map" << pid << ".txt";
    return TestUtils::getUtilsTestData(os.str());
  }
};

TEST(MemoryMapTest, ReadCornerCaseMap) {
  profiler::MockProcfsMapFiles procfs;

  MemoryMap map(procfs, 1);
  EXPECT_EQ(true, map.Update());
  auto regions = map.GetRegions();

  auto expected_regions = {MemoryRegion{"/path/to/executable", 0, 1, 0},
                           MemoryRegion{"/path/to/executable", 2, 3, 2},
                           MemoryRegion{"", 4, 5, 0},
                           MemoryRegion{"/path/to/lib", 0x100, 0x200, 0x12345},
                           MemoryRegion{"[vdso]", 0xeee, 0xfff, 0},
                           MemoryRegion{"[heap]", 0x1234, 0x2000, 0}};
  EXPECT_EQ(expected_regions.size(), regions.size());
  int i = 0;
  for (auto &r : expected_regions) {
    EXPECT_EQ(r.name, regions[i].name);
    EXPECT_EQ(r.start_address, regions[i].start_address);
    EXPECT_EQ(r.end_address, regions[i].end_address);
    EXPECT_EQ(r.file_offset, regions[i].file_offset);
    i++;
  }
}

TEST(MemoryMapTest, ReadRealMap) {
  profiler::MockProcfsMapFiles procfs;

  MemoryMap map(procfs, 2);
  EXPECT_EQ(true, map.Update());
  auto regions = map.GetRegions();

  auto expected_regions = {
      MemoryRegion{"/system/bin/app_process32", 0x0e70c000, 0x0e711000,
                   0x00000000},
      MemoryRegion{"/system/bin/app_process32", 0x0e712000, 0x0e713000,
                   0x00005000},
      MemoryRegion{"", 0x0e713000, 0x0e714000, 0x00000000},
      MemoryRegion{"[anon:dalvik-main space (region space)]", 0x12c00000,
                   0x12dc0000, 0x00000000},
      MemoryRegion{"[anon:dalvik-main space (region space)]", 0x12dc0000,
                   0x13480000, 0x001c0000},
      MemoryRegion{"[anon:dalvik-main space (region space)]", 0x13480000,
                   0x13740000, 0x00880000},
      MemoryRegion{"[anon:dalvik-main space (region space)]", 0x13740000,
                   0x13800000, 0x00b40000},
      MemoryRegion{"[anon:dalvik-main space (region space)]", 0x13800000,
                   0x14140000, 0x00c00000},
      MemoryRegion{"[anon:dalvik-main space (region space)]", 0x14140000,
                   0x52c00000, 0x01540000},
      MemoryRegion{"/data/dalvik-cache/arm/system@framework@boot.art",
                   0x6f870000, 0x6fa1e000, 0x00000000},
      MemoryRegion{
          "/data/dalvik-cache/arm/system@framework@boot-core-libart.art",
          0x6fa1e000, 0x6faba000, 0x00000000},
      MemoryRegion{"/data/dalvik-cache/arm/system@framework@boot-conscrypt.art",
                   0x6faba000, 0x6faf8000, 0x00000000},
      MemoryRegion{"/data/dalvik-cache/arm/system@framework@boot-okhttp.art",
                   0x6faf8000, 0x6fb19000, 0x00000000},
      MemoryRegion{
          "/data/dalvik-cache/arm/system@framework@boot-bouncycastle.art",
          0x6fb19000, 0x6fb43000, 0x00000000},
      MemoryRegion{
          "/data/dalvik-cache/arm/system@framework@boot-apache-xml.art",
          0x6fb43000, 0x6fb4f000, 0x00000000},
      MemoryRegion{
          "/data/dalvik-cache/arm/system@framework@boot-legacy-test.art",
          0x6fb4f000, 0x6fb53000, 0x00000000},
      MemoryRegion{"/data/dalvik-cache/arm/system@framework@boot-ext.art",
                   0x6fb53000, 0x6fb79000, 0x00000000},
      MemoryRegion{"/data/dalvik-cache/arm/system@framework@boot-framework.art",
                   0x6fb79000, 0x70008000, 0x00000000},
      MemoryRegion{
          "/data/dalvik-cache/arm/system@framework@boot-telephony-common.art",
          0x70008000, 0x70046000, 0x00000000},
      MemoryRegion{
          "/data/dalvik-cache/arm/system@framework@boot-voip-common.art",
          0x70046000, 0x7004e000, 0x00000000},
      MemoryRegion{
          "/data/dalvik-cache/arm/system@framework@boot-ims-common.art",
          0x7004e000, 0x70056000, 0x00000000},
      MemoryRegion{"/data/dalvik-cache/arm/"
                   "system@framework@boot-org.apache.http.legacy.boot.art",
                   0x70056000, 0x70074000, 0x00000000},
      MemoryRegion{"/data/dalvik-cache/arm/"
                   "system@framework@boot-android.hidl.base-V1.0-java.art",
                   0x70074000, 0x70075000, 0x00000000},
      MemoryRegion{"/data/dalvik-cache/arm/"
                   "system@framework@boot-android.hidl.manager-V1.0-java.art",
                   0x70075000, 0x70078000, 0x00000000},
      MemoryRegion{"/data/dalvik-cache/arm/"
                   "system@framework@boot-com.google.vr.platform.art",
                   0x70078000, 0x70079000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot.oat", 0x70079000, 0x70244000,
                   0x00000000},
      MemoryRegion{"/system/framework/arm/boot.oat", 0x70244000, 0x7076d000,
                   0x001cb000},
      MemoryRegion{"[anon:.bss]", 0x7076d000, 0x7076f000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot.oat", 0x7076f000, 0x70770000,
                   0x006f4000},
      MemoryRegion{"/system/framework/arm/boot.oat", 0x70770000, 0x70771000,
                   0x006f5000},
      MemoryRegion{"/system/framework/arm/boot-core-libart.oat", 0x70771000,
                   0x70841000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-core-libart.oat", 0x70841000,
                   0x70a43000, 0x000d0000},
      MemoryRegion{"[anon:.bss]", 0x70a43000, 0x70a44000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-core-libart.oat", 0x70a44000,
                   0x70a45000, 0x002d2000},
      MemoryRegion{"/system/framework/arm/boot-core-libart.oat", 0x70a45000,
                   0x70a46000, 0x002d3000},
      MemoryRegion{"/system/framework/arm/boot-conscrypt.oat", 0x70a46000,
                   0x70a63000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-conscrypt.oat", 0x70a63000,
                   0x70aa8000, 0x0001d000},
      MemoryRegion{"[anon:.bss]", 0x70aa8000, 0x70aa9000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-conscrypt.oat", 0x70aa9000,
                   0x70aaa000, 0x00062000},
      MemoryRegion{"/system/framework/arm/boot-conscrypt.oat", 0x70aaa000,
                   0x70aab000, 0x00063000},
      MemoryRegion{"/system/framework/arm/boot-okhttp.oat", 0x70aab000,
                   0x70ad0000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-okhttp.oat", 0x70ad0000,
                   0x70b2b000, 0x00025000},
      MemoryRegion{"[anon:.bss]", 0x70b2b000, 0x70b2c000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-okhttp.oat", 0x70b2c000,
                   0x70b2d000, 0x00080000},
      MemoryRegion{"/system/framework/arm/boot-okhttp.oat", 0x70b2d000,
                   0x70b2e000, 0x00081000},
      MemoryRegion{"/system/framework/arm/boot-bouncycastle.oat", 0x70b2e000,
                   0x70b4b000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-bouncycastle.oat", 0x70b4b000,
                   0x70b90000, 0x0001d000},
      MemoryRegion{"[anon:.bss]", 0x70b90000, 0x70b91000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-bouncycastle.oat", 0x70b91000,
                   0x70b92000, 0x00062000},
      MemoryRegion{"/system/framework/arm/boot-bouncycastle.oat", 0x70b92000,
                   0x70b93000, 0x00063000},
      MemoryRegion{"/system/framework/arm/boot-apache-xml.oat", 0x70b93000,
                   0x70b99000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-apache-xml.oat", 0x70b99000,
                   0x70b9a000, 0x00006000},
      MemoryRegion{"/system/framework/arm/boot-apache-xml.oat", 0x70b9a000,
                   0x70b9b000, 0x00007000},
      MemoryRegion{"/system/framework/arm/boot-apache-xml.oat", 0x70b9b000,
                   0x70b9c000, 0x00008000},
      MemoryRegion{"/system/framework/arm/boot-legacy-test.oat", 0x70b9c000,
                   0x70b9f000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-legacy-test.oat", 0x70b9f000,
                   0x70ba1000, 0x00003000},
      MemoryRegion{"[anon:.bss]", 0x70ba1000, 0x70ba2000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-legacy-test.oat", 0x70ba2000,
                   0x70ba3000, 0x00005000},
      MemoryRegion{"/system/framework/arm/boot-legacy-test.oat", 0x70ba3000,
                   0x70ba4000, 0x00006000},
      MemoryRegion{"/system/framework/arm/boot-ext.oat", 0x70ba4000, 0x70bb6000,
                   0x00000000},
      MemoryRegion{"/system/framework/arm/boot-ext.oat", 0x70bb6000, 0x70bdb000,
                   0x00012000},
      MemoryRegion{"[anon:.bss]", 0x70bdb000, 0x70bdc000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-ext.oat", 0x70bdc000, 0x70bdd000,
                   0x00037000},
      MemoryRegion{"/system/framework/arm/boot-ext.oat", 0x70bdd000, 0x70bde000,
                   0x00038000},
      MemoryRegion{"/system/framework/arm/boot-framework.oat", 0x70bde000,
                   0x711ad000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-framework.oat", 0x711ad000,
                   0x71fc5000, 0x005cf000},
      MemoryRegion{"[anon:.bss]", 0x71fc5000, 0x71fcc000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-framework.oat", 0x71fcc000,
                   0x71fcd000, 0x013e7000},
      MemoryRegion{"/system/framework/arm/boot-framework.oat", 0x71fcd000,
                   0x71fce000, 0x013e8000},
      MemoryRegion{"/system/framework/arm/boot-telephony-common.oat",
                   0x71fce000, 0x72081000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-telephony-common.oat",
                   0x72081000, 0x72246000, 0x000b3000},
      MemoryRegion{"[anon:.bss]", 0x72246000, 0x72248000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-telephony-common.oat",
                   0x72248000, 0x72249000, 0x00278000},
      MemoryRegion{"/system/framework/arm/boot-telephony-common.oat",
                   0x72249000, 0x7224a000, 0x00279000},
      MemoryRegion{"/system/framework/arm/boot-voip-common.oat", 0x7224a000,
                   0x72251000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-voip-common.oat", 0x72251000,
                   0x7225c000, 0x00007000},
      MemoryRegion{"[anon:.bss]", 0x7225c000, 0x7225d000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-voip-common.oat", 0x7225d000,
                   0x7225e000, 0x00012000},
      MemoryRegion{"/system/framework/arm/boot-voip-common.oat", 0x7225e000,
                   0x7225f000, 0x00013000},
      MemoryRegion{"/system/framework/arm/boot-ims-common.oat", 0x7225f000,
                   0x72267000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-ims-common.oat", 0x72267000,
                   0x72275000, 0x00008000},
      MemoryRegion{"[anon:.bss]", 0x72275000, 0x72276000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-ims-common.oat", 0x72276000,
                   0x72277000, 0x00016000},
      MemoryRegion{"/system/framework/arm/boot-ims-common.oat", 0x72277000,
                   0x72278000, 0x00017000},
      MemoryRegion{"/system/framework/arm/boot-org.apache.http.legacy.boot.oat",
                   0x72278000, 0x7229e000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-org.apache.http.legacy.boot.oat",
                   0x7229e000, 0x722fa000, 0x00026000},
      MemoryRegion{"[anon:.bss]", 0x722fa000, 0x722fb000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-org.apache.http.legacy.boot.oat",
                   0x722fb000, 0x722fc000, 0x00082000},
      MemoryRegion{"/system/framework/arm/boot-org.apache.http.legacy.boot.oat",
                   0x722fc000, 0x722fd000, 0x00083000},
      MemoryRegion{"/system/framework/arm/boot-android.hidl.base-V1.0-java.oat",
                   0x722fd000, 0x72300000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-android.hidl.base-V1.0-java.oat",
                   0x72300000, 0x72301000, 0x00003000},
      MemoryRegion{"/system/framework/arm/boot-android.hidl.base-V1.0-java.oat",
                   0x72301000, 0x72302000, 0x00004000},
      MemoryRegion{"/system/framework/arm/boot-android.hidl.base-V1.0-java.oat",
                   0x72302000, 0x72303000, 0x00005000},
      MemoryRegion{
          "/system/framework/arm/boot-android.hidl.manager-V1.0-java.oat",
          0x72303000, 0x72307000, 0x00000000},
      MemoryRegion{
          "/system/framework/arm/boot-android.hidl.manager-V1.0-java.oat",
          0x72307000, 0x7230a000, 0x00004000},
      MemoryRegion{"[anon:.bss]", 0x7230a000, 0x7230b000, 0x00000000},
      MemoryRegion{
          "/system/framework/arm/boot-android.hidl.manager-V1.0-java.oat",
          0x7230b000, 0x7230c000, 0x00007000},
      MemoryRegion{
          "/system/framework/arm/boot-android.hidl.manager-V1.0-java.oat",
          0x7230c000, 0x7230d000, 0x00008000},
      MemoryRegion{"/system/framework/arm/boot-com.google.vr.platform.oat",
                   0x7230d000, 0x72310000, 0x00000000},
      MemoryRegion{"/system/framework/arm/boot-com.google.vr.platform.oat",
                   0x72310000, 0x72311000, 0x00003000},
      MemoryRegion{"/system/framework/arm/boot-com.google.vr.platform.oat",
                   0x72311000, 0x72312000, 0x00004000},
      MemoryRegion{"/system/framework/arm/boot-com.google.vr.platform.oat",
                   0x72312000, 0x72313000, 0x00005000},
      MemoryRegion{"[anon:dalvik-zygote space]", 0x72313000, 0x723a3000,
                   0x00000000},
      MemoryRegion{"[anon:dalvik-non moving space]", 0x723a3000, 0x723a4000,
                   0x00000000},
      MemoryRegion{"[anon:dalvik-non moving space]", 0x723a4000, 0x723b6000,
                   0x00001000},
      MemoryRegion{"[anon:dalvik-non moving space]", 0x723b6000, 0x75b14000,
                   0x00013000},
      MemoryRegion{"[anon:dalvik-non moving space]", 0x75b14000, 0x76313000,
                   0x03771000},
      MemoryRegion{"[anon:dalvik-/data/app/"
                   "com.eugene.sum-9OdJq78uzWm2qWAzveNyHA==/oat/arm/base.art]",
                   0x76313000, 0x76315000, 0x00000000},
      MemoryRegion{"[anon:dalvik-/data/app/"
                   "com.eugene.sum-9OdJq78uzWm2qWAzveNyHA==/oat/arm/base.art]",
                   0x76315000, 0x76318000, 0x00002000},
      MemoryRegion{"[anon:dalvik-/data/app/"
                   "com.eugene.sum-9OdJq78uzWm2qWAzveNyHA==/oat/arm/base.art]",
                   0x76318000, 0x7631b000, 0x00005000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbea00000, 0xbeac0000, 0x00057000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeac0000, 0xbead0000, 0x00050000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbead0000, 0xbeae0000, 0x0004f000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeae0000, 0xbeae8000, 0x0004e000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeae8000, 0xbeaec000, 0x0004c000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeaec000, 0xbeaf0000, 0x0004b000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeaf0000, 0xbeb00000, 0x0004a000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeb00000, 0xbeb10000, 0x00049000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeb10000, 0xbeb20000, 0x00048000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeb20000, 0xbeb30000, 0x00047000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeb30000, 0xbeb40000, 0x00046000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeb40000, 0xbeb60000, 0x00042000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeb60000, 0xbeb80000, 0x00041000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeb82000, 0xbeb83000, 0x0004d000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeb83000, 0xbeb87000, 0x00045000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeb87000, 0xbeb8b000, 0x00044000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeb8b000, 0xbeb8f000, 0x00043000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeb93000, 0xbeb94000, 0x0003f000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeb94000, 0xbeb96000, 0x0003e000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeb96000, 0xbeb9a000, 0x0003d000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeb9a000, 0xbebaa000, 0x0003a000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebaa000, 0xbebac000, 0x00039000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebac000, 0xbebae000, 0x00038000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebae000, 0xbebb0000, 0x00037000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebb0000, 0xbebb2000, 0x00036000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebb2000, 0xbebb4000, 0x00035000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebb4000, 0xbebb6000, 0x00034000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebb6000, 0xbebb8000, 0x00033000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebb8000, 0xbebba000, 0x00032000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebba000, 0xbebbc000, 0x00031000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebbc000, 0xbebbe000, 0x00030000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebbe000, 0xbebc0000, 0x0002f000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebc0000, 0xbebe0000, 0x0002d000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebf3000, 0xbebf5000, 0x00056000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebf5000, 0xbebf7000, 0x00055000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebf7000, 0xbebf9000, 0x00054000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebf9000, 0xbebfb000, 0x00053000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebfb000, 0xbebfd000, 0x00052000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbebfd000, 0xbec01000, 0x00051000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbec01000, 0xbec02000, 0x0003c000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbec02000, 0xbec04000, 0x0002e000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbec04000, 0xbec06000, 0x0002c000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbec06000, 0xbec08000, 0x0002b000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbec08000, 0xbec0c000, 0x00028000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbec0c000, 0xbec10000, 0x00027000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbec10000, 0xbec50000, 0x00025000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbec50000, 0xbec70000, 0x00024000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbec70000, 0xbed30000, 0x00023000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbed30000, 0xbed50000, 0x00022000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbed50000, 0xbed60000, 0x0001d000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbed60000, 0xbeea0000, 0x0001c000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeea0000, 0xbefc1000, 0x0001b000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefc2000, 0xbefc3000, 0x0003b000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefc3000, 0xbefc5000, 0x0002a000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefc5000, 0xbefc6000, 0x00026000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefc6000, 0xbefc8000, 0x00020000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefc8000, 0xbefca000, 0x0001f000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefca000, 0xbefcc000, 0x0001e000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefcc000, 0xbefd0000, 0x0001a000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefd0000, 0xbefd4000, 0x00019000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefd4000, 0xbefd5000, 0x00018000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefd5000, 0xbefdd000, 0x00017000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefdd000, 0xbefde000, 0x00016000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefde000, 0xbefe0000, 0x00015000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefe0000, 0xbefe1000, 0x00014000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefe1000, 0xbefe5000, 0x00013000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefe5000, 0xbefe6000, 0x00012000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefe6000, 0xbefe7000, 0x00011000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefe7000, 0xbefe8000, 0x00010000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefe8000, 0xbefe9000, 0x0000f000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefe9000, 0xbefea000, 0x0000e000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefea000, 0xbefeb000, 0x0000d000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefeb000, 0xbefec000, 0x0000c000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbefec000, 0xbeff0000, 0x0000b000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeff0000, 0xbeff1000, 0x0000a000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeff1000, 0xbeff5000, 0x00009000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeff5000, 0xbeff6000, 0x00008000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeff6000, 0xbeff7000, 0x00007000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeff7000, 0xbeff8000, 0x00006000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeff8000, 0xbeff9000, 0x00005000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeff9000, 0xbeffa000, 0x00004000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeffa000, 0xbeffb000, 0x00003000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeffb000, 0xbeffc000, 0x00002000},
      MemoryRegion{"/dev/kgsl-3d0", 0xbeffc000, 0xbf000000, 0x00001000},
      MemoryRegion{"[anon:libc_malloc]", 0xcc600000, 0xcc780000, 0x00000000},
      MemoryRegion{"[anon:thread stack guard page]", 0xcc789000, 0xcc78a000,
                   0x00000000},
      MemoryRegion{"", 0xcc78a000, 0xcc78b000, 0x00000000},
      MemoryRegion{"", 0xcc78b000, 0xcc886000, 0x00000000},
      MemoryRegion{"[anon:thread stack guard page]", 0xcc886000, 0xcc887000,
                   0x00000000},
      MemoryRegion{"", 0xcc887000, 0xcc888000, 0x00000000},
      MemoryRegion{"", 0xcc888000, 0xcc983000, 0x00000000},
      MemoryRegion{"[anon:thread stack guard page]", 0xcc983000, 0xcc984000,
                   0x00000000},
      MemoryRegion{"", 0xcc984000, 0xcca80000, 0x00000000},
      MemoryRegion{"[anon:libc_malloc]", 0xcca80000, 0xccb00000, 0x00000000},
      MemoryRegion{"/dev/hwbinder", 0xccb02000, 0xccc00000, 0x00000000},
      MemoryRegion{"[anon:libc_malloc]", 0xccc00000, 0xccc80000, 0x00000000},
      MemoryRegion{"/vendor/lib/hw/gralloc.msm8998.so", 0xccc8a000, 0xccc9b000,
                   0x00000000},
      MemoryRegion{"", 0xccc9b000, 0xccc9c000, 0x00000000},
      MemoryRegion{"/vendor/lib/hw/gralloc.msm8998.so", 0xccc9c000, 0xccc9d000,
                   0x00011000},
      MemoryRegion{"/vendor/lib/hw/gralloc.msm8998.so", 0xccc9d000, 0xccc9e000,
                   0x00012000},
      MemoryRegion{"/vendor/lib/libdrmutils.so", 0xcccd4000, 0xcccd8000,
                   0x00000000},
      MemoryRegion{"", 0xcccd8000, 0xcccd9000, 0x00000000},
      MemoryRegion{"/vendor/lib/libdrmutils.so", 0xcccd9000, 0xcccda000,
                   0x00004000},
      MemoryRegion{"/vendor/lib/libdrmutils.so", 0xcccda000, 0xcccdb000,
                   0x00005000},
      MemoryRegion{"/vendor/lib/libdrm.so", 0xccd11000, 0xccd1e000, 0x00000000},
      MemoryRegion{"/vendor/lib/libdrm.so", 0xccd1e000, 0xccd1f000, 0x0000c000},
      MemoryRegion{"/vendor/lib/libdrm.so", 0xccd1f000, 0xccd20000, 0x0000d000},
      MemoryRegion{"/vendor/lib/libqdMetaData.so", 0xccd69000, 0xccd6c000,
                   0x00000000},
      MemoryRegion{"", 0xccd6c000, 0xccd6d000, 0x00000000},
      MemoryRegion{"/vendor/lib/libqdMetaData.so", 0xccd6d000, 0xccd6e000,
                   0x00003000},
      MemoryRegion{"/vendor/lib/libqdMetaData.so", 0xccd6e000, 0xccd6f000,
                   0x00004000},
      MemoryRegion{"/vendor/lib/egl/eglSubDriverAndroid.so", 0xccd87000,
                   0xccd93000, 0x00000000},
      MemoryRegion{"/vendor/lib/egl/eglSubDriverAndroid.so", 0xccd93000,
                   0xccd94000, 0x0000c000},
      MemoryRegion{"/vendor/lib/egl/eglSubDriverAndroid.so", 0xccd94000,
                   0xccd95000, 0x0000d000},
      MemoryRegion{"/data/app/com.eugene.sum-9OdJq78uzWm2qWAzveNyHA==/lib/arm/"
                   "libnative-lib.so",
                   0xccdc9000, 0xccde4000, 0x00000000},
      MemoryRegion{"/data/app/com.eugene.sum-9OdJq78uzWm2qWAzveNyHA==/lib/arm/"
                   "libnative-lib.so",
                   0xccde4000, 0xccde6000, 0x0001a000},
      MemoryRegion{"/data/app/com.eugene.sum-9OdJq78uzWm2qWAzveNyHA==/lib/arm/"
                   "libnative-lib.so",
                   0xccde6000, 0xccde7000, 0x0001c000},
      MemoryRegion{"[anon:.bss]", 0xccde7000, 0xccdeb000, 0x00000000},
      MemoryRegion{"[anon:libc_malloc]", 0xcce00000, 0xcce80000, 0x00000000},
  };
  EXPECT_EQ(1699, regions.size());
  int i = 0;
  for (auto &r : expected_regions) {
    EXPECT_EQ(r.name, regions[i].name);
    EXPECT_EQ(r.start_address, regions[i].start_address);
    EXPECT_EQ(r.end_address, regions[i].end_address);
    EXPECT_EQ(r.file_offset, regions[i].file_offset);
    i++;
  }
}

TEST(MemoryMapTest, Lookup) {
  profiler::MockProcfsMapFiles procfs;

  MemoryMap map(procfs, 2);
  map.Update();

  // unmapped parts
  EXPECT_EQ(nullptr, map.LookupRegion(0));
  EXPECT_EQ(nullptr, map.LookupRegion(0x100));
  EXPECT_EQ(nullptr, map.LookupRegion(0x0ffffffffffffff));
  EXPECT_EQ(nullptr, map.LookupRegion(0xccd95000 + 1));

  // inside a region
  auto region = map.LookupRegion(0xccd6d000 + 4);
  ASSERT_TRUE(region != nullptr);
  EXPECT_EQ(0xccd6d000, region->start_address);
  EXPECT_EQ(0xccd6e000, region->end_address);
  EXPECT_EQ(0x00003000, region->file_offset);
  EXPECT_EQ("/vendor/lib/libqdMetaData.so", region->name);

  // beginning of a region
  region = map.LookupRegion(0xccc9c000);
  ASSERT_TRUE(region != nullptr);
  EXPECT_EQ(0xccc9c000, region->start_address);
  EXPECT_EQ(0xccc9d000, region->end_address);
  EXPECT_EQ(0x00011000, region->file_offset);
  EXPECT_EQ("/vendor/lib/hw/gralloc.msm8998.so", region->name);

  // Rebuild the map and test again
  map.Update();
  region = map.LookupRegion(0xccde7000 + 1000);
  ASSERT_TRUE(region != nullptr);
  EXPECT_EQ(0xccde7000, region->start_address);
  EXPECT_EQ(0xccdeb000, region->end_address);
  EXPECT_EQ(0, region->file_offset);
  EXPECT_EQ("[anon:.bss]", region->name);
}

}  // namespace profiler