load("//tools/base/bazel:bazel.bzl", "fileset")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

fileset(
    name = "resources",
    srcs = glob(["src/main/resources/**"]) + [
        # TODO: figure out how to include all resources from aaptjlib automatically.
        "//prebuilts/tools/common/aapt/jni:linux64/libaapt2_jni.so",
        "//prebuilts/tools/common/aapt/jni:linux64/libc++.so",
        "//prebuilts/tools/common/aapt/jni:mac64/libaapt2_jni.dylib",
        "//prebuilts/tools/common/aapt/jni:mac64/libc++.dylib",
        "//prebuilts/tools/common/aapt/jni:win32/libaapt2_jni.dll",
        "//prebuilts/tools/common/aapt/jni:win64/libaapt2_jni.dll",
        "//prebuilts/tools/common/aapt/jni:win32/libwinpthread-1.dll",
        "//prebuilts/tools/common/aapt/jni:win64/libwinpthread-1.dll",
        "//tools/buildSrc/base:version.properties",
        "//tools/external/desugar:desugar_deploy.jar",
        "//tools/external/desugar/java/com/google/devtools/build/android/desugar/runtime:libthrowable_extension.jar",
    ],
    mappings = {
        "//tools/buildSrc/base:": "com/android/builder/",
        "//tools/external/desugar:": "",
        "//tools/external/desugar/java/com/google/devtools/build/android/desugar/runtime:": "",
        "//prebuilts/tools/common/aapt/jni:": "",
        "src/main/resources/": "",
    },
)

maven_java_library(
    name = "builder",
    srcs = glob(["src/main/java/**/*.java"]) + [
        "//tools/base/apkzlib:srcjar",
        "//tools/base/build-system/profile:srcjar",
        "//tools/base/aaptjlib:srcjar",
        "//tools/dx/dalvik/dx:srcjar",
    ],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    plugins = [
        ":auto-value-plugin",
    ],
    pom = ":pom",
    resource_strip_prefix = "tools/base/build-system/builder",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        ":com.google.auto.value_auto-value_neverlink",
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/analytics-library/tracker:tools.analytics-tracker",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.apksig",
        "//tools/base/build-system:tools.builder-test-api",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/jack/jack-api",
        "//tools/base/jack/jill-api",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
        "//tools/base/third_party:com.squareup_javawriter",
        "//tools/base/third_party:commons-codec_commons-codec",
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
        "//tools/base/third_party:org.bouncycastle_bcprov-jdk15on",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
    ],
)

maven_pom(
    name = "pom",
    artifact = "builder",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
)

java_test(
    name = "tests",
    srcs = glob(["src/test/java/**"]),
    data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
    ],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        # Need to put this on the classpath before TestRunner_deploy.jar which contains
        # old JUnit classes. See https://github.com/bazelbuild/bazel/issues/2146.
        "//tools/base/third_party:junit_junit",
    ],
    deps = [
        ":builder",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.builder-test-api",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.jimfs_jimfs",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:nl.jqno.equalsverifier_equalsverifier",
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
        "//tools/base/third_party:org.mockito_mockito-core",
        "//tools/base/third_party:org.ow2.asm_asm",
    ],
)

java_plugin(
    name = "auto-value-plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        "//tools/base/third_party:com.google.auto.value_auto-value",
    ],
)

# Compile-time only dependency on the auto-value.
java_library(
    name = "com.google.auto.value_auto-value_neverlink",
    neverlink = 1,
    exports = ["//tools/base/third_party:com.google.auto.value_auto-value"],
)
