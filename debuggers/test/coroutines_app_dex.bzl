load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:utils.bzl", "flat_archive")

# rules used to build the app's dex using a specific version of coroutine lib
def coroutines_app_dex(name, coroutines_libs, meta_inf_resources = []):
    kotlin_library(
        name = "app_kotlin_%s" % name,
        testonly = True,
        srcs = native.glob([
            "data/app/*.kt",
        ]),
        deps = [
            coroutines_libs,
            "//tools/base/fakeandroid:android-mock",
        ],
    )
    native.java_binary(
        name = "app_java_%s" % name,
        create_executable = 0,
        runtime_deps = [
            ":app_kotlin_%s" % name,
        ],
    )
    dex_library(
        name = name + "_dex",
        dexer = "D8",
        # Test dex compiles with a non-release build.
        # Also make it desugar as much as possible with API 23.
        flags = [
            "--debug",
            "--min-api 23",
        ],
        jars = [":app_java_%s_deploy.jar" % name],
    )

    # At this point, we have a jar file containing a classes.dex.
    # Since we are creating a fake apk, META-INF/kotlinx_coroutines_core.version is lost.
    # We should manually add it to our final APK.
    # This rule extracts classes.dex from the jar.
    native.genrule(
        name = name + "_unzipped",
        srcs = [name + "_dex.jar"],
        outs = [name + "_dexed/classes.dex"],
        tools = ["@bazel_tools//tools/zip:zipper"],
        cmd = "$(location @bazel_tools//tools/zip:zipper) x $< -d $(@D)",
    )
    mapping = {}
    for resource in meta_inf_resources:
        mapping[resource] = "META-INF"
    flat_archive(
        name = name,
        ext = "apk",
        deps = mapping,
        files = {name + "_dexed/classes.dex": "classes.dex"},
        visibility = ["//visibility:public"],
    )
