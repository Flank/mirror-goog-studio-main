load(":functions.bzl", "create_option_file", "explicit_target")
load(":coverage.bzl", "coverage_baseline")
load("@bazel_tools//tools/jdk:toolchain_utils.bzl", "find_java_toolchain")

def generate_pom(
        ctx,
        group,
        artifact,
        version,
        output_pom,
        deps = [],
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
    inputs += deps

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
        deps = ctx.files.deps,
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
        deps = None,
        runtime_deps = None,
        exclusions = None,
        export_artifact = None,
        srcs = None,
        resources = [],
        exports = None,
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

    java_exports = exports + [export_artifact] if export_artifact else exports
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
    maven_deps = (deps or []) + (exports or []) + (runtime_deps or [])

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
            notice = repo_path + "/NOTICE" if repo_path else "NOTICE",
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

def maven_aar(name, aar, pom, visibility = None):
    native.filegroup(
        name = name,
        srcs = [aar],
        visibility = visibility,
    )

    maven_pom(
        name = name + "_maven",
        file = aar,
        visibility = visibility,
        source = pom,
    )

MavenRepoInfo = provider(fields = {
    "artifacts": "The list of files in the repo",
    "build_manifest": "The repo's manifest file with full paths for build rules",
})

def _collect_pom_provider(pom, jars, clsjars, include_sources):
    collected = [(pom, None)]
    collected += [(jar, None) for jar in jars.to_list()]
    for classifier, jars in clsjars.items():
        if include_sources or classifier != "sources":
            collected += [(jar, classifier) for jar in jars.to_list()]
    return collected

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

# Collects all parent and dependency artifacts for a given list of artifacts.
# Each artifact is represented as a tuple (artifact, classifier), with classifier = None if there is no classifier.
#
# If include_deps = False, the collected artifacts do not include dependency artifacts.
def _collect_artifacts(artifacts, include_sources, include_deps):
    seen = {}
    collected = []

    for artifact in artifacts:
        if seen.get(artifact.maven.pom):
            continue

        # Always include parent artifacts even when include_deps=False. Otherwise, Maven
        # model builders won't accept the set of collected artifacts as a valid Maven repo.
        # Note that this also includes the dependencies of parent.
        for pom in artifact.maven.parent.poms.to_list():
            if seen.get(pom):
                continue
            jars = artifact.maven.parent.jars[pom]
            clsjars = artifact.maven.parent.clsjars[pom]
            collected += _collect_pom_provider(pom, jars, clsjars, include_sources)
            seen[pom] = True

        collected += _collect_pom_provider(artifact.maven.pom, artifact.maven.jars, artifact.maven.clsjars, include_sources)
        seen[artifact.maven.pom] = True

        if include_deps:
            for pom in artifact.maven.deps.poms.to_list():
                if seen.get(pom):
                    continue
                jars = artifact.maven.deps.jars[pom]
                clsjars = artifact.maven.deps.clsjars[pom]
                collected += _collect_pom_provider(pom, jars, clsjars, include_sources)
                seen[pom] = True

    return collected

def _maven_repo_impl(ctx):
    artifacts = _collect_artifacts(ctx.attr.artifacts, ctx.attr.include_sources, ctx.attr.include_transitive_deps)
    args = [artifact.short_path + ("," + classifier if classifier else "") for artifact, classifier in artifacts]
    inputs = [artifact for artifact, _ in artifacts]

    args = [artifact.path + "," + artifact.short_path + ("," + classifier if classifier else "") for artifact, classifier in artifacts]
    artifact_lst = ctx.actions.declare_file(ctx.label.name + ".artifact.lst")
    ctx.actions.write(artifact_lst, "\n".join(args))

    # Generate manifests of where files should be located in the repository.
    # The format is the same used by @bazel_tools//tools/zip:zipper, where
    # each line is"path_to_target_location=path_to_file"
    build_manifest = ctx.actions.declare_file(ctx.label.name + ".build.manifest")
    ctx.actions.run(
        inputs = [artifact_lst] + inputs,
        outputs = [build_manifest, ctx.outputs.manifest],
        mnemonic = "mavenrepobuilder",
        arguments = [artifact_lst.path, build_manifest.path, ctx.outputs.manifest.path],
        executable = ctx.executable._repo_builder,
    )
    _zipper(ctx.actions, ctx.executable._zipper, "Creating repo zip...", build_manifest, inputs, ctx.outputs.zip)

    runfiles = ctx.runfiles(files = [ctx.outputs.manifest] + inputs)
    return [
        DefaultInfo(
            # Do not include the zip as a default output (like _deploy.jar)
            files = depset([ctx.outputs.manifest]),
            runfiles = runfiles,
        ),
        MavenRepoInfo(
            artifacts = inputs,
            build_manifest = build_manifest,
        ),
    ]

_maven_repo = rule(
    attrs = {
        "artifacts": attr.label_list(),
        "include_sources": attr.bool(),
        "include_transitive_deps": attr.bool(),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "exec",
            executable = True,
        ),
        "_repo_builder": attr.label(
            executable = True,
            cfg = "exec",
            default = Label("//tools/base/bazel:repo_builder"),
            allow_files = True,
        ),
    },
    outputs = {
        "manifest": "%{name}.manifest",
        "zip": "%{name}.zip",
    },
    implementation = _maven_repo_impl,
)

