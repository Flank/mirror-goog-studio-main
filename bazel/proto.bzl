load(":functions.bzl", "label_workspace_path", "workspace_path")
load(":maven.bzl", "maven_java_library")
load(":utils.bzl", "java_jarjar")
load(":android.bzl", "select_android")

# Enum-like values to determine the language the gen_proto rule will compile
# the .proto files to.
proto_languages = struct(
    CPP = 0,
    JAVA = 1,
)

PROTOC_VERSION = "3.10.0"

ProtoPackageInfo = provider(fields = ["proto_src", "proto_path"])

def _gen_proto_impl(ctx):
    inputs = []
    inputs += ctx.files.srcs + ctx.files.include

    args = []
    needs_label_path = False
    for src_target in ctx.attr.srcs:
        if ProtoPackageInfo in src_target:
            args += ["--proto_path=" + workspace_path(src_target[ProtoPackageInfo].proto_path)]
        else:
            # if src_target doesn't have ProtoPackageInfo provider that should be used to look up proto files
            # then we're going to path where BUILD is placed.
            # Example: //rule/proto/BUILD -> --proto_path="rule/proto"
            needs_label_path = True

    label_dir = label_workspace_path(ctx.label)
    if needs_label_path:
        args += ["--proto_path=" + label_dir]

    args += [
        "--proto_path=" + workspace_path("prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/" + ctx.attr.proto_include_version + "/include"),
    ]

    for dep in ctx.attr.deps:
        args += ["--proto_path=" + workspace_path(dep[ProtoPackageInfo].proto_path)]
        inputs += dep[ProtoPackageInfo].proto_src

    args += [s.path for s in ctx.files.srcs]

    # Try to generate cc protos first.
    if ctx.attr.target_language == proto_languages.CPP:
        out_path = ctx.var["GENDIR"] + "/" + label_dir
        args += [
            "--cpp_out=" + out_path,
        ]
        if ctx.executable.grpc_plugin != None and ctx.executable.grpc_plugin.path != None:
            args += [
                "--grpc_out=" + out_path,
                "--plugin=protoc-gen-grpc=" + ctx.executable.grpc_plugin.path,
            ]
        outs = ctx.outputs.outs

        # Try to generate java protos only if we won't generate cc protos
    elif ctx.attr.target_language == proto_languages.JAVA:
        srcjar = ctx.outputs.outs[0]  # outputs.out size should be 1
        outs = [ctx.actions.declare_file(srcjar.basename + ".jar")]

        out_path = outs[0].path
        args += [
            "--java_out=" + out_path,
        ]
        if ctx.executable.grpc_plugin != None:
            args += [
                "--java_rpc_out=" + out_path,
                "--plugin=protoc-gen-java_rpc=" + ctx.executable.grpc_plugin.path,
            ]

    tools = []
    if ctx.executable.grpc_plugin != None:
        tools += [ctx.executable.grpc_plugin]

    ctx.actions.run(
        mnemonic = "GenProto",
        inputs = inputs,
        outputs = outs,
        tools = tools,
        arguments = args,
        executable = ctx.executable.protoc,
    )

    if ctx.attr.target_language == proto_languages.JAVA:
        # This is required because protoc only understands .jar extensions, but Bazel
        # requires source JAR files end in .srcjar.
        ctx.actions.run_shell(
            mnemonic = "FixProtoSrcJar",
            inputs = outs,
            outputs = [srcjar],
            command = "cp " + srcjar.path + ".jar" + " " + srcjar.path,
        )

    return ProtoPackageInfo(
        proto_src = ctx.files.srcs,
        proto_path = ctx.label.package,
    )

_gen_proto_rule = rule(
    attrs = {
        "srcs": attr.label_list(
            allow_files = [".proto"],
            providers = [ProtoPackageInfo],
        ),
        "deps": attr.label_list(
            allow_files = False,
            providers = [ProtoPackageInfo],
        ),
        "include": attr.label(
            allow_files = [".proto"],
        ),
        "proto_include_version": attr.string(),
        "protoc": attr.label(
            cfg = "exec",
            executable = True,
            mandatory = True,
            allow_single_file = True,
        ),
        "grpc_plugin": attr.label(
            cfg = "exec",
            executable = True,
            allow_single_file = True,
        ),
        "target_language": attr.int(),
        "outs": attr.output_list(),
    },
    output_to_genfiles = True,
    implementation = _gen_proto_impl,
)

