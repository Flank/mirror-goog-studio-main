def _fileset_impl(ctx):
    srcs = depset(order = "postorder", transitive = [src.files for src in ctx.attr.srcs])

    remap = {}
    for a, b in ctx.attr.maps.items():
        remap[ctx.label.relative(a)] = b

    cmd = ""
    for f in ctx.files.srcs:
        # Use the label of file, which is more reliable than fiddling with paths.
        # If f is not a source file, then the rule that generates f may have
        # a different name.
        label = f.owner if f.is_source else f.owner.relative(f.basename)
        if label in remap:
            dest = remap[label]
            fd = ctx.actions.declare_file(dest)
            cmd += "mkdir -p " + fd.dirname + "\n"
            cmd += "cp -f '" + f.path + "' '" + fd.path + "'\n"

    script = ctx.actions.declare_file(ctx.label.name + ".cmd.sh")
    ctx.actions.write(output = script, content = cmd)

    # Execute the command
    ctx.actions.run_shell(
        inputs = (
            ctx.files.srcs +
            [script]
        ),
        outputs = ctx.outputs.outs,
        mnemonic = "fileset",
        command = "set -e; sh " + script.path,
        use_default_shell_env = True,
    )

_fileset = rule(
    attrs = {
        "srcs": attr.label_list(allow_files = True),
        "maps": attr.string_dict(
            mandatory = True,
            allow_empty = False,
        ),
        "outs": attr.output_list(
            mandatory = True,
            allow_empty = False,
        ),
    },
    executable = False,
    implementation = _fileset_impl,
)

def fileset(name, srcs = [], mappings = {}, tags = [], **kwargs):
    outs = []
    maps = {}
    rem = []
    for src in srcs:
        done = False
        for prefix, destination in mappings.items():
            if src.startswith(prefix):
                f = destination + src[len(prefix):]
                maps[src] = f
                outs += [f]
                done = True
        if not done:
            rem += [src]

    if outs:
        _fileset(
            name = name + ".map",
            srcs = srcs,
            maps = maps,
            outs = outs,
            tags = tags,
        )

    native.filegroup(
        name = name,
        srcs = outs + rem,
        tags = tags,
        **kwargs
    )

# Usage:
# java_jarjar(
#     name = <the name of the rule. The output of the rule will be ${name}.jar.
#     srcs = <a list of all the jars to jarjar and include into the output jar>
#     rules = <the rule file to apply>
# )
#
# TODO: This rule is using anarres jarjar which doesn't produce stable zips (timestamps)
# jarjar is available in bazel but the current version is old and uses ASM4, so no Java8
# will migrate to it when it's fixed.
def java_jarjar(name, rules, srcs = [], visibility = None):
    native.genrule(
        name = "java_jarjar_" + name,
        srcs = srcs + [rules],
        outs = [name + ".jar"],
        tools = ["//tools/base/bazel:jarjar"],
        cmd = ("$(location //tools/base/bazel:jarjar) --rules " +
               "$(location " + rules + ") " +
               " ".join(["$(location " + src + ")" for src in srcs]) + " " +
               "--output '$@'"),
        visibility = visibility,
    )

    native.java_import(
        name = name,
        jars = [name + ".jar"],
        visibility = visibility,
    )

def merged_properties(name, srcs, mappings, visibility = None):
    native.genrule(
        name = name,
        srcs = srcs,
        outs = [name + ".properties"],
        tools = ["//tools/base/bazel:properties_merger"],
        visibility = visibility,
        cmd = ("$(location //tools/base/bazel:properties_merger) " +
               " ".join(["--mapping " + m for m in mappings]) + " " +
               " ".join(["--input $(location " + src + ") " for src in srcs]) + " " +
               "--output '$@'"),
    )

def srcjar(name, java_library, visibility = None):
    implicit_jar = ":lib" + java_library[1:] + "-src.jar"
    native.genrule(
        name = name,
        srcs = [implicit_jar],
        outs = [java_library[1:] + ".srcjar"],
        visibility = visibility,
        cmd = "cp $(location " + implicit_jar + ") $@",
    )

