load(":functions.bzl", "create_option_file", "explicit_target")
load(":coverage.bzl", "coverage_baseline")
load(":kotlin.bzl", "kotlin_library")
load(":merge_archives.bzl", "run_singlejar")
load(":utils.bzl", "is_release")
load(":jvm_import.bzl", "jvm_import")

def generate_pom(
        ctx,
        group,
        artifact,
        version,
        output_pom,
        deps = [],
        exports = [],
        description = None,
        pom_name = None,
        source = None,
        export = False,
        properties = None,
        properties_files = None,
        version_property = None,
        exclusions = None):
    inputs = []
    args = []

    # Input file to take as base
    if source:
        args += ["-i", source.path]
        inputs += [source]

    # Output file
    args += ["-o", output_pom.path]

    args += ["-x"] if export else []

    # Overrides
    if group:
        args += ["--group", group]
    if artifact:
        args += ["--artifact", artifact]
    if version:
        args += ["--version", version]
    if description:
        args += ["--description", description]
    if pom_name:
        args += ["--pom_name", pom_name]
    if properties:
        args += ["--properties", properties.path]
        inputs += [properties]
    if properties_files:
        args += ["--properties", ":".join([file.path for file in properties_files])]
        inputs += properties_files
    if version_property:
        args += ["--version_property", version_property]

    # Exclusions
    if exclusions:
        for (dependency, exclusions) in exclusions.items():
            args += ["--exclusion", dependency, ",".join([e for e in exclusions])]

    args += ["--deps", ":".join([dep.path for dep in deps])]
    args += ["--exports", ":".join([dep.path for dep in exports])]
    inputs += deps + exports

    ctx.actions.run(
        mnemonic = "GenPom",
        inputs = inputs,
        outputs = [output_pom],
        arguments = args,
        executable = ctx.executable._pom,
    )

def _zipper(actions, zipper, desc, map_file, files, out):
    zipper_args = ["c", out.path]
    zipper_args += ["@" + map_file.path]
    actions.run(
        inputs = files + [map_file],
        outputs = [out],
        executable = zipper,
        arguments = zipper_args,
        progress_message = desc,
        mnemonic = "zipper",
    )

# Rule set that supports both Java and Maven providers, still under development

MavenInfo = provider(fields = {
    "pom": "A File referencing the pom",
    "repo_path": "A String with the repo relative path",
    "files": "The files of this artifact",
    "transitive": "The transitive files of this artifact",
})

def _maven_artifact_impl(ctx):
    files = [(ctx.attr.repo_path + "/" + file.basename, file) for file in ctx.files.files]
    return [
        DefaultInfo(files = depset(ctx.files.files)),
        MavenInfo(
            pom = ctx.file.pom,
            files = files,
            transitive = depset(direct = files, transitive = [d[MavenInfo].transitive for d in ctx.attr.deps]),
        ),
    ]

_maven_artifact = rule(
    implementation = _maven_artifact_impl,
    attrs = {
        "files": attr.label_list(allow_files = True),
        "deps": attr.label_list(),
        "pom": attr.label(allow_single_file = True),
        "repo_path": attr.string(),
        "repo_root_path": attr.string(),
    },
)

# Files that should be excluded from glob() expressions
# when collecting files from repository.
_REPO_GLOB_EXCLUDES = [
    "resolver-status.properties",
    "_remote.repositories",
    "maven-metadata-*.xml",
    "maven-metadata-*.xml.sha1",
]

def _get_artifact_dir(repo_root_path, repo_path):
    if repo_root_path and repo_path:
        return repo_root_path + "/" + repo_path + "/"
    elif repo_path:
        return repo_path + "/"
    else:
        return ""

def maven_artifact(
        name,
        pom,
        repo_path = "",
        repo_root_path = "",
        parent = None,
        deps = [],
        **kwargs):
    artifact_dir = _get_artifact_dir(repo_root_path, repo_path)
    _maven_artifact(
        name = name,
        pom = pom,
        repo_path = repo_path,
        repo_root_path = repo_root_path,
        files = native.glob(
            include = [artifact_dir + "**"],
            exclude = [artifact_dir + "**/" + exclude for exclude in _REPO_GLOB_EXCLUDES],
        ),
        deps = ([parent] if parent else []) + deps,
        **kwargs
    )

