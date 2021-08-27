load(":functions.bzl", "create_option_file", "explicit_target")
load(":coverage.bzl", "coverage_baseline")
load(":kotlin.bzl", "kotlin_library")
load(":utils.bzl", "is_release")
load("@bazel_tools//tools/jdk:toolchain_utils.bzl", "find_java_toolchain")

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

def _maven_pom_impl(ctx):
    # Contains both *.jar and *.aar files.
    jars = depset()

    # classfied jars. Sources are in clsjars["sources"]
    clsjars = {}  # classifier -> depset(jars)
    clsjars["sources"] = depset()

    if ctx.attr.library:
        if ctx.attr.file or ctx.attr.classified_files:
            fail("Cannot set both file and library for a maven_pom.")
        jars = depset([jar.class_jar for jar in ctx.attr.library[JavaInfo].outputs.jars], transitive = [jars])
        clsjars["sources"] = depset(ctx.attr.library[JavaInfo].source_jars, transitive = [clsjars["sources"]])
        for classifier, library in zip(ctx.attr.classifiers, ctx.attr.classified_libraries):
            if classifier not in clsjars:
                clsjars[classifier] = depset()
            clsjars[classifier] = depset(direct = [jar.class_jar for jar in library[JavaInfo].outputs.jars], transitive = [clsjars[classifier]])

    if ctx.attr.file:
        if ctx.attr.library or ctx.attr.classified_libraries:
            fail("Cannot set both file and library for a maven_pom.")
        jars = depset(transitive = [ctx.attr.file.files, jars])

    if ctx.attr.classified_files:
        for classifier, file in zip(ctx.attr.classifiers, ctx.attr.classified_files):
            if classifier not in clsjars:
                clsjars[classifier] = depset()
            clsjars[classifier] = depset(transitive = [file.files, clsjars[classifier]])

    if ctx.attr.properties and ctx.files.properties_files:
        fail("Cannot set both properties and properties_files for a maven_pom.")

    parent_poms = depset([], order = "postorder")
    parent_jars = {}
    parent_clsjars = {}  # pom -> classifier -> depset(jars)

    deps_poms = depset([], order = "postorder")
    deps_jars = {}
    deps_clsjars = {}  # pom -> classifier -> depset(jars)

    # Transitive deps through the parent attribute
    if ctx.attr.parent:
        parent_poms = depset(transitive = [ctx.attr.parent.maven.parent.poms, parent_poms], order = "postorder")
        parent_poms = depset(transitive = [ctx.attr.parent.maven.deps.poms, parent_poms], order = "postorder")
        parent_poms = depset(direct = [ctx.file.parent], transitive = [parent_poms], order = "postorder")
        parent_jars.update(ctx.attr.parent.maven.parent.jars)
        parent_jars.update(ctx.attr.parent.maven.deps.jars)
        parent_jars[ctx.file.parent] = ctx.attr.parent.maven.jars
        parent_clsjars.update(ctx.attr.parent.maven.parent.clsjars)
        parent_clsjars.update(ctx.attr.parent.maven.deps.clsjars)
        parent_clsjars[ctx.file.parent] = ctx.attr.parent.maven.clsjars
    elif hasattr(ctx.attr.source, "maven"):
        parent_poms = ctx.attr.source.maven.parent.poms
        parent_jars = ctx.attr.source.maven.parent.jars
        parent_clsjars = ctx.attr.source.maven.parent.clsjars
        jars = depset([], transitive = [ctx.attr.source.maven.jars, jars])
        clsjars.update(ctx.attr.source.maven.clsjars)

    # Transitive deps through deps
    if ctx.attr.deps:
        for label in ctx.attr.deps:
            deps_poms = depset(transitive = [label.maven.parent.poms, deps_poms], order = "postorder")
            deps_poms = depset(transitive = [label.maven.deps.poms, deps_poms], order = "postorder")
            deps_poms = depset(direct = [label.maven.pom], transitive = [deps_poms], order = "postorder")
            deps_jars.update(label.maven.parent.jars)
            deps_jars.update(label.maven.deps.jars)
            deps_jars[label.maven.pom] = label.maven.jars
            deps_clsjars.update(label.maven.parent.clsjars)
            deps_clsjars.update(label.maven.deps.clsjars)
            deps_clsjars[label.maven.pom] = label.maven.clsjars
    elif hasattr(ctx.attr.source, "maven"):
        deps_poms = ctx.attr.source.maven.deps.poms
        deps_jars = ctx.attr.source.maven.deps.jars
        deps_clsjars = ctx.attr.source.maven.deps.clsjars

    generate_pom(
        ctx,
        source = ctx.file.source,
        output_pom = ctx.outputs.pom,
        export = ctx.attr.export_pom,
        group = ctx.attr.group,
        artifact = ctx.attr.artifact,
        version = ctx.attr.version,
        properties = ctx.file.properties if ctx.attr.properties else None,
        properties_files = ctx.files.properties_files,
        version_property = ctx.attr.version_property,
        exclusions = ctx.attr.exclusions,
        exports = ctx.files.deps,  # deps are compile dependencies in the legacy rule
    )

    return struct(maven = struct(
        parent = struct(
            poms = parent_poms,
            jars = parent_jars,
            clsjars = parent_clsjars,
        ),
        deps = struct(
            poms = deps_poms,
            jars = deps_jars,
            clsjars = deps_clsjars,
        ),
        pom = ctx.outputs.pom,
        jars = jars,
        clsjars = clsjars,
    ))

