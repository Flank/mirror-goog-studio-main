exports_files([
    "emulator_launcher.sh.template",
])

java_library(
    name = "emulator_rule",
    srcs = [
        "src/com/android/tools/bazel/avd/Emulator.java",
    ],
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//tools/base/third_party:junit_junit",
    ],
)
