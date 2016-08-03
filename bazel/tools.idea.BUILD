# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "idea.groovy_rt",
    srcs = ["idea/plugins/groovy/rt/src"],
    deps = [
        "//tools:idea/lib/groovy-all-2.4.6",
        "//tools:idea.jps-builders[module]",
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.groovy-rt-constants[module]",
        "//tools:idea/plugins/groovy/testdata/griffon/griffon-rt-1.1.0",
        "//tools:idea/plugins/groovy/testdata/griffon/griffon-cli-1.1.0",
        "//tools:idea/lib/slf4j-api-1.7.10",
        "//tools:idea/lib/slf4j-log4j12-1.7.10",
    ],
    exports = ["//tools:idea/lib/groovy-all-2.4.6"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/xmlrpc-2.0",
  jars = [
      "idea/lib/xmlrpc-2.0.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/snappy-in-java-0.3.1",
  jars = [
      "idea/lib/snappy-in-java-0.3.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/gradle-process-services-2.14.1",
  jars = [
      "idea/plugins/gradle/lib/gradle-process-services-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/jayatana-1.2.4",
  jars = [
      "idea/lib/jayatana-1.2.4.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.platform-main",
    srcs = ["idea/platform/platform-main/src"],
    deps = [
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.bootstrap[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.java-structure-view",
    srcs = ["idea/java/java-structure-view/src"],
    deps = [
        "//tools:idea.structure-view-impl[module]",
        "//tools:idea.java-psi-impl[module]",
    ],
    exports = ["//tools:idea.structure-view-impl"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/groovy/testdata/griffon/griffon-rt-1.1.0",
  jars = [
      "idea/plugins/groovy/testdata/griffon/griffon-rt-1.1.0.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/asm4-all",
  jars = [
      "idea/lib/asm4-all.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/picocontainer",
  jars = [
      "idea/lib/picocontainer.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.MM_idea-ui",
    srcs = [
        "idea/java/idea-ui/src",
        "idea/platform/external-system-impl/src",
        "idea/java/testFramework/src",
        "idea/java/execution/impl/src",
        "idea/java/debugger/impl/src",
        "idea/java/compiler/impl/src",
    ],
    test_srcs = [
        "idea/platform/external-system-impl/testSrc",
        "idea/java/compiler/impl/testSrc",
    ],
    resources = ["idea/platform/external-system-impl/resources"],
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.compiler-openapi[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea/lib/oromatcher",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea/lib/guava-18.0",
        "//tools:idea.external-system-api[module]",
        "//tools:idea.projectModel-impl[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.vcs-api[module]",
        "//tools:idea/lib/groovy-all-2.4.6",
        "//tools:idea.testRunner[module]",
        "//tools:idea.smRunner[module]",
        "//tools:idea/lib/hamcrest-core-1.3",
        "//tools:idea/lib/junit-4.12",
        "//tools:idea.util[module]",
        "//tools:idea/lib/jdom",
        "//tools:idea/lib/log4j",
        "//tools:idea.lang-api[module]",
        "//tools:idea.java-runtime[module]",
        "//tools:idea/lib/jgoodies-forms",
        "//tools:idea.java-indexing-api[module]",
        "//tools:idea/lib/coverage-agent",
        "//tools:idea/lib/coverage-instrumenter",
        "//tools:idea/lib/coverage-util",
        "//tools:idea.debugger-openapi[module]",
        "//tools:idea.resources[module]",
        "//tools:idea.xdebugger-api[module]",
        "//tools:idea.jsp-openapi[module]",
        "//tools:idea.diff-api[module]",
        "//tools:idea.jps-builders[module]",
        "//tools:idea/lib/trove4j",
        "//tools:idea.instrumentation-util[module]",
        "//tools:idea/lib/asm-all",
        "//tools:idea.platform-api[module]",
        "//tools:idea.jps-launcher[module]",
        "//tools:idea/lib/netty-all-4.1.0.CR7",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.java-analysis-impl[module]",
    ],
    exports = [
        "//tools:idea.openapi",
        "//tools:idea.util",
        "//tools:idea.lang-api",
        "//tools:idea.java-impl",
        "//tools:idea.MM_RegExpSupport",
        "//tools:idea.execution-openapi",
        "//tools:idea.testRunner",
        "//tools:idea.debugger-openapi",
        "//tools:idea.compiler-openapi",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/xml-apis-ext",
  jars = [
      "idea/lib/xml-apis-ext.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/dev/jmock-junit4-2.5.1",
  jars = [
      "idea/lib/dev/jmock-junit4-2.5.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/coverage-util",
  jars = [
      "idea/lib/coverage-util.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-jai",
  jars = [
      "idea/lib/ant/lib/ant-jai.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.MM_maven2-server-impl",
    srcs = [
        "idea/plugins/maven/maven2-server-impl/src",
        "idea/plugins/maven/src/main/java",
    ],
    test_srcs = [
        "idea/plugins/maven/maven2-server-impl/test",
        "idea/plugins/maven/src/test/java",
    ],
    resources = ["idea/plugins/maven/src/main/resources"],
    deps = [
        "//tools:idea.maven-server-api[module]",
        "//tools:idea.util[module]",
        "//tools:idea/plugins/maven/maven2-server-impl/lib/plexus-utils-1.5.5",
        "//tools:idea/plugins/maven/maven2-server-impl/lib/maven-dependency-tree-1.2",
        "//tools:idea/plugins/maven/maven2-server-impl/lib/archetype-common-2.0-alpha-4-SNAPSHOT",
        "//tools:idea/plugins/maven/maven2-server-impl/lib/mercury-artifact-1.0-alpha-6",
        "//tools:idea/plugins/maven/maven2-server-impl/lib/maven2/lib/maven-2.2.1-uber",
        "//tools:idea/plugins/maven/maven2-server-impl/lib/maven2/boot/classworlds-1.1",
        "//tools:idea/plugins/maven/maven2-server-impl/lib/nexus-indexer-1.2.3",
        "//tools:idea/plugins/maven/maven2-server-impl/lib/activation-1.1",
        "//tools:idea/lib/commons-logging-1.2",
        "//tools:idea/plugins/maven/maven2-server-impl/lib/commons-beanutils",
        "//tools:idea.openapi[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.IntelliLang-xml[module]",
        "//tools:idea.properties[module]",
        "//tools:idea.compiler-openapi[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.forms_rt[module]",
        "//tools:idea/lib/jgoodies-forms",
        "//tools:idea/lib/jsr173_1.0_api",
        "//tools:idea/lib/xbean",
        "//tools:idea/lib/resolver",
        "//tools:idea/plugins/maven/lib/wadl-core",
        "//tools:idea/lib/gson-2.5",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools:idea.jetgroovy[module]",
        "//tools:idea/lib/guava-18.0",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.maven-jps-plugin[module]",
        "//tools:idea.maven-artifact-resolver-m2[module]",
        "//tools:idea.maven-artifact-resolver-m3[module]",
        "//tools:idea.maven-artifact-resolver-m31[module]",
        "//tools:idea.vcs-api[module]",
        "//tools:idea.properties-psi-api[module]",
        "//tools:idea/plugins/maven/lib/plexus-archiver-2.4.4",
        "//tools:idea.external-system-api[module]",
        "//tools:idea/lib/slf4j-api-1.7.10",
        "//tools:idea/lib/slf4j-log4j12-1.7.10",
        "//tools:idea/lib/log4j",
        "//tools:idea.util-tests[module, test]",
    ],
    exports = [
        "//tools:idea.maven-server-api",
        "//tools:idea.openapi",
        "//tools:idea.MM_RegExpSupport",
        "//tools:idea.compiler-openapi",
        "//tools:idea.MM_idea-ui",
        "//tools:idea.execution-openapi",
        "//tools:idea.forms_rt",
        "//tools:idea/lib/hamcrest-core-1.3",
        "//tools:idea/lib/junit-4.12",
        "//tools:idea.external-system-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/commons-io-1.4",
  jars = [
      "idea/plugins/gradle/lib/commons-io-1.4.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.indexing-api",
    srcs = ["idea/platform/indexing-api/src"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea/lib/nanoxml-2.2.3",
    ],
    exports = ["//tools:idea.core-api"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-jsch",
  jars = [
      "idea/lib/ant/lib/ant-jsch.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.typeMigration",
    srcs = ["idea/java/typeMigration/src"],
    test_srcs = ["idea/java/typeMigration/test"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.openapi[module]",
        "//tools:idea.structuralsearch[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.structuralsearch-java[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.platform-resources-en",
    resources = ["idea/platform/platform-resources-en/src"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.structure-view-impl",
    srcs = ["idea/platform/structure-view-impl/src"],
    deps = [
        "//tools:idea.editor-ui-api[module]",
        "//tools:idea.util[module]",
        "//tools:idea.extensions[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea/lib/picocontainer",
        "//tools:idea.bootstrap[module]",
        "//tools:idea.platform-resources-en[module]",
        "//tools:idea.icons[module]",
        "//tools:idea/lib/automaton",
        "//tools:idea.projectModel-api[module]",
    ],
    exports = [
        "//tools:idea.editor-ui-api",
        "//tools:idea.util",
        "//tools:idea.extensions",
        "//tools:idea.core-api",
        "//tools:idea/lib/picocontainer",
        "//tools:idea.platform-resources-en",
        "//tools:idea.projectModel-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.util-rt",
    srcs = ["idea/platform/util-rt/src"],
    deps = ["//tools:idea.annotations[module]"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/guava-18.0",
  jars = [
      "idea/lib/guava-18.0.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/xmlgraphics-commons-1.5",
  jars = [
      "idea/lib/xmlgraphics-commons-1.5.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/jsr305-1.3.9",
  jars = [
      "idea/plugins/gradle/lib/jsr305-1.3.9.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.maven-artifact-resolver-m31",
    srcs = ["idea/plugins/maven/artifact-resolver-m31/src"],
    deps = [
        "//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-core-3.0.5",
        "//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/plexus-component-annotations-1.5.5",
        "//tools:idea/plugins/maven/artifact-resolver-m31/lib/eclipse-aether/aether-api-0.9.0.M2",
        "//tools:idea.maven-artifact-resolver-common[module]",
    ],
    exports = ["//tools:idea.maven-artifact-resolver-common"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/tooling-extension-impl/lib/gradle-ear-2.14.1",
  jars = [
      "idea/plugins/gradle/tooling-extension-impl/lib/gradle-ear-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/junit-4.12",
  jars = [
      "idea/lib/junit-4.12.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.xml-openapi",
    srcs = ["idea/xml/openapi/src"],
    deps = [
        "//tools:idea.lang-api[module]",
        "//tools:idea.xml-psi-api[module]",
        "//tools:idea.xml-analysis-api[module]",
        "//tools:idea.xml-structure-view-api[module]",
    ],
    exports = [
        "//tools:idea.xml-psi-api",
        "//tools:idea.xml-analysis-api",
        "//tools:idea.xml-structure-view-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/lib/plexus-utils-2.0.6",
  jars = [
      "idea/plugins/maven/lib/plexus-utils-2.0.6.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/xercesImpl",
  jars = [
      "idea/lib/xercesImpl.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-apache-regexp",
  jars = [
      "idea/lib/ant/lib/ant-apache-regexp.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/gradle-base-services-2.14.1",
  jars = [
      "idea/plugins/gradle/lib/gradle-base-services-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.testng_rt",
    srcs = ["idea/plugins/testng_rt/src"],
    deps = [
        "//tools:idea/plugins/testng/lib/testng",
        "//tools:idea/plugins/testng/lib/jcommander",
        "//tools:idea.java-runtime[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/dev/jmock-2.5.1",
  jars = [
      "idea/lib/dev/jmock-2.5.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.jps-model-impl",
    srcs = ["idea/jps/model-impl/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.jps-model-api[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/hamcrest-library-1.3",
  jars = [
      "idea/lib/hamcrest-library-1.3.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/gradle-wrapper-2.14.1",
  jars = [
      "idea/plugins/gradle/lib/gradle-wrapper-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.boot",
    srcs = ["idea/platform/boot/src"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.vcs-api-core",
    srcs = ["idea/platform/vcs-api/vcs-api-core/src"],
    deps = [
        "//tools:idea.annotations[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea.editor-ui-api[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.jps-launcher",
    srcs = ["idea/jps/jps-launcher/src"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-apache-resolver",
  jars = [
      "idea/lib/ant/lib/ant-apache-resolver.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/dev/jmock-legacy-2.5.1",
  jars = [
      "idea/lib/dev/jmock-legacy-2.5.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/sanselan-0.98-snapshot",
  jars = [
      "idea/lib/sanselan-0.98-snapshot.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/gradle-resources-2.14.1",
  jars = [
      "idea/plugins/gradle/lib/gradle-resources-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.gradle-tooling-extension-tests",
    test_srcs = ["idea/plugins/gradle/tooling-extension-impl/testSources"],
    test_resources = ["idea/plugins/gradle/tooling-extension-impl/testData"],
    deps = [
        "//tools:idea.gradle-tooling-extension-impl[module]",
        "//tools:idea.gradle[module]",
        "//tools:idea.MM_idea-ui[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/xml-apis",
  jars = [
      "idea/lib/xml-apis.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ecj-4.5.2",
  jars = [
      "idea/lib/ecj-4.5.2.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/maven-server-api/lib/lucene-core-2.4.1",
  jars = [
      "idea/plugins/maven/maven-server-api/lib/lucene-core-2.4.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/xml/relaxng/lib/isorelax",
  jars = [
      "idea/xml/relaxng/lib/isorelax.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/xbean",
  jars = [
      "idea/lib/xbean.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.java-indexing-impl",
    srcs = ["idea/java/java-indexing-impl/src"],
    deps = [
        "//tools:idea.java-psi-api[module]",
        "//tools:idea.java-psi-impl[module]",
        "//tools:idea.indexing-api[module]",
        "//tools:idea.indexing-impl[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.projectModel-impl[module]",
        "//tools:idea.java-indexing-api[module]",
        "//tools:idea/lib/guava-18.0",
    ],
    exports = [
        "//tools:idea.java-psi-api",
        "//tools:idea.java-psi-impl",
        "//tools:idea.indexing-api",
        "//tools:idea.indexing-impl",
        "//tools:idea.projectModel-api",
        "//tools:idea.java-indexing-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-build-init-2.14.1",
  jars = [
      "idea/plugins/gradle/tooling-extension-api/lib/gradle-build-init-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/cli-parser-1.1",
  jars = [
      "idea/lib/cli-parser-1.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.diff-api",
    srcs = ["idea/platform/diff-api/src"],
    deps = ["//tools:idea.platform-api[module]"],
    exports = ["//tools:idea.platform-api"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.util-tests",
    test_srcs = ["idea/platform/util/testSrc"],
    deps = [
        "//tools:idea/lib/groovy-all-2.4.6[test]",
        "//tools:idea.util[module, test]",
        "//tools:idea/lib/jdom[test]",
        "//tools:idea/lib/dev/assertj-core-3.2.0[test]",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools:idea/lib/picocontainer[test]",
        "//tools:idea/lib/jna[test]",
        "//tools:idea/lib/jna-platform[test]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/velocity",
  jars = [
      "idea/lib/velocity.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/log4j",
  jars = [
      "idea/lib/log4j.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/swingx-core-1.6.2",
  jars = [
      "idea/lib/swingx-core-1.6.2.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.openapi",
    srcs = ["idea/java/openapi/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools:idea/lib/jdom",
        "//tools:idea.forms_rt[module]",
        "//tools:idea/lib/trove4j",
        "//tools:idea.extensions[module]",
        "//tools:idea.icons[module]",
        "//tools:idea/lib/nanoxml-2.2.3",
        "//tools:idea/lib/microba",
        "//tools:idea/lib/jgoodies-forms",
        "//tools:idea.xml-openapi[module]",
        "//tools:idea.platform-api[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.vcs-api[module]",
        "//tools:idea/lib/xmlrpc-2.0",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.resources-en[module]",
        "//tools:idea.java-psi-api[module]",
        "//tools:idea.java-indexing-api[module]",
        "//tools:idea.java-analysis-api[module]",
    ],
    exports = [
        "//tools:idea.util",
        "//tools:idea.extensions",
        "//tools:idea/lib/nanoxml-2.2.3",
        "//tools:idea/lib/microba",
        "//tools:idea.xml-openapi",
        "//tools:idea.platform-api",
        "//tools:idea.lang-api",
        "//tools:idea.vcs-api",
        "//tools:idea.resources-en",
        "//tools:idea.java-psi-api",
        "//tools:idea.java-indexing-api",
        "//tools:idea.java-analysis-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/asm",
  jars = [
      "idea/lib/asm.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/gradle-logging-2.14.1",
  jars = [
      "idea/plugins/gradle/lib/gradle-logging-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/maven2-server-impl/lib/maven-dependency-tree-1.2",
  jars = [
      "idea/plugins/maven/maven2-server-impl/lib/maven-dependency-tree-1.2.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/plexus-component-annotations-1.5.5",
  jars = [
      "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/plexus-component-annotations-1.5.5.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.jps-model-tests",
    test_srcs = ["idea/jps/model-impl/testSrc"],
    deps = [
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.MM_RegExpSupport[module]",
    ],
    exports = ["//tools:idea.MM_RegExpSupport"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.structuralsearch-java",
    srcs = ["idea/java/structuralsearch-java/src"],
    deps = [
        "//tools:idea.structuralsearch[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.openapi[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.duplicates-analysis[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.java-analysis-api",
    srcs = ["idea/java/java-analysis-api/src"],
    deps = [
        "//tools:idea.analysis-api[module]",
        "//tools:idea.java-psi-api[module]",
        "//tools:idea.projectModel-api[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.icons",
    srcs = ["idea/platform/icons/src"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/asm-commons",
  jars = [
      "idea/lib/asm-commons.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.java-psi-impl",
    srcs = [
        "idea/java/java-psi-impl/src",
        "idea/java/java-psi-impl/gen",
    ],
    deps = [
        "//tools:idea.java-psi-api[module]",
        "//tools:idea.core-impl[module]",
        "//tools:idea.resources-en[module]",
        "//tools:idea/lib/asm-all",
        "//tools:idea/lib/guava-18.0",
    ],
    exports = [
        "//tools:idea.java-psi-api",
        "//tools:idea.core-impl",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/cglib-2.2.2",
  jars = [
      "idea/lib/cglib-2.2.2.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.bootstrap",
    srcs = ["idea/platform/bootstrap/src"],
    deps = ["//tools:idea.util[module]"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.execution-openapi",
    srcs = ["idea/java/execution/openapi/src"],
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.resources[module]",
        "//tools:idea.xdebugger-api[module]",
    ],
    exports = ["//tools:idea.xdebugger-api"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.resources-en",
    srcs = ["idea/resources-en/src"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/fluent-hc-4.4.1",
  jars = [
      "idea/lib/fluent-hc-4.4.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/httpclient-4.4.1",
  jars = [
      "idea/lib/httpclient-4.4.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/slf4j-api-1.7.10",
  jars = [
      "idea/lib/slf4j-api-1.7.10.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/protobuf-2.5.0",
  jars = [
      "idea/lib/protobuf-2.5.0.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/xml/relaxng/lib/jing",
  jars = [
      "idea/xml/relaxng/lib/jing.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.editor-ui-ex",
    srcs = ["idea/platform/editor-ui-ex/src"],
    deps = [
        "//tools:idea.editor-ui-api[module]",
        "//tools:idea.util[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.core-impl[module]",
        "//tools:idea.indexing-impl[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.analysis-api[module]",
    ],
    exports = [
        "//tools:idea.editor-ui-api",
        "//tools:idea.util",
        "//tools:idea.annotations",
        "//tools:idea.core-impl",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-platform-base-2.14.1",
  jars = [
      "idea/plugins/gradle/tooling-extension-api/lib/gradle-platform-base-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/junit",
  jars = [
      "idea/lib/junit.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/jaxen-1.1.3",
  jars = [
      "idea/lib/jaxen-1.1.3.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.gradle-jps-plugin",
    srcs = ["idea/plugins/gradle/jps-plugin/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.jps-builders[module]",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea/plugins/gradle/lib/gradle-tooling-api-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-core-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-messaging-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-model-core-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-model-groovy-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-wrapper-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-base-services-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-base-services-groovy-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-native-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-resources-2.14.1",
        "//tools:idea/lib/ant/lib/ant-commons-net",
        "//tools:idea/lib/ant/lib/ant-jmf",
        "//tools:idea/lib/ant/lib/ant-apache-resolver",
        "//tools:idea/lib/ant/lib/ant-jai",
        "//tools:idea/lib/ant/lib/ant-apache-bsf",
        "//tools:idea/lib/ant/lib/ant-commons-logging",
        "//tools:idea/lib/ant/lib/ant-junit",
        "//tools:idea/lib/ant/lib/ant-jsch",
        "//tools:idea/lib/ant/lib/ant-apache-bcel",
        "//tools:idea/lib/ant/lib/ant",
        "//tools:idea/lib/ant/lib/ant-netrexx",
        "//tools:idea/lib/ant/lib/ant-apache-oro",
        "//tools:idea/lib/ant/lib/ant-antlr",
        "//tools:idea/lib/ant/lib/ant-jdepend",
        "//tools:idea/lib/ant/lib/ant-launcher",
        "//tools:idea/lib/ant/lib/ant-apache-regexp",
        "//tools:idea/lib/ant/lib/ant-apache-log4j",
        "//tools:idea/lib/ant/lib/ant-swing",
        "//tools:idea/lib/ant/lib/ant-javamail",
        "//tools:idea/lib/gson-2.5",
    ],
    exports = [
        "//tools:idea/lib/ant/lib/ant-commons-net",
        "//tools:idea/lib/ant/lib/ant-jmf",
        "//tools:idea/lib/ant/lib/ant-apache-resolver",
        "//tools:idea/lib/ant/lib/ant-jai",
        "//tools:idea/lib/ant/lib/ant-apache-bsf",
        "//tools:idea/lib/ant/lib/ant-commons-logging",
        "//tools:idea/lib/ant/lib/ant-junit",
        "//tools:idea/lib/ant/lib/ant-jsch",
        "//tools:idea/lib/ant/lib/ant-apache-bcel",
        "//tools:idea/lib/ant/lib/ant",
        "//tools:idea/lib/ant/lib/ant-netrexx",
        "//tools:idea/lib/ant/lib/ant-apache-oro",
        "//tools:idea/lib/ant/lib/ant-antlr",
        "//tools:idea/lib/ant/lib/ant-jdepend",
        "//tools:idea/lib/ant/lib/ant-launcher",
        "//tools:idea/lib/ant/lib/ant-apache-regexp",
        "//tools:idea/lib/ant/lib/ant-apache-log4j",
        "//tools:idea/lib/ant/lib/ant-swing",
        "//tools:idea/lib/ant/lib/ant-javamail",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/pty4j-0.6",
  jars = [
      "idea/lib/pty4j-0.6.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/coverage-instrumenter",
  jars = [
      "idea/lib/coverage-instrumenter.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.java-psi-api",
    srcs = ["idea/java/java-psi-api/src"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.projectModel-api[module]",
    ],
    exports = ["//tools:idea.core-api"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-scala-2.14.1",
  jars = [
      "idea/plugins/gradle/tooling-extension-api/lib/gradle-scala-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/netty-all-4.1.0.CR7",
  jars = [
      "idea/lib/netty-all-4.1.0.CR7.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/proxy-vole_20131209",
  jars = [
      "idea/lib/proxy-vole_20131209.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.gradle-tooling-extension-api",
    srcs = ["idea/plugins/gradle/tooling-extension-api/src"],
    deps = [
        "//tools:idea.external-system-rt[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea/lib/slf4j-api-1.7.10",
        "//tools:idea/lib/slf4j-log4j12-1.7.10",
        "//tools:idea/plugins/gradle/lib/gradle-tooling-api-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-core-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-messaging-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-wrapper-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-base-services-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-base-services-groovy-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-logging-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-native-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-process-services-2.14.1",
        "//tools:idea/plugins/gradle/lib/guava-jdk5-17.0",
        "//tools:idea/lib/groovy-all-2.4.6",
    ],
    exports = [
        "//tools:idea/lib/slf4j-api-1.7.10",
        "//tools:idea/lib/slf4j-log4j12-1.7.10",
        "//tools:idea/plugins/gradle/lib/gradle-tooling-api-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-core-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-messaging-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-wrapper-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-base-services-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-base-services-groovy-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-logging-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-native-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-process-services-2.14.1",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.ant",
    srcs = ["idea/plugins/ant/src"],
    test_srcs = ["idea/plugins/ant/tests/src"],
    resources = ["idea/plugins/ant/resources"],
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools:idea/lib/ant/lib/ant-commons-net",
        "//tools:idea/lib/ant/lib/ant-jmf",
        "//tools:idea/lib/ant/lib/ant-apache-resolver",
        "//tools:idea/lib/ant/lib/ant-jai",
        "//tools:idea/lib/ant/lib/ant-apache-bsf",
        "//tools:idea/lib/ant/lib/ant-commons-logging",
        "//tools:idea/lib/ant/lib/ant-junit",
        "//tools:idea/lib/ant/lib/ant-jsch",
        "//tools:idea/lib/ant/lib/ant-apache-bcel",
        "//tools:idea/lib/ant/lib/ant",
        "//tools:idea/lib/ant/lib/ant-netrexx",
        "//tools:idea/lib/ant/lib/ant-apache-oro",
        "//tools:idea/lib/ant/lib/ant-antlr",
        "//tools:idea/lib/ant/lib/ant-jdepend",
        "//tools:idea/lib/ant/lib/ant-launcher",
        "//tools:idea/lib/ant/lib/ant-apache-regexp",
        "//tools:idea/lib/ant/lib/ant-apache-log4j",
        "//tools:idea/lib/ant/lib/ant-swing",
        "//tools:idea/lib/ant/lib/ant-javamail",
        "//tools:idea.java-runtime[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.properties[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.xml-openapi[module]",
        "//tools:idea.util[module]",
        "//tools:idea.ant-jps-plugin[module]",
        "//tools:idea.properties-psi-api[module]",
    ],
    exports = [
        "//tools:idea/lib/ant/lib/ant-commons-net",
        "//tools:idea/lib/ant/lib/ant-jmf",
        "//tools:idea/lib/ant/lib/ant-apache-resolver",
        "//tools:idea/lib/ant/lib/ant-jai",
        "//tools:idea/lib/ant/lib/ant-apache-bsf",
        "//tools:idea/lib/ant/lib/ant-commons-logging",
        "//tools:idea/lib/ant/lib/ant-junit",
        "//tools:idea/lib/ant/lib/ant-jsch",
        "//tools:idea/lib/ant/lib/ant-apache-bcel",
        "//tools:idea/lib/ant/lib/ant",
        "//tools:idea/lib/ant/lib/ant-netrexx",
        "//tools:idea/lib/ant/lib/ant-apache-oro",
        "//tools:idea/lib/ant/lib/ant-antlr",
        "//tools:idea/lib/ant/lib/ant-jdepend",
        "//tools:idea/lib/ant/lib/ant-launcher",
        "//tools:idea/lib/ant/lib/ant-apache-regexp",
        "//tools:idea/lib/ant/lib/ant-apache-log4j",
        "//tools:idea/lib/ant/lib/ant-swing",
        "//tools:idea/lib/ant/lib/ant-javamail",
        "//tools:idea.MM_RegExpSupport",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/maven2-server-impl/lib/maven2/lib/maven-2.2.1-uber",
  jars = [
      "idea/plugins/maven/maven2-server-impl/lib/maven2/lib/maven-2.2.1-uber.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.instrumentation-util",
    srcs = ["idea/java/compiler/instrumentation-util/src"],
    deps = ["//tools:idea/lib/asm-all"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/dev/objenesis-1.0",
  jars = [
      "idea/lib/dev/objenesis-1.0.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/maven2-server-impl/lib/nexus-indexer-1.2.3",
  jars = [
      "idea/plugins/maven/maven2-server-impl/lib/nexus-indexer-1.2.3.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-plugins-2.14.1",
  jars = [
      "idea/plugins/gradle/tooling-extension-api/lib/gradle-plugins-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/jh",
  jars = [
      "idea/lib/jh.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/gradle-tooling-api-2.14.1",
  jars = [
      "idea/plugins/gradle/lib/gradle-tooling-api-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/commons-logging-1.2",
  jars = [
      "idea/lib/commons-logging-1.2.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/gradle-model-core-2.14.1",
  jars = [
      "idea/plugins/gradle/lib/gradle-model-core-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.java-indexing-api",
    srcs = ["idea/java/java-indexing-api/src"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.java-psi-api[module]",
        "//tools:idea.indexing-api[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.smRunner",
    srcs = ["idea/platform/smRunner/src"],
    test_srcs = ["idea/platform/smRunner/testSrc"],
    deps = [
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.testRunner[module]",
        "//tools:idea.xdebugger-api[module]",
        "//tools:idea/lib/serviceMessages",
        "//tools:idea.annotations[module]",
        "//tools:idea/lib/dev/easymock[test]",
        "//tools:idea/lib/dev/easymockclassextension[test]",
        "//tools:idea/lib/dev/jmock-2.5.1[test]",
        "//tools:idea/lib/dev/jmock-junit4-2.5.1[test]",
        "//tools:idea/lib/dev/jmock-legacy-2.5.1[test]",
        "//tools:idea/lib/dev/objenesis-1.0[test]",
        "//tools:idea.lang-api[module]",
    ],
    exports = [
        "//tools:idea.testRunner",
        "//tools:idea/lib/serviceMessages",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.structuralsearch",
    srcs = ["idea/platform/structuralsearch/source"],
    deps = [
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools:idea.util[module]",
        "//tools:idea/lib/jdom",
        "//tools:idea/lib/trove4j",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea/lib/groovy-all-2.4.6",
        "//tools:idea.platform-api[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.openapi[module]",
        "//tools:idea.duplicates-analysis[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.jps-builders",
    srcs = ["idea/jps/jps-builders/src"],
    test_srcs = ["idea/jps/jps-builders/testSrc"],
    exclude = [
        "idea/jps/jps-builders/src/org/jetbrains/jps/javac/OptimizedFileManager.java",
        "idea/jps/jps-builders/src/org/jetbrains/jps/javac/OptimizedFileManager17.java",
    ],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.forms_rt[module]",
        "//tools:idea.forms-compiler[module]",
        "//tools:idea.instrumentation-util[module]",
        "//tools:idea/lib/asm-all",
        "//tools:idea/lib/jdom",
        "//tools:idea/lib/nanoxml-2.2.3",
        "//tools:idea/lib/jgoodies-forms",
        "//tools:idea/lib/netty-all-4.1.0.CR7",
        "//tools:idea/lib/protobuf-2.5.0",
        "//tools:idea/jps/lib/optimizedFileManager",
        "//tools:idea.java-runtime[module]",
        "//tools:idea/lib/log4j",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea/lib/ecj-4.5.2[test]",
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.jps-serialization-tests[module, test]",
    ],
    exports = [
        "//tools:idea.util",
        "//tools:idea/lib/asm-all",
        "//tools:idea/lib/protobuf-2.5.0",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-testing-base-2.14.1",
  jars = [
      "idea/plugins/gradle/tooling-extension-api/lib/gradle-testing-base-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.annotations-common",
    srcs = ["idea/platform/annotations/common/src"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.extensions",
    srcs = ["idea/platform/extensions/src"],
    test_srcs = ["idea/platform/extensions/testSrc"],
    deps = [
        "//tools:idea/lib/xstream-1.4.8",
        "//tools:idea/lib/jdom",
        "//tools:idea/lib/picocontainer",
        "//tools:idea.util[module]",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools:idea/lib/xercesImpl[test]",
        "//tools:idea/lib/xml-apis[test]",
        "//tools:idea/lib/hamcrest-library-1.3[test]",
    ],
    exports = [
        "//tools:idea/lib/xstream-1.4.8",
        "//tools:idea/lib/jdom",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.analysis-impl",
    srcs = ["idea/platform/analysis-impl/src"],
    deps = [
        "//tools:idea.analysis-api[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea.core-impl[module]",
        "//tools:idea.util[module]",
        "//tools:idea.extensions[module]",
        "//tools:idea.resources-en[module]",
        "//tools:idea.projectModel-impl[module]",
        "//tools:idea.editor-ui-ex[module]",
        "//tools:idea.indexing-impl[module]",
    ],
    exports = [
        "//tools:idea.analysis-api",
        "//tools:idea.core-impl",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/gson-2.5",
  jars = [
      "idea/lib/gson-2.5.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.lvcs-api",
    srcs = ["idea/platform/lvcs-api/src"],
    deps = ["//tools:idea.platform-api[module]"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.external-system-rt",
    srcs = ["idea/platform/external-system-rt/src"],
    deps = ["//tools:idea.annotations[module]"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.testRunner",
    srcs = ["idea/platform/testRunner/src"],
    deps = [
        "//tools:idea.platform-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.xdebugger-api[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/jna",
  jars = [
      "idea/lib/jna.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/objenesis-1.2",
  jars = [
      "idea/lib/objenesis-1.2.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.properties-psi-impl",
    srcs = ["idea/plugins/properties/properties-psi-impl/src"],
    deps = [
        "//tools:idea.properties-psi-api[module]",
        "//tools:idea.core-impl[module]",
        "//tools:idea.editor-ui-api[module]",
        "//tools:idea.analysis-api[module]",
        "//tools:idea.xml-psi-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.indexing-impl[module]",
        "//tools:idea.structure-view-impl[module]",
        "//tools:idea.analysis-impl[module]",
    ],
    exports = ["//tools:idea.properties-psi-api"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/imgscalr-lib-4.2",
  jars = [
      "idea/lib/imgscalr-lib-4.2.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-swing",
  jars = [
      "idea/lib/ant/lib/ant-swing.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.IntelliLang-java",
    srcs = ["idea/plugins/IntelliLang/java-support"],
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea/lib/asm-all",
        "//tools:idea.platform-api[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.IntelliLang[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/jsr173_1.0_api",
  jars = [
      "idea/lib/jsr173_1.0_api.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.forms-compiler",
    srcs = ["idea/java/compiler/forms-compiler/src"],
    test_srcs = ["idea/java/compiler/forms-compiler/testSrc"],
    deps = [
        "//tools:idea/lib/jdom",
        "//tools:idea.forms_rt[module]",
        "//tools:idea/lib/asm-all",
        "//tools:idea/lib/junit[test]",
        "//tools:idea/lib/jgoodies-forms",
        "//tools:idea.instrumentation-util[module]",
    ],
    exports = ["//tools:idea.instrumentation-util"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/commons-codec-1.9",
  jars = [
      "idea/lib/commons-codec-1.9.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/jps/lib/optimizedFileManager",
  jars = [
      "idea/jps/lib/optimizedFileManager.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.annotations",
    srcs = ["idea/platform/annotations/java5/src"],
    deps = ["//tools:idea.annotations-common[module]"],
    exports = ["//tools:idea.annotations-common"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-apache-bsf",
  jars = [
      "idea/lib/ant/lib/ant-apache-bsf.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-netrexx",
  jars = [
      "idea/lib/ant/lib/ant-netrexx.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/aether-api-1.13.1",
  jars = [
      "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/aether-api-1.13.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-testing-jvm-2.14.1",
  jars = [
      "idea/plugins/gradle/tooling-extension-api/lib/gradle-testing-jvm-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.junit_rt",
    srcs = ["idea/plugins/junit_rt/src"],
    deps = [
        "//tools:idea/lib/hamcrest-core-1.3",
        "//tools:idea/lib/junit-4.12",
        "//tools:idea.java-runtime[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.lang-api",
    srcs = ["idea/platform/lang-api/src"],
    test_srcs = ["idea/platform/lang-api/testSources"],
    deps = [
        "//tools:idea.platform-api[module]",
        "//tools:idea.lvcs-api[module]",
        "//tools:idea/lib/nanoxml-2.2.3",
        "//tools:idea.indexing-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools:idea/lib/dev/easymock[test]",
        "//tools:idea/lib/dev/easymockclassextension[test]",
        "//tools:idea/lib/dev/jmock-2.5.1[test]",
        "//tools:idea/lib/dev/jmock-junit4-2.5.1[test]",
        "//tools:idea/lib/dev/jmock-legacy-2.5.1[test]",
        "//tools:idea/lib/dev/objenesis-1.0[test]",
        "//tools:idea.analysis-api[module]",
    ],
    exports = [
        "//tools:idea.platform-api",
        "//tools:idea.lvcs-api",
        "//tools:idea/lib/nanoxml-2.2.3",
        "//tools:idea.indexing-api",
        "//tools:idea.projectModel-api",
        "//tools:idea.analysis-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/jsr305",
  jars = [
      "idea/lib/jsr305.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.built-in-server-api",
    srcs = ["idea/platform/built-in-server-api/src"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea/lib/netty-all-4.1.0.CR7",
        "//tools:idea/lib/guava-18.0",
        "//tools:idea.platform-api[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/jna-3.2.7",
  jars = [
      "idea/plugins/gradle/lib/jna-3.2.7.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.projectModel-impl",
    srcs = ["idea/platform/projectModel-impl/src"],
    deps = [
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.core-impl[module]",
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea/lib/snappy-in-java-0.3.1",
    ],
    exports = [
        "//tools:idea.projectModel-api",
        "//tools:idea.jps-model-serialization",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/testng/lib/jcommander",
  jars = [
      "idea/plugins/testng/lib/jcommander.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/resolver",
  jars = [
      "idea/lib/resolver.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/xml/relaxng/lib/rngom-20051226-patched",
  jars = [
      "idea/xml/relaxng/lib/rngom-20051226-patched.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.resources",
    srcs = ["idea/resources/src"],
    deps = [
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.community-resources[module]",
        "//tools:idea.util[module]",
    ],
    exports = [
        "//tools:idea.MM_RegExpSupport",
        "//tools:idea.community-resources",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.jsp-openapi",
    srcs = ["idea/java/jsp-openapi/src"],
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.util[module]",
        "//tools:idea.jsp-base-openapi[module]",
    ],
    exports = ["//tools:idea.jsp-base-openapi"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/xml/relaxng/lib/trang-core",
  jars = [
      "idea/xml/relaxng/lib/trang-core.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/kryo-2.22",
  jars = [
      "idea/lib/kryo-2.22.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/testng/lib/testng",
  jars = [
      "idea/plugins/testng/lib/testng.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.eclipse",
    srcs = [
        "idea/plugins/eclipse/src",
        "idea/plugins/eclipse/gen",
    ],
    test_srcs = ["idea/plugins/eclipse/testSources"],
    resources = ["idea/plugins/eclipse/resources"],
    deps = [
        "//tools:idea/lib/jdom",
        "//tools:idea.openapi[module]",
        "//tools:idea.bootstrap[module]",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.compiler-openapi[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.eclipse-jps-plugin[module]",
        "//tools:idea.common-eclipse-util[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.xdebugger-api",
    srcs = ["idea/platform/xdebugger-api/src"],
    deps = ["//tools:idea.lang-api[module]"],
    exports = ["//tools:idea.lang-api"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.duplicates-analysis",
    srcs = ["idea/platform/duplicates-analysis/src"],
    deps = [
        "//tools:idea.analysis-impl[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.extensions[module]",
        "//tools:idea.util[module]",
        "//tools:idea.indexing-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.projectModel-impl[module]",
    ],
    exports = [
        "//tools:idea.analysis-impl",
        "//tools:idea.annotations",
        "//tools:idea.extensions",
        "//tools:idea.util",
        "//tools:idea.indexing-api",
        "//tools:idea.projectModel-api",
        "//tools:idea.projectModel-impl",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/jcip-annotations",
  jars = [
      "idea/lib/jcip-annotations.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-apache-oro",
  jars = [
      "idea/lib/ant/lib/ant-apache-oro.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-platform-jvm-2.14.1",
  jars = [
      "idea/plugins/gradle/tooling-extension-api/lib/gradle-platform-jvm-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.testng",
    srcs = ["idea/plugins/testng/src"],
    test_srcs = ["idea/plugins/testng/testSources"],
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.debugger-openapi[module]",
        "//tools:idea/lib/junit[test]",
        "//tools:idea.testRunner[module]",
        "//tools:idea.java-runtime[module]",
        "//tools:idea.testng_rt[module]",
        "//tools:idea.java-i18n[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.jsp-openapi[module]",
        "//tools:idea/plugins/testng/lib/testng",
        "//tools:idea/plugins/testng/lib/jcommander",
        "//tools:idea.java-indexing-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.smRunner[module]",
        "//tools:idea.typeMigration[module]",
    ],
    exports = ["//tools:idea.smRunner"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-apache-bcel",
  jars = [
      "idea/lib/ant/lib/ant-apache-bcel.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/automaton",
  jars = [
      "idea/lib/automaton.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/dev/easymockclassextension",
  jars = [
      "idea/lib/dev/easymockclassextension.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.ant-jps-plugin",
    srcs = ["idea/plugins/ant/jps-plugin/src"],
    test_srcs = ["idea/plugins/ant/jps-plugin/testSrc"],
    deps = [
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea.util[module]",
        "//tools:idea.jps-builders[module]",
        "//tools:idea.java-runtime[module]",
        "//tools:idea.jps-serialization-tests[module, test]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-antlr",
  jars = [
      "idea/lib/ant/lib/ant-antlr.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/httpmime-4.4.1",
  jars = [
      "idea/lib/httpmime-4.4.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/oromatcher",
  jars = [
      "idea/lib/oromatcher.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/jgoodies-forms",
  jars = [
      "idea/lib/jgoodies-forms.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/batik-all",
  jars = [
      "idea/lib/batik-all.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/gradle-cli-2.14.1",
  jars = [
      "idea/plugins/gradle/lib/gradle-cli-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-language-jvm-2.14.1",
  jars = [
      "idea/plugins/gradle/tooling-extension-api/lib/gradle-language-jvm-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.gradle-tests",
    test_srcs = [
        "idea/plugins/gradle/testData",
        "idea/plugins/gradle/testSources",
    ],
    deps = [
        "//tools:idea/lib/dev/easymock[test]",
        "//tools:idea/lib/dev/easymockclassextension[test]",
        "//tools:idea/lib/dev/jmock-2.5.1[test]",
        "//tools:idea/lib/dev/jmock-junit4-2.5.1[test]",
        "//tools:idea/lib/dev/jmock-legacy-2.5.1[test]",
        "//tools:idea/lib/dev/objenesis-1.0[test]",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.gradle[module, test]",
        "//tools:idea.gradle-tooling-extension-tests[module, test]",
        "//tools:idea.maven-server-api[module, test]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/eawtstub",
  jars = [
      "idea/lib/eawtstub.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.xslt-rt",
    srcs = ["idea/plugins/xpath/xslt-rt/src"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.gradle",
    srcs = ["idea/plugins/gradle/src"],
    resources = ["idea/plugins/gradle/resources"],
    deps = [
        "//tools:idea.external-system-api[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.gradle-tooling-extension-api[module]",
        "//tools:idea.gradle-tooling-extension-impl[module]",
        "//tools:idea.gradle-jps-plugin[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.compiler-openapi[module]",
        "//tools:idea.jetgroovy[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.openapi[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.MM_maven2-server-impl[module]",
        "//tools:idea.junit[module]",
        "//tools:idea/lib/swingx-core-1.6.2",
        "//tools:idea/lib/slf4j-api-1.7.10",
        "//tools:idea/lib/slf4j-log4j12-1.7.10",
        "//tools:idea/plugins/gradle/lib/gradle-tooling-api-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-core-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-messaging-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-model-core-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-model-groovy-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-wrapper-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-base-services-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-base-services-groovy-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-native-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-resources-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-cli-2.14.1",
        "//tools:idea/plugins/gradle/lib/guava-jdk5-17.0",
        "//tools:idea/plugins/gradle/lib/jsr305-1.3.9",
        "//tools:idea/plugins/gradle/lib/commons-io-1.4",
        "//tools:idea/plugins/gradle/lib/jna-3.2.7",
        "//tools:idea/plugins/gradle/lib/native-platform-0.10",
        "//tools:idea.smRunner[module]",
        "//tools:idea/lib/minlog-1.2",
        "//tools:idea/lib/kryo-2.22",
        "//tools:idea/lib/reflectasm-1.07",
        "//tools:idea/lib/objenesis-1.2",
        "//tools:idea/lib/ant/lib/ant-commons-net",
        "//tools:idea/lib/ant/lib/ant-jmf",
        "//tools:idea/lib/ant/lib/ant-apache-resolver",
        "//tools:idea/lib/ant/lib/ant-jai",
        "//tools:idea/lib/ant/lib/ant-apache-bsf",
        "//tools:idea/lib/ant/lib/ant-commons-logging",
        "//tools:idea/lib/ant/lib/ant-junit",
        "//tools:idea/lib/ant/lib/ant-jsch",
        "//tools:idea/lib/ant/lib/ant-apache-bcel",
        "//tools:idea/lib/ant/lib/ant",
        "//tools:idea/lib/ant/lib/ant-netrexx",
        "//tools:idea/lib/ant/lib/ant-apache-oro",
        "//tools:idea/lib/ant/lib/ant-antlr",
        "//tools:idea/lib/ant/lib/ant-jdepend",
        "//tools:idea/lib/ant/lib/ant-launcher",
        "//tools:idea/lib/ant/lib/ant-apache-regexp",
        "//tools:idea/lib/ant/lib/ant-apache-log4j",
        "//tools:idea/lib/ant/lib/ant-swing",
        "//tools:idea/lib/ant/lib/ant-javamail",
        "//tools:idea/lib/gson-2.5",
    ],
    exports = [
        "//tools:idea.external-system-api",
        "//tools:idea.MM_idea-ui",
        "//tools:idea.gradle-tooling-extension-api",
        "//tools:idea/lib/slf4j-api-1.7.10",
        "//tools:idea/lib/slf4j-log4j12-1.7.10",
        "//tools:idea/plugins/gradle/lib/gradle-tooling-api-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-core-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-messaging-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-model-core-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-model-groovy-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-wrapper-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-base-services-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-base-services-groovy-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-native-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-resources-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-cli-2.14.1",
        "//tools:idea/plugins/gradle/lib/guava-jdk5-17.0",
        "//tools:idea/plugins/gradle/lib/commons-io-1.4",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/gradle-messaging-2.14.1",
  jars = [
      "idea/plugins/gradle/lib/gradle-messaging-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/slf4j-log4j12-1.7.10",
  jars = [
      "idea/lib/slf4j-log4j12-1.7.10.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.groovy-psi",
    srcs = [
        "idea/plugins/groovy/groovy-psi/src",
        "idea/plugins/groovy/groovy-psi/gen",
    ],
    resources = ["idea/plugins/groovy/groovy-psi/resources"],
    deps = [
        "//tools:idea/lib/groovy-all-2.4.6",
        "//tools:idea/lib/guava-18.0",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.junit[module, test]",
        "//tools:idea.java-indexing-api[module]",
        "//tools:idea.java-psi-impl[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.core-impl[module]",
        "//tools:idea.extensions[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.editor-ui-ex[module]",
        "//tools:idea.java-analysis-impl[module]",
        "//tools:idea.java-structure-view[module]",
        "//tools:idea.properties-psi-impl[module]",
        "//tools:idea.properties-psi-api[module]",
    ],
    exports = ["//tools:idea/lib/groovy-all-2.4.6"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/trove4j",
  jars = [
      "idea/lib/trove4j.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.tests_bootstrap",
    srcs = ["idea/platform/testFramework/bootstrap/src"],
    deps = [
        "//tools:idea/lib/hamcrest-core-1.3",
        "//tools:idea/lib/junit-4.12",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/xpp3-1.1.4-min",
  jars = [
      "idea/lib/xpp3-1.1.4-min.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/gradle-base-services-groovy-2.14.1",
  jars = [
      "idea/plugins/gradle/lib/gradle-base-services-groovy-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.jps-serialization-tests",
    test_srcs = ["idea/jps/model-serialization/testSrc"],
    deps = [
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea.jps-model-tests[module, test]",
    ],
    exports = ["//tools:idea.jps-model-tests"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/maven2-server-impl/lib/plexus-utils-1.5.5",
  jars = [
      "idea/plugins/maven/maven2-server-impl/lib/plexus-utils-1.5.5.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.xml-analysis-api",
    srcs = ["idea/xml/xml-analysis-api/src"],
    deps = [
        "//tools:idea.xml-psi-api[module]",
        "//tools:idea.analysis-api[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea.extensions[module]",
        "//tools:idea.util[module]",
    ],
    exports = [
        "//tools:idea.xml-psi-api",
        "//tools:idea.analysis-api",
        "//tools:idea.annotations",
        "//tools:idea.core-api",
        "//tools:idea.extensions",
        "//tools:idea.util",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.indexing-impl",
    srcs = ["idea/platform/indexing-impl/src"],
    deps = [
        "//tools:idea.core-impl[module]",
        "//tools:idea.indexing-api[module]",
        "//tools:idea.projectModel-impl[module]",
        "//tools:idea/lib/nanoxml-2.2.3",
    ],
    exports = [
        "//tools:idea.indexing-api",
        "//tools:idea/lib/nanoxml-2.2.3",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.forms_rt",
    srcs = ["idea/platform/forms_rt/src"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.jps-model-api",
    srcs = ["idea/jps/model-api/src"],
    deps = [
        "//tools:idea.annotations[module]",
        "//tools:idea.util-rt[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/hamcrest-core-1.3",
  jars = [
      "idea/lib/hamcrest-core-1.3.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-jmf",
  jars = [
      "idea/lib/ant/lib/ant-jmf.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.external-system-api",
    srcs = ["idea/platform/external-system-api/src"],
    resources = ["idea/platform/external-system-api/resources"],
    deps = [
        "//tools:idea.annotations[module]",
        "//tools:idea.util[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.platform-api[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.compiler-openapi[module]",
        "//tools:idea.external-system-rt[module]",
    ],
    exports = [
        "//tools:idea.annotations",
        "//tools:idea.external-system-rt",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.community-resources",
    resources = ["idea/community-resources/src"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.gradle-tooling-extension-impl",
    srcs = ["idea/plugins/gradle/tooling-extension-impl/src"],
    deps = [
        "//tools:idea.annotations[module]",
        "//tools:idea.external-system-rt[module]",
        "//tools:idea.gradle-tooling-extension-api[module]",
        "//tools:idea/plugins/gradle/lib/gradle-tooling-api-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-core-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-model-core-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-model-groovy-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-messaging-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-wrapper-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-base-services-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-base-services-groovy-2.14.1",
        "//tools:idea/plugins/gradle/lib/gradle-native-2.14.1",
        "//tools:idea/plugins/gradle/lib/guava-jdk5-17.0",
        "//tools:idea/lib/groovy-all-2.4.6",
        "//tools:idea/lib/gson-2.5",
        "//tools:idea/plugins/gradle/tooling-extension-api/lib/gradle-build-init-2.14.1",
        "//tools:idea/plugins/gradle/tooling-extension-api/lib/gradle-ide-2.14.1",
        "//tools:idea/plugins/gradle/tooling-extension-api/lib/gradle-language-java-2.14.1",
        "//tools:idea/plugins/gradle/tooling-extension-api/lib/gradle-language-jvm-2.14.1",
        "//tools:idea/plugins/gradle/tooling-extension-api/lib/gradle-platform-base-2.14.1",
        "//tools:idea/plugins/gradle/tooling-extension-api/lib/gradle-platform-jvm-2.14.1",
        "//tools:idea/plugins/gradle/tooling-extension-api/lib/gradle-plugins-2.14.1",
        "//tools:idea/plugins/gradle/tooling-extension-api/lib/gradle-testing-base-2.14.1",
        "//tools:idea/plugins/gradle/tooling-extension-api/lib/gradle-testing-jvm-2.14.1",
        "//tools:idea/plugins/gradle/tooling-extension-api/lib/gradle-scala-2.14.1",
        "//tools:idea/plugins/gradle/tooling-extension-api/lib/gradle-language-scala-2.14.1",
        "//tools:idea/plugins/gradle/tooling-extension-impl/lib/gradle-reporting-2.14.1",
        "//tools:idea/plugins/gradle/tooling-extension-impl/lib/gradle-ear-2.14.1",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/jdom",
  jars = [
      "idea/lib/jdom.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/maven2-server-impl/lib/archetype-common-2.0-alpha-4-SNAPSHOT",
  jars = [
      "idea/plugins/maven/maven2-server-impl/lib/archetype-common-2.0-alpha-4-SNAPSHOT.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant",
  jars = [
      "idea/lib/ant/lib/ant.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/minlog-1.2",
  jars = [
      "idea/lib/minlog-1.2.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/maven2-server-impl/lib/maven2/boot/classworlds-1.1",
  jars = [
      "idea/plugins/maven/maven2-server-impl/lib/maven2/boot/classworlds-1.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/lib/plexus-archiver-2.4.4",
  jars = [
      "idea/plugins/maven/lib/plexus-archiver-2.4.4.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.IntelliLang-xml",
    srcs = ["idea/plugins/IntelliLang/xml-support"],
    deps = [
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea/lib/asm-all",
        "//tools:idea.xpath[module]",
        "//tools:idea.platform-api[module]",
        "//tools:idea/lib/jaxen-1.1.3",
        "//tools:idea.IntelliLang[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-language-scala-2.14.1",
  jars = [
      "idea/plugins/gradle/tooling-extension-api/lib/gradle-language-scala-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.maven-server-api",
    srcs = ["idea/plugins/maven/maven-server-api/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea/lib/jdom",
        "//tools:idea/plugins/maven/maven-server-api/lib/lucene-core-2.4.1",
    ],
    exports = [
        "//tools:idea.util",
        "//tools:idea/lib/jdom",
        "//tools:idea/plugins/maven/maven-server-api/lib/lucene-core-2.4.1",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/artifact-resolver-m31/lib/eclipse-aether/aether-api-0.9.0.M2",
  jars = [
      "idea/plugins/maven/artifact-resolver-m31/lib/eclipse-aether/aether-api-0.9.0.M2.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/dev/easymock",
  jars = [
      "idea/lib/dev/easymock.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.properties",
    srcs = ["idea/plugins/properties/src"],
    test_srcs = ["idea/plugins/properties/testSrc"],
    deps = [
        "//tools:idea.boot[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.platform-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.resources[module, test]",
        "//tools:idea.properties-psi-api[module]",
        "//tools:idea.properties-psi-impl[module]",
        "//tools:idea.MM_idea-ui[module]",
    ],
    exports = [
        "//tools:idea.properties-psi-api",
        "//tools:idea.properties-psi-impl",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-ide-2.14.1",
  jars = [
      "idea/plugins/gradle/tooling-extension-api/lib/gradle-ide-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/guava-jdk5-17.0",
  jars = [
      "idea/plugins/gradle/lib/guava-jdk5-17.0.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/maven2-server-impl/lib/commons-beanutils",
  jars = [
      "idea/plugins/maven/maven2-server-impl/lib/commons-beanutils.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.java-runtime",
    srcs = ["idea/java/java-runtime/src"],
    deps = [
        "//tools:idea/lib/junit",
        "//tools:idea/lib/hamcrest-core-1.3",
        "//tools:idea/lib/junit-4.12",
        "//tools:idea/lib/ant/lib/ant-commons-net",
        "//tools:idea/lib/ant/lib/ant-jmf",
        "//tools:idea/lib/ant/lib/ant-apache-resolver",
        "//tools:idea/lib/ant/lib/ant-jai",
        "//tools:idea/lib/ant/lib/ant-apache-bsf",
        "//tools:idea/lib/ant/lib/ant-commons-logging",
        "//tools:idea/lib/ant/lib/ant-junit",
        "//tools:idea/lib/ant/lib/ant-jsch",
        "//tools:idea/lib/ant/lib/ant-apache-bcel",
        "//tools:idea/lib/ant/lib/ant",
        "//tools:idea/lib/ant/lib/ant-netrexx",
        "//tools:idea/lib/ant/lib/ant-apache-oro",
        "//tools:idea/lib/ant/lib/ant-antlr",
        "//tools:idea/lib/ant/lib/ant-jdepend",
        "//tools:idea/lib/ant/lib/ant-launcher",
        "//tools:idea/lib/ant/lib/ant-apache-regexp",
        "//tools:idea/lib/ant/lib/ant-apache-log4j",
        "//tools:idea/lib/ant/lib/ant-swing",
        "//tools:idea/lib/ant/lib/ant-javamail",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-junit",
  jars = [
      "idea/lib/ant/lib/ant-junit.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/groovy/testdata/griffon/griffon-cli-1.1.0",
  jars = [
      "idea/plugins/groovy/testdata/griffon/griffon-cli-1.1.0.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.compiler-openapi",
    srcs = ["idea/java/compiler/openapi/src"],
    deps = ["//tools:idea.openapi[module]"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.java-analysis-impl",
    srcs = [
        "idea/java/java-analysis-impl/src",
        "idea/plugins/InspectionGadgets/InspectionGadgetsAnalysis/src",
    ],
    deps = [
        "//tools:idea.analysis-impl[module]",
        "//tools:idea.java-indexing-impl[module]",
        "//tools:idea.java-psi-impl[module]",
        "//tools:idea.projectModel-impl[module]",
        "//tools:idea.java-analysis-api[module]",
        "//tools:idea.resources-en[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea/lib/asm-all",
    ],
    exports = [
        "//tools:idea.analysis-impl",
        "//tools:idea.java-indexing-impl",
        "//tools:idea.java-psi-impl",
        "//tools:idea.projectModel-impl",
        "//tools:idea.java-analysis-api",
        "//tools:idea/lib/asm-all",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/microba",
  jars = [
      "idea/lib/microba.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/coverage-agent",
  jars = [
      "idea/lib/coverage-agent.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-apache-log4j",
  jars = [
      "idea/lib/ant/lib/ant-apache-log4j.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.vcs-api",
    srcs = ["idea/platform/vcs-api/src"],
    deps = [
        "//tools:idea.platform-api[module]",
        "//tools:idea/lib/microba",
        "//tools:idea.vcs-api-core[module]",
        "//tools:idea.diff-api[module]",
    ],
    exports = [
        "//tools:idea.platform-api",
        "//tools:idea.vcs-api-core",
        "//tools:idea.diff-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.projectModel-api",
    srcs = ["idea/platform/projectModel-api/src"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.jps-model-api[module]",
    ],
    exports = [
        "//tools:idea.core-api",
        "//tools:idea.jps-model-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/httpcore-4.4.1",
  jars = [
      "idea/lib/httpcore-4.4.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.xml-psi-api",
    srcs = ["idea/xml/xml-psi-api/src"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.analysis-api[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.maven-jps-plugin",
    srcs = ["idea/plugins/maven/jps-plugin/src"],
    test_srcs = ["idea/plugins/maven/jps-plugin/testSrc"],
    deps = [
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.util[module]",
        "//tools:idea.jps-builders[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea/plugins/maven/lib/plexus-utils-2.0.6",
        "//tools:idea.jps-serialization-tests[module, test]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/nanoxml-2.2.3",
  jars = [
      "idea/lib/nanoxml-2.2.3.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-core-3.0.5",
  jars = [
      "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-core-3.0.5.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/maven2-server-impl/lib/activation-1.1",
  jars = [
      "idea/plugins/maven/maven2-server-impl/lib/activation-1.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.copyright",
    srcs = ["idea/plugins/copyright/src"],
    deps = [
        "//tools:idea.jsp-openapi[module]",
        "//tools:idea.xml-openapi[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.openapi[module]",
        "//tools:idea/lib/velocity",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/lib/wadl-core",
  jars = [
      "idea/plugins/maven/lib/wadl-core.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.jsp-spi",
    srcs = ["idea/java/jsp-spi/src"],
    deps = [
        "//tools:idea.jsp-openapi[module]",
        "//tools:idea.openapi[module]",
        "//tools:idea.jsp-base-openapi[module]",
    ],
    exports = [
        "//tools:idea.jsp-openapi",
        "//tools:idea.openapi",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.junit",
    srcs = ["idea/plugins/junit/src"],
    test_srcs = ["idea/plugins/junit/test"],
    deps = [
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.openapi[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.java-runtime[module]",
        "//tools:idea.testRunner[module]",
        "//tools:idea/lib/junit",
        "//tools:idea/lib/hamcrest-core-1.3",
        "//tools:idea/lib/junit-4.12",
        "//tools:idea.junit_rt[module]",
        "//tools:idea.compiler-openapi[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.java-indexing-api[module]",
        "//tools:idea.smRunner[module]",
    ],
    exports = ["//tools:idea.smRunner"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-commons-net",
  jars = [
      "idea/lib/ant/lib/ant-commons-net.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.platform-api",
    srcs = ["idea/platform/platform-api/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.extensions[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea/lib/picocontainer",
        "//tools:idea.bootstrap[module]",
        "//tools:idea/lib/jgoodies-forms",
        "//tools:idea.forms_rt[module]",
        "//tools:idea/lib/commons-codec-1.9",
        "//tools:idea.platform-resources-en[module]",
        "//tools:idea/lib/oromatcher",
        "//tools:idea.icons[module]",
        "//tools:idea/lib/automaton",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea/lib/netty-all-4.1.0.CR7",
        "//tools:idea/lib/proxy-vole_20131209",
        "//tools:idea.analysis-api[module]",
        "//tools:idea.editor-ui-api[module]",
        "//tools:idea/lib/pty4j-0.6",
        "//tools:idea/lib/guava-18.0",
        "//tools:idea/lib/httpcore-4.4.1",
        "//tools:idea/lib/httpmime-4.4.1",
        "//tools:idea/lib/httpclient-4.4.1",
        "//tools:idea/lib/fluent-hc-4.4.1",
        "//tools:idea/lib/jna",
        "//tools:idea/lib/jna-platform",
    ],
    exports = [
        "//tools:idea.util",
        "//tools:idea.extensions",
        "//tools:idea.core-api",
        "//tools:idea/lib/picocontainer",
        "//tools:idea/lib/jgoodies-forms",
        "//tools:idea.forms_rt",
        "//tools:idea.platform-resources-en",
        "//tools:idea.projectModel-api",
        "//tools:idea.analysis-api",
        "//tools:idea.editor-ui-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/reflectasm-1.07",
  jars = [
      "idea/lib/reflectasm-1.07.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.groovy-jps-plugin",
    srcs = ["idea/plugins/groovy/jps-plugin/src"],
    deps = [
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-builders[module]",
        "//tools:idea.groovy-rt-constants[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea/plugins/groovy/lib/groovy-eclipse-batch-2.3.4-01",
        "//tools:idea.instrumentation-util[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/gradle-model-groovy-2.14.1",
  jars = [
      "idea/plugins/gradle/lib/gradle-model-groovy-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.jps-model-serialization",
    srcs = ["idea/jps/model-serialization/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.jps-model-api[module]",
        "//tools:idea/lib/jdom",
    ],
    exports = ["//tools:idea/lib/jdom"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.IntelliLang",
    srcs = ["idea/plugins/IntelliLang/src"],
    deps = [
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.platform-api[module]",
        "//tools:idea/lib/jaxen-1.1.3",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/maven2-server-impl/lib/mercury-artifact-1.0-alpha-6",
  jars = [
      "idea/plugins/maven/maven2-server-impl/lib/mercury-artifact-1.0-alpha-6.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.jetgroovy",
    srcs = ["idea/plugins/groovy/src"],
    test_srcs = ["idea/plugins/groovy/test"],
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.debugger-openapi[module]",
        "//tools:idea.groovy_rt[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.properties[module]",
        "//tools:idea.compiler-openapi[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools:idea/lib/groovy-all-2.4.6",
        "//tools:idea.java-impl[module]",
        "//tools:idea.copyright[module]",
        "//tools:idea.IntelliLang[module]",
        "//tools:idea.ant[module]",
        "//tools:idea/lib/guava-18.0",
        "//tools:idea.IntelliLang-java[module]",
        "//tools:idea.IntelliLang-xml[module, test]",
        "//tools:idea.jps-builders[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.junit[module, test]",
        "//tools:idea.java-indexing-api[module]",
        "//tools:idea.groovy-jps-plugin[module]",
        "//tools:idea.ByteCodeViewer[module]",
        "//tools:idea.properties-psi-api[module]",
        "//tools:idea.groovy-psi[module]",
        "//tools:idea.external-system-api[module]",
    ],
    exports = [
        "//tools:idea/lib/groovy-all-2.4.6",
        "//tools:idea.groovy-psi",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/miglayout-swing",
  jars = [
      "idea/lib/miglayout-swing.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.debugger-openapi",
    srcs = ["idea/java/debugger/openapi/src"],
    deps = [
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.jsp-openapi[module]",
        "//tools:idea.java-psi-api[module]",
        "//tools:idea.resources-en[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.maven-artifact-resolver-common",
    srcs = ["idea/plugins/maven/artifact-resolver/common/src"],
    deps = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-artifact-3.0.5"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.MM_RegExpSupport",
    srcs = [
        "idea/RegExpSupport/src",
        "idea/RegExpSupport/gen",
        "idea/xml/xml-analysis-impl/src",
        "idea/xml/xml-psi-impl/src",
        "idea/xml/xml-psi-impl/gen",
        "idea/platform/lvcs-impl/src",
        "idea/platform/vcs-impl/src",
        "idea/xml/impl/src",
        "idea/spellchecker/src",
        "idea/xml/relaxng/src",
        "idea/xml/dom-openapi/src",
        "idea/json/src",
        "idea/json/gen",
        "idea/images/src",
        "idea/platform/xdebugger-impl/src",
        "idea/xml/dom-impl/src",
        "idea/platform/lang-impl/src",
        "idea/platform/lang-impl/gen",
        "idea/platform/diff-impl/src",
        "idea/platform/configuration-store-impl/src",
        "idea/platform/platform-impl/src",
        "idea/platform/built-in-server/src",
        "idea/platform/testFramework/src",
        "idea/xml/xml-structure-view-impl/src",
    ],
    test_srcs = [
        "idea/RegExpSupport/test",
        "idea/platform/vcs-impl/testSrc",
        "idea/spellchecker/testSrc",
        "idea/xml/relaxng/test",
        "idea/platform/xdebugger-impl/testSrc",
        "idea/platform/testFramework/testSrc",
    ],
    resources = [
        "idea/xml/xml-analysis-impl/resources",
        "idea/xml/xml-psi-impl/resources",
        "idea/xml/impl/resources",
        "idea/platform/platform-resources/src",
        "idea/json/resources",
    ],
    deps = [
        "//tools:idea.lang-api[module]",
        "//tools:idea/lib/hamcrest-core-1.3",
        "//tools:idea/lib/junit-4.12",
        "//tools:idea/lib/jaxen-1.1.3",
        "//tools:idea.xml-psi-api[module]",
        "//tools:idea.analysis-impl[module]",
        "//tools:idea.core-impl[module]",
        "//tools:idea.xml-analysis-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea.projectModel-impl[module]",
        "//tools:idea.indexing-impl[module]",
        "//tools:idea/lib/xercesImpl",
        "//tools:idea/lib/xml-apis",
        "//tools:idea/lib/jsr173_1.0_api",
        "//tools:idea/lib/xbean",
        "//tools:idea/lib/resolver",
        "//tools:idea/lib/picocontainer",
        "//tools:idea.vcs-api[module]",
        "//tools:idea.lvcs-api[module]",
        "//tools:idea/lib/jcip-annotations",
        "//tools:idea.diff-api[module]",
        "//tools:idea.platform-api[module]",
        "//tools:idea/lib/commons-codec-1.9",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea/lib/guava-18.0",
        "//tools:idea/lib/gson-2.5",
        "//tools:idea.xml-openapi[module]",
        "//tools:idea.icons[module]",
        "//tools:idea/lib/swingx-core-1.6.2",
        "//tools:idea/lib/netty-all-4.1.0.CR7",
        "//tools:idea.xdebugger-api[module]",
        "//tools:idea.built-in-server-api[module]",
        "//tools:idea/xml/relaxng/lib/rngom-20051226-patched",
        "//tools:idea/xml/relaxng/lib/isorelax",
        "//tools:idea/xml/relaxng/lib/trang-core",
        "//tools:idea/xml/relaxng/lib/jing",
        "//tools:idea.extensions[module]",
        "//tools:idea.util[module]",
        "//tools:idea/lib/sanselan-0.98-snapshot",
        "//tools:idea/lib/asm",
        "//tools:idea/lib/asm-commons",
        "//tools:idea/lib/cglib-2.2.2",
        "//tools:idea.boot[module]",
        "//tools:idea/lib/oromatcher",
        "//tools:idea/lib/velocity",
        "//tools:idea.usageView[module]",
        "//tools:idea/lib/xpp3-1.1.4-min",
        "//tools:idea/lib/cli-parser-1.1",
        "//tools:idea.indexing-api[module]",
        "//tools:idea/lib/snappy-in-java-0.3.1",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.structure-view-impl[module]",
        "//tools:idea/lib/commons-logging-1.2",
        "//tools:idea.vcs-api-core[module]",
        "//tools:idea.bootstrap[module]",
        "//tools:idea/lib/eawtstub",
        "//tools:idea/lib/log4j",
        "//tools:idea/lib/jh",
        "//tools:idea/lib/jna",
        "//tools:idea/lib/jna-platform",
        "//tools:idea/lib/winp-1.23",
        "//tools:idea/lib/miglayout-swing",
        "//tools:idea/lib/jayatana-1.2.4",
        "//tools:idea.editor-ui-ex[module]",
        "//tools:idea/lib/httpcore-4.4.1",
        "//tools:idea/lib/httpmime-4.4.1",
        "//tools:idea/lib/httpclient-4.4.1",
        "//tools:idea/lib/fluent-hc-4.4.1",
        "//tools:idea/lib/imgscalr-lib-4.2",
        "//tools:idea/lib/slf4j-api-1.7.10",
        "//tools:idea/lib/slf4j-log4j12-1.7.10",
        "//tools/analytics-library:analytics-tracker[module]",
        "//tools/analytics-library:analytics-shared[module]",
        "//tools/analytics-library:analytics-publisher[module]",
        "//tools/base/common:common[module]",
        "//tools/analytics-library:analytics-protos[module]",
        "//tools:idea/lib/xmlrpc-2.0",
        "//tools:idea.tests_bootstrap[module]",
        "//tools:idea.resources-en[module]",
        "//tools:idea/lib/dev/easymock[test]",
        "//tools:idea/lib/dev/easymockclassextension[test]",
        "//tools:idea/lib/dev/jmock-2.5.1[test]",
        "//tools:idea/lib/dev/jmock-junit4-2.5.1[test]",
        "//tools:idea/lib/dev/jmock-legacy-2.5.1[test]",
        "//tools:idea/lib/dev/objenesis-1.0[test]",
        "//tools:idea.java-runtime[module]",
        "//tools:idea/lib/groovy-all-2.4.6",
        "//tools:idea/lib/dev/assertj-core-3.2.0[test]",
        "//tools:idea.xml-structure-view-api[module]",
    ],
    exports = [
        "//tools:idea.xml-analysis-api",
        "//tools:idea.xml-psi-api",
        "//tools:idea.vcs-api",
        "//tools:idea.xml-openapi",
        "//tools:idea/lib/cglib-2.2.2",
        "//tools:idea.lang-api",
        "//tools:idea.usageView",
        "//tools:idea/lib/cli-parser-1.1",
        "//tools:idea.indexing-impl",
        "//tools:idea.projectModel-impl",
        "//tools:idea.analysis-impl",
        "//tools:idea.structure-view-impl",
        "//tools:idea.diff-api",
        "//tools:idea.platform-api",
        "//tools:idea/lib/commons-codec-1.9",
        "//tools:idea.lvcs-api",
        "//tools:idea.core-impl",
        "//tools:idea/lib/miglayout-swing",
        "//tools:idea/lib/netty-all-4.1.0.CR7",
        "//tools:idea.editor-ui-ex",
        "//tools:idea.built-in-server-api",
        "//tools:idea/lib/hamcrest-core-1.3",
        "//tools:idea/lib/junit-4.12",
        "//tools:idea/lib/log4j",
        "//tools:idea/lib/dev/easymock",
        "//tools:idea/lib/dev/easymockclassextension",
        "//tools:idea/lib/dev/jmock-2.5.1",
        "//tools:idea/lib/dev/jmock-junit4-2.5.1",
        "//tools:idea/lib/dev/jmock-legacy-2.5.1",
        "//tools:idea/lib/dev/objenesis-1.0",
        "//tools:idea.java-runtime",
        "//tools:idea/lib/groovy-all-2.4.6",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.jsp-base-openapi",
    srcs = ["idea/java/jsp-base-openapi/src"],
    deps = [
        "//tools:idea.platform-api[module]",
        "//tools:idea.xml-openapi[module]",
        "//tools:idea.lang-api[module]",
    ],
    exports = ["//tools:idea.xml-openapi"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-jdepend",
  jars = [
      "idea/lib/ant/lib/ant-jdepend.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.xml-structure-view-api",
    srcs = ["idea/xml/xml-structure-view-api/src"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.xml-psi-api[module]",
        "//tools:idea.editor-ui-api[module]",
    ],
    exports = ["//tools:idea.xml-psi-api"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-commons-logging",
  jars = [
      "idea/lib/ant/lib/ant-commons-logging.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/jna-platform",
  jars = [
      "idea/lib/jna-platform.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.properties-psi-api",
    srcs = [
        "idea/plugins/properties/properties-psi-api/src",
        "idea/plugins/properties/properties-psi-api/gen",
    ],
    resources = ["idea/plugins/properties/properties-psi-api/resources"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.editor-ui-api[module]",
        "//tools:idea.analysis-api[module]",
        "//tools:idea.indexing-api[module]",
        "//tools:idea.xml-psi-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.lang-api[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/nekohtml-1.9.14",
  jars = [
      "idea/lib/nekohtml-1.9.14.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.core-api",
    srcs = ["idea/platform/core-api/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.extensions[module]",
        "//tools:idea/lib/picocontainer",
        "//tools:idea.platform-resources-en[module]",
        "//tools:idea/lib/automaton",
        "//tools:idea/lib/asm",
        "//tools:idea/lib/asm-commons",
        "//tools:idea/lib/cglib-2.2.2",
    ],
    exports = [
        "//tools:idea.util",
        "//tools:idea.extensions",
        "//tools:idea/lib/picocontainer",
        "//tools:idea.platform-resources-en",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/gradle-native-2.14.1",
  jars = [
      "idea/plugins/gradle/lib/gradle-native-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.usageView",
    srcs = ["idea/platform/usageView/src"],
    deps = [
        "//tools:idea.lang-api[module]",
        "//tools:idea.core-impl[module]",
        "//tools:idea.editor-ui-ex[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/groovy-all-2.4.6",
  jars = [
      "idea/lib/groovy-all-2.4.6.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.common-eclipse-util",
    srcs = ["idea/plugins/eclipse/common-eclipse-util/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea/lib/jdom",
        "//tools:idea.jps-model-serialization[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/winp-1.23",
  jars = [
      "idea/lib/winp-1.23.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/jsr166e",
  jars = [
      "idea/lib/jsr166e.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/tooling-extension-impl/lib/gradle-reporting-2.14.1",
  jars = [
      "idea/plugins/gradle/tooling-extension-impl/lib/gradle-reporting-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.eclipse-jps-plugin",
    srcs = ["idea/plugins/eclipse/jps-plugin/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea.common-eclipse-util[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-javamail",
  jars = [
      "idea/lib/ant/lib/ant-javamail.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.ByteCodeViewer",
    srcs = ["idea/plugins/ByteCodeViewer/src"],
    deps = [
        "//tools:idea/lib/asm-all",
        "//tools:idea.util[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea.platform-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.java-psi-api[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.openapi[module]",
        "//tools:idea.MM_idea-ui[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/ant/lib/ant-launcher",
  jars = [
      "idea/lib/ant/lib/ant-launcher.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/asm-all",
  jars = [
      "idea/lib/asm-all.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/dev/assertj-core-3.2.0",
  jars = [
      "idea/lib/dev/assertj-core-3.2.0.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.util",
    srcs = ["idea/platform/util/src"],
    resources = ["idea/platform/util/resources"],
    deps = [
        "//tools:idea/lib/eawtstub",
        "//tools:idea/lib/jdom",
        "//tools:idea/lib/log4j",
        "//tools:idea/lib/trove4j",
        "//tools:idea/lib/picocontainer",
        "//tools:idea.platform-resources-en[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.util-rt[module]",
        "//tools:idea/lib/jna",
        "//tools:idea/lib/jna-platform",
        "//tools:idea/lib/oromatcher",
        "//tools:idea/lib/jsr166e",
        "//tools:idea/lib/snappy-in-java-0.3.1",
        "//tools:idea/lib/imgscalr-lib-4.2",
        "//tools:idea/lib/batik-all",
        "//tools:idea/lib/xmlgraphics-commons-1.5",
        "//tools:idea/lib/xml-apis-ext",
    ],
    exports = [
        "//tools:idea/lib/trove4j",
        "//tools:idea.annotations",
        "//tools:idea.util-rt",
        "//tools:idea/lib/jsr166e",
        "//tools:idea/lib/snappy-in-java-0.3.1",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/serviceMessages",
  jars = [
      "idea/lib/serviceMessages.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.java-impl",
    srcs = [
        "idea/java/java-impl/src",
        "idea/java/java-impl/gen",
        "idea/plugins/InspectionGadgets/src",
        "idea/plugins/IntentionPowerPak/src",
        "idea/plugins/generate-tostring/src",
    ],
    resources = ["idea/plugins/generate-tostring/resources"],
    deps = [
        "//tools:idea.boot[module]",
        "//tools:idea.util[module]",
        "//tools:idea.openapi[module]",
        "//tools:idea/lib/trove4j",
        "//tools:idea/lib/oromatcher",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.java-runtime[module]",
        "//tools:idea.compiler-openapi[module]",
        "//tools:idea.jsp-openapi[module]",
        "//tools:idea.jsp-spi[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools:idea/lib/asm",
        "//tools:idea/lib/asm-commons",
        "//tools:idea.icons[module]",
        "//tools:idea/lib/jcip-annotations",
        "//tools:idea/lib/groovy-all-2.4.6",
        "//tools:idea.java-psi-impl[module]",
        "//tools:idea.java-indexing-impl[module]",
        "//tools:idea.java-indexing-api[module]",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.java-analysis-impl[module]",
        "//tools:idea.external-system-api[module]",
        "//tools:idea/lib/asm-all",
        "//tools:idea/lib/guava-18.0",
        "//tools:idea/lib/xercesImpl",
        "//tools:idea/lib/xml-apis",
        "//tools:idea/lib/velocity",
        "//tools:idea.java-structure-view[module]",
        "//tools:idea/lib/nekohtml-1.9.14",
    ],
    exports = [
        "//tools:idea.MM_RegExpSupport",
        "//tools:idea.java-psi-impl",
        "//tools:idea.java-indexing-impl",
        "//tools:idea.java-analysis-impl",
        "//tools:idea.java-structure-view",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.java-i18n",
    srcs = ["idea/plugins/java-i18n/src"],
    test_srcs = ["idea/plugins/java-i18n/testSrc"],
    resources = ["idea/plugins/java-i18n/resources"],
    deps = [
        "//tools:idea.lang-api[module]",
        "//tools:idea.openapi[module]",
        "//tools:idea.properties[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.jsp-base-openapi[module]",
        "//tools:idea.jsp-openapi[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.properties-psi-api[module]",
        "//tools:idea.properties-psi-impl[module]",
    ],
    exports = [
        "//tools:idea.properties",
        "//tools:idea.java-impl",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.analysis-api",
    srcs = ["idea/platform/analysis-api/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea/lib/jdom",
        "//tools:idea.extensions[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.editor-ui-api[module]",
    ],
    exports = ["//tools:idea.editor-ui-api"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/native-platform-0.10",
  jars = [
      "idea/plugins/gradle/lib/native-platform-0.10.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-artifact-3.0.5",
  jars = [
      "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-artifact-3.0.5.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.maven-artifact-resolver-m3",
    srcs = ["idea/plugins/maven/artifact-resolver-m3/src"],
    deps = [
        "//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/aether-api-1.13.1",
        "//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-core-3.0.5",
        "//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-artifact-3.0.5",
        "//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/plexus-component-annotations-1.5.5",
        "//tools:idea.maven-artifact-resolver-common[module]",
    ],
    exports = ["//tools:idea.maven-artifact-resolver-common"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.maven-artifact-resolver-m2",
    srcs = ["idea/plugins/maven/artifact-resolver-m2/src"],
    deps = [
        "//tools:idea/plugins/maven/maven2-server-impl/lib/maven2/lib/maven-2.2.1-uber",
        "//tools:idea.maven-artifact-resolver-common[module]",
    ],
    exports = ["//tools:idea.maven-artifact-resolver-common"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.xpath",
    srcs = [
        "idea/plugins/xpath/xpath-lang/src",
        "idea/plugins/xpath/xpath-view/src",
    ],
    test_srcs = ["idea/plugins/xpath/xpath-lang/test"],
    deps = [
        "//tools:idea.xml-openapi[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.xslt-rt[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea/lib/jaxen-1.1.3",
        "//tools:idea.resources-en[module]",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools:idea/lib/groovy-all-2.4.6[test]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.editor-ui-api",
    srcs = ["idea/platform/editor-ui-api/src"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.util[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.indexing-api[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.groovy-rt-constants",
    srcs = ["idea/plugins/groovy/rt-constants/src"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/lib/gradle-core-2.14.1",
  jars = [
      "idea/plugins/gradle/lib/gradle-core-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/groovy/lib/groovy-eclipse-batch-2.3.4-01",
  jars = [
      "idea/plugins/groovy/lib/groovy-eclipse-batch-2.3.4-01.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.core-impl",
    srcs = ["idea/platform/core-impl/src"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea/lib/picocontainer",
        "//tools:idea.boot[module]",
        "//tools:idea/lib/guava-18.0",
    ],
    exports = ["//tools:idea.core-api"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-language-java-2.14.1",
  jars = [
      "idea/plugins/gradle/tooling-extension-api/lib/gradle-language-java-2.14.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "idea/lib/xstream-1.4.8",
  jars = [
      "idea/lib/xstream-1.4.8.jar",
    ],
  visibility = ["//visibility:public"],
)
