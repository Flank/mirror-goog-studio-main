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

java_binary(
    name = "singlejar",
    main_class = "com.android.tools.utils.SingleJar",
    visibility = ["//visibility:public"],
    runtime_deps = [":utils"],
)

java_library(
    name = "maven",
    srcs = glob(["src/**/maven/*.java"]),
    deps = [
        ":utils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
        "//tools/base/third_party:org.eclipse.aether_aether-transport-http",
        "//tools/base/third_party:org.eclipse.aether_aether-connector-basic",
    ],
)

# See README.md
java_binary(
    name = "java_import_generator",
    main_class = "com.android.tools.maven.JavaImportGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [":maven"],
)

# See README.md
java_binary(
    name = "third_party_build_generator",
    main_class = "com.android.tools.maven.ThirdPartyBuildGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [":maven"],
    args = [
            "com.google.code.findbugs:jsr305:1.3.9",
            "com.google.code.gson:gson:2.2.4",
            "com.google.guava:guava:19.0",
            "com.google.jimfs:jimfs:1.1",
            "com.google.protobuf:protobuf-java:2.5.0",
            "com.intellij:annotations:12.0",
            "com.squareup:javawriter:2.5.0",
            "net.sf.kxml:kxml2:2.3.0",
            "org.apache.commons:commons-compress:1.8.1",
            "org.apache.httpcomponents:httpclient:4.1.1",
            "org.apache.httpcomponents:httpmime:4.1",
            "org.apache.maven:maven-aether-provider:3.3.9",
            "org.bouncycastle:bcpkix-jdk15on:1.48",
            "org.bouncycastle:bcprov-jdk15on:1.48",
            "org.eclipse.aether:aether-connector-basic:1.0.2.v20150114",
            "org.eclipse.aether:aether-transport-file:1.0.2.v20150114",
            "org.eclipse.aether:aether-transport-http:1.0.2.v20150114",
            "org.ow2.asm:asm-tree:5.0.4",
            "org.ow2.asm:asm:5.0.4",

            # Include test dependencies here as well, so that we take them into
            # consideration when computing version numbers.
            "com.google.truth:truth:0.28",
            "junit:junit:4.12",
            "nl.jqno.equalsverifier:equalsverifier:2.1.5",
            "org.easymock:easymock:3.3",
            "org.mockito:mockito-all:1.9.5",
    ],
)

# See README.md
java_binary(
    name = "add_dependency",
    main_class = "com.android.tools.maven.AddDependency",
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
    "//tools/adt/idea:android_tests",
    "//tools/analytics-library:analytics-publisher_tests",
    "//tools/analytics-library:analytics-shared_tests",
    "//tools/analytics-library:analytics-tracker_tests",
    "//tools/analytics-library:tools.analytics-shared_tests",
    "//tools/analytics-library:tools.analytics-tracker_tests",
    "//tools/base/apkparser:studio.binary-resources_tests",
    "//tools/base/asset-studio:studio.assetstudio_tests",
    "//tools/base/jack/jack-api:tests",
    "//tools/base/build-system/profile:tests",
    "//tools/base/build-system:studio.builder-test-api_tests",
    "//tools/base/build-system:studio.manifest-merger_tests",
    "//tools/base/build-system:tools.builder-test-api_tests",
    "//tools/base/build-system:tools.manifest-merger_tests",
    "//tools/base/common:studio.common_tests",
    "//tools/base/common:tools.tests",
    "//tools/base/ddmlib:studio.ddmlib_tests",
    "//tools/base/ddmlib:tools.tests",
    "//tools/base/draw9patch:studio.draw9patch_tests",
    "//tools/base/instant-run:studio.instant-run-client_tests",
    "//tools/base/layoutlib-api:studio.layoutlib-api_tests",
    "//tools/base/layoutlib-api:tools.tests",
    "//tools/base/ninepatch:studio.ninepatch_tests",
    "//tools/base/perflib:studio.perflib_tests",
    "//tools/base/pixelprobe:studio.pixelprobe_tests",
    "//tools/base/repository:studio.repository_tests",
    "//tools/base/repository:tools.tests",
    "//tools/base/sdk-common:studio.sdk-common_tests",
    "//tools/base/sdk-common:tools.tests",
    "//tools/base/sdklib:studio.sdklib_tests",
    "//tools/base/sdklib:tools.tests",
    "//tools/base/testutils:studio.testutils_tests",
    "//tools/base/testutils:tools.tests",
    "//tools/data-binding:db-compilerCommon_tests",
    "//tools:idea.extensions_tests",
  # "//tools:idea.forms-compiler_tests", # Passes on linux, flaky on some make configurations.
    "//tools:idea.lang-api_tests",
  ],
)
