load("//tools/base/bazel:maven.bzl", "maven_repo")

java_library(
    name = "utils",
    srcs = glob(["src/**/utils/*.java"]),
    visibility = [
        "//tools/base/bazel/sdk:__pkg__",
        "//tools/base/build-system/instant-run-instrumentation:__pkg__",
    ],
)

java_binary(
    name = "jasmin-compiler",
    srcs = glob(["src/com/android/tools/jasmin/JasminCompiler.java"]),
    main_class = "com.android.tools.jasmin.JasminCompiler",
    visibility = ["//visibility:public"],
    deps = [
        ":utils",
        "//prebuilts/tools/common/jasmin",
    ],
)

java_library(
    name = "groovy-compiler",
    srcs = glob(["src/**/groovy/*.java"]),
    # Note that version 2.3 of groovy, embeds timestamps into the generated classes
    # making it unsuitable for bazel or any other reasonable tool.
    # TODO: Update and use the prebuilts dependency
    deps = [
        ":utils",
        "//tools/idea/lib:groovy-all-2.4.6",
    ],
)

java_binary(
    name = "groovy_stub_gen",
    main_class = "com.android.tools.groovy.GroovyStubGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [":groovy-compiler"],
)

java_binary(
    name = "groovyc",
    main_class = "com.android.tools.groovy.GroovyCompiler",
    visibility = ["//visibility:public"],
    runtime_deps = [":groovy-compiler"],
)

java_binary(
    name = "unzipper",
    main_class = "com.android.tools.utils.Unzipper",
    visibility = ["//visibility:public"],
    runtime_deps = [":utils"],
)

genrule(
    name = "kotlin-compiler.genrule",
    srcs = ["//prebuilts/tools/common/kotlin-plugin:kotlin-plugin-1.1.0-release-112.zip"],
    outs = ["kotlin-compiler.jar"],
    cmd = "$(location :unzipper) $< Kotlin/kotlinc/lib/kotlin-compiler.jar:$@",
    tools = [":unzipper"],
)

java_import(
    name = "kotlin-compiler",
    jars = [":kotlin-compiler.genrule"],
)

genrule(
    name = "kotlin-runtime.genrule",
    srcs = ["//prebuilts/tools/common/kotlin-plugin:kotlin-plugin-1.1.0-release-112.zip"],
    outs = [
        "kotlin-runtime.jar",
        "kotlin-reflect.jar",
        "kotlin-script-runtime.jar",
    ],
    cmd = "$(location :unzipper) $< " +
          "Kotlin/kotlinc/lib/kotlin-runtime.jar:$(@D)/kotlin-runtime.jar " +
          "Kotlin/kotlinc/lib/kotlin-reflect.jar:$(@D)/kotlin-reflect.jar " +
          "Kotlin/kotlinc/lib/kotlin-script-runtime.jar:$(@D)/kotlin-script-runtime.jar",
    tools = [":unzipper"],
)

java_import(
    name = "kotlin-runtime",
    jars = [":kotlin-runtime.genrule"],
    visibility = ["//visibility:public"],
)

genrule(
    name = "kotlin-test.genrule",
    srcs = ["//prebuilts/tools/common/kotlin-plugin:kotlin-plugin-1.1.0-release-112.zip"],
    outs = ["kotlin-test.jar"],
    cmd = "$(location :unzipper) $< Kotlin/kotlinc/lib/kotlin-test.jar:$(@D)/kotlin-test.jar",
    tools = [":unzipper"],
)

java_import(
    name = "kotlin-test",
    jars = [":kotlin-test.genrule"],
    visibility = ["//visibility:public"],
)

java_binary(
    name = "kotlinc",
    srcs = ["src/com/android/tools/kotlin/KotlinCompiler.java"],
    main_class = "com.android.tools.kotlin.KotlinCompiler",
    visibility = ["//visibility:public"],
    runtime_deps = [":kotlin-runtime"],
    deps = [
        ":kotlin-compiler",
        ":utils",
    ],
)

