load("//tools/base/bazel:bazel.bzl", "fileset", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("@bazel_tools//tools/build_defs/pkg:pkg.bzl", "pkg_tar")
load("//tools/base/bazel/sdk:sdk_utils.bzl", "tool_start_script")
load("//tools/base/bazel/sdk:sdk_utils.bzl", "calculate_jar_name_for_sdk_package")

platforms = ["win", "linux", "mac"]

def sdk_java_binary(name, command_name = None, main_class = None, runtime_deps = [], default_jvm_opts = {}, visibility = None):
    command_name = command_name if command_name else name
    native.java_library(
        name = command_name,
        runtime_deps = runtime_deps,
        visibility = visibility,
    )
    for platform in platforms:
        tool_start_script(
            name = name + "_wrapper_" + platform,
            platform = platform,
            command_name = command_name,
            default_jvm_opts = default_jvm_opts.get(platform) or "",
            main_class_name = main_class,
            java_binary = name,
            visibility = visibility,
        )

def _license_aspect_impl(target, ctx):
    files = []
    attrs = ctx.rule.attr
    files = []
    if "require_license" in attrs.tags:
        out = ctx.actions.declare_file(target.notice.name + ".NOTICE", sibling = target.notice.file.files.to_list()[0])
        ctx.actions.run_shell(
            outputs = [out],
            inputs = target.notice.file.files.to_list(),
            arguments = [target.notice.file.files.to_list()[0].path, out.path],
            command = "cp $1 $2",
        )
        files = [out]

    all_deps = (attrs.deps if hasattr(attrs, "deps") else []) + \
               (attrs.runtime_deps if hasattr(attrs, "runtime_deps") else []) + \
               (attrs.exports if hasattr(attrs, "exports") else [])
    transitive_notices = []
    for dep in all_deps:
        transitive_notices = transitive_notices + [dep.notices]
    return struct(notices = depset(files, transitive = transitive_notices))

license_aspect = aspect(
    implementation = _license_aspect_impl,
    attr_aspects = ["deps", "runtime_deps", "exports"],
)

def _combine_licenses_impl(ctx):
    inputs = depset([f for dep in ctx.attr.deps for f in dep.notices]).to_list()
    ctx.actions.run(
        inputs = inputs,
        outputs = [ctx.outputs.out],
        arguments = [ctx.outputs.out.path] + [f.path for f in inputs],
        executable = ctx.executable._combine_notices,
    )

combine_licenses = rule(
    implementation = _combine_licenses_impl,
    attrs = {
        "deps": attr.label_list(aspects = [license_aspect]),
        "out": attr.output(mandatory = True),
        "_combine_notices": attr.label(executable = True, cfg = "host", default = Label("//tools/base/bazel/sdk:combine_notices")),
    },
)

def _package_component_impl(ctx):
    inputs = []
    args = ["c", ctx.outputs.out.path]
    for bin in ctx.attr.bins:
        file = bin.files.to_list()[0]
        args.append("tools/bin/%s=%s" % (file.basename, file.path))
        inputs += [file]
    runtime_jars = depset(transitive = [java_lib[JavaInfo].transitive_runtime_jars for java_lib in ctx.attr.java_libs])
    runtime_jar_names = {}
    for jar in runtime_jars:
        name = calculate_jar_name_for_sdk_package(jar)
        existing = runtime_jar_names.get(name)
        if existing:
            fail("Multiple jars have same name for SDK component with the same name! name= " + name + " jars= " + existing.path + "       " + jar.path)
        runtime_jar_names[name] = jar
        args.append("tools/lib/%s=%s" % (name, jar.path))
        inputs += [jar]
    args.append("tools/source.properties=" + ctx.file.sourceprops.path)
    inputs += [ctx.file.sourceprops]
    args.append("tools/NOTICE.txt=" + ctx.file.notice.path)
    inputs += [ctx.file.notice]
    ctx.action(
        inputs = inputs,
        outputs = [ctx.outputs.out],
        executable = ctx.executable._zipper,
        arguments = args,
        progress_message = "Creating archive...",
        mnemonic = "archiver",
    )

package_component = rule(
    implementation = _package_component_impl,
    attrs = {
        "bins": attr.label_list(),
        "java_libs": attr.label_list(),
        "sourceprops": attr.label(allow_single_file = True),
        "notice": attr.label(allow_single_file = True),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
    },
    outputs = {"out": "%{name}.zip"},
)

def sdk_package(name, binaries, sourceprops, visibility):
    version_file = "//tools/buildSrc/base:version.properties"
    native.genrule(
        name = "generate_source_props",
        srcs = [sourceprops, version_file],
        outs = ["source.properties"],
        cmd = """
              version=$$(sed -n '/^buildVersion/s/.* //p' $(location {version_file}));
              sed "s/{{VERSION}}/$$version/" $(location {sourceprops}) > $(location source.properties)
              """.format(version_file = version_file, sourceprops = sourceprops),
    )
    combine_licenses(name = name + "_combined_licenses", out = "NOTICE.txt", deps = binaries)
    for platform in platforms:
        package_component(
            name = "%s_%s" % (name, platform),
            bins = [bin + "_wrapper_" + platform for bin in binaries],
            java_libs = binaries,
            sourceprops = "source.properties",
            notice = name + "_combined_licenses",
            visibility = visibility,
        )
    native.filegroup(
        name = name,
        srcs = ["%s_%s.zip" % (name, platform) for platform in platforms],
        visibility = visibility,
    )
