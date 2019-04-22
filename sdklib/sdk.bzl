load("//tools/base/bazel:bazel.bzl", "fileset", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("@bazel_tools//tools/build_defs/pkg:pkg.bzl", "pkg_tar")
load("//tools/base/bazel/sdk:sdk_utils.bzl", "tool_start_script")

platforms = ["win", "linux", "mac"]

def sdk_java_binary(name, command_name = None, main_class = None, runtime_deps = [], default_jvm_opts = {}, visibility = None):
    command_name = command_name if command_name else name
    native.java_binary(
        name = command_name,
        create_executable = False,
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
            jars = [name + "_deploy.jar"],
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
    for lib in ctx.attr.libs:
        file = lib.files.to_list()[0]
        args.append("tools/lib/%s=%s" % (file.basename, file.path))
        inputs += [file]
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
        "libs": attr.label_list(allow_files = True),
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

def sdk_package(name, binaries, sourceprops):
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
            libs = [lib + "_deploy.jar" for lib in binaries],
            sourceprops = "source.properties",
            notice = name + "_combined_licenses",
        )
    native.filegroup(
        name = name,
        srcs = ["%s_%s.zip" % (name, platform) for platform in platforms],
    )

def _sdk_package_test_impl(ctx):
    target = ctx.files.package
    bin_files = sorted(["jobb", "avdmanager", "apkanalyzer", "lint", "screenshot2", "sdkmanager"])
    lib_files = ["tools/lib/" + f + "_deploy.jar" for f in bin_files]
    bin_per_os = {platform: ["tools/bin/" + file + (".bat" if platform == "win" else "") for file in bin_files] for platform in platforms}
    other_files = ["tools/NOTICE.txt", "tools/source.properties"]
    complete_contents_per_os = {platform: " ".join(bin_per_os[platform] + lib_files + other_files) for platform in platforms}
    desired_file_per_os = {platform: "{}_{}.zip".format(ctx.attr.package.label.name, platform) for platform in platforms}
    zip_paths_per_os = {platform: [f.short_path for f in target if platform + ".zip" in f.basename][0] for platform in platforms}
    ctx.actions.write(
        ctx.outputs.executable,
        """
        fail() {{
          echo $1 >&2
          exit 1;
        }}
        check() {{
          [[ $1 == $2 ]] || fail "expected $1 to be $2"
        }}

        check "{zip_base_names}" "{desired_zip_names}"
        """.format(
            zip_base_names = " ".join(sorted([t.basename for t in target])),
            desired_zip_names = " ".join(sorted(desired_file_per_os.values())),
        ) +
        "\n".join([
            #TODO: set permissions correctly
            #check "`zipinfo {zip}|grep -- '-r-xr-xr-x'|sed 's/.* //'|sort`" "{bins}"
            #check "`zipinfo {zip}|grep -- '-r--r--r--'|sed 's/.* //'|sort`" "{others}"
            """
                   sorted_contents=`echo {contents}|xargs -n 1|sort`
                   check "`zipinfo -1 {zip}|grep -v /$|sort`" "$sorted_contents"
                   """.format(
                zip = zip_paths_per_os[platform],
                contents = complete_contents_per_os[platform],
                bins = "\n".join(bin_per_os[platform]),
                others = "\n".join(lib_files + other_files),
            )
            for platform in platforms
        ]),
        True,
    )
    return [DefaultInfo(runfiles = ctx.runfiles(files = target))]

sdk_package_test = rule(
    implementation = _sdk_package_test_impl,
    attrs = {
        "package": attr.label(),
    },
    test = True,
)