def _flat_archive_impl(ctx):
    inputs = []
    zipper_args = ["c", ctx.outputs.out.path]
    files = []
    if not ctx.attr.deps.items() + ctx.attr.files.items():
        fail("At least one of 'deps' or 'files' attributes must be non empty")
    for dep, target in ctx.attr.deps.items():
        list = dep.files.to_list()
        if len(list) != 1:
            fail("Only one file per entry is allowed.")
        file = list[0]
        files += [("%s/%s" % (target, file.basename), file)]
    for dep, target in ctx.attr.files.items():
        list = dep.files.to_list()
        if len(list) != 1:
            fail("Only one file per entry is allowed.")
        file = list[0]
        files += [(target, file)]

    for path, file in files:
        name = "%s=%s" % (path, file.path)
        zipper_args.append(name)
        inputs += [file]

    ctx.actions.run(
        inputs = inputs,
        outputs = [ctx.outputs.out],
        executable = ctx.executable._zipper,
        arguments = zipper_args,
        progress_message = "Creating archive...",
        mnemonic = "archiver",
    )

flat_archive = rule(
    attrs = {
        "deps": attr.label_keyed_string_dict(allow_files = True),
        "files": attr.label_keyed_string_dict(allow_files = True),
        "ext": attr.string(default = "jar"),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
    },
    outputs = {"out": "%{name}.%{ext}"},
    implementation = _flat_archive_impl,
)

def _dir_archive_impl(ctx):
    zipper_args = ctx.actions.args()
    zipper_args.use_param_file("@%s", use_always = True)
    zipper_args.set_param_file_format("multiline")

    prefix = ctx.attr.dir
    for file in ctx.files.files:
        if not file.short_path.startswith(prefix):
            fail(file.short_path + "is not in " + prefix)
        else:
            zipper_args.add("{}={}".format(file.short_path[len(prefix) + 1:], file.path))
    files = []
    files += ctx.files.files
    if ctx.attr.stamp:
        stamp = ctx.actions.declare_file(ctx.label.name + ".stamp.txt")
        ctx.actions.run(
            inputs = [ctx.info_file],
            outputs = [stamp],
            executable = ctx.executable._status_reader,
            arguments = ["--src", ctx.info_file.path, "--dst", stamp.path, "--key", "BUILD_EMBED_LABEL"],
            progress_message = "Extracting label...",
            mnemonic = "status",
        )
        zipper_args.add("%s=%s" % (ctx.attr.stamp, stamp.path))
        files.append(stamp)

    ctx.actions.run(
        inputs = files,
        outputs = [ctx.outputs.out],
        executable = ctx.executable._zipper,
        arguments = ["c", ctx.outputs.out.path, zipper_args],
        progress_message = "Creating archive...",
        mnemonic = "archiver",
    )

dir_archive = rule(
    attrs = {
        "files": attr.label_list(
            allow_files = True,
        ),
        "dir": attr.string(mandatory = True),
        "stamp": attr.string(),
        "ext": attr.string(default = "zip"),
        "_status_reader": attr.label(
            default = Label("//tools/base/bazel:status_reader"),
            cfg = "host",
            executable = True,
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
    },
    outputs = {"out": "%{name}.%{ext}"},
    implementation = _dir_archive_impl,
)

def _replace_manifest_iml(ctx):
    java_runtime = ctx.attr._jdk[java_common.JavaRuntimeInfo]
    jar_path = "%s/bin/jar" % java_runtime.java_home

    ctx.actions.run_shell(
        inputs = [ctx.file.original_jar, ctx.file.manifest] + ctx.files._jdk,
        outputs = [ctx.outputs.output_jar],
        command = "cp {input} {output}; {jar} ufm {output} {manifest}".format(
            input = ctx.file.original_jar.path,
            output = ctx.outputs.output_jar.path,
            jar = jar_path,
            manifest = ctx.file.manifest.path,
        ),
    )

replace_manifest = rule(
    attrs = {
        "_jdk": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
            providers = [java_common.JavaRuntimeInfo],
        ),
        "original_jar": attr.label(allow_single_file = True),
        "manifest": attr.label(allow_single_file = True),
    },
    outputs = {"output_jar": "%{name}.jar"},
    fragments = ["java"],
    implementation = _replace_manifest_iml,
)
