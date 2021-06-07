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

package com.android.build.gradle.integration.sanity;

import com.android.Version;
import com.android.testutils.TestUtils;
import com.android.tools.bazel.repolinker.RepoLinker;
import com.android.utils.FileUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.truth.Expect;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Rule;
import org.junit.Test;

/** Checks what we distribute in our jars. */
public class JarContentsTest {

    private static final Set<String> LICENSE_NAMES =
            ImmutableSet.of("NOTICE", "NOTICE.txt", "LICENSE");

    private static final String EXTERNAL_DEPS = "/com/android/tools/external/";

    private static final String GMAVEN_MANIFEST = "tools/base/gmaven.manifest";

    private static final SetMultimap<String, String> EXPECTED;

    private static final String R8_NAMESPACE = "com/android/tools/r8/";
    private static final String R8_PACKAGE_PREFIX = "com.android.tools.r8";

    static {
        // Useful command for getting these lists:
        // unzip -l path/to.jar | grep -v ".class$" | tail -n +4 | head -n -2 | cut -c 31- | sort -f | awk '{print "\"" $0 "\"," }'

        ImmutableSetMultimap.Builder<String, String> expected = ImmutableSetMultimap.builder();
        expected.putAll(
                "com/android/tools/ddms/ddmlib",
                "com/",
                "com/android/",
                "com/android/commands/",
                "com/android/commands/am/",
                "com/android/ddmlib/",
                "com/android/ddmlib/jdwp/",
                "com/android/ddmlib/jdwp/packets/",
                "com/android/ddmlib/log/",
                "com/android/ddmlib/logcat/",
                "com/android/ddmlib/testrunner/",
                "com/android/ddmlib/utils/",
                "com/android/ddmlib/internal/",
                "com/android/ddmlib/internal/jdwp/",
                "com/android/ddmlib/internal/jdwp/chunkhandler/",
                "com/android/ddmlib/internal/jdwp/interceptor/",
                "com/android/incfs/",
                "com/android/incfs/install/",
                "com/android/incfs/install/adb/",
                "com/android/incfs/install/adb/ddmlib/",
                "com/android/server/",
                "com/android/server/adb/",
                "com/android/server/adb/protos/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/testutils",
                "com/",
                "com/android/",
                "com/android/testutils/",
                "com/android/testutils/apk/",
                "com/android/testutils/classloader/",
                "com/android/testutils/concurrency/",
                "com/android/testutils/diff/",
                "com/android/testutils/file/",
                "com/android/testutils/filesystemdiff/",
                "com/android/testutils/ignore/",
                "com/android/testutils/internal/",
                "com/android/testutils/truth/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/gradle-api",
                "com/",
                "com/android/",
                "com/android/build/",
                "com/android/build/api/",
                "com/android/build/api/artifact/",
                "com/android/build/api/attributes/",
                "com/android/build/api/component/",
                "com/android/build/api/dsl/",
                "com/android/build/api/extension/",
                "com/android/build/api/instrumentation/",
                "com/android/build/api/transform/",
                "com/android/build/api/variant/",
                "com/android/build/gradle/",
                "com/android/build/gradle/api/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/builder-test-api",
                "com/",
                "com/android/",
                "com/android/builder/",
                "com/android/builder/testing/",
                "com/android/builder/testing/api/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/builder-model",
                "com/",
                "com/android/",
                "com/android/build/",
                "com/android/builder/",
                "com/android/builder/model/",
                "com/android/builder/model/level2/",
                "com/android/builder/model/version.properties",
                "com/android/builder/model/v2/",
                "com/android/builder/model/v2/dsl/",
                "com/android/builder/model/v2/ide/",
                "com/android/builder/model/v2/models/",
                "com/android/builder/model/v2/models/ndk/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/apkzlib",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/build/",
                "com/android/tools/build/apkzlib/",
                "com/android/tools/build/apkzlib/bytestorage/",
                "com/android/tools/build/apkzlib/sign/",
                "com/android/tools/build/apkzlib/utils/",
                "com/android/tools/build/apkzlib/zfile/",
                "com/android/tools/build/apkzlib/zip/",
                "com/android/tools/build/apkzlib/zip/compress/",
                "com/android/tools/build/apkzlib/zip/utils/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/aapt2:windows",
                "aapt2.exe",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/aapt2:osx",
                "aapt2",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/aapt2:linux",
                "aapt2",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/aapt2-proto",
                "android/",
                "android/aapt/",
                "android/aapt/pb/",
                "android/aapt/pb/internal/", // ResourcesInternal.proto
                "com/",
                "com/android/",
                "com/android/aapt/", // Resources.proto & Configuration.proto
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/aaptcompiler",
                "com/",
                "com/android/",
                "com/android/aaptcompiler/",
                "com/android/aaptcompiler/android/",
                "com/android/aaptcompiler/buffer/",
                "com/android/aaptcompiler/proto/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/builder",
                "com/",
                "com/android/",
                "com/android/builder/",
                "com/android/builder/aar/",
                "com/android/builder/compiling/",
                "com/android/builder/core/",
                "com/android/builder/dependency/",
                "com/android/builder/dependency/level2/",
                "com/android/builder/dexing/",
                "com/android/builder/dexing/r8/",
                "com/android/builder/errors/",
                "com/android/builder/files/",
                "com/android/builder/internal/",
                "com/android/builder/internal/aapt/",
                "com/android/builder/internal/aapt/v2/",
                "com/android/builder/internal/AndroidManifest.template",
                "com/android/builder/internal/AndroidManifest.UnitTestTemplate",
                "com/android/builder/internal/compiler/",
                "com/android/builder/internal/incremental/",
                "com/android/builder/internal/packaging/",
                "com/android/builder/merge/",
                "com/android/builder/multidex/",
                "com/android/builder/packaging/",
                "com/android/builder/png/",
                "com/android/builder/profile/",
                "com/android/builder/sdk/",
                "com/android/builder/signing/",
                "com/android/builder/symbols/",
                "com/android/builder/tasks/",
                "com/android/builder/testing/",
                "com/android/builder/utils/",
                "com/android/tools/",
                R8_NAMESPACE,
                "LICENSE",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/services/com.android.tools.r8",
                "NOTICE",
                "r8-version.properties");
        expected.putAll(
                "com/android/tools/build/manifest-merger",
                "com/",
                "com/android/",
                "com/android/manifmerger/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/apksig",
                "com/",
                "com/android/",
                "com/android/apksig/",
                "com/android/apksig/apk/",
                "com/android/apksig/internal/",
                "com/android/apksig/internal/apk/",
                "com/android/apksig/internal/apk/stamp/",
                "com/android/apksig/internal/apk/v1/",
                "com/android/apksig/internal/apk/v2/",
                "com/android/apksig/internal/apk/v3/",
                "com/android/apksig/internal/apk/v4/",
                "com/android/apksig/internal/asn1/",
                "com/android/apksig/internal/asn1/ber/",
                "com/android/apksig/internal/jar/",
                "com/android/apksig/internal/oid/",
                "com/android/apksig/internal/pkcs7/",
                "com/android/apksig/internal/util/",
                "com/android/apksig/internal/x509/",
                "com/android/apksig/internal/zip/",
                "com/android/apksig/util/",
                "com/android/apksig/zip/",
                "LICENSE",
                "META-INF/",
                "META-INF/MANIFEST.MF");
        expected.putAll(
                "com/android/tools/build/gradle",
                "com/",
                "com/android/",
                "com/android/build/",
                "com/android/build/api/",
                "com/android/build/api/artifact/",
                "com/android/build/api/artifact/impl/",
                "com/android/build/api/component/",
                "com/android/build/api/component/analytics/",
                "com/android/build/api/component/impl/",
                "com/android/build/api/extension/",
                "com/android/build/api/extension/impl/",
                "com/android/build/api/variant/",
                "com/android/build/api/variant/impl/",
                "com/android/build/gradle/",
                "com/android/build/gradle/api/",
                "com/android/build/gradle/external/",
                "com/android/build/gradle/external/cmake/",
                "com/android/build/gradle/external/cmake/server/",
                "com/android/build/gradle/external/cmake/server/receiver/",
                "com/android/build/gradle/external/gnumake/",
                "com/android/build/gradle/internal/",
                "com/android/build/gradle/internal/aapt/",
                "com/android/build/gradle/internal/annotations/",
                "com/android/build/gradle/internal/api/",
                "com/android/build/gradle/internal/api/artifact/",
                "com/android/build/gradle/internal/attributes/",
                "com/android/build/gradle/internal/attribution/",
                "com/android/build/gradle/internal/component/",
                "com/android/build/gradle/internal/core/",
                "com/android/build/gradle/internal/coverage/",
                "com/android/build/gradle/internal/crash/",
                "com/android/build/gradle/internal/cxx/",
                "com/android/build/gradle/internal/cxx/attribution/",
                "com/android/build/gradle/internal/cxx/build/",
                "com/android/build/gradle/internal/cxx/cmake/",
                "com/android/build/gradle/internal/cxx/caching/",
                "com/android/build/gradle/internal/cxx/configure/",
                "com/android/build/gradle/internal/cxx/hashing/",
                "com/android/build/gradle/internal/cxx/gradle/",
                "com/android/build/gradle/internal/cxx/gradle/generator/",
                "com/android/build/gradle/internal/cxx/json/",
                "com/android/build/gradle/internal/cxx/logging/",
                "com/android/build/gradle/internal/cxx/model/",
                "com/android/build/gradle/internal/cxx/ninja/",
                "com/android/build/gradle/internal/cxx/process/",
                "com/android/build/gradle/internal/cxx/settings/",
                "com/android/build/gradle/internal/cxx/string/",
                "com/android/build/gradle/internal/cxx/stripping/",
                "com/android/build/gradle/internal/cxx/timing/",
                "com/android/build/gradle/internal/databinding/",
                "com/android/build/gradle/internal/dependency/",
                "com/android/build/gradle/internal/dexing/",
                "com/android/build/gradle/internal/dsl/",
                "com/android/build/gradle/internal/dsl/decorator/",
                "com/android/build/gradle/internal/dsl/decorator/annotation/",
                "com/android/build/gradle/internal/instrumentation/",
                "com/android/build/gradle/internal/errors/",
                "com/android/build/gradle/internal/feature/",
                "com/android/build/gradle/internal/generators/",
                "com/android/build/gradle/internal/ide/",
                "com/android/build/gradle/internal/ide/dependencies/",
                "com/android/build/gradle/internal/ide/level2/",
                "com/android/build/gradle/internal/ide/v2/",
                "com/android/build/gradle/internal/incremental/",
                "com/android/build/gradle/internal/lint/",
                "com/android/build/gradle/internal/manifest/",
                "com/android/build/gradle/internal/matcher/",
                "com/android/build/gradle/internal/model/",
                "com/android/build/gradle/internal/ndk/",
                "com/android/build/gradle/internal/packaging/",
                "com/android/build/gradle/internal/pipeline/",
                "com/android/build/gradle/internal/plugins/",
                "com/android/build/gradle/internal/process/",
                "com/android/build/gradle/internal/profile/",
                "com/android/build/gradle/internal/publishing/",
                "com/android/build/gradle/internal/res/",
                "com/android/build/gradle/internal/res/aapt2_version.properties",
                "com/android/build/gradle/internal/res/namespaced/",
                "com/android/build/gradle/internal/res/shrinker/",
                "com/android/build/gradle/internal/res/shrinker/gatherer/",
                "com/android/build/gradle/internal/res/shrinker/graph/",
                "com/android/build/gradle/internal/res/shrinker/obfuscation/",
                "com/android/build/gradle/internal/res/shrinker/usages/",
                "com/android/build/gradle/internal/services/",
                "com/android/build/gradle/internal/scope/",
                "com/android/build/gradle/internal/signing/",
                "com/android/build/gradle/internal/tasks/",
                "com/android/build/gradle/internal/tasks/databinding/",
                "com/android/build/gradle/internal/tasks/factory/",
                "com/android/build/gradle/internal/tasks/featuresplit/",
                "com/android/build/gradle/internal/tasks/manifest/",
                "com/android/build/gradle/internal/tasks/mlkit/",
                "com/android/build/gradle/internal/tasks/mlkit/codegen/",
                "com/android/build/gradle/internal/tasks/mlkit/codegen/codeinjector/",
                "com/android/build/gradle/internal/tasks/mlkit/codegen/codeinjector/codeblock/",
                "com/android/build/gradle/internal/tasks/mlkit/codegen/codeinjector/codeblock/processor/",
                "com/android/build/gradle/internal/tasks/mlkit/codegen/codeinjector/fields/",
                "com/android/build/gradle/internal/tasks/mlkit/codegen/codeinjector/innerclass/",
                "com/android/build/gradle/internal/tasks/mlkit/codegen/codeinjector/methods/",
                "com/android/build/gradle/internal/tasks/integrity_config_schema.xsd",
                "com/android/build/gradle/internal/tasks/",
                "com/android/build/gradle/internal/test/",
                "com/android/build/gradle/internal/test/report/",
                "com/android/build/gradle/internal/test/report/base-style.css",
                "com/android/build/gradle/internal/test/report/report.js",
                "com/android/build/gradle/internal/test/report/style.css",
                "com/android/build/gradle/internal/testFixtures/",
                "com/android/build/gradle/internal/testing/",
                "com/android/build/gradle/internal/testing/utp/",
                "com/android/build/gradle/internal/testing/utp/worker/",
                "com/android/build/gradle/internal/transforms/",
                "com/android/build/gradle/internal/utils/",
                "com/android/build/gradle/internal/variant/",
                "com/android/build/gradle/internal/workeractions/",
                "com/android/build/gradle/options/",
                "com/android/build/gradle/proguard-common.txt",
                "com/android/build/gradle/proguard-header.txt",
                "com/android/build/gradle/proguard-optimizations.txt",
                "com/android/build/gradle/tasks/",
                "com/android/build/gradle/tasks/factory/",
                "com/android/builder/",
                "com/android/builder/core/",
                "com/android/tools/",
                "com/android/tools/build/",
                "com/android/tools/build/libraries/",
                "com/android/tools/build/libraries/metadata/",
                "com/android/tools/mlkit/",
                "com/android/tools/profgen/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/gradle-plugins/",
                "META-INF/gradle-plugins/com.android.internal.application.properties",
                "META-INF/gradle-plugins/com.android.internal.asset-pack.properties",
                "META-INF/gradle-plugins/com.android.internal.asset-pack-bundle.properties",
                "META-INF/gradle-plugins/com.android.internal.dynamic-feature.properties",
                "META-INF/gradle-plugins/com.android.internal.library.properties",
                "META-INF/gradle-plugins/com.android.internal.reporting.properties",
                "META-INF/gradle-plugins/com.android.internal.test.properties",
                "META-INF/gradle-plugins/com.android.lint.properties",
                "META-INF/gradle-plugins/com.android.internal.version-check.properties",
                // Following to be moved to gradle-api
                "META-INF/gradle-plugins/android.properties",
                "META-INF/gradle-plugins/android-library.properties",
                "META-INF/gradle-plugins/android-reporting.properties",
                "META-INF/gradle-plugins/com.android.application.properties",
                "META-INF/gradle-plugins/com.android.asset-pack.properties",
                "META-INF/gradle-plugins/com.android.asset-pack-bundle.properties",
                "META-INF/gradle-plugins/com.android.base.properties",
                "META-INF/gradle-plugins/com.android.dynamic-feature.properties",
                "META-INF/gradle-plugins/com.android.library.properties",
                "META-INF/gradle-plugins/com.android.reporting.properties",
                "META-INF/gradle-plugins/com.android.test.properties",
                "META-INF/services/",
                "META-INF/services/com.android.build.api.variant.BuiltArtifactsLoader",
                "NOTICE");
        expected.putAll(
                "com/android/tools/sdk-common",
                "com/",
                "com/android/",
                "com/android/ide/",
                "com/android/ide/common/",
                "com/android/ide/common/attribution/",
                "com/android/ide/common/blame/",
                "com/android/ide/common/blame/parser/",
                "com/android/ide/common/blame/parser/aapt/",
                "com/android/ide/common/blame/parser/util/",
                "com/android/ide/common/build/",
                "com/android/ide/common/caching/",
                "com/android/ide/common/fonts/",
                "com/android/ide/common/internal/",
                "com/android/ide/common/process/",
                "com/android/ide/common/rendering/",
                "com/android/ide/common/repository/",
                "com/android/ide/common/resources/",
                "com/android/ide/common/resources/configuration/",
                "com/android/ide/common/resources/sampledata/",
                "com/android/ide/common/resources/usage/",
                "com/android/ide/common/sdk/",
                "com/android/ide/common/signing/",
                "com/android/ide/common/symbols/",
                "com/android/ide/common/util/",
                "com/android/ide/common/vectordrawable/",
                "com/android/ide/common/workers/",
                "com/android/ide/common/xml/",
                "com/android/instantapp/",
                "com/android/instantapp/provision/",
                "com/android/instantapp/run/",
                "com/android/instantapp/sdk/",
                "com/android/instantapp/utils/",
                "com/android/projectmodel/",
                "versions-offline/",
                "versions-offline/android/",
                "versions-offline/android/arch/",
                "versions-offline/android/arch/core/",
                "versions-offline/android/arch/core/group-index.xml",
                "versions-offline/android/arch/lifecycle/",
                "versions-offline/android/arch/lifecycle/group-index.xml",
                "versions-offline/android/arch/navigation/",
                "versions-offline/android/arch/navigation/group-index.xml",
                "versions-offline/android/arch/paging/",
                "versions-offline/android/arch/paging/group-index.xml",
                "versions-offline/android/arch/persistence/",
                "versions-offline/android/arch/persistence/group-index.xml",
                "versions-offline/android/arch/persistence/room/",
                "versions-offline/android/arch/persistence/room/group-index.xml",
                "versions-offline/android/arch/work/",
                "versions-offline/android/arch/work/group-index.xml",
                "versions-offline/androidx/",
                "versions-offline/androidx/activity/",
                "versions-offline/androidx/activity/group-index.xml",
                "versions-offline/androidx/ads/",
                "versions-offline/androidx/ads/group-index.xml",
                "versions-offline/androidx/annotation/",
                "versions-offline/androidx/annotation/group-index.xml",
                "versions-offline/androidx/appcompat/",
                "versions-offline/androidx/appcompat/group-index.xml",
                "versions-offline/androidx/arch/",
                "versions-offline/androidx/arch/core/",
                "versions-offline/androidx/arch/core/group-index.xml",
                "versions-offline/androidx/asynclayoutinflater/",
                "versions-offline/androidx/asynclayoutinflater/group-index.xml",
                "versions-offline/androidx/autofill/",
                "versions-offline/androidx/autofill/group-index.xml",
                "versions-offline/androidx/benchmark/",
                "versions-offline/androidx/benchmark/group-index.xml",
                "versions-offline/androidx/biometric/",
                "versions-offline/androidx/biometric/group-index.xml",
                "versions-offline/androidx/browser/",
                "versions-offline/androidx/browser/group-index.xml",
                "versions-offline/androidx/camera/",
                "versions-offline/androidx/camera/group-index.xml",
                "versions-offline/androidx/car/",
                "versions-offline/androidx/car/app/",
                "versions-offline/androidx/car/app/group-index.xml",
                "versions-offline/androidx/cardview/",
                "versions-offline/androidx/cardview/group-index.xml",
                "versions-offline/androidx/car/group-index.xml",
                "versions-offline/androidx/collection/",
                "versions-offline/androidx/collection/group-index.xml",
                "versions-offline/androidx/compose/",
                "versions-offline/androidx/compose/animation/",
                "versions-offline/androidx/compose/animation/group-index.xml",
                "versions-offline/androidx/compose/compiler/",
                "versions-offline/androidx/compose/compiler/group-index.xml",
                "versions-offline/androidx/compose/foundation/",
                "versions-offline/androidx/compose/foundation/group-index.xml",
                "versions-offline/androidx/compose/group-index.xml",
                "versions-offline/androidx/compose/material/",
                "versions-offline/androidx/compose/material/group-index.xml",
                "versions-offline/androidx/compose/runtime/",
                "versions-offline/androidx/compose/runtime/group-index.xml",
                "versions-offline/androidx/compose/ui/",
                "versions-offline/androidx/compose/ui/group-index.xml",
                "versions-offline/androidx/concurrent/",
                "versions-offline/androidx/concurrent/group-index.xml",
                "versions-offline/androidx/constraintlayout/",
                "versions-offline/androidx/constraintlayout/group-index.xml",
                "versions-offline/androidx/contentpager/",
                "versions-offline/androidx/contentpager/group-index.xml",
                "versions-offline/androidx/coordinatorlayout/",
                "versions-offline/androidx/coordinatorlayout/group-index.xml",
                "versions-offline/androidx/core/",
                "versions-offline/androidx/core/group-index.xml",
                "versions-offline/androidx/cursoradapter/",
                "versions-offline/androidx/cursoradapter/group-index.xml",
                "versions-offline/androidx/customview/",
                "versions-offline/androidx/customview/group-index.xml",
                "versions-offline/androidx/databinding/",
                "versions-offline/androidx/databinding/group-index.xml",
                "versions-offline/androidx/datastore/",
                "versions-offline/androidx/datastore/group-index.xml",
                "versions-offline/androidx/documentfile/",
                "versions-offline/androidx/documentfile/group-index.xml",
                "versions-offline/androidx/drawerlayout/",
                "versions-offline/androidx/drawerlayout/group-index.xml",
                "versions-offline/androidx/dynamicanimation/",
                "versions-offline/androidx/dynamicanimation/group-index.xml",
                "versions-offline/androidx/emoji/",
                "versions-offline/androidx/emoji/group-index.xml",
                "versions-offline/androidx/enterprise/",
                "versions-offline/androidx/enterprise/group-index.xml",
                "versions-offline/androidx/exifinterface/",
                "versions-offline/androidx/exifinterface/group-index.xml",
                "versions-offline/androidx/fragment/",
                "versions-offline/androidx/fragment/group-index.xml",
                "versions-offline/androidx/games/",
                "versions-offline/androidx/games/group-index.xml",
                "versions-offline/androidx/gaming/",
                "versions-offline/androidx/gaming/group-index.xml",
                "versions-offline/androidx/gridlayout/",
                "versions-offline/androidx/gridlayout/group-index.xml",
                "versions-offline/androidx/heifwriter/",
                "versions-offline/androidx/heifwriter/group-index.xml",
                "versions-offline/androidx/hilt/",
                "versions-offline/androidx/hilt/group-index.xml",
                "versions-offline/androidx/interpolator/",
                "versions-offline/androidx/interpolator/group-index.xml",
                "versions-offline/androidx/leanback/",
                "versions-offline/androidx/leanback/group-index.xml",
                "versions-offline/androidx/legacy/",
                "versions-offline/androidx/legacy/group-index.xml",
                "versions-offline/androidx/lifecycle/",
                "versions-offline/androidx/lifecycle/group-index.xml",
                "versions-offline/androidx/loader/",
                "versions-offline/androidx/loader/group-index.xml",
                "versions-offline/androidx/localbroadcastmanager/",
                "versions-offline/androidx/localbroadcastmanager/group-index.xml",
                "versions-offline/androidx/media/",
                "versions-offline/androidx/media2/",
                "versions-offline/androidx/media2/group-index.xml",
                "versions-offline/androidx/media/group-index.xml",
                "versions-offline/androidx/mediarouter/",
                "versions-offline/androidx/mediarouter/group-index.xml",
                "versions-offline/androidx/multidex/",
                "versions-offline/androidx/multidex/group-index.xml",
                "versions-offline/androidx/navigation/",
                "versions-offline/androidx/navigation/group-index.xml",
                "versions-offline/androidx/paging/",
                "versions-offline/androidx/paging/group-index.xml",
                "versions-offline/androidx/palette/",
                "versions-offline/androidx/palette/group-index.xml",
                "versions-offline/androidx/percentlayout/",
                "versions-offline/androidx/percentlayout/group-index.xml",
                "versions-offline/androidx/preference/",
                "versions-offline/androidx/preference/group-index.xml",
                "versions-offline/androidx/print/",
                "versions-offline/androidx/print/group-index.xml",
                "versions-offline/androidx/recommendation/",
                "versions-offline/androidx/recommendation/group-index.xml",
                "versions-offline/androidx/recyclerview/",
                "versions-offline/androidx/recyclerview/group-index.xml",
                "versions-offline/androidx/remotecallback/",
                "versions-offline/androidx/remotecallback/group-index.xml",
                "versions-offline/androidx/resourceinspection/",
                "versions-offline/androidx/resourceinspection/group-index.xml",
                "versions-offline/androidx/room/",
                "versions-offline/androidx/room/group-index.xml",
                "versions-offline/androidx/savedstate/",
                "versions-offline/androidx/savedstate/group-index.xml",
                "versions-offline/androidx/security/",
                "versions-offline/androidx/security/group-index.xml",
                "versions-offline/androidx/sharetarget/",
                "versions-offline/androidx/sharetarget/group-index.xml",
                "versions-offline/androidx/slice/",
                "versions-offline/androidx/slice/group-index.xml",
                "versions-offline/androidx/slidingpanelayout/",
                "versions-offline/androidx/slidingpanelayout/group-index.xml",
                "versions-offline/androidx/sqlite/",
                "versions-offline/androidx/sqlite/group-index.xml",
                "versions-offline/androidx/startup/",
                "versions-offline/androidx/startup/group-index.xml",
                "versions-offline/androidx/swiperefreshlayout/",
                "versions-offline/androidx/swiperefreshlayout/group-index.xml",
                "versions-offline/androidx/test/",
                "versions-offline/androidx/test/espresso/",
                "versions-offline/androidx/test/espresso/group-index.xml",
                "versions-offline/androidx/test/espresso/idling/",
                "versions-offline/androidx/test/espresso/idling/group-index.xml",
                "versions-offline/androidx/test/ext/",
                "versions-offline/androidx/test/ext/group-index.xml",
                "versions-offline/androidx/test/group-index.xml",
                "versions-offline/androidx/test/janktesthelper/",
                "versions-offline/androidx/test/janktesthelper/group-index.xml",
                "versions-offline/androidx/test/services/",
                "versions-offline/androidx/test/services/group-index.xml",
                "versions-offline/androidx/test/uiautomator/",
                "versions-offline/androidx/test/uiautomator/group-index.xml",
                "versions-offline/androidx/textclassifier/",
                "versions-offline/androidx/textclassifier/group-index.xml",
                "versions-offline/androidx/tracing/",
                "versions-offline/androidx/tracing/group-index.xml",
                "versions-offline/androidx/transition/",
                "versions-offline/androidx/transition/group-index.xml",
                "versions-offline/androidx/tvprovider/",
                "versions-offline/androidx/tvprovider/group-index.xml",
                "versions-offline/androidx/ui/",
                "versions-offline/androidx/ui/group-index.xml",
                "versions-offline/androidx/vectordrawable/",
                "versions-offline/androidx/vectordrawable/group-index.xml",
                "versions-offline/androidx/versionedparcelable/",
                "versions-offline/androidx/versionedparcelable/group-index.xml",
                "versions-offline/androidx/viewpager/",
                "versions-offline/androidx/viewpager2/",
                "versions-offline/androidx/viewpager2/group-index.xml",
                "versions-offline/androidx/viewpager/group-index.xml",
                "versions-offline/androidx/wear/",
                "versions-offline/androidx/wear/group-index.xml",
                "versions-offline/androidx/wear/tiles/",
                "versions-offline/androidx/wear/tiles/group-index.xml",
                "versions-offline/androidx/webkit/",
                "versions-offline/androidx/webkit/group-index.xml",
                "versions-offline/androidx/window/",
                "versions-offline/androidx/window/group-index.xml",
                "versions-offline/androidx/work/",
                "versions-offline/androidx/work/group-index.xml",
                "versions-offline/com/",
                "versions-offline/com/android/",
                "versions-offline/com/android/application/",
                "versions-offline/com/android/application/group-index.xml",
                "versions-offline/com/android/asset-pack/",
                "versions-offline/com/android/asset-pack/group-index.xml",
                "versions-offline/com/android/asset-pack-bundle/",
                "versions-offline/com/android/asset-pack-bundle/group-index.xml",
                "versions-offline/com/android/billingclient/",
                "versions-offline/com/android/billingclient/group-index.xml",
                "versions-offline/com/android/databinding/",
                "versions-offline/com/android/databinding/group-index.xml",
                "versions-offline/com/android/dynamic-feature/",
                "versions-offline/com/android/dynamic-feature/group-index.xml",
                "versions-offline/com/android/group-index.xml",
                "versions-offline/com/android/installreferrer/",
                "versions-offline/com/android/installreferrer/group-index.xml",
                "versions-offline/com/android/java/",
                "versions-offline/com/android/java/tools/",
                "versions-offline/com/android/java/tools/build/",
                "versions-offline/com/android/java/tools/build/group-index.xml",
                "versions-offline/com/android/library/",
                "versions-offline/com/android/library/group-index.xml",
                "versions-offline/com/android/lint/",
                "versions-offline/com/android/lint/group-index.xml",
                "versions-offline/com/android/ndk/",
                "versions-offline/com/android/ndk/thirdparty/",
                "versions-offline/com/android/ndk/thirdparty/group-index.xml",
                "versions-offline/com/android/reporting/",
                "versions-offline/com/android/reporting/group-index.xml",
                "versions-offline/com/android/support/",
                "versions-offline/com/android/support/constraint/",
                "versions-offline/com/android/support/constraint/group-index.xml",
                "versions-offline/com/android/support/group-index.xml",
                "versions-offline/com/android/support/test/",
                "versions-offline/com/android/support/test/espresso/",
                "versions-offline/com/android/support/test/espresso/group-index.xml",
                "versions-offline/com/android/support/test/espresso/idling/",
                "versions-offline/com/android/support/test/espresso/idling/group-index.xml",
                "versions-offline/com/android/support/test/group-index.xml",
                "versions-offline/com/android/support/test/janktesthelper/",
                "versions-offline/com/android/support/test/janktesthelper/group-index.xml",
                "versions-offline/com/android/support/test/services/",
                "versions-offline/com/android/support/test/services/group-index.xml",
                "versions-offline/com/android/support/test/uiautomator/",
                "versions-offline/com/android/support/test/uiautomator/group-index.xml",
                "versions-offline/com/android/test/",
                "versions-offline/com/android/test/group-index.xml",
                "versions-offline/com/android/tools/",
                "versions-offline/com/android/tools/analytics-library/",
                "versions-offline/com/android/tools/analytics-library/group-index.xml",
                "versions-offline/com/android/tools/apkparser/",
                "versions-offline/com/android/tools/apkparser/group-index.xml",
                "versions-offline/com/android/tools/build/",
                "versions-offline/com/android/tools/build/group-index.xml",
                "versions-offline/com/android/tools/build/jetifier/",
                "versions-offline/com/android/tools/build/jetifier/group-index.xml",
                "versions-offline/com/android/tools/chunkio/",
                "versions-offline/com/android/tools/chunkio/group-index.xml",
                "versions-offline/com/android/tools/ddms/",
                "versions-offline/com/android/tools/ddms/group-index.xml",
                "versions-offline/com/android/tools/emulator/",
                "versions-offline/com/android/tools/emulator/group-index.xml",
                "versions-offline/com/android/tools/external/",
                "versions-offline/com/android/tools/external/com-intellij/",
                "versions-offline/com/android/tools/external/com-intellij/group-index.xml",
                "versions-offline/com/android/tools/external/org-jetbrains/",
                "versions-offline/com/android/tools/external/org-jetbrains/group-index.xml",
                "versions-offline/com/android/tools/fakeadbserver/",
                "versions-offline/com/android/tools/fakeadbserver/group-index.xml",
                "versions-offline/com/android/tools/group-index.xml",
                "versions-offline/com/android/tools/internal/",
                "versions-offline/com/android/tools/internal/build/",
                "versions-offline/com/android/tools/internal/build/test/",
                "versions-offline/com/android/tools/internal/build/test/group-index.xml",
                "versions-offline/com/android/tools/layoutlib/",
                "versions-offline/com/android/tools/layoutlib/group-index.xml",
                "versions-offline/com/android/tools/lint/",
                "versions-offline/com/android/tools/lint/group-index.xml",
                "versions-offline/com/android/tools/metalava/",
                "versions-offline/com/android/tools/metalava/group-index.xml",
                "versions-offline/com/android/tools/pixelprobe/",
                "versions-offline/com/android/tools/pixelprobe/group-index.xml",
                "versions-offline/com/android/tools/utp/",
                "versions-offline/com/android/tools/utp/group-index.xml",
                "versions-offline/com/android/volley/",
                "versions-offline/com/android/volley/group-index.xml",
                "versions-offline/com/crashlytics/",
                "versions-offline/com/crashlytics/sdk/",
                "versions-offline/com/crashlytics/sdk/android/",
                "versions-offline/com/crashlytics/sdk/android/group-index.xml",
                "versions-offline/com/google/",
                "versions-offline/com/google/ads/",
                "versions-offline/com/google/ads/afsn/",
                "versions-offline/com/google/ads/afsn/group-index.xml",
                "versions-offline/com/google/ads/interactivemedia/",
                "versions-offline/com/google/ads/interactivemedia/v3/",
                "versions-offline/com/google/ads/interactivemedia/v3/group-index.xml",
                "versions-offline/com/google/ads/mediation/",
                "versions-offline/com/google/ads/mediation/group-index.xml",
                "versions-offline/com/google/android/",
                "versions-offline/com/google/android/ads/",
                "versions-offline/com/google/android/ads/consent/",
                "versions-offline/com/google/android/ads/consent/group-index.xml",
                "versions-offline/com/google/android/ads/group-index.xml",
                "versions-offline/com/google/android/apps/",
                "versions-offline/com/google/android/apps/common/",
                "versions-offline/com/google/android/apps/common/testing/",
                "versions-offline/com/google/android/apps/common/testing/accessibility/",
                "versions-offline/com/google/android/apps/common/testing/accessibility/framework/",
                "versions-offline/com/google/android/apps/common/testing/accessibility/framework/group-index.xml",
                "versions-offline/com/google/android/datatransport/",
                "versions-offline/com/google/android/datatransport/group-index.xml",
                "versions-offline/com/google/android/enterprise/",
                "versions-offline/com/google/android/enterprise/connectedapps/",
                "versions-offline/com/google/android/enterprise/connectedapps/group-index.xml",
                "versions-offline/com/google/android/exoplayer/",
                "versions-offline/com/google/android/exoplayer/group-index.xml",
                "versions-offline/com/google/android/fhir/",
                "versions-offline/com/google/android/fhir/group-index.xml",
                "versions-offline/com/google/androidbrowserhelper/",
                "versions-offline/com/google/androidbrowserhelper/group-index.xml",
                "versions-offline/com/google/android/games/",
                "versions-offline/com/google/android/games/group-index.xml",
                "versions-offline/com/google/android/gms/",
                "versions-offline/com/google/android/gms/group-index.xml",
                "versions-offline/com/google/android/instantapps/",
                "versions-offline/com/google/android/instantapps/group-index.xml",
                "versions-offline/com/google/android/instantapps/thirdpartycompat/",
                "versions-offline/com/google/android/instantapps/thirdpartycompat/group-index.xml",
                "versions-offline/com/google/android/libraries/",
                "versions-offline/com/google/android/libraries/car/",
                "versions-offline/com/google/android/libraries/car/group-index.xml",
                "versions-offline/com/google/android/libraries/maps/",
                "versions-offline/com/google/android/libraries/maps/group-index.xml",
                "versions-offline/com/google/android/libraries/places/",
                "versions-offline/com/google/android/libraries/places/group-index.xml",
                "versions-offline/com/google/android/material/",
                "versions-offline/com/google/android/material/group-index.xml",
                "versions-offline/com/google/android/play/",
                "versions-offline/com/google/android/play/group-index.xml",
                "versions-offline/com/google/android/support/",
                "versions-offline/com/google/android/support/group-index.xml",
                "versions-offline/com/google/android/things/",
                "versions-offline/com/google/android/things/group-index.xml",
                "versions-offline/com/google/android/ump/",
                "versions-offline/com/google/android/ump/group-index.xml",
                "versions-offline/com/google/android/wearable/",
                "versions-offline/com/google/android/wearable/group-index.xml",
                "versions-offline/com/google/ar/",
                "versions-offline/com/google/ar/group-index.xml",
                "versions-offline/com/google/ar/sceneform/",
                "versions-offline/com/google/ar/sceneform/group-index.xml",
                "versions-offline/com/google/ar/sceneform/ux/",
                "versions-offline/com/google/ar/sceneform/ux/group-index.xml",
                "versions-offline/com/google/assistant/",
                "versions-offline/com/google/assistant/suggestion/",
                "versions-offline/com/google/assistant/suggestion/group-index.xml",
                "versions-offline/com/google/devtools/",
                "versions-offline/com/google/devtools/ksp/",
                "versions-offline/com/google/devtools/ksp/group-index.xml",
                "versions-offline/com/google/fhir/",
                "versions-offline/com/google/fhir/group-index.xml",
                "versions-offline/com/google/firebase/",
                "versions-offline/com/google/firebase/group-index.xml",
                "versions-offline/com/google/gms/",
                "versions-offline/com/google/gms/group-index.xml",
                "versions-offline/com/google/mlkit/",
                "versions-offline/com/google/mlkit/group-index.xml",
                "versions-offline/com/google/oboe/",
                "versions-offline/com/google/oboe/group-index.xml",
                "versions-offline/com/google/prefab/",
                "versions-offline/com/google/prefab/group-index.xml",
                "versions-offline/com/google/test/",
                "versions-offline/com/google/testing/",
                "versions-offline/com/google/testing/platform/",
                "versions-offline/com/google/testing/platform/group-index.xml",
                "versions-offline/com/google/test/platform/",
                "versions-offline/com/google/test/platform/group-index.xml",
                "versions-offline/io/",
                "versions-offline/io/fabric/",
                "versions-offline/io/fabric/sdk/",
                "versions-offline/io/fabric/sdk/android/",
                "versions-offline/io/fabric/sdk/android/group-index.xml",
                "versions-offline/master-index.xml",
                "versions-offline/org/",
                "versions-offline/org/chromium/",
                "versions-offline/org/chromium/net/",
                "versions-offline/org/chromium/net/group-index.xml",
                "versions-offline/org/jetbrains/",
                "versions-offline/org/jetbrains/kotlin/",
                "versions-offline/org/jetbrains/kotlin/group-index.xml",
                "versions-offline/tools/",
                "versions-offline/tools/base/",
                "versions-offline/tools/base/build-system/",
                "versions-offline/tools/base/build-system/debug/",
                "versions-offline/tools/base/build-system/debug/group-index.xml",
                "versions-offline/zipflinger/",
                "versions-offline/zipflinger/group-index.xml",
                "wireless/",
                "wireless/android/",
                "wireless/android/instantapps/",
                "wireless/android/instantapps/sdk/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE",
                "README.md");
        expected.putAll(
                "com/android/tools/analytics-library/crash",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/analytics/",
                "com/android/tools/analytics/crash/",
                "NOTICE",
                "META-INF/MANIFEST.MF",
                "META-INF/");
        expected.putAll(
                "com/android/tools/analytics-library/shared",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/analytics/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/analytics-library/inspector",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/analytics/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/analytics-library/tracker",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/analytics/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/analytics-library/protos",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/build/",
                "com/android/tools/build/gradle/",
                "com/android/tools/build/gradle/internal/",
                "com/android/tools/build/gradle/internal/profile/",
                "com/google/",
                "com/google/wireless/",
                "com/google/wireless/android/",
                "com/google/wireless/android/play/",
                "com/google/wireless/android/play/playlog/",
                "com/google/wireless/android/play/playlog/proto/",
                "com/google/wireless/android/sdk/",
                "com/google/wireless/android/sdk/stats/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/analytics-library/publisher",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/analytics/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/annotations",
                "com/",
                "com/android/",
                "com/android/annotations/",
                "com/android/annotations/concurrency/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/device/",
                "com/android/tools/device/internal/",
                "com/android/tools/device/internal/adb/",
                "com/android/tools/device/internal/adb/commands/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/sdklib",
                "com/",
                "com/android/",
                "com/android/sdklib/",
                "com/android/sdklib/devices/",
                "com/android/sdklib/devices/automotive.xml",
                "com/android/sdklib/devices/devices.xml",
                "com/android/sdklib/devices/nexus.xml",
                "com/android/sdklib/devices/tv.xml",
                "com/android/sdklib/devices/wear.xml",
                "com/android/sdklib/internal/",
                "com/android/sdklib/internal/avd/",
                "com/android/sdklib/internal/build/",
                "com/android/sdklib/internal/project/",
                "com/android/sdklib/repository/",
                "com/android/sdklib/repository/generated/",
                "com/android/sdklib/repository/generated/addon/",
                "com/android/sdklib/repository/generated/addon/v1/",
                "com/android/sdklib/repository/generated/addon/v2/",
                "com/android/sdklib/repository/generated/common/",
                "com/android/sdklib/repository/generated/common/v1/",
                "com/android/sdklib/repository/generated/common/v2/",
                "com/android/sdklib/repository/generated/repository/",
                "com/android/sdklib/repository/generated/repository/v1/",
                "com/android/sdklib/repository/generated/repository/v2/",
                "com/android/sdklib/repository/generated/sysimg/",
                "com/android/sdklib/repository/generated/sysimg/v1/",
                "com/android/sdklib/repository/generated/sysimg/v2/",
                "com/android/sdklib/repository/installer/",
                "com/android/sdklib/repository/legacy/",
                "com/android/sdklib/repository/legacy/descriptors/",
                "com/android/sdklib/repository/legacy/local/",
                "com/android/sdklib/repository/legacy/remote/",
                "com/android/sdklib/repository/legacy/remote/internal/",
                "com/android/sdklib/repository/legacy/remote/internal/archives/",
                "com/android/sdklib/repository/legacy/remote/internal/packages/",
                "com/android/sdklib/repository/legacy/remote/internal/sources/",
                "xsd/",
                "xsd/legacy/",
                "xsd/legacy/sdk-addon-01.xsd",
                "xsd/legacy/sdk-addon-02.xsd",
                "xsd/legacy/sdk-addon-03.xsd",
                "xsd/legacy/sdk-addon-04.xsd",
                "xsd/legacy/sdk-addon-05.xsd",
                "xsd/legacy/sdk-addon-06.xsd",
                "xsd/legacy/sdk-addon-07.xsd",
                "xsd/legacy/sdk-addons-list-1.xsd",
                "xsd/legacy/sdk-addons-list-2.xsd",
                "xsd/legacy/sdk-repository-01.xsd",
                "xsd/legacy/sdk-repository-02.xsd",
                "xsd/legacy/sdk-repository-03.xsd",
                "xsd/legacy/sdk-repository-04.xsd",
                "xsd/legacy/sdk-repository-05.xsd",
                "xsd/legacy/sdk-repository-06.xsd",
                "xsd/legacy/sdk-repository-07.xsd",
                "xsd/legacy/sdk-repository-08.xsd",
                "xsd/legacy/sdk-repository-09.xsd",
                "xsd/legacy/sdk-repository-10.xsd",
                "xsd/legacy/sdk-repository-11.xsd",
                "xsd/legacy/sdk-repository-12.xsd",
                "xsd/legacy/sdk-stats-1.xsd",
                "xsd/legacy/sdk-sys-img-01.xsd",
                "xsd/legacy/sdk-sys-img-02.xsd",
                "xsd/legacy/sdk-sys-img-03.xsd",
                "com/android/sdklib/repository/meta/",
                "xsd/catalog.xml",
                "xsd/sdk-addon-01.xsd",
                "xsd/sdk-addon-02.xsd",
                "xsd/sdk-common-01.xsd",
                "xsd/sdk-common-02.xsd",
                "xsd/sdk-common-custom-01.xjb",
                "xsd/sdk-common-custom-02.xjb",
                "xsd/sdk-repository-01.xsd",
                "xsd/sdk-repository-02.xsd",
                "xsd/sdk-sys-img-01.xsd",
                "xsd/sdk-sys-img-02.xsd",
                "com/android/sdklib/repository/sources/",
                "com/android/sdklib/repository/sources/generated/",
                "com/android/sdklib/repository/sources/generated/v1/",
                "com/android/sdklib/repository/sources/generated/v2/",
                "com/android/sdklib/repository/sources/generated/v3/",
                "com/android/sdklib/repository/sources/generated/v4/",
                "xsd/sources/",
                "xsd/sources/sdk-sites-list-1.xsd",
                "xsd/sources/sdk-sites-list-2.xsd",
                "xsd/sources/sdk-sites-list-3.xsd",
                "xsd/sources/sdk-sites-list-4.xsd",
                "com/android/sdklib/repository/targets/",
                "com/android/sdklib/util/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/common",
                "com/",
                "com/android/",
                "com/android/ide/",
                "com/android/ide/common/",
                "com/android/ide/common/blame/",
                "com/android/io/",
                "com/android/prefs/",
                "com/android/resources/",
                "com/android/sdklib/",
                "com/android/support/",
                "com/android/support/migrateToAndroidx/",
                "com/android/support/migrateToAndroidx/migration.xml",
                "com/android/testing/",
                "com/android/testing/utils/",
                "com/android/tools/",
                "com/android/tools/proguard/",
                "com/android/utils/",
                "com/android/utils/cxx/",
                "com/android/utils/concurrency/",
                "com/android/utils/reflection/",
                "com/android/version.properties",
                "com/android/xml/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/repository",
                "com/",
                "com/android/",
                "com/android/repository/",
                "com/android/repository/api/",
                "com/android/repository/impl/",
                "com/android/repository/impl/downloader/",
                "com/android/repository/impl/generated/",
                "com/android/repository/impl/generated/generic/",
                "com/android/repository/impl/generated/generic/v1/",
                "com/android/repository/impl/generated/generic/v2/",
                "com/android/repository/impl/generated/v1/",
                "com/android/repository/impl/generated/v2/",
                "com/android/repository/impl/installer/",
                "com/android/repository/impl/manager/",
                "com/android/repository/impl/meta/",
                "com/android/repository/impl/sources/",
                "com/android/repository/impl/sources/generated/",
                "com/android/repository/impl/sources/generated/v1/",
                "com/android/repository/io/",
                "com/android/repository/io/impl/",
                "com/android/repository/util/",
                "xsd/",
                "xsd/catalog.xml",
                "xsd/common-custom-01.xjb",
                "xsd/common-custom-02.xjb",
                "xsd/generic-01.xsd",
                "xsd/generic-02.xsd",
                "xsd/generic-custom-01.xjb",
                "xsd/generic-custom-02.xjb",
                "xsd/global.xjb",
                "xsd/repo-common-01.xsd",
                "xsd/repo-common-02.xsd",
                "xsd/sources/",
                "xsd/sources/repo-sites-common-1.xsd",
                "xsd/sources/repo-sites-common-custom-1.xjb",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/layoutlib/layoutlib-api",
                "com/",
                "com/android/",
                "com/android/ide/",
                "com/android/ide/common/",
                "com/android/ide/common/rendering/",
                "com/android/ide/common/rendering/api/",
                "com/android/resources/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/lint/lint-checks",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/lint/",
                "com/android/tools/lint/checks/",
                "private-apis.txt",
                "sdks-offline.xml",
                "typos/",
                "typos/typos-de.txt",
                "typos/typos-en.txt",
                "typos/typos-es.txt",
                "typos/typos-hu.txt",
                "typos/typos-it.txt",
                "typos/typos-nb.txt",
                "typos/typos-pt.txt",
                "typos/typos-tr.txt",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/lint/lint-api",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/lint/",
                "com/android/tools/lint/client/",
                "com/android/tools/lint/client/api/",
                "com/android/tools/lint/detector/",
                "com/android/tools/lint/detector/api/",
                "com/android/tools/lint/detector/api/interprocedural/",
                "com/android/tools/lint/helpers/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/lint/lint-gradle",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/lint/",
                "com/android/tools/lint/annotations/",
                "com/android/tools/lint/gradle/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/lint/lint-model",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/lint/",
                "com/android/tools/lint/model/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/lint/lint-tests",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/lint/",
                "com/android/tools/lint/checks/",
                "com/android/tools/lint/checks/infrastructure/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/lint/lint",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/lint/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/dvlib",
                "com/",
                "com/android/",
                "com/android/dvlib/",
                "com/android/dvlib/devices-1.xsd",
                "com/android/dvlib/devices-2.xsd",
                "com/android/dvlib/devices-3.xsd",
                "com/android/dvlib/devices-4.xsd",
                "com/android/dvlib/devices-5.xsd",
                "com/android/dvlib/devices-6.xsd",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "androidx/databinding/databinding-compiler-common",
                "android/",
                "android/databinding/",
                "android/databinding/parser/",
                "android/databinding/tool/",
                "android/databinding/tool/expr/",
                "android/databinding/tool/ext/",
                "android/databinding/tool/processing/",
                "android/databinding/tool/processing/scopes/",
                "android/databinding/tool/store/",
                "android/databinding/tool/util/",
                "android/databinding/tool/writer/",
                "data_binding_version_info.properties",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "androidx/databinding/databinding-common",
                "androidx/",
                "androidx/databinding/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "androidx/databinding/databinding-compiler",
                "android/",
                "android/databinding/",
                "android/databinding/annotationprocessor/",
                "android/databinding/tool/",
                "android/databinding/tool/expr/",
                "android/databinding/tool/reflection/",
                "android/databinding/tool/reflection/annotation/",
                "android/databinding/tool/solver/",
                "android/databinding/tool/store/",
                "android/databinding/tool/util/",
                "android/databinding/tool/writer/",
                "api-versions.xml",
                "NOTICE.txt",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/gradle/",
                "META-INF/services/",
                "META-INF/gradle/incremental.annotation.processors",
                "META-INF/services/javax.annotation.processing.Processor");
        expected.putAll( // kept for pre-android-x compatibility
                "com/android/databinding/baseLibrary",
                "android/",
                "android/databinding/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/apkparser/binary-resources",
                "com/",
                "com/google/",
                "com/google/devrel/",
                "com/google/devrel/gmscore/",
                "com/google/devrel/gmscore/tools/",
                "com/google/devrel/gmscore/tools/apk/",
                "com/google/devrel/gmscore/tools/apk/arsc/",
                "LICENSE",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/apkparser/apkanalyzer",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/apk/",
                "com/android/tools/apk/analyzer/",
                "com/android/tools/apk/analyzer/dex/",
                "com/android/tools/apk/analyzer/dex/tree/",
                "com/android/tools/apk/analyzer/internal/",
                "com/android/tools/apk/analyzer/optimizer/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/pixelprobe/pixelprobe",
                "com/",
                "META-INF/MANIFEST.MF",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/pixelprobe/",
                "com/android/tools/pixelprobe/color/",
                "com/android/tools/pixelprobe/decoder/",
                "com/android/tools/pixelprobe/decoder/psd/",
                "com/android/tools/pixelprobe/effect/",
                "com/android/tools/pixelprobe/util/",
                "icc/",
                "icc/cmyk/",
                "icc/cmyk/USWebCoatedSWOP.icc",
                "META-INF/",
                "NOTICE");

        expected.putAll(
                "com/android/tools/draw9patch",
                "com/",
                "com/android/",
                "com/android/draw9patch/",
                "com/android/draw9patch/graphics/",
                "com/android/draw9patch/ui/",
                "com/android/draw9patch/ui/action/",
                "images/",
                "images/checker.png",
                "images/drop.png",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/ninepatch",
                "com/",
                "com/android/",
                "com/android/ninepatch/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/fakeadbserver/fakeadbserver",
                "com/",
                "com/android/",
                "com/android/fakeadbserver/",
                "com/android/fakeadbserver/devicecommandhandlers/",
                "com/android/fakeadbserver/devicecommandhandlers/ddmsHandlers/",
                "com/android/fakeadbserver/hostcommandhandlers/",
                "com/android/fakeadbserver/shellcommandhandlers/",
                "com/android/fakeadbserver/statechangehubs/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/chunkio/chunkio",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/chunkio/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/analytics-library/publisher",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/analytics/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/analytics-library/testing",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/analytics/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/utp/android-device-provider-gradle-proto",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/utp/",
                "com/android/tools/utp/plugins/",
                "com/android/tools/utp/plugins/deviceprovider/",
                "com/android/tools/utp/plugins/deviceprovider/gradle/",
                "com/android/tools/utp/plugins/deviceprovider/gradle/proto/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/utp/android-test-plugin-host-coverage-proto",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/utp/",
                "com/android/tools/utp/plugins/",
                "com/android/tools/utp/plugins/host/",
                "com/android/tools/utp/plugins/host/coverage/",
                "com/android/tools/utp/plugins/host/coverage/proto/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/utp/android-test-plugin-host-retention-proto",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/utp/",
                "com/android/tools/utp/plugins/",
                "com/android/tools/utp/plugins/host/",
                "com/android/tools/utp/plugins/host/icebox/",
                "com/android/tools/utp/plugins/host/icebox/proto/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/utp/android-test-plugin-result-listener-gradle-proto",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/utp/",
                "com/android/tools/utp/plugins/",
                "com/android/tools/utp/plugins/result/",
                "com/android/tools/utp/plugins/result/listener/",
                "com/android/tools/utp/plugins/result/listener/gradle/",
                "com/android/tools/utp/plugins/result/listener/gradle/proto/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        if (TestUtils.runningFromBazel()) {
            // TODO: fix these. (b/64921827)
            ImmutableSet<String> bazelNotImplementedYet =
                    ImmutableSet.of(
                            "com/android/tools/apkparser/binary-resources",
                            "com/android/tools/apkparser/apkanalyzer",
                            "com/android/tools/pixelprobe/pixelprobe",
                            "com/android/tools/draw9patch",
                            "com/android/tools/ninepatch",
                            "com/android/tools/fakeadbserver/fakeadbserver",
                            "com/android/tools/chunkio/chunkio",
                            "com/android/tools/analytics-library/testing");

            EXPECTED =
                    ImmutableSetMultimap.copyOf(
                            Multimaps.filterEntries(
                                    expected.build(),
                                    entry -> !bazelNotImplementedYet.contains(entry.getKey())));
        } else {
            EXPECTED = expected.build();
        }
    }

