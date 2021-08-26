package(default_visibility = ["//visibility:public"])

java_library(
    name = "agent",
    srcs = glob(["src/main/java/**/*.java"]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
        "@maven//:androidx.annotation.annotation",
    ],
)
