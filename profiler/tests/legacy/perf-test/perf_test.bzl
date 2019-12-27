load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/fakeandroid:fakeandroid.bzl", "fake_android_test")

# Note to code reviewers: All changes in here can be ignored as these are
# temporary patches to get legacy tests running. This file will be deleted
# within a few followup CLs.

# Convert a list of targets to locations separated by ':'
def _targets_to_paths(targets):
    paths = ""
    for target in targets:
        if paths != "":
            paths += ":"

        paths += "$(location " + target + ")"
    return paths

def perf_test(name, srcs, test_app, deps = [], jvm_flags = [], data = [], tags = [], size = "small"):
    native.genrule(
        name = name + "_transform-app_java",
        srcs = [test_app + "_java_deploy.jar"],
        outs = [name + "_transform-app_java.jar"],
        cmd = select({
            "//tools/base/bazel:darwin": "cp ./$< ./$@",
            "//tools/base/bazel:windows": "cp ./$< ./$@",
            "//conditions:default": "$(location //tools/base/profiler/tests/profiler-transform-main:profilers-transform-main) ./$< ./$@",
        }),
        executable = 1,
        tools = select({
            "//tools/base/bazel:darwin": [],
            "//tools/base/bazel:windows": [],
            "//conditions:default": [
                "//tools/base/profiler/tests/profiler-transform-main:profilers-transform-main",
            ],
        }),
        tags = tags,
    )

    dex_library(
        name = name + "_transform-app",
        jars = [":" + name + "_transform-app_java"],
    )

    perf_app_runtime_deps = [
        "//tools/base/transport/native/agent:libjvmtiagent.so",
        "//tools/base/profiler/tests/legacy/test-app:libjni.so",
        "//tools/base/profiler/native/agent:libsupportjni.so",
        "//tools/base/profiler/app:perfa",
    ]

    fake_android_test(
        name = name,
        srcs = srcs,
        deps = deps + [
            ":profiler-service",
            "//tools/base/transport/proto:transport_java_proto",
            "//tools/base/bazel:studio-grpc",
            "//tools/base/bazel:studio-proto",
            "//tools/base/profiler/app:perfa",
            "//tools/base/transport/native/agent:libjvmtiagent.so",
            "//tools/base/transport:transport_main",
            ":" + name + "_transform-app",
            test_app,
        ],
        runtime_deps = select({
            "//tools/base/bazel:darwin": [],
            "//tools/base/bazel:windows": [],
            "//conditions:default": [
                ":profiler-service",
                "//tools/base/profiler:netty-grpc-jar",
                "//tools/base/profiler/native/agent:libsupportjni.so",
                "//tools/base/profiler/app:perfa",
                "//tools/base/profiler/app:perfa_java",
                "//tools/base/profiler/app:perfa_okhttp",
                "//tools/base/transport/native/agent:libjvmtiagent.so",
            ],
        }),
        tags = tags,
        size = size,
        jvm_flags = jvm_flags + [
            "-Dapp.libs=" + _targets_to_paths(perf_app_runtime_deps),
            "-Dperfd.location=$(location //tools/base/transport:transport_main)",
            "-Dagent.location=/tools/base/profiler/native/agent",
            "-Dprofiler.service.location=$(location :profiler-service)",
            "-Dperfa.dir.location=/tools/base/transport/native/agent",
            "-Dperfa.location=$(location //tools/base/transport/native/agent:libjvmtiagent.so)",
            "-Dperfa.jar.location=$(location //tools/base/profiler/app:perfa)",
            "-Dnative.lib.location=$(location " + test_app + ").dirname",
            # Non transformed app should be used here.
            "-Djvmti.app.dex.location=$(location " + test_app + ")",
            # Transformed app should be used here. This way we can test our support for both O+ and pre-O devices.
            "-Dinstrumented.app.dex.location=$(location :" + name + "_transform-app)",
        ],
        data = data + select({
            "//tools/base/bazel:darwin": [],
            "//tools/base/bazel:windows": [],
            "//conditions:default": [
                ":profiler-service",
                "//tools/base/profiler/app:perfa",
                "//tools/base/profiler/app:perfa_java",
                "//tools/base/profiler/app:perfa_okhttp",
                "//tools/base/transport/native/agent:libjvmtiagent.so",
                "//tools/base/profiler/native/agent:libsupportjni.so",
            ],
        }),
    )