# Creates a maven repo with the given artifacts and all their transitive dependencies.
#
# The rule exposes a MavenRepoInfo provider and outputs a manifest file. The manifest file contains
# relative runfile paths, which are available only during bazel run/test (see
# https://docs.bazel.build/versions/master/skylark/rules.html#runfiles-location).
# If the repo is used as part of a Bazel rule, the provider should be used instead to obtain the
# full paths to each of the artifacts.
#
# Usage:
# maven_repo(
#     name = The name of the rule. The output of the rule will be ${name}.manifest.
#     artifacts = A list of all maven_java_libraries to add to the repo.
#     include_transitive_deps = Also include the transitive dependencies of artifacts in the repo.
#     include_sources = Add source jars to the repo as well (useful for tests).
# )
def maven_repo(artifacts = [], include_sources = False, include_transitive_deps = True, **kwargs):
    _maven_repo(
        artifacts = [explicit_target(artifact) + "_maven" for artifact in artifacts],
        include_sources = include_sources,
        include_transitive_deps = include_transitive_deps,
        **kwargs
    )

# Rule set that supports both Java and Maven providers, still under development

VERSION = "30.1.0-dev"

MavenInfo = provider(fields = {
    "pom": "A File referencing the pom",
    "repo_path": "A String with the repo relative path",
    "files": "The files of this artifact",
    "transitive": "The transitive files of this artifact",
})

def _maven_artifact_impl(ctx):
    files = [(ctx.attr.repo_path, file) for file in ctx.files.files]
    return [
        DefaultInfo(files = depset(ctx.files.files)),
        MavenInfo(
            pom = ctx.file.pom,
            files = ctx.files.files,
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
    },
)

def maven_artifact(
        name,
        pom,
        repo_path = "",
        parent = None,
        deps = [],
        **kwargs):
    _maven_artifact(
        name = name,
        pom = pom,
        repo_path = repo_path,
        files = native.glob([repo_path + "/**"]),
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
        ))
    runfiles = None

    mavens = [dep[MavenInfo] for dep in ctx.attr.deps + ([ctx.attr.parent] if ctx.attr.parent else [])]
    files = [(ctx.attr.repo_path, file) for file in ctx.files.files]

    return struct(
        providers = [
            DefaultInfo(files = depset(ctx.files.jars), runfiles = runfiles),
            MavenInfo(
                pom = ctx.file.pom,
                files = ctx.files.files,
                transitive = depset(direct = files, transitive = [info.transitive for info in mavens]),
            ),
            java_common.merge(infos),
        ],
    )

_maven_import = rule(
    implementation = _maven_import_impl,
    attrs = {
        "jars": attr.label_list(allow_files = True),
        "files": attr.label_list(allow_files = True),
        "deps": attr.label_list(),
        "repo_path": attr.string(),
        "parent": attr.label(),
        "pom": attr.label(allow_single_file = True),
        "notice": attr.label(allow_single_file = True),
        "_java_toolchain": attr.label(default = Label("@bazel_tools//tools/jdk:current_java_toolchain")),
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
        parent = None,
        classified_only = False,
        **kwargs):
    _maven_import(
        name = name,
        visibility = visibility,
        jars = jars,
        pom = pom,
        repo_path = repo_path,
        parent = parent,
        files = native.glob([repo_path + "/**"]),
        notice = repo_path + "/NOTICE" if repo_path else "NOTICE",
        tags = ["require_license"],
        **kwargs
    )

def _maven_repository_impl(ctx):
    rel_paths = []
    files = []
    for artifact in ctx.attr.artifacts:
        if MavenInfo not in artifact:
            fail("Maven repositories can only contain maven artifacts")
        for d, f in artifact[MavenInfo].transitive.to_list():
            files.append(f)
            rel_paths.append((d + "/" + f.basename, f))

    zipper_args = ["c", ctx.outputs.repo.path]
    zipper_files = "".join([k + "=" + v.path + "\n" for k, v in rel_paths])
    zipper_list = create_option_file(ctx, ctx.outputs.repo.basename + ".res.lst", zipper_files)
    zipper_args += ["@" + zipper_list.path]
    ctx.actions.run(
        inputs = files + [zipper_list],
        outputs = [ctx.outputs.repo],
        executable = ctx.executable._zipper,
        arguments = zipper_args,
        progress_message = "Creating repository zip...",
        mnemonic = "zipper",
    )

_maven_repository = rule(
    attrs = {
        "artifacts": attr.label_list(),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
    },
    outputs = {
        "repo": "%{name}.zip",
    },
    implementation = _maven_repository_impl,
)

def maven_repository(artifacts = [], **kwargs):
    _maven_repository(
        artifacts = artifacts,
        **kwargs
    )

def maven_coordinate(coordinate):
    parts = coordinate.split(":")
    if len(parts) != 3:
        print("Unsupported coordinate")
    segments = parts[0].split(".") + [parts[1], parts[2]]
    return struct(
        group_id = parts[0],
        artifact_id = parts[1],
        version = parts[2],
        repo_path = "/".join(segments),
    )
