load(":functions.bzl", "create_option_file", "explicit_target")

def _maven_pom_impl(ctx):
    # Contains both *.jar and *.aar files.
    jars = depset()

    # classfied jars. Sources are in clsjars["sources"]
    clsjars = {}  # classifier -> depset(jars)
    clsjars["sources"] = depset()

    if ctx.attr.library:
        if ctx.attr.file or ctx.attr.classified_files:
            fail("Cannot set both file and library for a maven_pom.")
        jars = depset([jar.class_jar for jar in ctx.attr.library.java.outputs.jars], transitive = [jars])
        clsjars["sources"] = depset(ctx.attr.library.java.source_jars.to_list(), transitive = [clsjars["sources"]])
        for classifier, library in zip(ctx.attr.classifiers, ctx.attr.classified_libraries):
            if classifier not in clsjars:
                clsjars[classifier] = depset()
            clsjars[classifier] = depset(direct = [jar.class_jar for jar in library.java.outputs.jars], transitive = [clsjars[classifier]])

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

    inputs = []
    args = []

    # Input file to take as base
    if ctx.file.source:
        args += ["-i", ctx.file.source.path]
        inputs += [ctx.file.source]

    # Output file
    args += ["-o", ctx.outputs.pom.path]

    args += ["-x"] if ctx.attr.export_pom else []

    # Overrides
    if ctx.attr.group:
        args += ["--group", ctx.attr.group]
    if ctx.attr.artifact:
        args += ["--artifact", ctx.attr.artifact]
    if ctx.attr.version:
        args += ["--version", ctx.attr.version]
    if ctx.attr.properties:
        args += ["--properties", ctx.file.properties.path]
        inputs += [ctx.file.properties]
    if ctx.files.properties_files:
        args += ["--properties", ":".join([file.path for file in ctx.files.properties_files])]
        inputs += ctx.files.properties_files
    if ctx.attr.version_property:
        args += ["--version_property", ctx.attr.version_property]

    # Exclusions
    for (dependency, exclusions) in ctx.attr.exclusions.items():
        args += ["--exclusion", dependency, ",".join([e for e in exclusions])]

    args += ["--deps", ":".join([dep.path for dep in ctx.files.deps])]
    inputs += ctx.files.deps

    ctx.actions.run(
        mnemonic = "GenPom",
        inputs = inputs,
        outputs = [ctx.outputs.pom],
        arguments = args,
        executable = ctx.executable._pom,
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
        visibility = None,
        **kwargs):
    if srcs and export_artifact:
        fail("Ony one of [srcs, export_artifact] can be used at a time")

    if export_artifact and pom:
        fail("If export_artifact is specified, the maven information cannot be changed.")

    java_exports = exports + [export_artifact] if export_artifact else exports
    native.java_library(
        name = name,
        deps = deps,
        runtime_deps = runtime_deps,
        srcs = srcs,
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
        java = ctx.attr.dep.java,
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
def maven_java_import(name, pom, classifiers = [], visibility = None, jars = [], **kwargs):
    native.java_import(
        name = name + "_import",
        jars = jars,
        **kwargs
    )

    import_with_license(
        name = name,
        visibility = visibility,
        dep = name + "_import",
        notice = "NOTICE",
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
        library = name,
        classifiers = classifiers,
        classified_libraries = classified_libraries,
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

def _maven_repo_impl(ctx):
    include_sources = ctx.attr.include_sources

    seen = {}
    inputs = []
    args = []
    for artifact in ctx.attr.artifacts:
        if not seen.get(artifact.maven.pom):
            for pom in artifact.maven.parent.poms.to_list():
                jars = artifact.maven.parent.jars[pom]
                if not seen.get(pom):
                    inputs += [pom] + jars.to_list()
                    args += [pom.path] + [jar.path for jar in jars.to_list()]
                    clsjars = artifact.maven.parent.clsjars[pom]
                    for classifier in clsjars:
                        inputs += clsjars[classifier].to_list()
                        args += [jar.path + ":" + classifier for jar in clsjars[classifier].to_list()]
                    seen[pom] = True
            inputs += [artifact.maven.pom] + artifact.maven.jars.to_list()
            args += [artifact.maven.pom.path] + [jar.path for jar in artifact.maven.jars.to_list()]
            for classifier in artifact.maven.clsjars:
                inputs += artifact.maven.clsjars[classifier].to_list()
                args += [jar.path + ":" + classifier for jar in artifact.maven.clsjars[classifier].to_list()]

            seen[artifact.maven.pom] = True
            for pom in artifact.maven.deps.poms.to_list():
                jars = artifact.maven.deps.jars[pom]
                if not seen.get(pom):
                    inputs += [pom] + jars.to_list()
                    args += [pom.path] + [jar.path for jar in jars.to_list()]
                    if include_sources:
                        clsjars = artifact.maven.deps.clsjars[pom]
                        inputs += clsjars[classifier].to_list()
                        args += [jar.path + ":" + classifier for jar in clsjars[classifier].to_list()]
                    seen[pom] = True

    # Execute the command
    option_file = create_option_file(
        ctx,
        ctx.outputs.repo.path + ".lst",
        "\n".join(args),
    )
    ctx.actions.run(
        inputs = inputs + [option_file],
        outputs = [ctx.outputs.repo],
        mnemonic = "mavenrepo",
        executable = ctx.executable._repo,
        arguments = [ctx.outputs.repo.path, "@" + option_file.path],
    )

_maven_repo = rule(
    attrs = {
        "artifacts": attr.label_list(),
        "include_sources": attr.bool(),
        "_repo": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:repo_builder"),
            allow_files = True,
        ),
    },
    outputs = {
        "repo": "%{name}.zip",
    },
    implementation = _maven_repo_impl,
)

# Creates a maven repo with the given artifacts and all their transitive
# dependencies.
#
# Usage:
# maven_repo(
#     name = The name of the rule. The output of the rule will be ${name}.zip.
#     artifacts = A list of all maven_java_libraries to add to the repo.
#     include_sources = Add source jars to the repo as well (useful for tests).
# )
def maven_repo(artifacts = [], include_sources = False, **kwargs):
    _maven_repo(
        artifacts = [explicit_target(artifact) + "_maven" for artifact in artifacts],
        include_sources = include_sources,
        **kwargs
    )
