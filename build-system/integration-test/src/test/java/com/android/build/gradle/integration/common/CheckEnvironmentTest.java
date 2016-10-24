/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.common;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Checking if the environment is set up correctly. If this test fails, most probably something is
 * not set up correctly.
 *
 * <p>Once http://b.android.com/226074 is fixed, this test can be removed.
 */
public class CheckEnvironmentTest {

    @Test
    public void checkBuildToolsLinux_25_0_0() throws IOException {
        AssumeUtil.assumeIsLinux();

        LocalPackage buildToolsPackage =
                AndroidSdkHandler.getInstance(TestUtils.getSdk())
                        .getPackageInRange(
                                SdkConstants.FD_BUILD_TOOLS,
                                Range.singleton(new Revision(25, 0, 0)),
                                new FakeProgressIndicator());

        assertThat(buildToolsPackage).isNotNull();
        @SuppressWarnings("ConstantConditions") // buildToolsPackage is never null
        BuildToolInfo buildToolInfo = BuildToolInfo.fromLocalPackage(buildToolsPackage);

        Function<Path, String> relativePathToBuildTools =
                p -> FileUtils.relativePath(p.toFile(), buildToolInfo.getLocation());
        Function<Path, Integer> fileHash =
                p -> {
                    try {
                        return com.google.common.io.Files.hash(p.toFile(), Hashing.crc32()).asInt();
                    } catch (IOException e) {
                        fail();
                        return 0;
                    }
                };

        Map<String, Integer> hashes =
                Files.walk(buildToolInfo.getLocation().toPath())
                        .filter(Files::isRegularFile)
                        .collect(Collectors.toMap(relativePathToBuildTools, fileHash));

        // comparing it to the hash generated on the healthy build tools 25.0.0 for linux
        assertThat(hashes).containsExactlyEntriesIn(buildToolsLinux_25_0_0());
    }

