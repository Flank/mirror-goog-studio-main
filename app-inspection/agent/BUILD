package(default_visibility = ["//visibility:public"])

java_library(
    name = "agent",
    srcs = glob(["src/main/java/**/*.java"]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0:jar",
    ],
)