java_binary(
    name = "formc",
    srcs = ["src/com/android/tools/form/FormCompiler.java"],
    main_class = "com.android.tools.form.FormCompiler",
    visibility = ["//visibility:public"],
    runtime_deps = [":kotlin-runtime"],
    deps = [
        ":utils",
        "//prebuilts/tools/common/bazel/formc-deps",
    ],
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

java_binary(
    name = "properties_merger",
    main_class = "com.android.tools.utils.PropertiesMerger",
    visibility = ["//visibility:public"],
    runtime_deps = [":utils"],
)

java_library(
    name = "maven",
    srcs = glob(["src/com/android/tools/maven/**/*.java"]),
    deps = [
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
        "//tools/base/third_party:org.eclipse.aether_aether-connector-basic",
        "//tools/base/third_party:org.eclipse.aether_aether-transport-http",
    ],
)

java_binary(
    name = "pom_generator",
    srcs = ["src/com/android/tools/binaries/PomGenerator.java"],
    jvm_flags = ["-Xmx1g"],
    main_class = "com.android.tools.binaries.PomGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-simple/1.7.2:jar",
    ],
    deps = [
        ":maven",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
    ],
)

java_binary(
    name = "repo_builder",
    srcs = ["src/com/android/tools/binaries/RepoBuilder.java"],
    main_class = "com.android.tools.binaries.RepoBuilder",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-simple/1.7.2:jar",
    ],
    deps = [
        ":maven",
        ":utils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
    ],
)

# See README.md
java_binary(
    name = "java_import_generator",
    srcs = ["src/com/android/tools/binaries/JavaImportGenerator.java"],
    main_class = "com.android.tools.binaries.JavaImportGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-simple/1.7.2:jar",
    ],
    deps = [
        ":maven",
        ":utils",
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
    ],
)

# See README.md
java_binary(
    name = "third_party_build_generator",
    srcs = [
        "src/com/android/tools/binaries/JavaImportGenerator.java",
        "src/com/android/tools/binaries/ThirdPartyBuildGenerator.java",
    ],
    main_class = "com.android.tools.binaries.ThirdPartyBuildGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-simple/1.7.2:jar",
    ],
    deps = [
        ":maven",
        ":utils",
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
    ],
)

# See README.md
java_binary(
    name = "add_dependency",
    srcs = [
        "src/com/android/tools/binaries/AddDependency.java",
        "src/com/android/tools/binaries/JavaImportGenerator.java",
    ],
    main_class = "com.android.tools.binaries.AddDependency",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-simple/1.7.2:jar",
    ],
    deps = [
        ":maven",
        ":utils",
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
    ],
)

java_binary(
    name = "jarjar",
    main_class = "com.tonicsystems.jarjar.Main",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2/repository/net/sf/jopt-simple/jopt-simple/4.9:jar",
        "//prebuilts/tools/common/m2/repository/org/anarres/jarjar/jarjar-command/1.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/anarres/jarjar/jarjar-core/1.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-commons/5.0.4:jar",
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm/5.0.4:jar",
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-api/1.7.2:jar",
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-simple/1.7.2:jar",
    ],
)

java_binary(
    name = "antlr",
    srcs = glob(["src/**/antlr/**"]),
    main_class = "com.android.tools.utils.antlr.AntlrCompiler",
    visibility = ["//visibility:public"],
    deps = [
        ":utils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.antlr_antlr",
    ],
)

config_setting(
    name = "darwin",
    values = {
        "cpu": "darwin",
    },
    visibility = ["//visibility:public"],
)

# TODO: Temporary test rule for repo generation
maven_repo(
    name = "offline_repo",
    artifacts = [
        "//tools/data-binding:tools.compiler",
        "//tools/base/build-system/gradle",
        "//tools/base/build-system/gradle-experimental",
        "//tools/base/build-system/java-lib-plugin/java-lib-model-builder",
    ],
    include_sources = True,
    visibility = ["//visibility:public"],
)