    /** Hashes of all files from linux build tools 25.0.0. */
    private static ImmutableMap<String, Integer> buildToolsLinux_25_0_0() {
        return ImmutableMap.<String, Integer>builder()
                .put("aarch64-linux-android-ld", 1110236709)
                .put("mainDexClasses.rules", -980774449)
                .put("runtime.properties", 1955585832)
                .put("aapt2", -204662103)
                .put("dx", 659613436)
                .put("split-select", -1419885851)
                .put("jack-jacoco-reporter.jar", -1992794396)
                .put("dexdump", 1157350890)
                .put("i686-linux-android-ld", 1503045857)
                .put("arm-linux-androideabi-ld", 247147718)
                .put("jack-coverage-plugin.jar", -2004938286)
                .put("jack.jar", -209180421)
                .put("NOTICE.txt", -343519435)
                .put("jill.jar", 2133783114)
                .put("source.properties", 1844002746)
                .put("aidl", -1651142688)
                .put("x86_64-linux-android-ld", -194631868)
                .put("lib64/libclang.so", 1180792778)
                .put("lib64/libc++.so", -1170551803)
                .put("lib64/libbcinfo.so", 2111973570)
                .put("lib64/libbcc.so", -1445720428)
                .put("lib64/libLLVM.so", -1812960437)
                .put("llvm-rs-cc", 1272052572)
                .put("aapt", -265734411)
                .put("renderscript/include/rs_convert.rsh", -1349216828)
                .put("renderscript/include/rs_io.rsh", 367514022)
                .put("renderscript/include/rs_quaternion.rsh", 1493673525)
                .put("renderscript/include/rs_value_types.rsh", 565841142)
                .put("renderscript/include/rs_object_types.rsh", 1236564858)
                .put("renderscript/include/rs_time.rsh", -1712053533)
                .put("renderscript/include/rs_matrix.rsh", -2022977915)
                .put("renderscript/include/rs_graphics.rsh", -1168572106)
                .put("renderscript/include/rs_core.rsh", -1668848522)
                .put("renderscript/include/rs_math.rsh", 812382778)
                .put("renderscript/include/rs_atomic.rsh", -1262677559)
                .put("renderscript/include/rs_for_each.rsh", -450571093)
                .put("renderscript/include/rs_object_info.rsh", -119419857)
                .put("renderscript/include/rs_debug.rsh", 1294452459)
                .put("renderscript/include/rs_allocation_data.rsh", -780340700)
                .put("renderscript/include/rs_vector_math.rsh", 1008646428)
                .put("renderscript/include/rs_allocation_create.rsh", 272537815)
                .put("renderscript/lib/renderscript-v8.jar", 1249128742)
                .put("renderscript/lib/intermediates/mips/libc.so", -730151646)
                .put("renderscript/lib/intermediates/mips/libm.so", -1013759021)
                .put("renderscript/lib/intermediates/mips/libcompiler_rt.a", 503191788)
                .put("renderscript/lib/intermediates/armeabi-v7a/libc.so", 1770981318)
                .put("renderscript/lib/intermediates/armeabi-v7a/libm.so", 2031443613)
                .put("renderscript/lib/intermediates/armeabi-v7a/libcompiler_rt.a", -565125664)
                .put("renderscript/lib/intermediates/x86/libc.so", -1194404583)
                .put("renderscript/lib/intermediates/x86/libm.so", 379087364)
                .put("renderscript/lib/intermediates/x86/libcompiler_rt.a", 1872826311)
                .put("renderscript/lib/intermediates/x86_64/libc.so", 281177127)
                .put("renderscript/lib/intermediates/x86_64/libm.so", -1381853120)
                .put("renderscript/lib/intermediates/x86_64/libcompiler_rt.a", -1460343661)
                .put("renderscript/lib/intermediates/arm64-v8a/libc.so", -334776094)
                .put("renderscript/lib/intermediates/arm64-v8a/libm.so", 368329686)
                .put("renderscript/lib/intermediates/arm64-v8a/libcompiler_rt.a", 1232280322)
                .put("renderscript/lib/blas/mips/libblasV8.so", -2056350731)
                .put("renderscript/lib/blas/armeabi-v7a/libblasV8.so", 574221013)
                .put("renderscript/lib/blas/x86/libblasV8.so", -665368324)
                .put("renderscript/lib/blas/x86_64/libblasV8.so", -755679047)
                .put("renderscript/lib/blas/arm64-v8a/libblasV8.so", -1784770213)
                .put("renderscript/lib/packaged/mips/libRSSupport.so", -2145276315)
                .put("renderscript/lib/packaged/mips/librsjni.so", 477657700)
                .put("renderscript/lib/packaged/armeabi-v7a/libRSSupport.so", -222789855)
                .put("renderscript/lib/packaged/armeabi-v7a/librsjni.so", -87298611)
                .put("renderscript/lib/packaged/x86/libRSSupport.so", -2021630221)
                .put("renderscript/lib/packaged/x86/librsjni.so", -2119276394)
                .put("renderscript/lib/packaged/x86_64/libRSSupport.so", 1837079058)
                .put("renderscript/lib/packaged/x86_64/librsjni.so", -1008573239)
                .put("renderscript/lib/packaged/arm64-v8a/libRSSupport.so", 44533205)
                .put("renderscript/lib/packaged/arm64-v8a/librsjni.so", 1697087803)
                .put("renderscript/lib/bc/mips/libclcore.bc", -592385022)
                .put("renderscript/lib/bc/armeabi-v7a/libclcore.bc", -592385022)
                .put("renderscript/lib/bc/x86/libclcore.bc", -1712945376)
                .put("renderscript/lib/bc/x86_64/libclcore.bc", -1932058861)
                .put("renderscript/lib/bc/arm64-v8a/libclcore.bc", 671650467)
                .put("renderscript/clang-include/__stddef_max_align_t.h", 1265281023)
                .put("renderscript/clang-include/fma4intrin.h", -1552005280)
                .put("renderscript/clang-include/inttypes.h", -1522216727)
                .put("renderscript/clang-include/__wmmintrin_pclmul.h", 40423960)
                .put("renderscript/clang-include/htmxlintrin.h", 1139537899)
                .put("renderscript/clang-include/adxintrin.h", -1597745913)
                .put("renderscript/clang-include/arm_acle.h", 339598089)
                .put("renderscript/clang-include/wmmintrin.h", 583890466)
                .put("renderscript/clang-include/bmiintrin.h", 2101025947)
                .put("renderscript/clang-include/pmmintrin.h", 1929697801)
                .put("renderscript/clang-include/__wmmintrin_aes.h", -166959970)
                .put("renderscript/clang-include/module.modulemap", -166418052)
                .put("renderscript/clang-include/popcntintrin.h", -2081187866)
                .put("renderscript/clang-include/__clang_cuda_runtime_wrapper.h", -1281245637)
                .put("renderscript/clang-include/mmintrin.h", 1075614792)
                .put("renderscript/clang-include/f16cintrin.h", 869933783)
                .put("renderscript/clang-include/CMakeLists.txt", 736273440)
                .put("renderscript/clang-include/stddef.h", -335954064)
                .put("renderscript/clang-include/immintrin.h", -1503919869)
                .put("renderscript/clang-include/avxintrin.h", -165990573)
                .put("renderscript/clang-include/fxsrintrin.h", 117237412)
                .put("renderscript/clang-include/vadefs.h", -1256793683)
                .put("renderscript/clang-include/cpuid.h", -1337465530)
                .put("renderscript/clang-include/avx512bwintrin.h", 179366364)
                .put("renderscript/clang-include/xmmintrin.h", -1367091407)
                .put("renderscript/clang-include/xsavecintrin.h", -1253032683)
                .put("renderscript/clang-include/rtmintrin.h", -951975048)
                .put("renderscript/clang-include/smmintrin.h", 789914200)
                .put("renderscript/clang-include/stdarg.h", -1450545176)
                .put("renderscript/clang-include/avx512vlbwintrin.h", 313230187)
                .put("renderscript/clang-include/avx512erintrin.h", -160061982)
                .put("renderscript/clang-include/stdint.h", -374553882)
                .put("renderscript/clang-include/bmi2intrin.h", -387437113)
                .put("renderscript/clang-include/rdseedintrin.h", 1257327192)
                .put("renderscript/clang-include/ia32intrin.h", 126982348)
                .put("renderscript/clang-include/avx512dqintrin.h", 1894766864)
                .put("renderscript/clang-include/tbmintrin.h", 878706746)
                .put("renderscript/clang-include/LICENSE.TXT", -920680664)
                .put("renderscript/clang-include/Intrin.h", -1257830366)
                .put("renderscript/clang-include/xtestintrin.h", -1932137483)
                .put("renderscript/clang-include/mm_malloc.h", 743428114)
                .put("renderscript/clang-include/xsavesintrin.h", -840326366)
                .put("renderscript/clang-include/s390intrin.h", 608713307)
                .put("renderscript/clang-include/prfchwintrin.h", -1664783720)
                .put("renderscript/clang-include/mm3dnow.h", -1928098969)
                .put("renderscript/clang-include/cuda_builtin_vars.h", 1134306793)
                .put("renderscript/clang-include/nmmintrin.h", 1199901456)
                .put("renderscript/clang-include/unwind.h", 1410979512)
                .put("renderscript/clang-include/stdnoreturn.h", 396599790)
                .put("renderscript/clang-include/shaintrin.h", 1612211358)
                .put("renderscript/clang-include/altivec.h", -1149228772)
                .put("renderscript/clang-include/ammintrin.h", -1120011667)
                .put("renderscript/clang-include/varargs.h", -486805744)
                .put("renderscript/clang-include/avx512cdintrin.h", -480087718)
                .put("renderscript/clang-include/emmintrin.h", -132575597)
                .put("renderscript/clang-include/stdalign.h", -1124390620)
                .put("renderscript/clang-include/xsaveoptintrin.h", 167371930)
                .put("renderscript/clang-include/iso646.h", -801417451)
                .put("renderscript/clang-include/avx512vlintrin.h", 1523043177)
                .put("renderscript/clang-include/tmmintrin.h", 1197037134)
                .put("renderscript/clang-include/float.h", -1447594509)
                .put("renderscript/clang-include/x86intrin.h", 1377978914)
                .put("renderscript/clang-include/stdatomic.h", 674609464)
                .put("renderscript/clang-include/avx512fintrin.h", -1997552419)
                .put("renderscript/clang-include/vecintrin.h", -1760573798)
                .put("renderscript/clang-include/limits.h", 996053208)
                .put("renderscript/clang-include/lzcntintrin.h", -2120884139)
                .put("renderscript/clang-include/avx2intrin.h", -344655310)
                .put("renderscript/clang-include/xsaveintrin.h", -1617271293)
                .put("renderscript/clang-include/avx512vldqintrin.h", -1541351973)
                .put("renderscript/clang-include/xopintrin.h", 627018406)
                .put("renderscript/clang-include/fmaintrin.h", 624162362)
                .put("renderscript/clang-include/stdbool.h", -1316271047)
                .put("renderscript/clang-include/htmintrin.h", 1629900032)
                .put("renderscript/clang-include/tgmath.h", -1682318995)
                .put("lib/apksigner.jar", -1370318395)
                .put("lib/dx.jar", -1428529272)
                .put("lib/shrinkedAndroid.jar", 955060792)
                .put("bcc_compat", 1234502710)
                .put("mainDexClasses", 1946903551)
                .put("package.xml", 246326811)
                .put("mipsel-linux-android-ld", 1800388034)
                .put("zipalign", 780410456)
                .put("apksigner", -2110055040)
                .build();
    }
}
