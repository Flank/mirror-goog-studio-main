load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

package(default_visibility = ["//visibility:public"])

cc_library(
    name = "profiler_sessions",
    srcs = [
        "sessions_manager.cc",
    ],
    hdrs = [
        "session_utils.h",
        "sessions_manager.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    deps = [
        "//tools/base/profiler/native/utils:profiler_utils",
    ],
)

cc_test(
    name = "perfd_sessions_tests",
    srcs = [
        "sessions_manager_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    data = [
    ],
    linkstatic = 1,
    deps = [
        ":profiler_sessions",
        "//external:gmock_main",
    ],
)
