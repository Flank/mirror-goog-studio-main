load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/fakeandroid:fakeandroid.bzl", "fake_android_test")

# Create a mock app given a collection of source files.
#
# After calling this rule, a target for the app with the specified name will be
# generated, which you can use as an argument to transport_test. The target
# will be backed by a single output jar named "$name_undexed_deploy.jar"
def transport_app(name, srcs, deps = []):
    # Build a deploy jar. The contents of this jar will be dexed before being
    # pushed onto a fake device.
    #
    # Note: Because android-mock is set to never link, compile dependencies use
    # android-mock but the built jar does not include it.
    native.java_binary(
        name = name + "_undexed",
        srcs = srcs,
        create_executable = 0,
        deps = deps + [
            "//tools/base/fakeandroid:android-mock",
        ],
    )

    dex_library(
        name = name,
        jars = [name + "_undexed_deploy.jar"],
        visibility = ["//visibility:public"],
    )

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
# app_runtime_deps: A list of zero or more targets to .so files or jar libraries needed
#                   by the app at at runtime.
#
# The rest of the arguments are standard parameters.
def transport_test(name,
                   srcs,
                   app_dexes,
                   app_dexes_nojvmti = [],
                   deps = [],
                   runtime_deps = [],
                   app_runtime_deps = [],
                   jvm_flags = [],
                   data = [],
                   tags = [],
                   shard_count = None,
                   size = None):
    app_runtime_deps = app_runtime_deps + [
        "//tools/base/profiler/app:perfa",
        "//tools/base/transport/native/agent:libjvmtiagent.so",
        "//tools/base/transport/test-framework/test-app:libjni.so",
    ]

    all_app_dexes = app_dexes
    if app_dexes_nojvmti == []:
        app_dexes_nojvmti = app_dexes
    else:
        all_app_dexes = all_app_dexes + app_dexes_nojvmti

    fake_android_test(
        name = name,
        srcs = srcs,
        deps = deps + [
            "//tools/base/transport/test-framework:test-framework",
            "//tools/base/fakeandroid",
            "//tools/base/transport/proto:transport_java_proto",
            "//tools/base/bazel:studio-grpc",
            "//tools/base/bazel:studio-proto",
            "//tools/base/transport:transport_main",
        ],
        runtime_deps = runtime_deps,
        tags = tags,
        shard_count = shard_count,
        size = size,
        jvm_flags = jvm_flags + [
            "-Dtransport.daemon.location=$(location //tools/base/transport:transport_main)",
            "-Dtransport.agent.location=$(location //tools/base/transport/native/agent:libjvmtiagent.so)",
            "-Dapp.libs=" + _targets_to_paths(app_runtime_deps),
            "-Dapp.dexes.jvmti=" + _targets_to_paths(app_dexes),
            "-Dapp.dexes.nojvmti=" + _targets_to_paths(app_dexes_nojvmti),
        ],
        # Data listed here to be made available for "$location" expansions
        data = all_app_dexes + app_runtime_deps + data,
    )
