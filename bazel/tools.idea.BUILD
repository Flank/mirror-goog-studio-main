load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "idea.annotations-common",
    srcs = ["idea/platform/annotations/common/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.annotations",
    srcs = ["idea/platform/annotations/java5/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.annotations-common"],
    deps = ["//tools:idea.annotations-common[module]"],
)

iml_module(
    name = "idea.external-system-rt",
    srcs = ["idea/platform/external-system-rt/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools:idea.annotations[module]"],
)

iml_module(
    name = "idea.util-rt",
    srcs = ["idea/platform/util-rt/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools:idea.annotations[module]"],
)

iml_module(
    name = "idea.jps-model-api",
    srcs = ["idea/jps/model-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.annotations[module]",
        "//tools:idea.util-rt[module]",
    ],
)

iml_module(
    name = "idea.boot",
    srcs = ["idea/platform/boot/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.community-resources",
    javacopts = ["-extra_checks:off"],
    resources = ["idea/community-resources/src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.forms_rt",
    srcs = ["idea/platform/forms_rt/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.groovy-rt-constants",
    srcs = ["idea/plugins/groovy/rt-constants/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.icons",
    srcs = ["idea/platform/icons/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.instrumentation-util",
    srcs = ["idea/java/compiler/instrumentation-util/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools/idea/.idea/libraries:asm5"],
)

iml_module(
    name = "idea.forms-compiler",
    srcs = ["idea/java/compiler/forms-compiler/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/java/compiler/forms-compiler/testSrc"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.instrumentation-util"],
    deps = [
        "//tools/idea/.idea/libraries:JDOM",
        "//tools:idea.forms_rt[module]",
        "//tools/idea/.idea/libraries:asm5",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools:idea.instrumentation-util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
)

iml_module(
    name = "idea.java-runtime",
    srcs = ["idea/java/java-runtime/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/idea/.idea/libraries:JUnit3",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/.idea/libraries:Ant",
    ],
)

iml_module(
    name = "idea.junit_rt",
    srcs = ["idea/plugins/junit_rt/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools:idea.java-runtime[module]",
    ],
)

iml_module(
    name = "idea.testng_rt",
    srcs = ["idea/plugins/testng_rt/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/idea/.idea/libraries:TestNG",
        "//tools:idea.java-runtime[module]",
    ],
)

iml_module(
    name = "idea.jps-launcher",
    srcs = ["idea/jps/jps-launcher/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.maven-artifact-resolver-common",
    srcs = ["idea/plugins/maven/artifact-resolver/common/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-artifact-3.0.5"],
)

iml_module(
    name = "idea.maven-artifact-resolver-m2",
    srcs = ["idea/plugins/maven/artifact-resolver-m2/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.maven-artifact-resolver-common"],
    deps = [
        "//tools:idea/plugins/maven/maven2-server-impl/lib/maven2/lib/maven-2.2.1-uber",
        "//tools:idea.maven-artifact-resolver-common[module]",
    ],
)

iml_module(
    name = "idea.maven-artifact-resolver-m3",
    srcs = ["idea/plugins/maven/artifact-resolver-m3/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.maven-artifact-resolver-common"],
    deps = [
        "//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/aether-api-1.13.1",
        "//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-core-3.0.5",
        "//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-artifact-3.0.5",
        "//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/plexus-component-annotations-1.5.5",
        "//tools:idea.maven-artifact-resolver-common[module]",
    ],
)

iml_module(
    name = "idea.maven-artifact-resolver-m31",
    srcs = ["idea/plugins/maven/artifact-resolver-m31/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.maven-artifact-resolver-common"],
    deps = [
        "//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-core-3.0.5",
        "//tools:idea/plugins/maven/maven30-server-impl/lib/maven3/lib/plexus-component-annotations-1.5.5",
        "//tools:idea/plugins/maven/artifact-resolver-m31/lib/eclipse-aether/aether-api-0.9.0.M2",
        "//tools:idea.maven-artifact-resolver-common[module]",
    ],
)

iml_module(
    name = "idea.platform-resources-en",
    javacopts = ["-extra_checks:off"],
    resources = ["idea/platform/platform-resources-en/src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.util",
    srcs = ["idea/platform/util/src"],
    javacopts = ["-extra_checks:off"],
    resources = ["idea/platform/util/resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools:idea.annotations",
        "//tools:idea.util-rt",
        "//tools/idea/.idea/libraries:Snappy-Java",
    ],
    deps = [
        "//tools/idea/.idea/libraries:Mac",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools:idea.platform-resources-en[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.util-rt[module]",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:OroMatcher",
        "//tools/idea/.idea/libraries:Snappy-Java",
        "//tools/idea/.idea/libraries:imgscalr",
        "//tools/idea/.idea/libraries:batik",
        "//tools/idea/.idea/libraries:xmlgraphics-commons",
        "//tools/idea/.idea/libraries:xml-apis-ext",
    ],
)

iml_module(
    name = "idea.jps-model-impl",
    srcs = ["idea/jps/model-impl/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.jps-model-api[module]",
    ],
)

iml_module(
    name = "idea.jps-model-serialization",
    srcs = ["idea/jps/model-serialization/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:JDOM"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.jps-model-api[module]",
        "//tools/idea/.idea/libraries:JDOM",
    ],
)

iml_module(
    name = "idea.common-eclipse-util",
    srcs = ["idea/plugins/eclipse/common-eclipse-util/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools:idea.jps-model-serialization[module]",
    ],
)

iml_module(
    name = "idea.eclipse-jps-plugin",
    srcs = ["idea/plugins/eclipse/jps-plugin/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea.common-eclipse-util[module]",
    ],
)

iml_module(
    name = "idea.bootstrap",
    srcs = ["idea/platform/bootstrap/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools:idea.util[module]"],
)

iml_module(
    name = "idea.extensions",
    srcs = ["idea/platform/extensions/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/platform/extensions/testSrc"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools/idea/.idea/libraries:XStream",
        "//tools/idea/.idea/libraries:JDOM",
    ],
    deps = [
        "//tools/idea/.idea/libraries:XStream",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Xerces[test]",
        "//tools/idea/.idea/libraries:hamcrest[test]",
    ],
)

iml_module(
    name = "idea.core-api",
    srcs = ["idea/platform/core-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.util",
        "//tools:idea.extensions",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools:idea.platform-resources-en",
    ],
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.extensions[module]",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools:idea.platform-resources-en[module]",
        "//tools:idea/lib/automaton",
        "//tools/idea/.idea/libraries:asm",
        "//tools/idea/.idea/libraries:CGLIB",
    ],
)

iml_module(
    name = "idea.projectModel-api",
    srcs = ["idea/platform/projectModel-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.core-api",
        "//tools:idea.jps-model-api",
    ],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.jps-model-api[module]",
    ],
)

iml_module(
    name = "idea.indexing-api",
    srcs = ["idea/platform/indexing-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.core-api"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools/idea/.idea/libraries:NanoXML",
    ],
)

iml_module(
    name = "idea.editor-ui-api",
    srcs = ["idea/platform/editor-ui-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.util[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.indexing-api[module]",
    ],
)

iml_module(
    name = "idea.analysis-api",
    srcs = ["idea/platform/analysis-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.editor-ui-api"],
    deps = [
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools:idea.extensions[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.editor-ui-api[module]",
    ],
)

iml_module(
    name = "idea.platform-api",
    srcs = ["idea/platform/platform-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
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
        "//tools:idea/lib/automaton",
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
)

iml_module(
    name = "idea.built-in-server-api",
    srcs = ["idea/platform/built-in-server-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools/idea/.idea/libraries:Netty",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools:idea.platform-api[module]",
    ],
)

iml_module(
    name = "idea.diff-api",
    srcs = ["idea/platform/diff-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.platform-api"],
    deps = ["//tools:idea.platform-api[module]"],
)

iml_module(
    name = "idea.lvcs-api",
    srcs = ["idea/platform/lvcs-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools:idea.platform-api[module]"],
)

iml_module(
    name = "idea.lang-api",
    srcs = ["idea/platform/lang-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/platform/lang-api/testSources"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.platform-api",
        "//tools:idea.lvcs-api",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools:idea.indexing-api",
        "//tools:idea.projectModel-api",
        "//tools:idea.analysis-api",
    ],
    deps = [
        "//tools:idea.platform-api[module]",
        "//tools:idea.lvcs-api[module]",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools:idea.indexing-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools:idea.analysis-api[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)

iml_module(
    name = "idea.xdebugger-api",
    srcs = ["idea/platform/xdebugger-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.lang-api"],
    deps = ["//tools:idea.lang-api[module]"],
)

iml_module(
    name = "idea.xml-psi-api",
    srcs = ["idea/xml/xml-psi-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.analysis-api[module]",
    ],
)

iml_module(
    name = "idea.xml-analysis-api",
    srcs = ["idea/xml/xml-analysis-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.xml-psi-api",
        "//tools:idea.analysis-api",
        "//tools:idea.annotations",
        "//tools:idea.core-api",
        "//tools:idea.extensions",
        "//tools:idea.util",
    ],
    deps = [
        "//tools:idea.xml-psi-api[module]",
        "//tools:idea.analysis-api[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea.extensions[module]",
        "//tools:idea.util[module]",
    ],
)

iml_module(
    name = "idea.xml-structure-view-api",
    srcs = ["idea/xml/xml-structure-view-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.xml-psi-api"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.xml-psi-api[module]",
        "//tools:idea.editor-ui-api[module]",
    ],
)

iml_module(
    name = "idea.xml-openapi",
    srcs = ["idea/xml/openapi/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.xml-psi-api",
        "//tools:idea.xml-analysis-api",
        "//tools:idea.xml-structure-view-api",
    ],
    deps = [
        "//tools:idea.lang-api[module]",
        "//tools:idea.xml-psi-api[module]",
        "//tools:idea.xml-analysis-api[module]",
        "//tools:idea.xml-structure-view-api[module]",
    ],
)

iml_module(
    name = "idea.jsp-base-openapi",
    srcs = ["idea/java/jsp-base-openapi/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.xml-openapi"],
    deps = [
        "//tools:idea.platform-api[module]",
        "//tools:idea.xml-openapi[module]",
        "//tools:idea.lang-api[module]",
    ],
)

iml_module(
    name = "idea.structure-view-impl",
    srcs = ["idea/platform/structure-view-impl/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.editor-ui-api",
        "//tools:idea.util",
        "//tools:idea.extensions",
        "//tools:idea.core-api",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools:idea.platform-resources-en",
        "//tools:idea.projectModel-api",
    ],
    deps = [
        "//tools:idea.editor-ui-api[module]",
        "//tools:idea.util[module]",
        "//tools:idea.extensions[module]",
        "//tools:idea.core-api[module]",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools:idea.bootstrap[module]",
        "//tools:idea.platform-resources-en[module]",
        "//tools:idea.icons[module]",
        "//tools:idea/lib/automaton",
        "//tools:idea.projectModel-api[module]",
    ],
)

iml_module(
    name = "idea.vcs-api-core",
    srcs = ["idea/platform/vcs-api/vcs-api-core/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.annotations[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea.editor-ui-api[module]",
    ],
)

iml_module(
    name = "idea.vcs-api",
    srcs = ["idea/platform/vcs-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.platform-api",
        "//tools:idea.vcs-api-core",
        "//tools:idea.diff-api",
    ],
    deps = [
        "//tools:idea.platform-api[module]",
        "//tools/idea/.idea/libraries:microba",
        "//tools:idea.vcs-api-core[module]",
        "//tools:idea.diff-api[module]",
    ],
)

iml_module(
    name = "idea.java-psi-api",
    srcs = ["idea/java/java-psi-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.core-api"],
    deps = ["//tools:idea.core-api[module]"],
)

iml_module(
    name = "idea.java-analysis-api",
    srcs = ["idea/java/java-analysis-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.analysis-api[module]",
        "//tools:idea.java-psi-api[module]",
        "//tools:idea.projectModel-api[module]",
    ],
)

iml_module(
    name = "idea.java-indexing-api",
    srcs = ["idea/java/java-indexing-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.java-psi-api[module]",
        "//tools:idea.indexing-api[module]",
    ],
)

iml_module(
    name = "idea.core-impl",
    srcs = ["idea/platform/core-impl/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.core-api"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)

iml_module(
    name = "idea.projectModel-impl",
    srcs = ["idea/platform/projectModel-impl/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.projectModel-api",
        "//tools:idea.jps-model-serialization",
    ],
    deps = [
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.core-impl[module]",
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools/idea/.idea/libraries:Snappy-Java",
    ],
)

iml_module(
    name = "idea.indexing-impl",
    srcs = ["idea/platform/indexing-impl/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.indexing-api",
        "//tools/idea/.idea/libraries:NanoXML",
    ],
    deps = [
        "//tools:idea.core-impl[module]",
        "//tools:idea.indexing-api[module]",
        "//tools:idea.projectModel-impl[module]",
        "//tools/idea/.idea/libraries:NanoXML",
    ],
)

iml_module(
    name = "idea.editor-ui-ex",
    srcs = ["idea/platform/editor-ui-ex/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.editor-ui-api",
        "//tools:idea.util",
        "//tools:idea.annotations",
        "//tools:idea.core-impl",
    ],
    deps = [
        "//tools:idea.editor-ui-api[module]",
        "//tools:idea.util[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.core-impl[module]",
        "//tools:idea.indexing-impl[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.analysis-api[module]",
    ],
)

iml_module(
    name = "idea.maven-server-api",
    srcs = ["idea/plugins/maven/maven-server-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.util",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools:idea/plugins/maven/maven-server-api/lib/lucene-core-2.4.1",
    ],
    deps = [
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools:idea/plugins/maven/maven-server-api/lib/lucene-core-2.4.1",
    ],
)

iml_module(
    name = "idea.util-tests",
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/platform/util/testSrc"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/idea/.idea/libraries:Groovy[test]",
        "//tools:idea.util[module, test]",
        "//tools/idea/.idea/libraries:JDOM[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:picocontainer[test]",
        "//tools/idea/.idea/libraries:jna[test]",
        "//tools:idea.MM_RegExpSupport[module]",
    ],
)

iml_module(
    name = "idea.resources-en",
    srcs = ["idea/resources-en/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.analysis-impl",
    srcs = ["idea/platform/analysis-impl/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.analysis-api",
        "//tools:idea.core-impl",
    ],
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
)

iml_module(
    name = "idea.duplicates-analysis",
    srcs = ["idea/platform/duplicates-analysis/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.analysis-impl",
        "//tools:idea.annotations",
        "//tools:idea.extensions",
        "//tools:idea.util",
        "//tools:idea.indexing-api",
        "//tools:idea.projectModel-api",
        "//tools:idea.projectModel-impl",
    ],
    deps = [
        "//tools:idea.analysis-impl[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.extensions[module]",
        "//tools:idea.util[module]",
        "//tools:idea.indexing-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.projectModel-impl[module]",
    ],
)

iml_module(
    name = "idea.java-psi-impl",
    srcs = [
        "idea/java/java-psi-impl/src",
        "idea/java/java-psi-impl/gen",
    ],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.java-psi-api",
        "//tools:idea.core-impl",
    ],
    deps = [
        "//tools:idea.java-psi-api[module]",
        "//tools:idea.core-impl[module]",
        "//tools:idea.resources-en[module]",
        "//tools/idea/.idea/libraries:asm5",
        "//tools/idea/.idea/libraries:Guava",
    ],
)

iml_module(
    name = "idea.java-structure-view",
    srcs = ["idea/java/java-structure-view/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.structure-view-impl"],
    deps = [
        "//tools:idea.structure-view-impl[module]",
        "//tools:idea.java-psi-impl[module]",
    ],
)

iml_module(
    name = "idea.java-indexing-impl",
    srcs = ["idea/java/java-indexing-impl/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.java-psi-api",
        "//tools:idea.java-psi-impl",
        "//tools:idea.indexing-api",
        "//tools:idea.indexing-impl",
        "//tools:idea.projectModel-api",
        "//tools:idea.java-indexing-api",
    ],
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
)

iml_module(
    name = "idea.tests_bootstrap",
    srcs = ["idea/platform/testFramework/bootstrap/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools/idea/.idea/libraries:JUnit4"],
)

iml_module(
    name = "idea.MM_RegExpSupport",
    srcs = [
        "idea/RegExpSupport/src",
        "idea/RegExpSupport/gen",
        "idea/xml/xml-analysis-impl/src",
        "idea/xml/xml-psi-impl/src",
        "idea/xml/xml-psi-impl/gen",
        "idea/platform/usageView/src",
        "idea/platform/lvcs-impl/src",
        "idea/platform/vcs-impl/src",
        "idea/xml/impl/src",
        "idea/spellchecker/src",
        "idea/spellchecker/gen",
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
    javacopts = ["-extra_checks:off"],
    resources = [
        "idea/xml/xml-analysis-impl/resources",
        "idea/xml/xml-psi-impl/resources",
        "idea/xml/impl/resources",
        "idea/spellchecker/resources",
        "idea/platform/platform-resources/src",
        "idea/json/resources",
        "idea/platform/lang-impl/resources",
    ],
    tags = ["managed"],
    test_srcs = [
        "idea/RegExpSupport/test",
        "idea/platform/vcs-impl/testSrc",
        "idea/spellchecker/testSrc",
        "idea/xml/relaxng/test",
        "idea/platform/xdebugger-impl/testSrc",
        "idea/platform/testFramework/testSrc",
    ],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.xml-analysis-api",
        "//tools:idea.xml-psi-api",
        "//tools:idea.vcs-api",
        "//tools:idea.xml-openapi",
        "//tools/idea/.idea/libraries:CGLIB",
        "//tools:idea.lang-api",
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
        "//tools:idea/xml/relaxng/lib/rngom-20051226-patched",
        "//tools:idea/xml/relaxng/lib/isorelax",
        "//tools:idea/xml/relaxng/lib/trang-core",
        "//tools:idea/xml/relaxng/lib/jing",
        "//tools:idea.extensions[module]",
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:Sanselan",
        "//tools/idea/.idea/libraries:asm",
        "//tools/idea/.idea/libraries:CGLIB",
        "//tools:idea.boot[module]",
        "//tools/idea/.idea/libraries:OroMatcher",
        "//tools/idea/.idea/libraries:Velocity",
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
        "//tools/idea/.idea/libraries:com.twelvemonkeys.imageio_imageio-tiff_3.2.1",
        "//tools/analytics-library:analytics-tracker[module]",
        "//tools/analytics-library:analytics-shared[module]",
        "//tools/analytics-library:analytics-publisher[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/analytics-library:analytics-protos[module]",
        "//tools/idea/.idea/libraries:pty4j",
        "//tools/idea/.idea/libraries:XmlRPC",
        "//tools:idea.tests_bootstrap[module]",
        "//tools:idea.resources-en[module]",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools:idea.java-runtime[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools:idea.xml-structure-view-api[module]",
    ],
)

iml_module(
    name = "idea.IntelliLang",
    srcs = ["idea/plugins/IntelliLang/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.platform-api[module]",
        "//tools/idea/.idea/libraries:Jaxen",
    ],
)

iml_module(
    name = "idea.resources",
    srcs = ["idea/resources/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.MM_RegExpSupport",
        "//tools:idea.community-resources",
    ],
    deps = [
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.community-resources[module]",
        "//tools:idea.util[module]",
    ],
)

iml_module(
    name = "idea.openapi",
    srcs = ["idea/java/openapi/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
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
)

iml_module(
    name = "idea.execution-openapi",
    srcs = ["idea/java/execution/openapi/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.xdebugger-api"],
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.resources[module]",
        "//tools:idea.xdebugger-api[module]",
    ],
)

iml_module(
    name = "idea.compiler-openapi",
    srcs = ["idea/java/compiler/openapi/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools:idea.openapi[module]"],
)

iml_module(
    name = "idea.external-system-api",
    srcs = ["idea/platform/external-system-api/src"],
    javacopts = ["-extra_checks:off"],
    resources = ["idea/platform/external-system-api/resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.annotations",
        "//tools:idea.external-system-rt",
    ],
    deps = [
        "//tools:idea.annotations[module]",
        "//tools:idea.util[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.platform-api[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.compiler-openapi[module]",
        "//tools:idea.external-system-rt[module]",
    ],
)

iml_module(
    name = "idea.jsp-openapi",
    srcs = ["idea/java/jsp-openapi/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.jsp-base-openapi"],
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.util[module]",
        "//tools:idea.jsp-base-openapi[module]",
    ],
)

iml_module(
    name = "idea.debugger-openapi",
    srcs = ["idea/java/debugger/openapi/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.jsp-openapi[module]",
        "//tools:idea.java-psi-api[module]",
        "//tools:idea.resources-en[module]",
    ],
)

iml_module(
    name = "idea.copyright",
    srcs = ["idea/plugins/copyright/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.jsp-openapi[module]",
        "//tools:idea.xml-openapi[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.openapi[module]",
        "//tools/idea/.idea/libraries:Velocity",
    ],
)

iml_module(
    name = "idea.jsp-spi",
    srcs = ["idea/java/jsp-spi/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.jsp-openapi",
        "//tools:idea.openapi",
    ],
    deps = [
        "//tools:idea.jsp-openapi[module]",
        "//tools:idea.openapi[module]",
        "//tools:idea.jsp-base-openapi[module]",
    ],
)

iml_module(
    name = "idea.structuralsearch",
    srcs = ["idea/platform/structuralsearch/source"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
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
)

iml_module(
    name = "idea.properties-psi-api",
    srcs = [
        "idea/plugins/properties/properties-psi-api/src",
        "idea/plugins/properties/properties-psi-api/gen",
    ],
    javacopts = ["-extra_checks:off"],
    resources = ["idea/plugins/properties/properties-psi-api/resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.editor-ui-api[module]",
        "//tools:idea.analysis-api[module]",
        "//tools:idea.indexing-api[module]",
        "//tools:idea.xml-psi-api[module]",
        "//tools:idea.projectModel-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.lang-api[module]",
        "//tools:idea.boot[module]",
    ],
)

iml_module(
    name = "idea.properties-psi-impl",
    srcs = ["idea/plugins/properties/properties-psi-impl/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.properties-psi-api"],
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
)

iml_module(
    name = "idea.testRunner",
    srcs = ["idea/platform/testRunner/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.platform-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.xdebugger-api[module]",
    ],
)

iml_module(
    name = "idea.smRunner",
    srcs = ["idea/platform/smRunner/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/platform/smRunner/testSrc"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.testRunner",
        "//tools/idea/.idea/libraries:tcServiceMessages",
    ],
    deps = [
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.testRunner[module]",
        "//tools:idea.xdebugger-api[module]",
        "//tools/idea/.idea/libraries:tcServiceMessages",
        "//tools:idea.annotations[module]",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools:idea.lang-api[module]",
    ],
)

iml_module(
    name = "idea.platform-main",
    srcs = ["idea/platform/platform-main/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.bootstrap[module]",
    ],
)

iml_module(
    name = "idea.jps-model-tests",
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/jps/model-impl/testSrc"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.MM_RegExpSupport"],
    deps = [
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.MM_RegExpSupport[module]",
    ],
)

iml_module(
    name = "idea.jps-serialization-tests",
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/jps/model-serialization/testSrc"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.jps-model-tests"],
    deps = [
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea.jps-model-tests[module, test]",
    ],
)

iml_module(
    name = "idea.jps-builders",
    srcs = ["idea/jps/jps-builders/src"],
    exclude = [
        "idea/jps/jps-builders/src/org/jetbrains/jps/javac/OptimizedFileManager.java",
        "idea/jps/jps-builders/src/org/jetbrains/jps/javac/OptimizedFileManager17.java",
    ],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/jps/jps-builders/testSrc"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.util",
        "//tools/idea/.idea/libraries:asm5",
        "//tools/idea/.idea/libraries:protobuf",
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
        "//tools:idea/jps/lib/optimizedFileManager",
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
)

iml_module(
    name = "idea.ant-jps-plugin",
    srcs = ["idea/plugins/ant/jps-plugin/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/plugins/ant/jps-plugin/testSrc"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea.util[module]",
        "//tools:idea.jps-builders[module]",
        "//tools:idea.java-runtime[module]",
        "//tools:idea.jps-serialization-tests[module, test]",
    ],
)

iml_module(
    name = "idea.gradle-jps-plugin",
    srcs = ["idea/plugins/gradle/jps-plugin/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:Ant"],
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
)

iml_module(
    name = "idea.groovy-jps-plugin",
    srcs = ["idea/plugins/groovy/jps-plugin/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-builders[module]",
        "//tools:idea.groovy-rt-constants[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea.jps-model-impl[module]",
        "//tools/idea/.idea/libraries:Groovy-Eclipse-Batch",
        "//tools:idea.instrumentation-util[module]",
    ],
)

iml_module(
    name = "idea.groovy_rt",
    srcs = ["idea/plugins/groovy/rt/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:Groovy"],
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools:idea.jps-builders[module]",
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.groovy-rt-constants[module]",
        "//tools/idea/.idea/libraries:griffon-rt",
        "//tools/idea/.idea/libraries:Slf4j",
    ],
)

iml_module(
    name = "idea.maven-jps-plugin",
    srcs = ["idea/plugins/maven/jps-plugin/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/plugins/maven/jps-plugin/testSrc"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.util[module]",
        "//tools:idea.jps-builders[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea/plugins/maven/lib/plexus-utils-2.0.6",
        "//tools:idea.jps-serialization-tests[module, test]",
    ],
)

iml_module(
    name = "idea.java-analysis-impl",
    srcs = [
        "idea/java/java-analysis-impl/src",
        "idea/plugins/InspectionGadgets/InspectionGadgetsAnalysis/src",
    ],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.analysis-impl",
        "//tools:idea.java-indexing-impl",
        "//tools:idea.java-psi-impl",
        "//tools:idea.projectModel-impl",
        "//tools:idea.java-analysis-api",
        "//tools/idea/.idea/libraries:asm5",
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
    javacopts = ["-extra_checks:off"],
    resources = ["idea/plugins/generate-tostring/resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.MM_RegExpSupport",
        "//tools:idea.java-psi-impl",
        "//tools:idea.java-indexing-impl",
        "//tools:idea.java-analysis-impl",
        "//tools:idea.java-structure-view",
    ],
    deps = [
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
    javacopts = ["-extra_checks:off"],
    resources = ["idea/platform/external-system-impl/resources"],
    tags = ["managed"],
    test_srcs = [
        "idea/platform/external-system-impl/testSrc",
        "idea/java/compiler/impl/testSrc",
    ],
    visibility = ["//visibility:public"],
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
)

iml_module(
    name = "idea.ByteCodeViewer",
    srcs = ["idea/plugins/ByteCodeViewer/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
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
)

iml_module(
    name = "idea.IntelliLang-java",
    srcs = ["idea/plugins/IntelliLang/java-support"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools/idea/.idea/libraries:asm5",
        "//tools:idea.platform-api[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.IntelliLang[module]",
    ],
)

iml_module(
    name = "idea.junit",
    srcs = ["idea/plugins/junit/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/plugins/junit/test"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.smRunner"],
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
        "//tools:idea.junit5_rt[module]",
        "//tools/idea/.idea/libraries:junit5_rt",
    ],
)

iml_module(
    name = "idea.groovy-psi",
    srcs = [
        "idea/plugins/groovy/groovy-psi/src",
        "idea/plugins/groovy/groovy-psi/gen",
    ],
    javacopts = ["-extra_checks:off"],
    resources = ["idea/plugins/groovy/groovy-psi/resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:Groovy"],
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
)

iml_module(
    name = "idea.eclipse",
    srcs = [
        "idea/plugins/eclipse/src",
        "idea/plugins/eclipse/gen",
    ],
    javacopts = ["-extra_checks:off"],
    resources = ["idea/plugins/eclipse/resources"],
    tags = ["managed"],
    test_srcs = ["idea/plugins/eclipse/testSources"],
    visibility = ["//visibility:public"],
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
)

iml_module(
    name = "idea.gradle-tooling-extension-api",
    srcs = ["idea/plugins/gradle/tooling-extension-api/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
    ],
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
)

iml_module(
    name = "idea.gradle-tooling-extension-impl",
    srcs = ["idea/plugins/gradle/tooling-extension-impl/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:Gradle"],
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
)

iml_module(
    name = "idea.properties",
    srcs = ["idea/plugins/properties/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/plugins/properties/testSrc"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.properties-psi-api",
        "//tools:idea.properties-psi-impl",
    ],
    deps = [
        "//tools:idea.lang-api[module]",
        "//tools:idea.platform-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.annotations[module]",
        "//tools:idea.resources[module, test]",
        "//tools:idea.properties-psi-api[module]",
        "//tools:idea.properties-psi-impl[module]",
        "//tools:idea.MM_idea-ui[module]",
    ],
)

iml_module(
    name = "idea.ant",
    srcs = ["idea/plugins/ant/src"],
    javacopts = ["-extra_checks:off"],
    resources = ["idea/plugins/ant/resources"],
    tags = ["managed"],
    test_srcs = ["idea/plugins/ant/tests/src"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools/idea/.idea/libraries:Ant",
        "//tools:idea.MM_RegExpSupport",
    ],
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
)

iml_module(
    name = "idea.java-i18n",
    srcs = ["idea/plugins/java-i18n/src"],
    javacopts = ["-extra_checks:off"],
    resources = ["idea/plugins/java-i18n/resources"],
    tags = ["managed"],
    test_srcs = ["idea/plugins/java-i18n/testSrc"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.properties",
        "//tools:idea.java-impl",
    ],
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
)

iml_module(
    name = "idea.structuralsearch-java",
    srcs = ["idea/java/structuralsearch-java/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.structuralsearch[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.openapi[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.duplicates-analysis[module]",
    ],
)

iml_module(
    name = "idea.typeMigration",
    srcs = ["idea/java/typeMigration/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/java/typeMigration/test"],
    visibility = ["//visibility:public"],
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
)

iml_module(
    name = "idea.testng",
    srcs = ["idea/plugins/testng/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/plugins/testng/testSources"],
    visibility = ["//visibility:public"],
    exports = ["//tools:idea.smRunner"],
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
)

iml_module(
    name = "idea.xslt-rt",
    srcs = ["idea/plugins/xpath/xslt-rt/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.xpath",
    srcs = [
        "idea/plugins/xpath/xpath-lang/src",
        "idea/plugins/xpath/xpath-view/src",
        "idea/plugins/xpath/xpath-lang/gen",
    ],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/plugins/xpath/xpath-lang/test"],
    visibility = ["//visibility:public"],
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
)

iml_module(
    name = "idea.IntelliLang-xml",
    srcs = ["idea/plugins/IntelliLang/xml-support"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools/idea/.idea/libraries:asm5",
        "//tools:idea.xpath[module]",
        "//tools:idea.platform-api[module]",
        "//tools/idea/.idea/libraries:Jaxen",
        "//tools:idea.IntelliLang[module]",
    ],
)

iml_module(
    name = "idea.jetgroovy",
    srcs = ["idea/plugins/groovy/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["idea/plugins/groovy/test"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools:idea.groovy-psi",
    ],
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
)

iml_module(
    name = "idea.MM_maven2-server-impl",
    srcs = [
        "idea/plugins/maven/maven2-server-impl/src",
        "idea/plugins/maven/src/main/java",
    ],
    javacopts = ["-extra_checks:off"],
    resources = ["idea/plugins/maven/src/main/resources"],
    tags = ["managed"],
    test_srcs = [
        "idea/plugins/maven/maven2-server-impl/test",
        "idea/plugins/maven/src/test/java",
    ],
    visibility = ["//visibility:public"],
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
    deps = [
        "//tools:idea.maven-server-api[module]",
        "//tools:idea.util[module]",
        "//tools:idea/plugins/maven/maven2-server-impl/lib/plexus-utils-1.5.5",
        "//tools/idea/.idea/libraries:Maven",
        "//tools:idea/plugins/maven/maven2-server-impl/lib/nexus-indexer-1.2.3",
        "//tools:idea/plugins/maven/maven2-server-impl/lib/activation-1.1",
        "//tools/idea/.idea/libraries:commons-logging",
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
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools/idea/.idea/libraries:XmlBeans",
        "//tools:idea/plugins/maven/lib/wadl-core",
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
        "//tools:idea/plugins/maven/lib/plexus-archiver-2.4.4",
        "//tools:idea.external-system-api[module]",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools:idea.util-tests[module, test]",
    ],
)

iml_module(
    name = "idea.gradle",
    srcs = ["idea/plugins/gradle/src"],
    javacopts = ["-extra_checks:off"],
    resources = ["idea/plugins/gradle/resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:idea.external-system-api",
        "//tools:idea.MM_idea-ui",
        "//tools:idea.gradle-tooling-extension-api",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:GradleGuava",
        "//tools/idea/.idea/libraries:commons-io",
    ],
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
)

iml_module(
    name = "idea.gradle-tooling-extension-tests",
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_resources = ["idea/plugins/gradle/tooling-extension-impl/testData"],
    test_srcs = ["idea/plugins/gradle/tooling-extension-impl/testSources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.gradle-tooling-extension-impl[module]",
        "//tools:idea.gradle[module]",
        "//tools:idea.MM_idea-ui[module]",
    ],
)

iml_module(
    name = "idea.gradle-tests",
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = [
        "idea/plugins/gradle/testData",
        "idea/plugins/gradle/testSources",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.gradle[module, test]",
        "//tools:idea.gradle-tooling-extension-tests[module, test]",
        "//tools:idea.maven-server-api[module, test]",
    ],
)

java_import(
    name = "idea/lib/protobuf-2.5.0",
    jars = ["idea/lib/protobuf-2.5.0.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/guava-18.0",
    jars = ["idea/lib/guava-18.0.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/hamcrest-core-1.3",
    jars = ["idea/lib/hamcrest-core-1.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/junit-4.12",
    jars = ["idea/lib/junit-4.12.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/gson-2.5",
    jars = ["idea/lib/gson-2.5.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/trove4j",
    jars = ["idea/lib/trove4j.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/jsr305",
    jars = ["idea/lib/jsr305.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/junit",
    jars = ["idea/lib/junit.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/httpcore-4.4.1",
    jars = ["idea/lib/httpcore-4.4.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/httpmime-4.4.1",
    jars = ["idea/lib/httpmime-4.4.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/httpclient-4.4.1",
    jars = ["idea/lib/httpclient-4.4.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/fluent-hc-4.4.1",
    jars = ["idea/lib/fluent-hc-4.4.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/xmlrpc-2.0",
    jars = ["idea/lib/xmlrpc-2.0.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-commons-net",
    jars = ["idea/lib/ant/lib/ant-commons-net.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-jmf",
    jars = ["idea/lib/ant/lib/ant-jmf.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-apache-resolver",
    jars = ["idea/lib/ant/lib/ant-apache-resolver.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-jai",
    jars = ["idea/lib/ant/lib/ant-jai.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-apache-bsf",
    jars = ["idea/lib/ant/lib/ant-apache-bsf.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-commons-logging",
    jars = ["idea/lib/ant/lib/ant-commons-logging.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-junit",
    jars = ["idea/lib/ant/lib/ant-junit.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-jsch",
    jars = ["idea/lib/ant/lib/ant-jsch.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-apache-bcel",
    jars = ["idea/lib/ant/lib/ant-apache-bcel.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant",
    jars = ["idea/lib/ant/lib/ant.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-netrexx",
    jars = ["idea/lib/ant/lib/ant-netrexx.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-apache-oro",
    jars = ["idea/lib/ant/lib/ant-apache-oro.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-antlr",
    jars = ["idea/lib/ant/lib/ant-antlr.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-jdepend",
    jars = ["idea/lib/ant/lib/ant-jdepend.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-launcher",
    jars = ["idea/lib/ant/lib/ant-launcher.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-apache-regexp",
    jars = ["idea/lib/ant/lib/ant-apache-regexp.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-apache-log4j",
    jars = ["idea/lib/ant/lib/ant-apache-log4j.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-swing",
    jars = ["idea/lib/ant/lib/ant-swing.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ant/lib/ant-javamail",
    jars = ["idea/lib/ant/lib/ant-javamail.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/asm-all",
    jars = ["idea/lib/asm-all.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/jdom",
    jars = ["idea/lib/jdom.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/jgoodies-forms",
    jars = ["idea/lib/jgoodies-forms.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/hamcrest-library-1.3",
    jars = ["idea/lib/hamcrest-library-1.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/testng/lib/testng",
    jars = ["idea/plugins/testng/lib/testng.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/testng/lib/jcommander",
    jars = ["idea/plugins/testng/lib/jcommander.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/coverage-agent",
    jars = ["idea/lib/coverage-agent.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/coverage-instrumenter",
    jars = ["idea/lib/coverage-instrumenter.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/coverage-util",
    jars = ["idea/lib/coverage-util.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-artifact-3.0.5",
    jars = ["idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-artifact-3.0.5.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/maven2/lib/maven-2.2.1-uber",
    jars = ["idea/plugins/maven/maven2-server-impl/lib/maven2/lib/maven-2.2.1-uber.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/aether-api-1.13.1",
    jars = ["idea/plugins/maven/maven30-server-impl/lib/maven3/lib/aether-api-1.13.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-core-3.0.5",
    jars = ["idea/plugins/maven/maven30-server-impl/lib/maven3/lib/maven-core-3.0.5.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven30-server-impl/lib/maven3/lib/plexus-component-annotations-1.5.5",
    jars = ["idea/plugins/maven/maven30-server-impl/lib/maven3/lib/plexus-component-annotations-1.5.5.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/artifact-resolver-m31/lib/eclipse-aether/aether-api-0.9.0.M2",
    jars = ["idea/plugins/maven/artifact-resolver-m31/lib/eclipse-aether/aether-api-0.9.0.M2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/eawtstub",
    jars = ["idea/lib/eawtstub.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/log4j",
    jars = ["idea/lib/log4j.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/picocontainer",
    jars = ["idea/lib/picocontainer.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/jna",
    jars = ["idea/lib/jna.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/jna-platform",
    jars = ["idea/lib/jna-platform.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/oromatcher",
    jars = ["idea/lib/oromatcher.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/imgscalr-lib-4.2",
    jars = ["idea/lib/imgscalr-lib-4.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/batik-all",
    jars = ["idea/lib/batik-all.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/xmlgraphics-commons-1.5",
    jars = ["idea/lib/xmlgraphics-commons-1.5.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/xml-apis-ext",
    jars = ["idea/lib/xml-apis-ext.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/xstream-1.4.8",
    jars = ["idea/lib/xstream-1.4.8.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/xercesImpl",
    jars = ["idea/lib/xercesImpl.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/xml-apis",
    jars = ["idea/lib/xml-apis.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/automaton",
    jars = ["idea/lib/automaton.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/asm",
    jars = ["idea/lib/asm.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/asm-commons",
    jars = ["idea/lib/asm-commons.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/cglib-2.2.2",
    jars = ["idea/lib/cglib-2.2.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/nanoxml-2.2.3",
    jars = ["idea/lib/nanoxml-2.2.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/commons-codec-1.9",
    jars = ["idea/lib/commons-codec-1.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/proxy-vole_20131209",
    jars = ["idea/lib/proxy-vole_20131209.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/dev/easymock",
    jars = ["idea/lib/dev/easymock.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/dev/easymockclassextension",
    jars = ["idea/lib/dev/easymockclassextension.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/dev/jmock-2.5.1",
    jars = ["idea/lib/dev/jmock-2.5.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/dev/jmock-junit4-2.5.1",
    jars = ["idea/lib/dev/jmock-junit4-2.5.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/dev/jmock-legacy-2.5.1",
    jars = ["idea/lib/dev/jmock-legacy-2.5.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/dev/objenesis-1.0",
    jars = ["idea/lib/dev/objenesis-1.0.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/microba",
    jars = ["idea/lib/microba.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven-server-api/lib/lucene-core-2.4.1",
    jars = ["idea/plugins/maven/maven-server-api/lib/lucene-core-2.4.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/groovy-all-2.4.6",
    jars = ["idea/lib/groovy-all-2.4.6.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/jaxen-1.1.3",
    jars = ["idea/lib/jaxen-1.1.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/jsr173_1.0_api",
    jars = ["idea/lib/jsr173_1.0_api.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/xbean",
    jars = ["idea/lib/xbean.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/resolver",
    jars = ["idea/lib/resolver.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/jcip-annotations",
    jars = ["idea/lib/jcip-annotations.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/swingx-core-1.6.2",
    jars = ["idea/lib/swingx-core-1.6.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/xml/relaxng/lib/rngom-20051226-patched",
    jars = ["idea/xml/relaxng/lib/rngom-20051226-patched.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/xml/relaxng/lib/isorelax",
    jars = ["idea/xml/relaxng/lib/isorelax.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/xml/relaxng/lib/trang-core",
    jars = ["idea/xml/relaxng/lib/trang-core.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/xml/relaxng/lib/jing",
    jars = ["idea/xml/relaxng/lib/jing.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/sanselan-0.98-snapshot",
    jars = ["idea/lib/sanselan-0.98-snapshot.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/velocity",
    jars = ["idea/lib/velocity.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/xpp3-1.1.4-min",
    jars = ["idea/lib/xpp3-1.1.4-min.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/cli-parser-1.1",
    jars = ["idea/lib/cli-parser-1.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/commons-logging-1.2",
    jars = ["idea/lib/commons-logging-1.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/jh",
    jars = ["idea/lib/jh.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/winp-1.23",
    jars = ["idea/lib/winp-1.23.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/miglayout-swing",
    jars = ["idea/lib/miglayout-swing.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/jayatana-1.2.4",
    jars = ["idea/lib/jayatana-1.2.4.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/slf4j-api-1.7.10",
    jars = ["idea/lib/slf4j-api-1.7.10.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/slf4j-log4j12-1.7.10",
    jars = ["idea/lib/slf4j-log4j12-1.7.10.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/minlog-1.2",
    jars = ["idea/lib/minlog-1.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/kryo-2.22",
    jars = ["idea/lib/kryo-2.22.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/reflectasm-1.07",
    jars = ["idea/lib/reflectasm-1.07.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/objenesis-1.2",
    jars = ["idea/lib/objenesis-1.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/serviceMessages",
    jars = ["idea/lib/serviceMessages.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/jps/lib/optimizedFileManager",
    jars = ["idea/jps/lib/optimizedFileManager.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/ecj-4.5.2",
    jars = ["idea/lib/ecj-4.5.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/gradle-tooling-api-3.1",
    jars = ["idea/plugins/gradle/lib/gradle-tooling-api-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/gradle-core-3.1",
    jars = ["idea/plugins/gradle/lib/gradle-core-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/gradle-messaging-3.1",
    jars = ["idea/plugins/gradle/lib/gradle-messaging-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/gradle-model-core-3.1",
    jars = ["idea/plugins/gradle/lib/gradle-model-core-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/gradle-model-groovy-3.1",
    jars = ["idea/plugins/gradle/lib/gradle-model-groovy-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/gradle-wrapper-3.1",
    jars = ["idea/plugins/gradle/lib/gradle-wrapper-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/gradle-base-services-3.1",
    jars = ["idea/plugins/gradle/lib/gradle-base-services-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/gradle-base-services-groovy-3.1",
    jars = ["idea/plugins/gradle/lib/gradle-base-services-groovy-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/gradle-dependency-management-3.1",
    jars = ["idea/plugins/gradle/lib/gradle-dependency-management-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/gradle-native-3.1",
    jars = ["idea/plugins/gradle/lib/gradle-native-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/gradle-resources-3.1",
    jars = ["idea/plugins/gradle/lib/gradle-resources-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/groovy/lib/groovy-eclipse-batch-2.3.4-01",
    jars = ["idea/plugins/groovy/lib/groovy-eclipse-batch-2.3.4-01.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/groovy/testdata/griffon/griffon-rt-1.1.0",
    jars = ["idea/plugins/groovy/testdata/griffon/griffon-rt-1.1.0.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/groovy/testdata/griffon/griffon-cli-1.1.0",
    jars = ["idea/plugins/groovy/testdata/griffon/griffon-cli-1.1.0.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/lib/plexus-utils-2.0.6",
    jars = ["idea/plugins/maven/lib/plexus-utils-2.0.6.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/nekohtml-1.9.14",
    jars = ["idea/lib/nekohtml-1.9.14.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/gradle-logging-3.1",
    jars = ["idea/plugins/gradle/lib/gradle-logging-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/gradle-process-services-3.1",
    jars = ["idea/plugins/gradle/lib/gradle-process-services-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/guava-jdk5-17.0",
    jars = ["idea/plugins/gradle/lib/guava-jdk5-17.0.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-build-init-3.1",
    jars = ["idea/plugins/gradle/tooling-extension-api/lib/gradle-build-init-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-ide-3.1",
    jars = ["idea/plugins/gradle/tooling-extension-api/lib/gradle-ide-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-language-java-3.1",
    jars = ["idea/plugins/gradle/tooling-extension-api/lib/gradle-language-java-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-language-jvm-3.1",
    jars = ["idea/plugins/gradle/tooling-extension-api/lib/gradle-language-jvm-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-platform-base-3.1",
    jars = ["idea/plugins/gradle/tooling-extension-api/lib/gradle-platform-base-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-platform-jvm-3.1",
    jars = ["idea/plugins/gradle/tooling-extension-api/lib/gradle-platform-jvm-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-plugins-3.1",
    jars = ["idea/plugins/gradle/tooling-extension-api/lib/gradle-plugins-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-testing-base-3.1",
    jars = ["idea/plugins/gradle/tooling-extension-api/lib/gradle-testing-base-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-testing-jvm-3.1",
    jars = ["idea/plugins/gradle/tooling-extension-api/lib/gradle-testing-jvm-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-scala-3.1",
    jars = ["idea/plugins/gradle/tooling-extension-api/lib/gradle-scala-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-api/lib/gradle-language-scala-3.1",
    jars = ["idea/plugins/gradle/tooling-extension-api/lib/gradle-language-scala-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-impl/lib/gradle-reporting-3.1",
    jars = ["idea/plugins/gradle/tooling-extension-impl/lib/gradle-reporting-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/tooling-extension-impl/lib/gradle-ear-3.1",
    jars = ["idea/plugins/gradle/tooling-extension-impl/lib/gradle-ear-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/plexus-utils-1.5.5",
    jars = ["idea/plugins/maven/maven2-server-impl/lib/plexus-utils-1.5.5.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/maven-dependency-tree-1.2",
    jars = ["idea/plugins/maven/maven2-server-impl/lib/maven-dependency-tree-1.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/archetype-common-2.0-alpha-4-SNAPSHOT",
    jars = ["idea/plugins/maven/maven2-server-impl/lib/archetype-common-2.0-alpha-4-SNAPSHOT.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/mercury-artifact-1.0-alpha-6",
    jars = ["idea/plugins/maven/maven2-server-impl/lib/mercury-artifact-1.0-alpha-6.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/maven2/boot/classworlds-1.1",
    jars = ["idea/plugins/maven/maven2-server-impl/lib/maven2/boot/classworlds-1.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/nexus-indexer-1.2.3",
    jars = ["idea/plugins/maven/maven2-server-impl/lib/nexus-indexer-1.2.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/activation-1.1",
    jars = ["idea/plugins/maven/maven2-server-impl/lib/activation-1.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/maven2-server-impl/lib/commons-beanutils",
    jars = ["idea/plugins/maven/maven2-server-impl/lib/commons-beanutils.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/lib/wadl-core",
    jars = ["idea/plugins/maven/lib/wadl-core.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/maven/lib/plexus-archiver-2.4.4",
    jars = ["idea/plugins/maven/lib/plexus-archiver-2.4.4.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/gradle-cli-3.1",
    jars = ["idea/plugins/gradle/lib/gradle-cli-3.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/jsr305-1.3.9",
    jars = ["idea/plugins/gradle/lib/jsr305-1.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/commons-io-2.2",
    jars = ["idea/plugins/gradle/lib/commons-io-2.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/jna-3.2.7",
    jars = ["idea/plugins/gradle/lib/jna-3.2.7.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/gradle/lib/native-platform-0.11",
    jars = ["idea/plugins/gradle/lib/native-platform-0.11.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "idea/java/jdkAnnotations",
    srcs = glob(["idea/java/jdkAnnotations/**"]),
    visibility = ["//visibility:public"],
)

iml_module(
    name = "idea.junit5_rt",
    srcs = ["idea/plugins/junit5_rt/src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools:idea.junit_rt[module]",
        "//tools:idea.java-runtime[module]",
        "//tools/idea/.idea/libraries:junit5_rt",
        "//tools/idea/.idea/libraries:opentest4j",
    ],
)

java_import(
    name = "idea/lib/commons-compress-1.10",
    jars = ["idea/lib/commons-compress-1.10.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/junit5_rt/lib/junit-jupiter-engine-5.0.0-M1",
    jars = ["idea/plugins/junit5_rt/lib/junit-jupiter-engine-5.0.0-M1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/junit5_rt/lib/junit-platform-commons-1.0.0-M1",
    jars = ["idea/plugins/junit5_rt/lib/junit-platform-commons-1.0.0-M1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/junit5_rt/lib/junit-platform-engine-1.0.0-M1",
    jars = ["idea/plugins/junit5_rt/lib/junit-platform-engine-1.0.0-M1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/junit5_rt/lib/junit-platform-launcher-1.0.0-M1",
    jars = ["idea/plugins/junit5_rt/lib/junit-platform-launcher-1.0.0-M1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/junit5_rt/lib/junit-platform-runner-1.0.0-M1",
    jars = ["idea/plugins/junit5_rt/lib/junit-platform-runner-1.0.0-M1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/junit5_rt/lib/junit-vintage-engine-4.12.0-M1",
    jars = ["idea/plugins/junit5_rt/lib/junit-vintage-engine-4.12.0-M1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/plugins/junit5_rt/lib/opentest4j-1.0.0-M1",
    jars = ["idea/plugins/junit5_rt/lib/opentest4j-1.0.0-M1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/netty-all-4.1.1.Final",
    jars = ["idea/lib/netty-all-4.1.1.Final.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/pty4j-0.7.1",
    jars = ["idea/lib/pty4j-0.7.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/imageio-tiff-3.2.1",
    jars = ["idea/lib/imageio-tiff-3.2.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/imageio-core-3.2.1",
    jars = ["idea/lib/imageio-core-3.2.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/common-lang-3.2.1",
    jars = ["idea/lib/common-lang-3.2.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/common-io-3.2.1",
    jars = ["idea/lib/common-io-3.2.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/common-image-3.2.1",
    jars = ["idea/lib/common-image-3.2.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/imageio-metadata-3.2.1",
    jars = ["idea/lib/imageio-metadata-3.2.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/dev/assertj-core-3.4.1",
    jars = ["idea/lib/dev/assertj-core-3.4.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "idea/lib/snappy-in-java-0.5.1",
    jars = ["idea/lib/snappy-in-java-0.5.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "fest-swing",
    srcs = ["swing-testing/fest-swing/src/main/java"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_resources = ["swing-testing/fest-swing/src/test/resources"],
    test_srcs = ["swing-testing/fest-swing/src/test/java"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools:swing-testing/fest-swing/lib/fest-reflect-2.0-SNAPSHOT",
        "//tools:swing-testing/fest-swing/lib/fest-util-1.3.0-SNAPSHOT",
    ],
    deps = [
        "//tools:swing-testing/fest-swing/lib/fest-reflect-2.0-SNAPSHOT",
        "//tools:swing-testing/fest-swing/lib/fest-util-1.3.0-SNAPSHOT",
        "//tools:swing-testing/fest-swing/lib/fest-assert-1.5.0-SNAPSHOT",
        "//tools:swing-testing/fest-swing/lib/jsr305-1.3.9",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools:swing-testing/fest-swing/lib/MultithreadedTC-1.01[test]",
    ],
)

java_import(
    name = "swing-testing/fest-swing/lib/fest-reflect-2.0-SNAPSHOT",
    jars = ["swing-testing/fest-swing/lib/fest-reflect-2.0-SNAPSHOT.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "swing-testing/fest-swing/lib/fest-util-1.3.0-SNAPSHOT",
    jars = ["swing-testing/fest-swing/lib/fest-util-1.3.0-SNAPSHOT.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "swing-testing/fest-swing/lib/fest-assert-1.5.0-SNAPSHOT",
    jars = ["swing-testing/fest-swing/lib/fest-assert-1.5.0-SNAPSHOT.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "swing-testing/fest-swing/lib/jsr305-1.3.9",
    jars = ["swing-testing/fest-swing/lib/jsr305-1.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "swing-testing/fest-swing/lib/MultithreadedTC-1.01",
    jars = ["swing-testing/fest-swing/lib/MultithreadedTC-1.01.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)