maven_pom = rule(
    attrs = {
        "deps": attr.label_list(),
        "library": attr.label(
            allow_files = True,
        ),
        "export_pom": attr.label(),
        "classifiers": attr.string_list(
            default = [],
        ),
        "classified_libraries": attr.label_list(
            allow_files = True,
            default = [],
        ),
        "file": attr.label(
            allow_files = True,
        ),
        "classified_files": attr.label_list(
            allow_files = True,
            default = [],
        ),
        "group": attr.string(),
        "version": attr.string(),
        "artifact": attr.string(),
        "source": attr.label(
            allow_single_file = True,
        ),
        "properties": attr.label(
            allow_single_file = True,
        ),
        "properties_files": attr.label_list(
            allow_files = True,
            default = [],
        ),
        "version_property": attr.string(),
        "parent": attr.label(
            allow_single_file = True,
        ),
        "exclusions": attr.string_list_dict(),
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
    implementation = _maven_pom_impl,
)

# A java library that can be used in a maven_repo rule.
#
# Usage:
# maven_java_library(
#     name = "name",
#     deps = A list of maven_java_library or maven_java_import dependencies
#     # all java_library attriutes
#     info = A maven coordinate for this artifact as a string
# )
def maven_java_library(
        name,
        deps = [],
        runtime_deps = [],
        exclusions = None,
        export_artifact = None,
        srcs = None,
        resources = [],
        java_exports = [],
        exports = [],
        pom = None,
        baseline_coverage = True,
        visibility = None,
        **kwargs):
    if srcs and export_artifact:
        fail("Ony one of [srcs, export_artifact] can be used at a time")

    if export_artifact and pom:
        fail("If export_artifact is specified, the maven information cannot be changed.")

    if srcs and baseline_coverage:
        coverage_baseline(name, srcs)

    java_exports = java_exports + exports + ([export_artifact] if export_artifact else [])
    native.java_library(
        name = name,
        deps = deps,
        runtime_deps = runtime_deps,
        srcs = srcs,
        javacopts = kwargs.pop("javacopts", []) + ["--release", "8"],
        resources = native.glob(["NOTICE", "LICENSE"]) + resources,
        exports = java_exports,
        visibility = visibility,
        **kwargs
    )

    # TODO: Properly exclude libraries from the pom instead of using _neverlink hacks.
    maven_deps = deps + exports + runtime_deps

    maven_pom(
        name = name + "_maven",
        deps = [explicit_target(dep) + "_maven" for dep in maven_deps if not dep.endswith("_neverlink")] if maven_deps else None,
        exclusions = exclusions,
        library = export_artifact if export_artifact else name,
        visibility = visibility,
        source = explicit_target(export_artifact) + "_maven" if export_artifact else pom,
        export_pom = explicit_target(export_artifact) + "_maven" if export_artifact else None,
    )

def _import_with_license_impl(ctx):
    names = []
    for jar in ctx.attr.dep[DefaultInfo].files.to_list():
        name = jar.basename
        if jar.extension:
            name = jar.basename[:-len(jar.extension) - 1]
        names.append(name)
    return struct(
        providers = [ctx.attr.dep[JavaInfo], ctx.attr.dep[DefaultInfo]],
        java = ctx.attr.dep[JavaInfo],
        notice = struct(
            file = ctx.attr.notice,
            name = ",".join(names),
        ),
    )

import_with_license = rule(
    implementation = _import_with_license_impl,
    attrs = {
        "dep": attr.label(),
        "notice": attr.label(allow_files = True),
    },
)

# A java_import rule extended with pom and parent attributes for maven libraries.
def maven_java_import(
        name,
        pom,
        classifiers = [],
        visibility = None,
        jars = [],
        notice = None,
        repo_path = "",
        classified_only = False,
        **kwargs):
    if not classified_only:
        native.java_import(
            name = name + "_import",
            jars = jars,
            **kwargs
        )

        import_with_license(
            name = name,
            visibility = visibility,
            dep = name + "_import",
            notice = notice if notice else (repo_path + "/NOTICE" if repo_path else "NOTICE"),
            tags = ["require_license"],
        )

    classified_libraries = []
    for classifier in classifiers:
        native.java_import(
            name = classifier + "-" + name,
            visibility = visibility,
            jars = [jar.replace(".jar", "-" + classifier + ".jar") for jar in jars],
            **kwargs
        )
        classified_libraries += [classifier + "-" + name]

    maven_pom(
        name = name + "_maven",
        library = None if classified_only else name,
        classifiers = classifiers,
        classified_libraries = classified_libraries if not classified_only else None,
        classified_files = classified_libraries if classified_only else None,
        visibility = visibility,
        source = pom,
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

def _local_maven_repository_impl(repository_ctx):
    workspace = repository_ctx.path(repository_ctx.attr._this_file).dirname.dirname.dirname.dirname
    local_repo = str(workspace) + "/" + repository_ctx.attr.path

    # Create a symlink at external/$repo_rule_name/repo and make it point to
    # to the actual location of the checked-in local maven repository so that
    # the BUILD file generated under the external repository does not have to
    # refer back to files in the internal repository.
    # Example:
    #  ~/studio-main/bazel-studio-master-dev/external/maven/repo
    #     -> ~/studio-main/prebuilts/tools/common/m2/repository
    #
    # This is needed to avoid the following Bazel bug:
    #    https://github.com/bazelbuild/bazel/issues/6752
    repo_path = "repo"
    repository_ctx.symlink(local_repo, repo_path)

    jdk11 = workspace.get_child("prebuilts").get_child("studio").get_child("jdk").get_child("jdk11")
    os_name = repository_ctx.os.name.lower()
    if os_name.startswith("mac os"):
        # Ignore mac-arm64, it's OK to use x86 version of java.
        jdk11_os = jdk11.get_child("mac").get_child("Contents").get_child("Home")
    elif os_name.find("windows") != -1:
        jdk11_os = jdk11.get_child("win")
    else:
        jdk11_os = jdk11.get_child("linux")

    java_exe = "java.exe" if os_name.find("windows") != -1 else "java"
    java = jdk11_os.get_child("bin").get_child(java_exe)

    if not java.exists:
        fail("Failed to find java binary at: " + str(java))

    # Invoke build file generator tool.
    inputs = repository_ctx.attr.artifacts
    arguments = (
        [java, "-jar", repository_ctx.path(repository_ctx.attr._generator)] +
        inputs +
        ["-o", "BUILD"] +
        ["--repo-path", repo_path] +
        (["--noresolve"] if not repository_ctx.attr.resolve else [])
    )
    result = repository_ctx.execute(
        arguments,
    )

    if result.return_code:
        fail("Failed to generate BUILD file using command:\n" +
             str(arguments) + "\n" +
             "Stdout: " + result.stdout + "\n" +
             "Stderr: " + result.stderr + "\n")

_local_maven_repository = repository_rule(
    attrs = {
        "path": attr.string(),
        "_this_file": attr.label(default = "@//tools/base/bazel:maven.bzl"),
        "resolve": attr.bool(),
        "artifacts": attr.string_list(),
        "_generator": attr.label(default = "@//tools/base/bazel:maven/generator.jar"),
        "_pom": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:pom_generator"),
            allow_files = True,
        ),
    },
    environ = ["MAVEN_FETCH"],
    local = True,
    implementation = _local_maven_repository_impl,
)

# Resolves artifacts transitively from a local Maven repository,
#  and generates a BUILD file that contains rules for all the resolved
# Maven artifacts.
#
# Consumers can depend on the listed Maven artifacts using the name
# of the repository rule, and the Maven coordinates of the artifact
# they need, without the need to express the version.
#
# For instance, to depend on Guava, add the following to deps:
# "@maven//:com.google.guava.guava"
#
# And the repository will provide the version of Guava that is obtained
# from Maven dependency resolution.
#
# Args:
#   path: Local path to a Maven repository relative to the workspace root
#   artifacts: Coordinates of the Maven artifacts to resolve
def local_maven_repository(name, path, resolve, artifacts):
    _local_maven_repository(
        name = name,
        path = path,
        resolve = resolve,
        artifacts = artifacts,
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
    infos = []
    for jar in ctx.files.jars:
        ijar = java_common.run_ijar(
            actions = ctx.actions,
            jar = jar,
            java_toolchain = find_java_toolchain(ctx, ctx.attr._java_toolchain),
        )
        infos.append(JavaInfo(
            output_jar = jar,
            compile_jar = ijar,
            deps = [dep[JavaInfo] for dep in ctx.attr.deps + ctx.attr.exports],
        ))

    infos += [dep[JavaInfo] for dep in ctx.attr.exports]

    data_deps = []
    data_deps += ctx.attr.deps if ctx.attr.deps else []
    data_deps += ctx.attr.exports if ctx.attr.exports else []
    data_deps += ctx.attr.original_deps if ctx.attr.original_deps else []
    data_deps += [ctx.attr.parent] if ctx.attr.parent else []

    mavens = [dep[MavenInfo] for dep in data_deps]
    files = [(ctx.attr.repo_path + "/" + file.basename, file) for file in ctx.files.files]

    names = []
    for jar in ctx.files.jars:
        name = jar.basename
        if jar.extension:
            name = jar.basename[:-len(jar.extension) - 1]
        names.append(name)

    return struct(
        providers = [
            DefaultInfo(files = depset(ctx.files.jars)),
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
    implementation = _maven_import_impl,
    attrs = {
        "jars": attr.label_list(allow_files = True),
        "files": attr.label_list(allow_files = True),
        "deps": attr.label_list(providers = [MavenInfo]),
        "exports": attr.label_list(providers = [MavenInfo]),
        "repo_path": attr.string(),
        "repo_root_path": attr.string(),
        "parent": attr.label(),
        "pom": attr.label(allow_single_file = True),
        "notice": attr.label(allow_single_file = True),
        "_java_toolchain": attr.label(default = Label("@bazel_tools//tools/jdk:current_java_toolchain")),
        "original_deps": attr.label_list(),
        "srcjar": attr.label(allow_files = True),
    },
)

# A java_import rule extended with pom and parent attributes for maven libraries.
def maven_import(
        name,
        pom,
        classifiers = [],
        visibility = None,
        jars = [],
        repo_path = "",
        repo_root_path = "",
        parent = None,
        classified_only = False,
        **kwargs):
    artifact_dir = _get_artifact_dir(repo_root_path, repo_path)
    _maven_import(
        name = name,
        visibility = visibility,
        jars = jars,
        pom = pom,
        repo_path = repo_path,
        repo_root_path = repo_root_path,
        parent = parent,
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
#     artifacts = A list of all maven_java_libraries to add to the repo.
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

    repo_files = [(coordinates.repo_path + "/" + n, f.files.to_list()[0]) for f, n in ctx.attr.files.items()]

    repo_files.append((coordinates.repo_path + "/" + basename + ".pom", ctx.outputs.pom))
    if ctx.attr.library:
        repo_files.append((coordinates.repo_path + "/" + basename + ".jar", ctx.file.library))

    transitive = depset(direct = repo_files, transitive = [info.transitive for info in infos_deps + infos_exports])

    default_files = [ctx.attr.library[DefaultInfo].files] if ctx.attr.library else []
    providers = [
        DefaultInfo(files = depset(direct = [ctx.outputs.pom], transitive = default_files)),
        MavenInfo(pom = ctx.outputs.pom, files = repo_files, transitive = transitive),
    ]
    if ctx.attr.library:
        providers += [ctx.attr.library[JavaInfo]]
    return providers

_maven_library = rule(
    attrs = {
        "notice": attr.label(allow_single_file = True),
        "library": attr.label(providers = [JavaInfo], allow_single_file = True),
        "files": attr.label_keyed_string_dict(allow_files = True),
        "coordinates": attr.string(),
        "description": attr.string(),
        "pom_name": attr.string(),
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
        exclusions: Files to exclude from the generated pom file.
        lint_*: Lint configuration arguments
        module_name: The kotlin module name.
    """

    kotlins = [src for src in srcs if src.endswith(".kt")]
    neverlink_deps = [dep for dep in bundled_deps if dep.endswith("_neverlink")]
    bundled_deps = [dep for dep in bundled_deps if dep not in neverlink_deps]

    kotlin_library(
        name = name + ".lib",
        jar_name = jar_name if jar_name else name + ".jar",
        srcs = srcs,
        compress_resources = is_release(),
        data = data,
        deps = deps + neverlink_deps,
        exports = exports,
        bundled_deps = bundled_deps,
        friends = friends,
        notice = notice,
        module_name = module_name,
        resources = resources,
        resource_strip_prefix = resource_strip_prefix,
        runtime_deps = runtime_deps,
        stdlib = None,  # Maven libraries use the stdlib in different scopes and versions.
        plugins = plugins,
        manifest_lines = manifest_lines,
        **kwargs
    )

    _maven_library(
        name = name,
        notice = notice,
        deps = deps,
        exports = exports,
        coordinates = coordinates,
        description = description,
        pom_name = pom_name,
        library = ":" + name + ".lib",
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