def java_proto_library(
        name,
        srcs = None,
        proto_deps = [],
        java_deps = [],
        pom = None,
        visibility = None,
        grpc_support = False,
        protoc_version = PROTOC_VERSION,
        protoc_grpc_version = None,
        proto_java_runtime_library = ["@//tools/base/third_party:com.google.protobuf_protobuf-java"],
        **kwargs):
    """Compiles protobuf into a .jar file and optionally creates a maven artifact.

    NOTE: Be cautious to use this rule. You may need to use android_java_proto_library instead.
    See the comments in android_java_proto_library rule before using it.

    Args:
      name: Name of the rule.
      srcs:  A list of file names of the protobuf definition to compile.
      proto_deps: A list of dependent proto_library to compile the library.
      java_deps: An additional java libraries to be packaged into the library.
      pom: A label of maven_pom target. If present, the rule creates maven artifact of the library.
      visibility: Visibility of the rule.
      grpc_support: True if the proto library requires grpc protoc plugin.
      protoc_version: The protoc version to use.
      protoc_grpc_version: A version of the grpc protoc plugin to use.
      proto_java_runtime_library: A label of java_library to be loaded at runtime.
    """

    # Targets that require grpc support should specify the version of protoc-gen-grpc-java plugin.
    if grpc_support and not protoc_grpc_version:
        fail("grpc support was requested, but the version of grpc java protoc plugin was not specified")

    srcs_name = name + "_srcs"
    outs = [srcs_name + ".srcjar"]
    _gen_proto_rule(
        name = srcs_name,
        srcs = srcs,
        deps = proto_deps,
        include = "@//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/" + protoc_version + "/include",
        outs = outs,
        proto_include_version = protoc_version,
        protoc = "@//prebuilts/tools/common/m2/repository/com/google/protobuf/protoc/" + protoc_version + ":exe",
        grpc_plugin =
            "@//prebuilts/tools/common/m2/repository/io/grpc/protoc-gen-grpc-java/" + protoc_grpc_version + ":exe" if grpc_support else None,
        target_language = proto_languages.JAVA,
        visibility = visibility,
    )

    grpc_extra_deps = ["@//prebuilts/tools/common/m2/repository/javax/annotation/javax.annotation-api/1.3.2:jar"]
    java_deps = list(java_deps) + (grpc_extra_deps if grpc_support else [])
    java_deps += proto_java_runtime_library

    if pom:
        maven_java_library(
            name = name,
            pom = pom,
            srcs = outs,
            deps = java_deps,
            baseline_coverage = False,
            visibility = visibility,
            **kwargs
        )
    else:
        native.java_library(
            name = name,
            srcs = outs,
            deps = java_deps,
            javacopts = kwargs.pop("javacopts", []) + ["--release", "8"],
            visibility = visibility,
            **kwargs
        )

def android_java_proto_library(
        name,
        srcs = None,
        grpc_support = False,
        protoc_grpc_version = None,
        java_deps = [],
        proto_deps = [],
        visibility = None):
    """Compiles protobuf into a .jar file in Android Studio compatible runtime version.

    Unlike java_proto_library rule defined above, android_java_proto_library
    repackage com.google.protobuf.* dependencies to com.android.tools.idea.protobuf
    by applying JarJar tool after the protobuf compilation. This repackaging is necessary
    in order to avoid version incompatibility to IntelliJ platform runtime dependencies.

    tl;dr; Use this rule if your proto library is linked to Android Studio, otherwise
    use java_proto_library.

    NOTE: The repackaged runtime is at //tools/base/bazel:studio-proto.

    Args:
      name: Name of the rule.
      srcs:  A list of file names of the protobuf definition to compile.
      grpc_support: True if the proto library requires grpc protoc plugin.
      protoc_grpc_version: A version of the grpc protoc plugin to use.
      java_deps: Any additional java libraries to be packaged into the library.
      proto_deps: Any protos depended upon by the protos in this library (via `import`)
      visibility: Visibility of the rule.
    """
    internal_name = "_" + name + "_internal"
    java_proto_library(name = internal_name, srcs = srcs, grpc_support = grpc_support, protoc_grpc_version = protoc_grpc_version, java_deps = java_deps, proto_deps = proto_deps)
    java_jarjar(
        name = name,
        srcs = [":" + internal_name],
        rules = "//tools/base/bazel:jarjar_rules.txt",
        visibility = visibility,
    )

def cc_grpc_proto_library(
        name,
        srcs = [],
        deps = [],
        includes = [],
        visibility = None,
        grpc_support = False,
        protoc_version = PROTOC_VERSION,
        tags = None,
        include_prefix = None):
    outs = []
    hdrs = []
    for src in srcs:
        # .proto suffix should not be present in the output files
        p_name = src[:-len(".proto")]
        outs += [p_name + ".pb.cc"]
        hdrs += [p_name + ".pb.h"]
        if grpc_support:
            outs += [p_name + ".grpc.pb.cc"]
            hdrs += [p_name + ".grpc.pb.h"]

    _gen_proto_rule(
        name = name + "_srcs",
        srcs = srcs,
        deps = deps,
        outs = outs + hdrs,
        include = "@//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/" + protoc_version + "/include",
        proto_include_version = protoc_version,
        protoc = "//external:protoc",
        grpc_plugin = "//external:grpc_cpp_plugin" if grpc_support else None,
        target_language = proto_languages.CPP,
        tags = tags,
    )
    native.cc_library(
        name = name,
        srcs = outs + hdrs,
        deps = deps + ["//external:grpc++_unsecure", "//external:protobuf"],
        includes = includes,
        visibility = visibility,
        tags = tags,
        hdrs = hdrs,
        copts = select_android(["-std=c++11"], []),
        strip_include_prefix = "",
        include_prefix = include_prefix,
    )
