load(":proto.bzl", "ProtoPackageInfo", "android_java_proto_library")
load(":maven.bzl", "maven_java_import")

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
    unpack_name = "_unpack_proto_" + name
    _unpack_app_inspection_proto(
        name = unpack_name,
        proto_file_name = proto_file_name,
        jar = jar,
        out = proto_file_name,
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