    @Rule public Expect expect = Expect.createAndEnableStackTrace();

    @Test
    public void checkTools() throws Exception {
        checkGroup("com/android/tools", GMAVEN_MANIFEST);
    }

    @Test
    public void checkDataBinding() throws Exception {
        checkGroup("androidx/databinding/databinding-common", GMAVEN_MANIFEST);
        checkGroup("androidx/databinding/databinding-compiler-common", GMAVEN_MANIFEST);
        checkGroup("androidx/databinding/databinding-compiler", GMAVEN_MANIFEST);
        // pre-android X
        checkGroup("com/android/databinding/baseLibrary", GMAVEN_MANIFEST);
    }

    private void checkGroup(String groupPrefix, String manifestLocation) throws Exception {
        List<String> jarNames = new ArrayList<>();

        Path repo = getRepo(manifestLocation);
        Path androidTools = repo.resolve(groupPrefix);

        List<Path> ourJars =
                Files.walk(androidTools)
                        .filter(path -> path.toString().endsWith(".jar"))
                        .filter(path -> !isIgnored(path.toString()))
                        .filter(JarContentsTest::isCurrentVersion)
                        .collect(Collectors.toList());

        for (Path jar : ourJars) {
            if (jar.toString().endsWith("-sources.jar")) {
                checkSourcesJar(jar);
            } else {
                checkJar(jar, repo);
                jarNames.add(jarRelativePathWithoutVersionWithClassifier(jar, repo));
            }
        }

        String groupPrefixThenForwardSlash = groupPrefix + "/";
        List<String> expectedJars =
                EXPECTED.keySet()
                        .stream()
                        // Allow subdirectories and exact matches, but don't conflate databinding/compilerCommon with databinding/compiler
                        .filter(
                                name ->
                                        name.startsWith(groupPrefixThenForwardSlash)
                                                || name.equals(groupPrefix))
                        .filter(path -> !isIgnored(path))
                        .collect(Collectors.toList());
        // Test only artifact need not be there.
        expectedJars.remove("com/android/tools/internal/build/test/devicepool");
        expect.that(expectedJars).isNotEmpty();
        expect.that(jarNames).named("Jars for " + groupPrefix).containsAllIn(expectedJars);
    }

