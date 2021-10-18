load(":maven.bzl", "MavenRepoInfo")

def _gradle_build_impl(ctx):
    # TODO (b/182291459) --singlejar is a workaround for the Windows classpath jar bug.
    # This argument should be removed once the underlying problem is fixed.
    args = ["--singlejar"]
    outputs = [ctx.outputs.output_log]
    args += ["--log_file", ctx.outputs.output_log.path]
    args += ["--gradle_file", ctx.file.build_file.path]
    if (ctx.attr.output_file_destinations):
        for source, dest in zip(ctx.attr.output_file_sources, ctx.outputs.output_file_destinations):
            outputs += [dest]
            args += ["--output", source, dest.path]
    distribution = ctx.attr.distribution.files.to_list()[0]
    args += ["--distribution", distribution.path]
    for task in ctx.attr.tasks:
        args += ["--task", task]
    if ctx.attr.max_workers > 0:
        args += ["--max_workers", str(ctx.attr.max_workers)]
    args += ["-P" + key + "=" + value for key, value in ctx.attr.gradle_properties.items()]

    manifest_inputs = []
    for repo in ctx.attr.repos:
        args += ["--repo", repo[MavenRepoInfo].build_manifest.path]
        manifest_inputs += repo[MavenRepoInfo].artifacts + [repo[MavenRepoInfo].build_manifest]
    for repo_zip in ctx.files.repo_zips:
        args += ["--repo", repo_zip.path]

    ctx.actions.run(
        inputs = ctx.files.data + ctx.files.repo_zips + manifest_inputs + [ctx.file.build_file, ctx.file._gradlew_deploy, distribution],
        outputs = outputs,
        mnemonic = "gradlew",
        arguments = args,
        executable = ctx.executable._gradlew,
    )

# This rule is wrapped to allow the output Label to location map to be expressed as a map in the
# build files.
_gradle_build = rule(
    attrs = {
        "data": attr.label_list(allow_files = True),
        "output_file_sources": attr.string_list(),
        "output_file_destinations": attr.output_list(),
        "tasks": attr.string_list(),
        "build_file": attr.label(
            allow_single_file = True,
        ),
        "repos": attr.label_list(providers = [MavenRepoInfo]),
        "repo_zips": attr.label_list(allow_files = [".zip"]),
        "output_log": attr.output(),
        "distribution": attr.label(allow_files = True),
        "max_workers": attr.int(default = 0, doc = "Max number of workers, 0 or negative means unset (Gradle will use the default: number of CPU cores)."),
        "gradle_properties": attr.string_dict(),
        "_gradlew": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:gradlew"),
            allow_files = True,
        ),
        # TODO (b/182291459) gradlew_deploy.jar is needed for the --singlejar flag.
        # This should be removed once the underlying problem is fixed.
        "_gradlew_deploy": attr.label(
            cfg = "host",
            default = Label("//tools/base/bazel:gradlew_deploy.jar"),
            allow_single_file = True,
        ),
    },
    implementation = _gradle_build_impl,
)

def gradle_build(
        name = None,
        build_file = None,
        gradle_version = None,
        data = [],
        output_file = None,
        output_file_source = None,
        output_files = {},
        repos = [],
        repo_zips = [],
        tasks = [],
        max_workers = 0,
        gradle_properties = {},
        tags = [],
        **kwargs):
    output_file_destinations = []
    output_file_sources = []

    if (output_file):
        output_file_destinations += [output_file]
        if (output_file_source):
            output_file_sources += ["build/" + output_file_source]
        else:
            output_file_sources += ["build/" + output_file]

    for output_file_destination, output_file_source_name in output_files.items():
        output_file_destinations += [output_file_destination]
        output_file_sources += [output_file_source_name]

    distribution = "//tools/base/build-system:gradle-distrib" + ("-" + gradle_version if (gradle_version) else "")

    _gradle_build(
        name = name,
        build_file = build_file,
        data = data,
        distribution = distribution,
        output_file_sources = output_file_sources,
        output_file_destinations = output_file_destinations,
        output_log = name + ".log",
        gradle_properties = gradle_properties,
        repos = repos,
        repo_zips = repo_zips,
        tags = tags,
        tasks = tasks,
        max_workers = max_workers,
        **kwargs
    )

def _gradle_test_impl(ctx):
    args = ["--singlejar"]
    args += ["--gradle_file", ctx.file.build_file.path]
    distribution = ctx.attr.distribution.files.to_list()[0]
    args += ["--distribution", distribution.path]
    for task in ctx.attr.tasks:
        args += ["--task", task]
    if ctx.attr.max_workers > 0:
        args += ["--max_workers", str(ctx.attr.max_workers)]
    args += ["-P" + key + "=" + value for key, value in ctx.attr.gradle_properties.items()]
    if ctx.attr.test_output_dir:
        args += ["--test_output_dir", ctx.attr.test_output_dir]

    manifest_inputs = []
    for repo in ctx.attr.repos:
        args += ["--repo", repo[MavenRepoInfo].manifest.short_path]
        manifest_inputs += repo[MavenRepoInfo].artifacts + [repo[MavenRepoInfo].manifest]
    for repo_zip in ctx.files.repo_zips:
        args += ["--repo", repo_zip.path]

    executable = ctx.actions.declare_file(ctx.label.name + ".bat")
    inputs = ctx.files.data + ctx.files.repo_zips + manifest_inputs + [ctx.file.build_file, ctx.file._gradlew_deploy, distribution, ctx.executable._gradlew]
    runfiles = ctx.runfiles(files = inputs).merge(ctx.attr._gradlew[DefaultInfo].default_runfiles)

    short_path = ctx.executable._gradlew.short_path
    short_path = short_path.replace("/", "\\") if ctx.attr.is_windows else short_path
    ctx.actions.write(output = executable, content = short_path + " " + " ".join(args), is_executable = True)
    return DefaultInfo(executable = executable, runfiles = runfiles)

_gradle_test = rule(
    attrs = {
        "data": attr.label_list(allow_files = True),
        "output_file_sources": attr.string_list(),
        "tasks": attr.string_list(),
        "test_output_dir": attr.string(),
        "build_file": attr.label(
            allow_single_file = True,
        ),
        "repos": attr.label_list(providers = [MavenRepoInfo]),
        "repo_zips": attr.label_list(allow_files = [".zip"]),
        "distribution": attr.label(allow_files = True),
        "max_workers": attr.int(default = 0, doc = "Max number of workers, 0 or negative means unset (Gradle will use the default: number of CPU cores)."),
        "gradle_properties": attr.string_dict(),
        "is_windows": attr.bool(),
        "_gradlew": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:gradlew"),
            allow_files = True,
        ),
        "_gradlew_deploy": attr.label(
            cfg = "host",
            default = Label("//tools/base/bazel:gradlew_deploy.jar"),
            allow_single_file = True,
        ),
    },
    test = True,
    implementation = _gradle_test_impl,
)

def gradle_test(
        name = None,
        build_file = None,
        gradle_version = None,
        repos = [],
        repo_zips = [],
        test_output_dir = None,
        max_workers = 0,
        gradle_properties = {},
        **kwargs):
    distribution = "//tools/base/build-system:gradle-distrib" + ("-" + gradle_version if (gradle_version) else "")
    _gradle_test(
        name = name,
        build_file = build_file,
        distribution = distribution,
        test_output_dir = test_output_dir,
        gradle_properties = gradle_properties,
        repos = repos,
        repo_zips = repo_zips,
        max_workers = max_workers,
        is_windows = select({"//tools/base/bazel:windows": True, "//conditions:default": False}),
        **kwargs
    )
