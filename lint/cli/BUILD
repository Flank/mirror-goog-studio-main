java_library(
    name = "cli",
    # TODO: move resources out of java?
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(
        ["src/main/java/**"],
        exclude = ["src/main/java/**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.android.tools.external.com-intellij_uast",
        "//tools/base/third_party:com.android.tools.external.lombok_lombok-ast",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.eclipse.jdt.core.compiler_ecj",
    ],
)
