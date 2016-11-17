load("//tools/base/bazel:maven.bzl", "maven_repo")

java_library(
    name = "utils",
    srcs = glob(["src/**/utils/*.java"]),
    visibility = [
        "//tools/base/bazel/sdk:__pkg__",
        "//tools/base/build-system/instant-run-instrumentation:__pkg__",
    ],
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
    name = "kotlin-compiler",
    srcs = ["//prebuilts/tools/common/kotlin-plugin:kotlin-plugin-1.0.1-release-IJ143-46.zip"],
    outs = ["kotlin-compiler.jar"],
    cmd = "$(location :unzipper) $< Kotlin/kotlinc/lib/kotlin-compiler.jar:$@",
    tools = [":unzipper"],
)

genrule(
    name = "kotlin-runtime",
    srcs = ["//prebuilts/tools/common/kotlin-plugin:kotlin-plugin-1.0.1-release-IJ143-46.zip"],
    outs = [
        "kotlin-runtime.jar",
        "kotlin-reflect.jar",
        "kotlin-test.jar",
    ],
    cmd = "$(location :unzipper) $< " +
          "Kotlin/kotlinc/lib/kotlin-runtime.jar:$(@D)/kotlin-runtime.jar " +
          "Kotlin/kotlinc/lib/kotlin-reflect.jar:$(@D)/kotlin-reflect.jar " +
          "Kotlin/kotlinc/lib/kotlin-test.jar:$(@D)/kotlin-test.jar",
    tools = [":unzipper"],
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
        ":formc-deps",
        ":utils",
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

java_library(
    name = "maven",
    srcs = glob(["src/**/maven/*.java"]),
    deps = [
        ":utils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
        "//tools/base/third_party:org.eclipse.aether_aether-connector-basic",
        "//tools/base/third_party:org.eclipse.aether_aether-transport-http",
    ],
)

java_binary(
    name = "pom_generator",
    main_class = "com.android.tools.maven.PomGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [":maven"],
)

java_binary(
    name = "repo_builder",
    main_class = "com.android.tools.maven.RepoBuilder",
    visibility = ["//visibility:public"],
    runtime_deps = [":maven"],
)

# See README.md
java_binary(
    name = "java_import_generator",
    main_class = "com.android.tools.maven.JavaImportGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [
        ":maven",
    ],
)

# See README.md
java_binary(
    name = "third_party_build_generator",
    main_class = "com.android.tools.maven.ThirdPartyBuildGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [":maven"],
)

# See README.md
java_binary(
    name = "add_dependency",
    main_class = "com.android.tools.maven.AddDependency",
    visibility = ["//visibility:public"],
    runtime_deps = [
        ":maven",
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
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-api/1.7.7:jar",
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
        "//tools/base/build-system/gradle",
        "//tools/base/build-system/gradle-experimental",
    ],
    visibility = ["//visibility:public"],
)
