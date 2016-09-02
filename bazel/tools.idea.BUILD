# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "idea.annotations-common",
    srcs = ["idea/platform/annotations/common/src"],
    javacopts = ["-extra_checks:off"],
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

iml_module(
    name = "idea.external-system-rt",
    srcs = ["idea/platform/external-system-rt/src"],
    deps = ["//tools:idea.annotations[module]"],
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

iml_module(
    name = "idea.boot",
    srcs = ["idea/platform/boot/src"],
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
    name = "idea.forms_rt",
    srcs = ["idea/platform/forms_rt/src"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.groovy-rt-constants",
    srcs = ["idea/plugins/groovy/rt-constants/src"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.icons",
    srcs = ["idea/platform/icons/src"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.instrumentation-util",
    srcs = ["idea/java/compiler/instrumentation-util/src"],
    deps = ["//tools/idea/.idea/libraries:asm5"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.forms-compiler",
    srcs = ["idea/java/compiler/forms-compiler/src"],
    test_srcs = ["idea/java/compiler/forms-compiler/testSrc"],
    deps = [
        "//tools/idea/.idea/libraries:JDOM",
        "//tools:idea.forms_rt[module]",
        "//tools/idea/.idea/libraries:asm5",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools:idea.instrumentation-util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
    exports = ["//tools:idea.instrumentation-util"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.java-runtime",
    srcs = ["idea/java/java-runtime/src"],
    deps = [
        "//tools/idea/.idea/libraries:JUnit3",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/.idea/libraries:Ant",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.junit_rt",
    srcs = ["idea/plugins/junit_rt/src"],
    deps = [
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools:idea.java-runtime[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.testng_rt",
    srcs = ["idea/plugins/testng_rt/src"],
    deps = [
        "//tools/idea/.idea/libraries:TestNG",
        "//tools:idea.java-runtime[module]",
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

iml_module(
    name = "idea.maven-artifact-resolver-common",
    srcs = ["idea/plugins/maven/artifact-resolver/common/src"],
    deps = ["//tools:maven-artifact-resolver-common"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.maven-artifact-resolver-m2",
    srcs = ["idea/plugins/maven/artifact-resolver-m2/src"],
    deps = [
        "//tools:maven-artifact-resolver-m2",
        "//tools:idea.maven-artifact-resolver-common[module]",
    ],
    exports = ["//tools:idea.maven-artifact-resolver-common"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.maven-artifact-resolver-m3",
    srcs = ["idea/plugins/maven/artifact-resolver-m3/src"],
    deps = [
        "//tools:maven-artifact-resolver-m3",
        "//tools:maven-artifact-resolver-m3_0",
        "//tools:maven-artifact-resolver-m3_1",
        "//tools:maven-artifact-resolver-m3_2",
        "//tools:idea.maven-artifact-resolver-common[module]",
    ],
    exports = ["//tools:idea.maven-artifact-resolver-common"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.maven-artifact-resolver-m31",
    srcs = ["idea/plugins/maven/artifact-resolver-m31/src"],
    deps = [
        "//tools:maven-artifact-resolver-m31",
        "//tools:maven-artifact-resolver-m31_0",
        "//tools:maven-artifact-resolver-m31_1",
        "//tools:idea.maven-artifact-resolver-common[module]",
    ],
    exports = ["//tools:idea.maven-artifact-resolver-common"],
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
    name = "idea.util",
    srcs = ["idea/platform/util/src"],
    resources = ["idea/platform/util/resources"],
    deps = [
        "//tools/idea/.idea/libraries:Mac",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools:idea.platform-resources-en[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.util-rt[module]",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:OroMatcher",
        "//tools/idea/.idea/libraries:ForkJoin",
        "//tools/idea/.idea/libraries:Snappy-Java",
        "//tools/idea/.idea/libraries:imgscalr",
        "//tools/idea/.idea/libraries:batik",
        "//tools/idea/.idea/libraries:xmlgraphics-commons",
        "//tools/idea/.idea/libraries:xml-apis-ext",
    ],
    exports = [
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools:idea.annotations",
        "//tools:idea.util-rt",
        "//tools/idea/.idea/libraries:ForkJoin",
        "//tools/idea/.idea/libraries:Snappy-Java",
    ],
    javacopts = ["-extra_checks:off"],
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

iml_module(
    name = "idea.jps-model-serialization",
    srcs = ["idea/jps/model-serialization/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.jps-model-api[module]",
        "//tools/idea/.idea/libraries:JDOM",
    ],
    exports = ["//tools/idea/.idea/libraries:JDOM"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.common-eclipse-util",
    srcs = ["idea/plugins/eclipse/common-eclipse-util/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools:idea.jps-model-serialization[module]",
    ],
    javacopts = ["-extra_checks:off"],
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

iml_module(
    name = "idea.bootstrap",
    srcs = ["idea/platform/bootstrap/src"],
    deps = ["//tools:idea.util[module]"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.extensions",
    srcs = ["idea/platform/extensions/src"],
    test_srcs = ["idea/platform/extensions/testSrc"],
    deps = [
        "//tools/idea/.idea/libraries:XStream",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Xerces[test]",
        "//tools/idea/.idea/libraries:hamcrest[test]",
    ],
    exports = [
        "//tools/idea/.idea/libraries:XStream",
        "//tools/idea/.idea/libraries:JDOM",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.core-api",
    srcs = ["idea/platform/core-api/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.extensions[module]",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools:idea.platform-resources-en[module]",
        "//tools:core-api",
        "//tools/idea/.idea/libraries:asm",
        "//tools/idea/.idea/libraries:CGLIB",
    ],
    exports = [
        "//tools:idea.util",
        "//tools:idea.extensions",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools:idea.platform-resources-en",
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

iml_module(
    name = "idea.indexing-api",
    srcs = ["idea/platform/indexing-api/src"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools/idea/.idea/libraries:NanoXML",
    ],
    exports = ["//tools:idea.core-api"],
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
    name = "idea.analysis-api",
    srcs = ["idea/platform/analysis-api/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools:idea.extensions[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.editor-ui-api[module]",
    ],
    exports = ["//tools:idea.editor-ui-api"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.platform-api",
    srcs = ["idea/platform/platform-api/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.extensions[module]",
        "//tools:idea.core-api[module]",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools:idea.bootstrap[module]",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools:idea.forms_rt[module]",
        "//tools/idea/.idea/libraries:commons-codec",
        "//tools:idea.platform-resources-en[module]",
        "//tools/idea/.idea/libraries:OroMatcher",
        "//tools:idea.icons[module]",
        "//tools:platform-api",
        "//tools:idea.projectModel-api[module]",
        "//tools/idea/.idea/libraries:Netty",
        "//tools/idea/.idea/libraries:proxy-vole",
        "//tools:idea.analysis-api[module]",
        "//tools:idea.editor-ui-api[module]",
        "//tools/idea/.idea/libraries:pty4j",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:jna",
    ],
    exports = [
        "//tools:idea.util",
        "//tools:idea.extensions",
        "//tools:idea.core-api",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools:idea.forms_rt",
        "//tools:idea.platform-resources-en",
        "//tools:idea.projectModel-api",
        "//tools:idea.analysis-api",
        "//tools:idea.editor-ui-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.built-in-server-api",
    srcs = ["idea/platform/built-in-server-api/src"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools/idea/.idea/libraries:Netty",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools:idea.platform-api[module]",
    ],
    javacopts = ["-extra_checks:off"],
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
    name = "idea.lvcs-api",
    srcs = ["idea/platform/lvcs-api/src"],
    deps = ["//tools:idea.platform-api[module]"],
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
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools:idea.indexing-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools:idea.analysis-api[module]",
    ],
    exports = [
        "//tools:idea.platform-api",
        "//tools:idea.lvcs-api",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools:idea.indexing-api",
        "//tools:idea.projectModel-api",
        "//tools:idea.analysis-api",
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

iml_module(
    name = "idea.structure-view-impl",
    srcs = ["idea/platform/structure-view-impl/src"],
    deps = [
        "//tools:idea.editor-ui-api[module]",
        "//tools:idea.util[module]",
        "//tools:idea.extensions[module]",
        "//tools:idea.core-api[module]",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools:idea.bootstrap[module]",
        "//tools:idea.platform-resources-en[module]",
        "//tools:idea.icons[module]",
        "//tools:structure-view-impl",
        "//tools:idea.projectModel-api[module]",
    ],
    exports = [
        "//tools:idea.editor-ui-api",
        "//tools:idea.util",
        "//tools:idea.extensions",
        "//tools:idea.core-api",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools:idea.platform-resources-en",
        "//tools:idea.projectModel-api",
    ],
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
    name = "idea.vcs-api",
    srcs = ["idea/platform/vcs-api/src"],
    deps = [
        "//tools:idea.platform-api[module]",
        "//tools/idea/.idea/libraries:microba",
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
    name = "idea.core-impl",
    srcs = ["idea/platform/core-impl/src"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools:idea.boot[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
    exports = ["//tools:idea.core-api"],
    javacopts = ["-extra_checks:off"],
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
        "//tools/idea/.idea/libraries:Snappy-Java",
    ],
    exports = [
        "//tools:idea.projectModel-api",
        "//tools:idea.jps-model-serialization",
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
        "//tools/idea/.idea/libraries:NanoXML",
    ],
    exports = [
        "//tools:idea.indexing-api",
        "//tools/idea/.idea/libraries:NanoXML",
    ],
    javacopts = ["-extra_checks:off"],
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

iml_module(
    name = "idea.maven-server-api",
    srcs = ["idea/plugins/maven/maven-server-api/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools:maven-server-api",
    ],
    exports = [
        "//tools:idea.util",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools:maven-server-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.util-tests",
    test_srcs = ["idea/platform/util/testSrc"],
    deps = [
        "//tools/idea/.idea/libraries:Groovy[test]",
        "//tools:idea.util[module, test]",
        "//tools/idea/.idea/libraries:JDOM[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:picocontainer[test]",
        "//tools/idea/.idea/libraries:jna[test]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.resources-en",
    srcs = ["idea/resources-en/src"],
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
        "//tools/idea/.idea/libraries:asm5",
        "//tools/idea/.idea/libraries:Guava",
    ],
    exports = [
        "//tools:idea.java-psi-api",
        "//tools:idea.core-impl",
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
        "//tools/idea/.idea/libraries:Guava",
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

iml_module(
    name = "idea.tests_bootstrap",
    srcs = ["idea/platform/testFramework/bootstrap/src"],
    deps = ["//tools/idea/.idea/libraries:JUnit4"],
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
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/.idea/libraries:Jaxen",
        "//tools:idea.xml-psi-api[module]",
        "//tools:idea.analysis-impl[module]",
        "//tools:idea.core-impl[module]",
        "//tools:idea.xml-analysis-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea.projectModel-impl[module]",
        "//tools:idea.indexing-impl[module]",
        "//tools/idea/.idea/libraries:Xerces",
        "//tools/idea/.idea/libraries:XmlBeans",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools:idea.vcs-api[module]",
        "//tools:idea.lvcs-api[module]",
        "//tools/idea/.idea/libraries:jcip",
        "//tools:idea.diff-api[module]",
        "//tools:idea.platform-api[module]",
        "//tools/idea/.idea/libraries:commons-codec",
        "//tools:idea.jps-model-serialization[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:gson",
        "//tools:idea.xml-openapi[module]",
        "//tools:idea.icons[module]",
        "//tools/idea/.idea/libraries:swingx",
        "//tools/idea/.idea/libraries:Netty",
        "//tools:idea.xdebugger-api[module]",
        "//tools:idea.built-in-server-api[module]",
        "//tools:MM_RegExpSupport",
        "//tools:MM_RegExpSupport_0",
        "//tools:MM_RegExpSupport_1",
        "//tools:MM_RegExpSupport_2",
        "//tools:idea.extensions[module]",
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:Sanselan",
        "//tools/idea/.idea/libraries:asm",
        "//tools/idea/.idea/libraries:CGLIB",
        "//tools:idea.boot[module]",
        "//tools/idea/.idea/libraries:OroMatcher",
        "//tools/idea/.idea/libraries:Velocity",
        "//tools:idea.usageView[module]",
        "//tools/idea/.idea/libraries:xpp3-1.1.4-min",
        "//tools/idea/.idea/libraries:cli-parser",
        "//tools:idea.indexing-api[module]",
        "//tools/idea/.idea/libraries:Snappy-Java",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.structure-view-impl[module]",
        "//tools/idea/.idea/libraries:commons-logging",
        "//tools:idea.vcs-api-core[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools:idea.bootstrap[module]",
        "//tools/idea/.idea/libraries:Mac",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:JavaHelp",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:winp",
        "//tools/idea/.idea/libraries:miglayout-swing",
        "//tools/idea/.idea/libraries:jayatana",
        "//tools:idea.editor-ui-ex[module]",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:imgscalr",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/analytics-library:analytics-tracker[module]",
        "//tools/analytics-library:analytics-shared[module]",
        "//tools/analytics-library:analytics-publisher[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/analytics-library:analytics-protos[module]",
        "//tools/idea/.idea/libraries:XmlRPC",
        "//tools:idea.tests_bootstrap[module]",
        "//tools:idea.resources-en[module]",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools:idea.java-runtime[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools:idea.xml-structure-view-api[module]",
    ],
    exports = [
        "//tools:idea.xml-analysis-api",
        "//tools:idea.xml-psi-api",
        "//tools:idea.vcs-api",
        "//tools:idea.xml-openapi",
        "//tools/idea/.idea/libraries:CGLIB",
        "//tools:idea.lang-api",
        "//tools:idea.usageView",
        "//tools/idea/.idea/libraries:cli-parser",
        "//tools:idea.indexing-impl",
        "//tools:idea.projectModel-impl",
        "//tools:idea.analysis-impl",
        "//tools:idea.structure-view-impl",
        "//tools:idea.diff-api",
        "//tools:idea.platform-api",
        "//tools/idea/.idea/libraries:commons-codec",
        "//tools:idea.lvcs-api",
        "//tools:idea.core-impl",
        "//tools/idea/.idea/libraries:miglayout-swing",
        "//tools/idea/.idea/libraries:Netty",
        "//tools:idea.editor-ui-ex",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools:idea.built-in-server-api",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:Mocks",
        "//tools:idea.java-runtime",
        "//tools/idea/.idea/libraries:Groovy",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.IntelliLang",
    srcs = ["idea/plugins/IntelliLang/src"],
    deps = [
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.platform-api[module]",
        "//tools/idea/.idea/libraries:Jaxen",
    ],
    javacopts = ["-extra_checks:off"],
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
    name = "idea.openapi",
    srcs = ["idea/java/openapi/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools:idea.forms_rt[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools:idea.extensions[module]",
        "//tools:idea.icons[module]",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools/idea/.idea/libraries:microba",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools:idea.xml-openapi[module]",
        "//tools:idea.platform-api[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.vcs-api[module]",
        "//tools/idea/.idea/libraries:XmlRPC",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.resources-en[module]",
        "//tools:idea.java-psi-api[module]",
        "//tools:idea.java-indexing-api[module]",
        "//tools:idea.java-analysis-api[module]",
    ],
    exports = [
        "//tools:idea.util",
        "//tools:idea.extensions",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools/idea/.idea/libraries:microba",
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
    name = "idea.compiler-openapi",
    srcs = ["idea/java/compiler/openapi/src"],
    deps = ["//tools:idea.openapi[module]"],
    javacopts = ["-extra_checks:off"],
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
    name = "idea.copyright",
    srcs = ["idea/plugins/copyright/src"],
    deps = [
        "//tools:idea.jsp-openapi[module]",
        "//tools:idea.xml-openapi[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.openapi[module]",
        "//tools/idea/.idea/libraries:Velocity",
    ],
    javacopts = ["-extra_checks:off"],
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
    name = "idea.structuralsearch",
    srcs = ["idea/platform/structuralsearch/source"],
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools:idea.platform-api[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.openapi[module]",
        "//tools:idea.duplicates-analysis[module]",
    ],
    javacopts = ["-extra_checks:off"],
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

iml_module(
    name = "idea.smRunner",
    srcs = ["idea/platform/smRunner/src"],
    test_srcs = ["idea/platform/smRunner/testSrc"],
    deps = [
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.testRunner[module]",
        "//tools:idea.xdebugger-api[module]",
        "//tools/idea/.idea/libraries:tcServiceMessages",
        "//tools:idea.annotations[module]",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools:idea.lang-api[module]",
    ],
    exports = [
        "//tools:idea.testRunner",
        "//tools/idea/.idea/libraries:tcServiceMessages",
    ],
    javacopts = ["-extra_checks:off"],
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
        "//tools/idea/.idea/libraries:asm5",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools/idea/.idea/libraries:Netty",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools:jps-builders",
        "//tools:idea.java-runtime[module]",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools/idea/.idea/libraries:Eclipse[test]",
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.jps-serialization-tests[module, test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/idea/.idea/libraries:KotlinTest[test]",
    ],
    exports = [
        "//tools:idea.util",
        "//tools/idea/.idea/libraries:asm5",
        "//tools/idea/.idea/libraries:protobuf",
    ],
    javacopts = ["-extra_checks:off"],
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

iml_module(
    name = "idea.gradle-jps-plugin",
    srcs = ["idea/plugins/gradle/jps-plugin/src"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.jps-builders[module]",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/.idea/libraries:gson",
    ],
    exports = ["//tools/idea/.idea/libraries:Ant"],
    javacopts = ["-extra_checks:off"],
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
        "//tools/idea/.idea/libraries:Groovy-Eclipse-Batch",
        "//tools:idea.instrumentation-util[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.groovy_rt",
    srcs = ["idea/plugins/groovy/rt/src"],
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools:idea.jps-builders[module]",
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.groovy-rt-constants[module]",
        "//tools/idea/.idea/libraries:griffon-rt",
        "//tools/idea/.idea/libraries:Slf4j",
    ],
    exports = ["//tools/idea/.idea/libraries:Groovy"],
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
        "//tools:maven-jps-plugin",
        "//tools:idea.jps-serialization-tests[module, test]",
    ],
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
        "//tools/idea/.idea/libraries:asm5",
    ],
    exports = [
        "//tools:idea.analysis-impl",
        "//tools:idea.java-indexing-impl",
        "//tools:idea.java-psi-impl",
        "//tools:idea.projectModel-impl",
        "//tools:idea.java-analysis-api",
        "//tools/idea/.idea/libraries:asm5",
    ],
    javacopts = ["-extra_checks:off"],
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
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/.idea/libraries:OroMatcher",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.java-runtime[module]",
        "//tools:idea.compiler-openapi[module]",
        "//tools:idea.jsp-openapi[module]",
        "//tools:idea.jsp-spi[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools/idea/.idea/libraries:asm",
        "//tools:idea.icons[module]",
        "//tools/idea/.idea/libraries:jcip",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools:idea.java-psi-impl[module]",
        "//tools:idea.java-indexing-impl[module]",
        "//tools:idea.java-indexing-api[module]",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.java-analysis-impl[module]",
        "//tools:idea.external-system-api[module]",
        "//tools/idea/.idea/libraries:asm5",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:Xerces",
        "//tools/idea/.idea/libraries:Velocity",
        "//tools:idea.java-structure-view[module]",
        "//tools/idea/.idea/libraries:nekohtml",
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
        "//tools/idea/.idea/libraries:OroMatcher",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools:idea.external-system-api[module]",
        "//tools:idea.projectModel-impl[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.vcs-api[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools:idea.testRunner[module]",
        "//tools:idea.smRunner[module]",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools:idea.lang-api[module]",
        "//tools:idea.java-runtime[module]",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools:idea.java-indexing-api[module]",
        "//tools/idea/.idea/libraries:Coverage",
        "//tools:idea.debugger-openapi[module]",
        "//tools:idea.resources[module]",
        "//tools:idea.xdebugger-api[module]",
        "//tools:idea.jsp-openapi[module]",
        "//tools:idea.diff-api[module]",
        "//tools:idea.jps-builders[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools:idea.instrumentation-util[module]",
        "//tools/idea/.idea/libraries:asm5",
        "//tools:idea.platform-api[module]",
        "//tools:idea.jps-launcher[module]",
        "//tools/idea/.idea/libraries:Netty",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.java-analysis-impl[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
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

iml_module(
    name = "idea.ByteCodeViewer",
    srcs = ["idea/plugins/ByteCodeViewer/src"],
    deps = [
        "//tools/idea/.idea/libraries:asm5",
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

iml_module(
    name = "idea.IntelliLang-java",
    srcs = ["idea/plugins/IntelliLang/java-support"],
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools/idea/.idea/libraries:asm5",
        "//tools:idea.platform-api[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.IntelliLang[module]",
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
        "//tools/idea/.idea/libraries:JUnit3",
        "//tools/idea/.idea/libraries:JUnit4",
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

iml_module(
    name = "idea.groovy-psi",
    srcs = [
        "idea/plugins/groovy/groovy-psi/src",
        "idea/plugins/groovy/groovy-psi/gen",
    ],
    resources = ["idea/plugins/groovy/groovy-psi/resources"],
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:Guava",
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
    exports = ["//tools/idea/.idea/libraries:Groovy"],
    javacopts = ["-extra_checks:off"],
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
        "//tools/idea/.idea/libraries:JDOM",
        "//tools:idea.openapi[module]",
        "//tools:idea.bootstrap[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
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
    name = "idea.gradle-tooling-extension-api",
    srcs = ["idea/plugins/gradle/tooling-extension-api/src"],
    deps = [
        "//tools:idea.external-system-rt[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:GradleGuava",
        "//tools/idea/.idea/libraries:Groovy",
    ],
    exports = [
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
    ],
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
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:GradleGuava",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:GradleToolingExtension",
    ],
    javacopts = ["-extra_checks:off"],
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

iml_module(
    name = "idea.ant",
    srcs = ["idea/plugins/ant/src"],
    test_srcs = ["idea/plugins/ant/tests/src"],
    resources = ["idea/plugins/ant/resources"],
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Ant",
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
        "//tools/idea/.idea/libraries:Ant",
        "//tools:idea.MM_RegExpSupport",
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
    name = "idea.testng",
    srcs = ["idea/plugins/testng/src"],
    test_srcs = ["idea/plugins/testng/testSources"],
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.debugger-openapi[module]",
        "//tools/idea/.idea/libraries:JUnit3[test]",
        "//tools:idea.testRunner[module]",
        "//tools:idea.java-runtime[module]",
        "//tools:idea.testng_rt[module]",
        "//tools:idea.java-i18n[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.jsp-openapi[module]",
        "//tools/idea/.idea/libraries:TestNG",
        "//tools:idea.java-indexing-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.smRunner[module]",
        "//tools:idea.typeMigration[module]",
    ],
    exports = ["//tools:idea.smRunner"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.xslt-rt",
    srcs = ["idea/plugins/xpath/xslt-rt/src"],
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
        "//tools/idea/.idea/libraries:Jaxen",
        "//tools:idea.resources-en[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Groovy[test]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.IntelliLang-xml",
    srcs = ["idea/plugins/IntelliLang/xml-support"],
    deps = [
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools/idea/.idea/libraries:asm5",
        "//tools:idea.xpath[module]",
        "//tools:idea.platform-api[module]",
        "//tools/idea/.idea/libraries:Jaxen",
        "//tools:idea.IntelliLang[module]",
    ],
    javacopts = ["-extra_checks:off"],
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
        "//tools/idea/.idea/libraries:Groovy",
        "//tools:idea.java-impl[module]",
        "//tools:idea.copyright[module]",
        "//tools:idea.IntelliLang[module]",
        "//tools:idea.ant[module]",
        "//tools/idea/.idea/libraries:Guava",
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
        "//tools/idea/.idea/libraries:Groovy",
        "//tools:idea.groovy-psi",
    ],
    javacopts = ["-extra_checks:off"],
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
        "//tools:MM_maven2-server-impl",
        "//tools/idea/.idea/libraries:Maven",
        "//tools:MM_maven2-server-impl_0",
        "//tools:MM_maven2-server-impl_1",
        "//tools/idea/.idea/libraries:commons-logging",
        "//tools:MM_maven2-server-impl_2",
        "//tools:idea.openapi[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.IntelliLang-xml[module]",
        "//tools:idea.properties[module]",
        "//tools:idea.compiler-openapi[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.forms_rt[module]",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools/idea/.idea/libraries:XmlBeans",
        "//tools:MM_maven2-server-impl_3",
        "//tools/idea/.idea/libraries:JAXB",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools:idea.jetgroovy[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.maven-jps-plugin[module]",
        "//tools:idea.maven-artifact-resolver-m2[module]",
        "//tools:idea.maven-artifact-resolver-m3[module]",
        "//tools:idea.maven-artifact-resolver-m31[module]",
        "//tools:idea.vcs-api[module]",
        "//tools:idea.properties-psi-api[module]",
        "//tools:MM_maven2-server-impl_4",
        "//tools:idea.external-system-api[module]",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Log4J",
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
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools:idea.external-system-api",
    ],
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
        "//tools/idea/.idea/libraries:swingx",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:GradleGuava",
        "//tools/idea/.idea/libraries:jsr305",
        "//tools/idea/.idea/libraries:commons-io",
        "//tools/idea/.idea/libraries:GradleJnaPosix",
        "//tools:idea.smRunner[module]",
        "//tools/idea/.idea/libraries:Kryo",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/.idea/libraries:gson",
    ],
    exports = [
        "//tools:idea.external-system-api",
        "//tools:idea.MM_idea-ui",
        "//tools:idea.gradle-tooling-extension-api",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:GradleGuava",
        "//tools/idea/.idea/libraries:commons-io",
    ],
    javacopts = ["-extra_checks:off"],
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

iml_module(
    name = "idea.gradle-tests",
    test_srcs = [
        "idea/plugins/gradle/testData",
        "idea/plugins/gradle/testSources",
    ],
    deps = [
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
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
    name = "idea/lib/protobuf-2.5.0",
    jars = [
        "idea/lib/protobuf-2.5.0.jar",
    ],
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
    name = "idea/lib/hamcrest-core-1.3",
    jars = [
        "idea/lib/hamcrest-core-1.3.jar",
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

java_import(
    name = "idea/lib/gson-2.5",
    jars = [
        "idea/lib/gson-2.5.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/trove4j",
    jars = [
        "idea/lib/trove4j.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/jsr305",
    jars = [
        "idea/lib/jsr305.jar",
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
    name = "idea/lib/httpcore-4.4.1",
    jars = [
        "idea/lib/httpcore-4.4.1.jar",
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
    name = "idea/lib/httpclient-4.4.1",
    jars = [
        "idea/lib/httpclient-4.4.1.jar",
    ],
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
    name = "idea/lib/xmlrpc-2.0",
    jars = [
        "idea/lib/xmlrpc-2.0.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-commons-net",
    jars = [
        "idea/lib/ant/lib/ant-commons-net.jar",
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

java_import(
    name = "idea/lib/ant/lib/ant-apache-resolver",
    jars = [
        "idea/lib/ant/lib/ant-apache-resolver.jar",
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

java_import(
    name = "idea/lib/ant/lib/ant-apache-bsf",
    jars = [
        "idea/lib/ant/lib/ant-apache-bsf.jar",
    ],
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
    name = "idea/lib/ant/lib/ant-junit",
    jars = [
        "idea/lib/ant/lib/ant-junit.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-jsch",
    jars = [
        "idea/lib/ant/lib/ant-jsch.jar",
    ],
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
    name = "idea/lib/ant/lib/ant",
    jars = [
        "idea/lib/ant/lib/ant.jar",
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
    name = "idea/lib/ant/lib/ant-apache-oro",
    jars = [
        "idea/lib/ant/lib/ant-apache-oro.jar",
    ],
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
    name = "idea/lib/ant/lib/ant-jdepend",
    jars = [
        "idea/lib/ant/lib/ant-jdepend.jar",
    ],
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
    name = "idea/lib/ant/lib/ant-apache-regexp",
    jars = [
        "idea/lib/ant/lib/ant-apache-regexp.jar",
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

java_import(
    name = "idea/lib/ant/lib/ant-swing",
    jars = [
        "idea/lib/ant/lib/ant-swing.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-javamail",
    jars = [
        "idea/lib/ant/lib/ant-javamail.jar",
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
    name = "idea/lib/jdom",
    jars = [
        "idea/lib/jdom.jar",
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
    name = "idea/lib/hamcrest-library-1.3",
    jars = [
        "idea/lib/hamcrest-library-1.3.jar",
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

java_import(
    name = "idea/plugins/testng/lib/jcommander",
    jars = [
        "idea/plugins/testng/lib/jcommander.jar",
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
    name = "idea/lib/coverage-instrumenter",
    jars = [
        "idea/lib/coverage-instrumenter.jar",
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

java_library(
    name = "maven-artifact-resolver-common",
    runtime_deps = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-artifact-3.0.5"],
    exports = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-artifact-3.0.5"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-artifact-3.0.5",
    jars = [
        "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-artifact-3.0.5.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "maven-artifact-resolver-m2",
    runtime_deps = ["//tools:idea/plugins/maven/maven2-server-impl/lib/maven2/lib/maven-2.2.1-uber"],
    exports = ["//tools:idea/plugins/maven/maven2-server-impl/lib/maven2/lib/maven-2.2.1-uber"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/maven2/lib/maven-2.2.1-uber",
    jars = [
        "idea/plugins/maven/maven2-server-impl/lib/maven2/lib/maven-2.2.1-uber.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "maven-artifact-resolver-m3",
    runtime_deps = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/aether-api-1.13.1"],
    exports = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/aether-api-1.13.1"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/aether-api-1.13.1",
    jars = [
        "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/aether-api-1.13.1.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "maven-artifact-resolver-m3_0",
    runtime_deps = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-core-3.0.5"],
    exports = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-core-3.0.5"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-core-3.0.5",
    jars = [
        "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-core-3.0.5.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "maven-artifact-resolver-m3_1",
    runtime_deps = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-artifact-3.0.5"],
    exports = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-artifact-3.0.5"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "maven-artifact-resolver-m3_2",
    runtime_deps = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/plexus-component-annotations-1.5.5"],
    exports = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/plexus-component-annotations-1.5.5"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/plexus-component-annotations-1.5.5",
    jars = [
        "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/plexus-component-annotations-1.5.5.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "maven-artifact-resolver-m31",
    runtime_deps = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-core-3.0.5"],
    exports = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-core-3.0.5"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "maven-artifact-resolver-m31_0",
    runtime_deps = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/plexus-component-annotations-1.5.5"],
    exports = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/plexus-component-annotations-1.5.5"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "maven-artifact-resolver-m31_1",
    runtime_deps = ["//tools:idea/plugins/maven/artifact-resolver-m31/lib/eclipse-aether/aether-api-0.9.0.M2"],
    exports = ["//tools:idea/plugins/maven/artifact-resolver-m31/lib/eclipse-aether/aether-api-0.9.0.M2"],
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
    name = "idea/lib/eawtstub",
    jars = [
        "idea/lib/eawtstub.jar",
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
    name = "idea/lib/picocontainer",
    jars = [
        "idea/lib/picocontainer.jar",
    ],
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
    name = "idea/lib/jna-platform",
    jars = [
        "idea/lib/jna-platform.jar",
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
    name = "idea/lib/jsr166e",
    jars = [
        "idea/lib/jsr166e.jar",
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
    name = "idea/lib/imgscalr-lib-4.2",
    jars = [
        "idea/lib/imgscalr-lib-4.2.jar",
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
    name = "idea/lib/xmlgraphics-commons-1.5",
    jars = [
        "idea/lib/xmlgraphics-commons-1.5.jar",
    ],
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
    name = "idea/lib/xstream-1.4.8",
    jars = [
        "idea/lib/xstream-1.4.8.jar",
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
    name = "idea/lib/xml-apis",
    jars = [
        "idea/lib/xml-apis.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "core-api",
    runtime_deps = ["//tools:idea/lib/automaton"],
    exports = ["//tools:idea/lib/automaton"],
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
    name = "idea/lib/asm",
    jars = [
        "idea/lib/asm.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/asm-commons",
    jars = [
        "idea/lib/asm-commons.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/cglib-2.2.2",
    jars = [
        "idea/lib/cglib-2.2.2.jar",
    ],
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
    name = "idea/lib/commons-codec-1.9",
    jars = [
        "idea/lib/commons-codec-1.9.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "platform-api",
    runtime_deps = ["//tools:idea/lib/automaton"],
    exports = ["//tools:idea/lib/automaton"],
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

java_import(
    name = "idea/lib/pty4j-0.6",
    jars = [
        "idea/lib/pty4j-0.6.jar",
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

java_import(
    name = "idea/lib/dev/easymockclassextension",
    jars = [
        "idea/lib/dev/easymockclassextension.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/dev/jmock-2.5.1",
    jars = [
        "idea/lib/dev/jmock-2.5.1.jar",
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
    name = "idea/lib/dev/jmock-legacy-2.5.1",
    jars = [
        "idea/lib/dev/jmock-legacy-2.5.1.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/dev/objenesis-1.0",
    jars = [
        "idea/lib/dev/objenesis-1.0.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "structure-view-impl",
    runtime_deps = ["//tools:idea/lib/automaton"],
    exports = ["//tools:idea/lib/automaton"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/microba",
    jars = [
        "idea/lib/microba.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "maven-server-api",
    runtime_deps = ["//tools:idea/plugins/maven/maven-server-api/lib/lucene-core-2.4.1"],
    exports = ["//tools:idea/plugins/maven/maven-server-api/lib/lucene-core-2.4.1"],
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
    name = "idea/lib/groovy-all-2.4.6",
    jars = [
        "idea/lib/groovy-all-2.4.6.jar",
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

java_import(
    name = "idea/lib/jaxen-1.1.3",
    jars = [
        "idea/lib/jaxen-1.1.3.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/jsr173_1.0_api",
    jars = [
        "idea/lib/jsr173_1.0_api.jar",
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

java_import(
    name = "idea/lib/resolver",
    jars = [
        "idea/lib/resolver.jar",
    ],
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
    name = "idea/lib/swingx-core-1.6.2",
    jars = [
        "idea/lib/swingx-core-1.6.2.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "MM_RegExpSupport",
    runtime_deps = ["//tools:idea/xml/relaxng/lib/rngom-20051226-patched"],
    exports = ["//tools:idea/xml/relaxng/lib/rngom-20051226-patched"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/xml/relaxng/lib/rngom-20051226-patched",
    jars = [
        "idea/xml/relaxng/lib/rngom-20051226-patched.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "MM_RegExpSupport_0",
    runtime_deps = ["//tools:idea/xml/relaxng/lib/isorelax"],
    exports = ["//tools:idea/xml/relaxng/lib/isorelax"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/xml/relaxng/lib/isorelax",
    jars = [
        "idea/xml/relaxng/lib/isorelax.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "MM_RegExpSupport_1",
    runtime_deps = ["//tools:idea/xml/relaxng/lib/trang-core"],
    exports = ["//tools:idea/xml/relaxng/lib/trang-core"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/xml/relaxng/lib/trang-core",
    jars = [
        "idea/xml/relaxng/lib/trang-core.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "MM_RegExpSupport_2",
    runtime_deps = ["//tools:idea/xml/relaxng/lib/jing"],
    exports = ["//tools:idea/xml/relaxng/lib/jing"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/xml/relaxng/lib/jing",
    jars = [
        "idea/xml/relaxng/lib/jing.jar",
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
    name = "idea/lib/velocity",
    jars = [
        "idea/lib/velocity.jar",
    ],
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
    name = "idea/lib/cli-parser-1.1",
    jars = [
        "idea/lib/cli-parser-1.1.jar",
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
    name = "idea/lib/jh",
    jars = [
        "idea/lib/jh.jar",
    ],
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
    name = "idea/lib/miglayout-swing",
    jars = [
        "idea/lib/miglayout-swing.jar",
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

java_import(
    name = "idea/lib/slf4j-api-1.7.10",
    jars = [
        "idea/lib/slf4j-api-1.7.10.jar",
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

java_import(
    name = "idea/lib/minlog-1.2",
    jars = [
        "idea/lib/minlog-1.2.jar",
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
    name = "idea/lib/reflectasm-1.07",
    jars = [
        "idea/lib/reflectasm-1.07.jar",
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

java_import(
    name = "idea/lib/serviceMessages",
    jars = [
        "idea/lib/serviceMessages.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "jps-builders",
    runtime_deps = ["//tools:idea/jps/lib/optimizedFileManager"],
    exports = ["//tools:idea/jps/lib/optimizedFileManager"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/jps/lib/optimizedFileManager",
    jars = [
        "idea/jps/lib/optimizedFileManager.jar",
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
    name = "idea/plugins/gradle/lib/gradle-tooling-api-2.14.1",
    jars = [
        "idea/plugins/gradle/lib/gradle-tooling-api-2.14.1.jar",
    ],
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
    name = "idea/plugins/gradle/lib/gradle-messaging-2.14.1",
    jars = [
        "idea/plugins/gradle/lib/gradle-messaging-2.14.1.jar",
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

java_import(
    name = "idea/plugins/gradle/lib/gradle-model-groovy-2.14.1",
    jars = [
        "idea/plugins/gradle/lib/gradle-model-groovy-2.14.1.jar",
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

java_import(
    name = "idea/plugins/gradle/lib/gradle-base-services-2.14.1",
    jars = [
        "idea/plugins/gradle/lib/gradle-base-services-2.14.1.jar",
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

java_import(
    name = "idea/plugins/gradle/lib/gradle-native-2.14.1",
    jars = [
        "idea/plugins/gradle/lib/gradle-native-2.14.1.jar",
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

java_import(
    name = "idea/plugins/groovy/lib/groovy-eclipse-batch-2.3.4-01",
    jars = [
        "idea/plugins/groovy/lib/groovy-eclipse-batch-2.3.4-01.jar",
    ],
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
    name = "idea/plugins/groovy/testdata/griffon/griffon-cli-1.1.0",
    jars = [
        "idea/plugins/groovy/testdata/griffon/griffon-cli-1.1.0.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "maven-jps-plugin",
    runtime_deps = ["//tools:idea/plugins/maven/lib/plexus-utils-2.0.6"],
    exports = ["//tools:idea/plugins/maven/lib/plexus-utils-2.0.6"],
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
    name = "idea/lib/nekohtml-1.9.14",
    jars = [
        "idea/lib/nekohtml-1.9.14.jar",
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
    name = "idea/plugins/gradle/lib/gradle-process-services-2.14.1",
    jars = [
        "idea/plugins/gradle/lib/gradle-process-services-2.14.1.jar",
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
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-build-init-2.14.1",
    jars = [
        "idea/plugins/gradle/tooling-extension-api/lib/gradle-build-init-2.14.1.jar",
    ],
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
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-language-java-2.14.1",
    jars = [
        "idea/plugins/gradle/tooling-extension-api/lib/gradle-language-java-2.14.1.jar",
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

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-platform-base-2.14.1",
    jars = [
        "idea/plugins/gradle/tooling-extension-api/lib/gradle-platform-base-2.14.1.jar",
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

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-plugins-2.14.1",
    jars = [
        "idea/plugins/gradle/tooling-extension-api/lib/gradle-plugins-2.14.1.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-testing-base-2.14.1",
    jars = [
        "idea/plugins/gradle/tooling-extension-api/lib/gradle-testing-base-2.14.1.jar",
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

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-scala-2.14.1",
    jars = [
        "idea/plugins/gradle/tooling-extension-api/lib/gradle-scala-2.14.1.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-language-scala-2.14.1",
    jars = [
        "idea/plugins/gradle/tooling-extension-api/lib/gradle-language-scala-2.14.1.jar",
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

java_import(
    name = "idea/plugins/gradle/tooling-extension-impl/lib/gradle-ear-2.14.1",
    jars = [
        "idea/plugins/gradle/tooling-extension-impl/lib/gradle-ear-2.14.1.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "MM_maven2-server-impl",
    runtime_deps = ["//tools:idea/plugins/maven/maven2-server-impl/lib/plexus-utils-1.5.5"],
    exports = ["//tools:idea/plugins/maven/maven2-server-impl/lib/plexus-utils-1.5.5"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/plexus-utils-1.5.5",
    jars = [
        "idea/plugins/maven/maven2-server-impl/lib/plexus-utils-1.5.5.jar",
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
    name = "idea/plugins/maven/maven2-server-impl/lib/archetype-common-2.0-alpha-4-SNAPSHOT",
    jars = [
        "idea/plugins/maven/maven2-server-impl/lib/archetype-common-2.0-alpha-4-SNAPSHOT.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/mercury-artifact-1.0-alpha-6",
    jars = [
        "idea/plugins/maven/maven2-server-impl/lib/mercury-artifact-1.0-alpha-6.jar",
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

java_library(
    name = "MM_maven2-server-impl_0",
    runtime_deps = ["//tools:idea/plugins/maven/maven2-server-impl/lib/nexus-indexer-1.2.3"],
    exports = ["//tools:idea/plugins/maven/maven2-server-impl/lib/nexus-indexer-1.2.3"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/nexus-indexer-1.2.3",
    jars = [
        "idea/plugins/maven/maven2-server-impl/lib/nexus-indexer-1.2.3.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "MM_maven2-server-impl_1",
    runtime_deps = ["//tools:idea/plugins/maven/maven2-server-impl/lib/activation-1.1"],
    exports = ["//tools:idea/plugins/maven/maven2-server-impl/lib/activation-1.1"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/activation-1.1",
    jars = [
        "idea/plugins/maven/maven2-server-impl/lib/activation-1.1.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "MM_maven2-server-impl_2",
    runtime_deps = ["//tools:idea/plugins/maven/maven2-server-impl/lib/commons-beanutils"],
    exports = ["//tools:idea/plugins/maven/maven2-server-impl/lib/commons-beanutils"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/commons-beanutils",
    jars = [
        "idea/plugins/maven/maven2-server-impl/lib/commons-beanutils.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "MM_maven2-server-impl_3",
    runtime_deps = ["//tools:idea/plugins/maven/lib/wadl-core"],
    exports = ["//tools:idea/plugins/maven/lib/wadl-core"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/lib/wadl-core",
    jars = [
        "idea/plugins/maven/lib/wadl-core.jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "MM_maven2-server-impl_4",
    runtime_deps = ["//tools:idea/plugins/maven/lib/plexus-archiver-2.4.4"],
    exports = ["//tools:idea/plugins/maven/lib/plexus-archiver-2.4.4"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/lib/plexus-archiver-2.4.4",
    jars = [
        "idea/plugins/maven/lib/plexus-archiver-2.4.4.jar",
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
    name = "idea/plugins/gradle/lib/jsr305-1.3.9",
    jars = [
        "idea/plugins/gradle/lib/jsr305-1.3.9.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/commons-io-2.2",
    jars = [
        "idea/plugins/gradle/lib/commons-io-2.2.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/jna-3.2.7",
    jars = [
        "idea/plugins/gradle/lib/jna-3.2.7.jar",
    ],
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
    name = "idea/lib/asm4-all",
    jars = [
        "idea/lib/asm4-all.jar",
    ],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "idea/java/jdkAnnotations",
    srcs = glob(["idea/java/jdkAnnotations/**"]),
    visibility = ["//visibility:public"],
)
