load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/fakeandroid:fakeandroid.bzl", "fake_android_test")

# Convert a list of targets to locations separated by ':'
def _targets_to_paths(targets):
    paths = ""
    for target in targets:
        if paths != "":
            paths += ":"

        paths += "$(location " + target + ")"
    return paths

# Run an integration test that communicates between test code and a fake
# Android device over the Transport API.
#
# srcs: One or more test classes to run under this test.
# app_dexes: A list of one or more targets referencing dexed components which
#            will get installed on a fake android device. The first target must
#            the core app itself, with any additional targets provided to
#            support it.
#            Note: The test framework provides a very basic app behind the
#            "test-framework/test-app:test-app" rule which can be used here.
# app_dexes_nojvmti: Like `app_dexes` but will be pushed if the target device
#                    does not support JVMTI. If not specified, `app_dexes`
#                    will be re-used.
#
# The rest of the arguments are standard parameters.
def transport_test(name, srcs, app_dexes, app_dexes_nojvmti = [], deps = [], runtime_deps = [], jvm_flags = [], data = [], tags = [], size = "small"):
    if app_dexes_nojvmti == []:
        app_dexes_nojvmti = app_dexes

    fake_android_test(
        name = name,
        srcs = srcs,
        deps = deps + app_dexes + [
            "//tools/base/transport/test-framework:test-framework",
            "//tools/base/fakeandroid",
            "//tools/base/transport/proto:transport_java_proto",
            "//tools/base/bazel:studio-grpc",
            "//tools/base/bazel:studio-proto",
            "//tools/base/transport/test-framework/test-app:libjni.so",
            "//tools/base/transport/native/agent:libjvmtiagent.so",
            "//tools/base/transport:transport_main",
        ],
        runtime_deps = runtime_deps + select({
            "//tools/base/bazel:darwin": [],
            "//tools/base/bazel:windows": [],
            "//conditions:default": [
                "//tools/base/transport/native/agent:libjvmtiagent.so",
            ],
        }),
        tags = tags,
        size = size,
        jvm_flags = jvm_flags + [
            "-Dtransport.daemon.location=$(location //tools/base/transport:transport_main)",
            "-Dtransport.agent.location=$(location //tools/base/transport/native/agent:libjvmtiagent.so)",
            "-Dnative.lib.location=$(location " + app_dexes[0] + ")",
            "-Dapp.dexes.jvmti=" + _targets_to_paths(app_dexes),
            "-Dapp.dexes.nojvmti=" + _targets_to_paths(app_dexes_nojvmti),
        ],
        data = data + select({
            "//tools/base/bazel:darwin": [],
            "//tools/base/bazel:windows": [],
            "//conditions:default": [
                "//tools/base/transport/native/agent:libjvmtiagent.so",
            ],
        }),
    )
