java_library(
    name = "cli",
    # TODO: move resources out of java?
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(
        include = ["src/main/java/**"],
        exclude = ["src/main/java/**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/uast:uast-162.2228.14",
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.android.tools.external.lombok_lombok-ast",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.eclipse.jdt.core.compiler_ecj",
    ],
)