    private void checkSourcesJar(Path jarPath) throws IOException {
        if (TestUtils.runningFromBazel()) {
            return;
        }
        checkLicense(jarPath);
    }

    private void checkLicense(Path jarPath) throws IOException {
        boolean found = false;
        try (ZipInputStream zipInputStream =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(jarPath)))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (LICENSE_NAMES.contains(entry.getName())) {
                    found = true;
                }
            }
        }
        if (!found) {
            expect.fail("No license file in " + jarPath + " from " + jarPath.getFileSystem());
        }
    }

    private static boolean isIgnored(String path) {
        String normalizedPath = FileUtils.toSystemIndependentPath(path);
        return normalizedPath.contains(EXTERNAL_DEPS);
    }

    private static boolean isCurrentVersion(Path path) {
        return path.toString().contains(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                || path.toString().contains(Version.ANDROID_TOOLS_BASE_VERSION);
    }

    private static String jarRelativePathWithoutVersionWithClassifier(Path jar, Path repo) {
        String pathWithoutVersion = repo.relativize(jar).getParent().getParent().toString();

        String name = jar.getParent().getParent().getFileName().toString();
        String revision = jar.getParent().getFileName().toString();
        String expectedNameNoClassifier = name + "-" + revision;
        String filename = jar.getFileName().toString();
        String path = FileUtils.toSystemIndependentPath(pathWithoutVersion);
        if (!filename.equals(expectedNameNoClassifier + ".jar")) {
            String classifier =
                    filename.substring(
                            expectedNameNoClassifier.length() + 1,
                            filename.length() - ".jar".length());
            return path + ":" + classifier;
        }
        return path;
    }

    private static boolean shouldCheckFile(String fileName) {
        if (fileName.endsWith(".class")) {
            return false;
        }

        if (fileName.endsWith(".kotlin_builtins")) {
            return false;
        }

        if (fileName.endsWith(".kotlin_metadata")) {
            return false;
        }

        if (fileName.endsWith(".kotlin_module")) {
            // TODO: Handle kotlin modules in Bazel. (b/64921827)
            return false;
        }

        if (fileName.endsWith(".proto")) {
            // Gradle packages the proto files in jars.
            // TODO: Can we remove these from the jars? (b/64921827)
            return false;
        }

        //noinspection RedundantIfStatement
        if (fileName.equals("build-data.properties")) {
            // Bazel packages this file in the deploy jar for desugar.
            //TODO: Can we remove these from the jars? (b/64921827)
            return false;
        }

        return true;
    }

    private static Set<String> getCheckableFilesFromEntry(
            ZipEntry entry, NonClosingInputStream entryInputStream, String prefix)
            throws Exception {
        Set<String> files = new HashSet<>();
        if (shouldCheckFile(entry.getName())) {
            String fileName = prefix + entry.getName();
            files.add(fileName);
            if (fileName.endsWith(".jar")) {
                files.addAll(getFilesFromInnerJar(entryInputStream, fileName + ":"));
            }
        }
        return files;
    }

    private void checkJar(Path jar, Path repo) throws Exception {
        checkLicense(jar);

        String key =
                FileUtils.toSystemIndependentPath(
                        jarRelativePathWithoutVersionWithClassifier(jar, repo));
        Set<String> expected = EXPECTED.get(key);
        if (expected == null) {
            expected = Collections.emptySet();
        }

        Set<String> actual = new HashSet<>();

        try (ZipInputStream zipInputStream =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(jar)))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Set<String> filesFromEntry =
                        getCheckableFilesFromEntry(
                                entry, new NonClosingInputStream(zipInputStream), "");
                actual.addAll(
                        filesFromEntry
                                .stream()
                                // Packages under the R8 namespace are renamed.
                                .filter(
                                        path ->
                                                !path.startsWith(R8_NAMESPACE)
                                                        || path.equals(R8_NAMESPACE))
                                // Services used by R8 are reloacted and renamed.
                                .map(
                                        path ->
                                                path.startsWith(
                                                                "META-INF/services/"
                                                                        + R8_PACKAGE_PREFIX)
                                                        ? "META-INF/services/" + R8_PACKAGE_PREFIX
                                                        : path)
                                .collect(Collectors.toList()));
            }

            expect.that(actual)
                    .named(jar.toString() + " with key " + key)
                    .containsExactlyElementsIn(expected);
        }
    }

    private static Set<String> getFilesFromInnerJar(InputStream entryInputStream, String prefix)
            throws Exception {
        Set<String> files = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(entryInputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                files.addAll(
                        getCheckableFilesFromEntry(entry, new NonClosingInputStream(zis), prefix));
            }
        }
        return files;
    }

    private static Path getRepo(String manifest) throws Exception {
        if (!TestUtils.runningFromBazel()) {
            String customRepo = System.getenv("CUSTOM_REPO");
            return Paths.get(
                    Splitter.on(File.pathSeparatorChar).split(customRepo).iterator().next());
        }

        Path repo = Files.createTempDirectory(null);
        RepoLinker linker = new RepoLinker();
        List<String> artifacts = Files.readAllLines(Paths.get(manifest));
        linker.link(repo, artifacts);

        return repo;
    }

    private static class NonClosingInputStream extends FilterInputStream {

        protected NonClosingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
            // Do nothing.
        }
    }
}