def _maven_import_impl(ctx):
    jars = []
    infos = []
    for java_dep in ctx.attr.java_deps:
        info = java_dep[JavaInfo]
        infos.append(info)
        jars.extend([java_out.class_jar for java_out in info.outputs.jars])

    infos += [dep[JavaInfo] for dep in ctx.attr.exports]

    data_deps = []
    data_deps += ctx.attr.deps if ctx.attr.deps else []
    data_deps += ctx.attr.exports if ctx.attr.exports else []
    data_deps += ctx.attr.original_deps if ctx.attr.original_deps else []
    data_deps += [ctx.attr.parent] if ctx.attr.parent else []

    mavens = [dep[MavenInfo] for dep in data_deps]
    files = [(ctx.attr.repo_path + "/" + file.basename, file) for file in ctx.files.files]

    names = []
    for jar in jars:
        name = jar.basename
        if jar.extension:
            name = jar.basename[:-len(jar.extension) - 1]
        names.append(name)

    return struct(
        providers = [
            DefaultInfo(files = depset(jars)),
            MavenInfo(
                pom = ctx.file.pom,
                files = files,
                transitive = depset(direct = files, transitive = [info.transitive for info in mavens]),
            ),
            java_common.merge(infos),
        ],
        notice = struct(
            file = ctx.attr.notice,
            name = ",".join(names),
        ),
    )

_maven_import = rule(
    doc = """
Imports java dependencies to be used by Maven rules.

Args:
  java_deps: The list of java deps provided by this target.
  files: A list of maven files to make available.
  deps: The list of Maven deps required by this target.
  repo_path: A path prefix used for all files.
  repo_root_path: unused.
  exports: Maven deps to make available to users of this rule.
  parent: A Maven dep to make available to users of this rule.
  pom: The Maven pom file.
  notice: A notice file to make available to users of this rule.
  original_deps: Additional targets to provide to users of this rule.
  srcjar: unused.
    """,
    implementation = _maven_import_impl,
    attrs = {
        "java_deps": attr.label_list(providers = [JavaInfo]),
        "files": attr.label_list(allow_files = True),
        "deps": attr.label_list(providers = [MavenInfo]),
        "exports": attr.label_list(providers = [MavenInfo]),
        "repo_path": attr.string(),
        "repo_root_path": attr.string(),
        "parent": attr.label(),
        "pom": attr.label(allow_single_file = True),
        "notice": attr.label(allow_single_file = True),
        "original_deps": attr.label_list(),
        "srcjar": attr.label(allow_files = True),
    },
)

def maven_import(
        name,
        jars = [],
        deps = [],
        repo_root_path = "",
        repo_path = "",
        classifiers = [],
        **kwargs):
    """Imports jars with a pom and parent attributes for use with Maven rules.

    A jvm_import target, ${name}_jars, is generated to import all jars.

    Files at repo_root_path and repo_path are included in the _maven_import target.
    This includes the NOTICE file.

    Args:
        name: The name for the maven_import target.
        jars: The list of jars to import.
        deps: The list of deps the imported jars depend on.
        repo_root_path: The root repository path, for globbing additional files.
        repo_path: A subpath under repo_root_path, for globbing additional files.
        classifiers: unused.
        **kwargs: See arguments for _maven_import.
    """
    import_name = name + "_jars"
    jvm_import(
        name = import_name,
        jars = jars,
        deps = deps,
    )

    artifact_dir = _get_artifact_dir(repo_root_path, repo_path)
    _maven_import(
        name = name,
        java_deps = [":" + import_name],
        deps = deps,
        repo_path = repo_path,
        repo_root_path = repo_root_path,
        files = native.glob(
            include = [artifact_dir + "**"],
            exclude = [artifact_dir + "**/" + exclude for exclude in _REPO_GLOB_EXCLUDES],
        ),
        notice = artifact_dir + "NOTICE",
        tags = ["require_license"],
        **kwargs
    )

