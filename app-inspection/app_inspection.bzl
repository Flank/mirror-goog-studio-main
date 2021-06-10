load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:maven.bzl", "maven_java_import")
load("//tools/base/bazel:merge_archives.bzl", "merge_jars")
load("//tools/base/bazel:proto.bzl", "ProtoPackageInfo", "android_java_proto_library", "java_proto_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

def _impl(ctx):
    args = [ctx.file.jar.path] + [ctx.attr.proto_file_name + ":" + ctx.outputs.out.path]

    # Action to call the script.
    ctx.actions.run(
        inputs = [ctx.file.jar],
        outputs = [ctx.outputs.out],
        arguments = args,
        progress_message = "unzipping into %s" % ctx.outputs.out.short_path,
        executable = ctx.executable._unzip_tool,
    )
    return ProtoPackageInfo(
        proto_src = [ctx.outputs.out],
        proto_path = ctx.outputs.out.dirname,
    )

_unpack_app_inspection_proto = rule(
    implementation = _impl,
    attrs = {
        "jar": attr.label(allow_single_file = True),
        "proto_file_name": attr.string(mandatory = True),
        "out": attr.output(mandatory = True),
        "_unzip_tool": attr.label(
            executable = True,
            cfg = "host",
            allow_files = True,
            default = Label("//tools/base/bazel:unzipper"),
        ),
    },
)

# rule that unpack and compile proto from a prebuilt artifact provided by androidx
def app_inspection_proto(name, jar, proto_file_name, visibility = None):
    unpack_name = name + "-proto"
    _unpack_app_inspection_proto(
        name = unpack_name,
        proto_file_name = proto_file_name,
        jar = jar,
        out = proto_file_name,
        visibility = visibility,
    )
    java_proto_library(
        name = name + "-nojarjar",
        srcs = [":" + unpack_name],
        grpc_support = 1,
        protoc_grpc_version = "1.21.1",
        visibility = visibility,
    )
    android_java_proto_library(
        name = name,
        srcs = [":" + unpack_name],
        grpc_support = 1,
        protoc_grpc_version = "1.21.1",
        visibility = visibility,
    )

# Rule that re-exports an aar file as a jar, as a way to
# allow connecting .aar outputs with rules that require
# .jar inputs.
#
# In the process of re-exporting, Android specific concepts
# are discarded (e.g. resources, manifests).
# because these artifacts are not required by app inspection.
def app_inspection_aar_import(name, aar, **kwargs):
    unpacked_jar = name + "_unpacked_classes.jar"
    native.genrule(
        name = name + "_unpack",
        srcs = [aar],
        outs = [unpacked_jar],
        tools = ["//tools/base/bazel:unzipper"],
        cmd = "$(location //tools/base/bazel:unzipper) $< classes.jar:$@",
    )
    maven_java_import(
        name = name,
        jars = [unpacked_jar],
        **kwargs
    )

# Rule that encapsulates all of the intermediate steps in the building
# of an inspector jar.
#
# This macro expands into several rules named after the *name* of this rule:
#   name_undexed[.jar]
#   name_jarjared[.jar]
#   name_dexed[.jar]
#   name (the final rule that puts everything together)
#
# The resulting jar is named out.jar if out is provided. Otherwise name.jar.
def app_inspection_jar(
        name,
        proto,
        inspection_resources,
        inspection_resource_strip_prefix,
        jarjar_srcs = [],
        out = "",
        d8_flags = [],
        **kwargs):
    kotlin_library(
        name = name + "-sources_undexed",
        **kwargs
    )

    jarjar_srcs_dedup = [
        ":" + name + "-sources_undexed",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-stdlib/1.4.21:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.4.1:jar",
    ]
    for src in jarjar_srcs:
        if src not in jarjar_srcs_dedup:
            jarjar_srcs_dedup.append(src)
    java_jarjar(
        name = name + "-sources_jarjared",
        srcs = jarjar_srcs_dedup,
        rules = "//tools/base/bazel:jarjar_rules.txt",
    )

    dex_library(
        name = name + "-sources_dexed",
        dexer = "D8",
        flags = d8_flags,
        jars = [
            ":" + name + "-sources_jarjared",
            "//tools/base/bazel:studio-proto",
            proto,
        ],
    )

    native.java_library(
        name = name + "_inspection_resources",
        resource_strip_prefix = inspection_resource_strip_prefix,
        resources = inspection_resources,
    )

    output_name = out
    if (out == ""):
        output_name = name
    merge_jars(
        name = name,
        out = output_name,
        jars = [
            ":" + name + "-sources_dexed",
            ":" + name + "_inspection_resources",
        ],
    )
