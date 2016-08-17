java_library(
  name = "utils",
  srcs = glob(["src/**/utils/*.java"]),
)

# Part of the standalone-jps-builder artifact from IDEA
java_import(
  name = "formc-deps",
  jars = [
    "prebuilts/asm-all.jar",
    "prebuilts/jdom.jar",
    "prebuilts/jgoodies-forms.jar",
    "prebuilts/jps-builders.jar",
  ],
)

java_library(
  name = "groovy-compiler",
  srcs = glob(["src/**/groovy/*.java"]),
  # Note that version 2.3 of groovy, embeds timestamps into the generated classes
  # making it unsuitable for bazel or any other reasonable tool.
  # TODO: Update and use the prebuilts dependency
  deps = [
    "//tools:idea/lib/groovy-all-2.4.6",
    ":utils"
  ],
)

java_binary(
  name = "groovy_stub_gen",
  runtime_deps = [":groovy-compiler"],
  main_class = "com.android.tools.groovy.GroovyStubGenerator",
  visibility = ["//visibility:public"],
)

java_binary(
  name = "groovyc",
  runtime_deps = [":groovy-compiler"],
  main_class = "com.android.tools.groovy.GroovyCompiler",
  visibility = ["//visibility:public"],
)

java_binary(
  name = "unzipper",
  main_class = "com.android.tools.utils.Unzipper",
  runtime_deps = [":utils"],
  visibility = ["//visibility:public"],
)

genrule(
    name = "kotlin-compiler",
    srcs = ["//prebuilts/tools/common/kotlin-plugin:kotlin-plugin-1.0.0-release-IJ143-78.zip"],
    outs = ["kotlin-compiler.jar"],
    cmd = "$(location :unzipper) $< Kotlin/kotlinc/lib/kotlin-compiler.jar:$@",
    tools = [":unzipper"],
)

genrule(
    name = "kotlin-runtime",
    srcs = ["//prebuilts/tools/common/kotlin-plugin:kotlin-plugin-1.0.0-release-IJ143-78.zip"],
    outs = ["kotlin-runtime.jar"],
    cmd = "$(location :unzipper) $< Kotlin/kotlinc/lib/kotlin-runtime.jar:$@",
    tools = [":unzipper"],
    visibility = ["//visibility:public"],
)

java_binary(
  name = "kotlinc",
  srcs = ["src/com/android/tools/kotlin/KotlinCompiler.java"],
  main_class = "com.android.tools.kotlin.KotlinCompiler",
  deps = [
    ":kotlin-compiler",
    ":utils",
  ],
  runtime_deps = [":kotlin-runtime"],
  visibility = ["//visibility:public"],
)

java_binary(
  name = "formc",
  srcs = ["src/com/android/tools/form/FormCompiler.java"],
  main_class = "com.android.tools.form.FormCompiler",
  deps = [
    ":formc-deps",
    ":utils",
  ],
  runtime_deps = [":kotlin-runtime"],
  visibility = ["//visibility:public"],
)

java_import(
  name = "langtools",
  jars = ["@local_jdk//:langtools"],
  visibility = ["//visibility:public"],
)

java_library(
    name = "maven",
    srcs = glob(["src/**/maven/*.java"]),
    deps = [
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
    ],
)

java_binary(
    name = "java_import_generator",
    main_class = "com.android.tools.maven.JavaImportGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [":maven"],
    # TODO: how to pass the path to workspace as argument?
)

java_binary(
    name = "third_party_build_generator",
    main_class = "com.android.tools.maven.ThirdPartyBuildGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [":maven"],
)


java_binary(
    name = "jarjar",
    main_class = "com.tonicsystems.jarjar.Main",
    runtime_deps = [
      "//prebuilts/tools/common/m2/repository/net/sf/jopt-simple/jopt-simple/4.9:jar",
      "//prebuilts/tools/common/m2/repository/org/anarres/jarjar/jarjar-command/1.0.0:jar",
      "//prebuilts/tools/common/m2/repository/org/anarres/jarjar/jarjar-core/1.0.0:jar",
      "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-commons/5.0.4:jar",
      "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm/5.0.4:jar",
      "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-api/1.7.7:jar",
    ],
    visibility = ["//visibility:public"],
)

test_suite(
  name = "all_tests",
  tests = [
    "//tools/adt/idea/adt-ui:adt-ui_tests",
    "//tools/adt/idea:android-common_tests",
    "//tools/analytics-library:analytics-publisher_tests",
    "//tools/analytics-library:analytics-shared_tests",
    "//tools/analytics-library:analytics-tracker_tests",
    "//tools/base/apkparser:binary-resources_tests",
    "//tools/base/asset-studio:assetstudio_tests",
    "//tools/base/build-system:builder-test-api_tests",
    "//tools/base/build-system:manifest-merger_tests",
    "//tools/base/common:common_tests",
    "//tools/base/ddmlib:ddmlib_tests",
    "//tools/base/draw9patch:draw9patch_tests",
    "//tools/base/instant-run:instant-run-client_tests",
    "//tools/base/layoutlib-api:layoutlib-api_tests",
    "//tools/base/ninepatch:ninepatch_tests",
    "//tools/base/perflib:perflib_tests",
    "//tools/base/pixelprobe:pixelprobe_tests",
    "//tools/base/repository:repository_tests",
    "//tools/base/sdk-common:sdk-common_tests",
    "//tools/base/sdklib:sdklib_tests",
    "//tools/base/testutils:testutils_tests",
    "//tools/data-binding:db-compilerCommon_tests",
    "//tools:idea.extensions_tests",
    "//tools:idea.forms-compiler_tests",
    "//tools:idea.lang-api_tests",
  ],
)