MavenRepoInfo = provider(fields = {
    "artifacts": "The list of files in the repo",
    "manifest": "The repo's manifest file with short paths for test rules",
    "build_manifest": "The repo's manifest file with full paths for build rules",
})

def _maven_repository_impl(ctx):
    rel_paths = []
    files = []
    artifacts = depset(
        direct = [f for artifact in ctx.attr.artifacts for f in artifact[MavenInfo].files],
        transitive = [artifact[MavenInfo].transitive for artifact in ctx.attr.artifacts] if ctx.attr.include_transitive_deps else [],
    )

    # Redundancy check:
    if not ctx.attr.allow_duplicates:
        has_duplicates = False
        rem = {a: True for a in ctx.attr.artifacts}
        for b in ctx.attr.artifacts:
            b_items = {e: None for e in b[MavenInfo].transitive.to_list()}
            for a in ctx.attr.artifacts:
                if a != b:
                    included = True
                    for e in a[MavenInfo].files:
                        if e not in b_items:
                            included = False
                            break
                    if included:
                        rem[a] = False
                        print("%s is redundant as it's a dependency of %s" % (a.label, b.label))
                        has_duplicates = True
        if has_duplicates:
            print("The minimum set of dependencies is:\n" + ",\n".join(["\"%s\"" % str(a.label) for a, v in rem.items() if v]))
            fail("Duplicated/Redundant dependencies found.")

    for r, f in artifacts.to_list():
        files.append(f)
        rel_paths.append((r, f))

    build_manifest_content = "".join([k + "=" + v.path + "\n" for k, v in rel_paths])
    manifest_content = "".join([k + "=" + v.short_path + "\n" for k, v in rel_paths])

    ctx.actions.write(ctx.outputs.manifest, manifest_content)
    build_manifest = create_option_file(ctx, ctx.label.name + ".build.manifest", build_manifest_content)
    _zipper(ctx.actions, ctx.executable._zipper, "Creating repo zip", build_manifest, files, ctx.outputs.zip)

    runfiles = ctx.runfiles(files = [ctx.outputs.manifest] + files)
    return [
        DefaultInfo(
            # Do not include the zip as a default output (like _deploy.jar)
            files = depset([ctx.outputs.manifest]),
            runfiles = runfiles,
        ),
        MavenRepoInfo(
            artifacts = files,
            manifest = ctx.outputs.manifest,
            build_manifest = build_manifest,
        ),
    ]

# Creates a maven repo with the given artifacts and all their transitive dependencies.
#
# The rule exposes a MavenRepoInfo provider and outputs a manifest file. The manifest file contains
# relative runfile paths, which are available only during bazel run/test (see
# https://docs.bazel.build/versions/master/skylark/rules.html#runfiles-location).
# If the repo is used as part of a Bazel rule, the provider should be used instead to obtain the
# full paths to each of the artifacts.
#
# Usage:
# maven_repository(
#     name = The name of the rule. The output of the rule will be ${name}.manifest.
#     artifacts = A list of all maven_library artifacts to add to the repo.
#     include_transitive_deps = Also include the transitive dependencies of artifacts in the repo.
# )
maven_repository = rule(
    attrs = {
        "artifacts": attr.label_list(providers = [MavenInfo]),
        "include_transitive_deps": attr.bool(default = True),
        "allow_duplicates": attr.bool(default = True),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "exec",
            executable = True,
        ),
    },
    outputs = {
        "manifest": "%{name}.manifest",
        "zip": "%{name}.zip",
    },
    implementation = _maven_repository_impl,
)

def split_coordinates(coordinates):
    parts = coordinates.split(":")
    if len(parts) != 3:
        print("Unsupported coordinates")
    segments = parts[0].split(".") + [parts[1], parts[2]]
    return struct(
        group_id = parts[0],
        artifact_id = parts[1],
        version = parts[2],
        repo_path = "/".join(segments),
    )

