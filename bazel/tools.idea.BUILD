load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "fest-swing",
    srcs = ["swing-testing/fest-swing/src/main/java"],
    iml_files = ["swing-testing/fest-swing/fest-swing.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    tags = ["managed"],
    test_resources = ["swing-testing/fest-swing/src/test/resources"],
    test_srcs = ["swing-testing/fest-swing/src/test/java"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools:swing-testing/fest-swing/lib/fest-reflect-2.0-SNAPSHOT",
        "//tools:swing-testing/fest-swing/lib/fest-util-1.3.0-SNAPSHOT",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools:swing-testing/fest-swing/lib/fest-reflect-2.0-SNAPSHOT",
        "//tools:swing-testing/fest-swing/lib/fest-util-1.3.0-SNAPSHOT",
        "//tools:swing-testing/fest-swing/lib/fest-assert-1.5.0-SNAPSHOT",
        "//tools:swing-testing/fest-swing/lib/annotations-13.0",
        "//tools:swing-testing/fest-swing/lib/jcip-annotations-1.0-1",
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
    name = "swing-testing/fest-swing/lib/annotations-13.0",
    jars = ["swing-testing/fest-swing/lib/annotations-13.0.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "swing-testing/fest-swing/lib/jcip-annotations-1.0-1",
    jars = ["swing-testing/fest-swing/lib/jcip-annotations-1.0-1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "swing-testing/fest-swing/lib/MultithreadedTC-1.01",
    jars = ["swing-testing/fest-swing/lib/MultithreadedTC-1.01.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)
