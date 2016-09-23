exports_files(glob(["**"]))

java_library(
    name = "tools.lint-api",
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
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.android.tools.external.com-intellij_uast",
        "//tools/base/third_party:com.android.tools.external.lombok_lombok-ast",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
    ],
)