def _maven_library_impl(ctx):
    infos_deps = [dep[MavenInfo] for dep in ctx.attr.deps]
    infos_exports = [dep[MavenInfo] for dep in ctx.attr.exports]
    pom_deps = [info.pom for info in infos_deps]
    pom_exports = [info.pom for info in infos_exports]

    coordinates = split_coordinates(ctx.attr.coordinates)
    basename = coordinates.artifact_id + "-" + coordinates.version
    pom_name = ctx.attr.pom_name if ctx.attr.pom_name else coordinates.group_id + "." + coordinates.artifact_id
    generate_pom(
        ctx,
        source = ctx.file.template_pom,
        output_pom = ctx.outputs.pom,
        group = coordinates.group_id,
        artifact = coordinates.artifact_id,
        version = coordinates.version,
        description = ctx.attr.description,
        pom_name = pom_name,
        deps = pom_deps,
        exports = pom_exports,
    )
    outputs = [ctx.outputs.pom]

    repo_files = [(coordinates.repo_path + "/" + n, f.files.to_list()[0]) for f, n in ctx.attr.files.items()]
    repo_files.append((coordinates.repo_path + "/" + basename + ".pom", ctx.outputs.pom))

    maven_jar = None
    maven_ijar = None
    if ctx.attr.library:
        maven_jar_name = ctx.attr.jar_name if ctx.attr.jar_name else "%s.jar" % ctx.attr.name
        maven_jar = ctx.actions.declare_file(maven_jar_name)
        maven_ijar = ctx.actions.declare_file("%s.compile.jar" % ctx.attr.name)

        library_java_info = ctx.attr.library[JavaInfo]
        bundled_jars = []
        bundled_jars.extend(library_java_info.outputs.jars)
        bundled_ijars = []
        bundled_ijars.extend(library_java_info.compile_jars.to_list())
        source_jars = []
        source_jars.extend(library_java_info.source_jars)
        for dep in ctx.attr.bundled_deps:
            bundled_jars.extend(dep[JavaInfo].outputs.jars)
            bundled_ijars.extend(dep[JavaInfo].compile_jars.to_list())
            source_jars.extend(dep[JavaInfo].source_jars)

        run_singlejar(
            ctx = ctx,
            jars = [java_out.class_jar for java_out in bundled_jars],
            manifest_lines = ctx.attr.manifest_lines,
            out = maven_jar,
        )
        run_singlejar(
            ctx = ctx,
            jars = bundled_ijars,
            out = maven_ijar,
            # There may be duplicates in ijars; protoc adds META-INF/* which can conflict
            # when there are multiple proto dependencies
            allow_duplicates = True,
        )
        outputs.append(maven_jar)
        repo_files.append((coordinates.repo_path + "/" + basename + ".jar", maven_jar))

        if ctx.attr.notice:
            notice_jar = ctx.actions.declare_file(ctx.label.name + ".notice.jar")
            ctx.actions.run(
                inputs = [ctx.file.notice],
                outputs = [notice_jar],
                executable = ctx.executable._zipper,
                arguments = ["c", notice_jar.path, ctx.file.notice.basename + "=" + ctx.file.notice.path],
                progress_message = "Creating notice jar",
                mnemonic = "zipper",
            )
            source_jars.append(notice_jar)

        if source_jars:
            source_jar = ctx.actions.declare_file(ctx.label.name + ".src.jar")
            run_singlejar(
                ctx = ctx,
                jars = source_jars,
                out = source_jar,
            )
            outputs.append(source_jar)
            repo_files.append((coordinates.repo_path + "/" + basename + "-sources.jar", source_jar))

    transitive = depset(direct = repo_files, transitive = [info.transitive for info in infos_deps + infos_exports])
    providers = [
        DefaultInfo(files = depset(outputs)),
        MavenInfo(pom = ctx.outputs.pom, files = repo_files, transitive = transitive),
    ]
    if maven_jar:
        providers.append(
            JavaInfo(
                output_jar = maven_jar,
                compile_jar = maven_ijar,
                exports = [export[JavaInfo] for export in ctx.attr.exports],
                deps = [dep[JavaInfo] for dep in ctx.attr.deps + ctx.attr.neverlink_deps],
            ),
        )
    return providers

