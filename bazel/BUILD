java_library(
    name = "utils",
    srcs = glob(["src/**/utils/*.java"]),
    visibility = ["//tools/base/bazel/sdk:__pkg__"],
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
        ":utils",
        "//tools:idea/lib/groovy-all-2.4.6",
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
    ],
    cmd = "$(location :unzipper) $< " +
          "Kotlin/kotlinc/lib/kotlin-runtime.jar:$(@D)/kotlin-runtime.jar " +
          "Kotlin/kotlinc/lib/kotlin-reflect.jar:$(@D)/kotlin-reflect.jar",
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
    name = "pom_modifier",
    main_class = "com.android.tools.maven.PomModifier",
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
    runtime_deps = [":maven"],
)

# See README.md
java_binary(
    name = "third_party_build_generator",
    args = [
        "com.android.tools.external.lombok:lombok-ast:0.2.3",
        "com.google.code.findbugs:jsr305:1.3.9",
        "com.google.code.gson:gson:2.2.4",
        "com.google.guava:guava:19.0",
        "com.google.jimfs:jimfs:1.1",
        "com.google.protobuf:protobuf-java:2.5.0",
        "com.googlecode.juniversalchardet:juniversalchardet:1.0.3",
        "com.intellij:annotations:12.0",
        "com.squareup:javawriter:2.5.0",
        "net.sf.kxml:kxml2:2.3.0",
        "org.antlr:antlr4:4.5.3",
        "org.apache.commons:commons-compress:1.8.1",
        "commons-io:commons-io:2.4",
        "org.apache.httpcomponents:httpclient:4.1.1",
        "org.apache.httpcomponents:httpmime:4.1",
        "org.apache.maven:maven-aether-provider:3.3.9",
        "org.bouncycastle:bcpkix-jdk15on:1.48",
        "org.bouncycastle:bcprov-jdk15on:1.48",
        "org.codehaus.groovy:groovy-all:2.2.1",
        "org.eclipse.aether:aether-connector-basic:1.0.2.v20150114",
        "org.eclipse.aether:aether-transport-file:1.0.2.v20150114",
        "org.eclipse.aether:aether-transport-http:1.0.2.v20150114",
        "org.eclipse.jdt.core.compiler:ecj:4.5.1",
        "org.ow2.asm:asm-analysis:5.0.4",
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
    main_class = "com.android.tools.maven.ThirdPartyBuildGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [":maven"],
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

config_setting(
    name = "darwin",
    values = {
        "cpu": "darwin",
    },
    visibility = ["//visibility:public"],
)