_maven_library = rule(
    attrs = {
        "notice": attr.label(allow_single_file = True),
        "jar_name": attr.string(),
        "library": attr.label(providers = [JavaInfo]),
        "neverlink_deps": attr.label_list(providers = [JavaInfo]),
        "files": attr.label_keyed_string_dict(allow_files = True),
        "coordinates": attr.string(),
        "description": attr.string(),
        "manifest_lines": attr.string_list(),
        "pom_name": attr.string(),
        "bundled_deps": attr.label_list(
            providers = [JavaInfo],
        ),
        "template_pom": attr.label(
            default = Label("//tools/base/bazel:maven/android.pom"),
            allow_single_file = True,
        ),
        "deps": attr.label_list(providers = [MavenInfo]),
        "exports": attr.label_list(providers = [MavenInfo]),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            cfg = "host",
            executable = True,
        ),
        "_pom": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:pom_generator"),
            allow_files = True,
        ),
    },
    outputs = {
        "pom": "%{name}.pom",
    },
    fragments = ["java"],
    implementation = _maven_library_impl,
)

def maven_library(
        name,
        srcs,
        javacopts = [],
        resources = [],
        resource_strip_prefix = None,
        data = [],
        deps = [],
        exports = [],
        runtime_deps = [],
        bundled_deps = [],
        friends = [],
        notice = None,
        coordinates = None,
        jar_name = None,
        description = None,
        pom_name = None,
        exclusions = None,
        lint_baseline = None,
        lint_classpath = [],
        lint_is_test_sources = False,
        lint_timeout = None,
        module_name = None,
        plugins = [],
        manifest_lines = None,
        **kwargs):
    """Compiles a library jar from Java and Kotlin sources

    Args:
        srcs: The sources of the library.
        javacopts: Additional javac options.
        resources: Resources to add to the jar.
        resources_strip_prefix: The prefix to strip from the resources path.
        deps: The dependencies of this library.
        exports: The exported dependencies of this library.
        runtime_deps: The runtime dependencies.
        bundled_deps: The dependencies that are bundled inside the output jar and not treated as a maven dependency
        friends: The list of kotlin-friends.
        notice: An optional notice file to be included in the jar.
        coordinates: The maven coordinates of this artifact.
        jar_name: Optional name for the output jar, otherwise {name}.jar is used.
        exclusions: Files to exclude from the generated pom file.
        lint_*: Lint configuration arguments
        module_name: The kotlin module name.
    """

    kotlins = [src for src in srcs if src.endswith(".kt")]
    neverlink_deps = [dep for dep in bundled_deps if dep.endswith("_neverlink")]
    bundled_deps = [dep for dep in bundled_deps if dep not in neverlink_deps]

    kotlin_library(
        name = name + ".lib",
        srcs = srcs,
        compress_resources = is_release(),
        data = data,
        deps = deps + bundled_deps + neverlink_deps,
        exports = exports,
        friends = friends,
        notice = notice,
        module_name = module_name,
        resources = resources,
        resource_strip_prefix = resource_strip_prefix,
        runtime_deps = runtime_deps,
        stdlib = None,  # Maven libraries use the stdlib in different scopes and versions.
        plugins = plugins,
        **kwargs
    )

    _maven_library(
        name = name,
        jar_name = jar_name,
        notice = notice,
        deps = deps,
        bundled_deps = bundled_deps,
        exports = exports,
        coordinates = coordinates,
        description = description,
        pom_name = pom_name,
        manifest_lines = manifest_lines,
        library = ":" + name + ".lib",
        neverlink_deps = neverlink_deps,
        **kwargs
    )

def custom_maven_library(
        name,
        files,
        **kwargs):
    """A rule to create a custom maven library with provided files.

    Args:
        name: the name of the rule
        files: a map of <file> -> <string> for all files to have the given name in maven.
    """
    _maven_library(
        name = name,
        files = files,
        **kwargs
    )
