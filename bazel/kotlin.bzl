load(":coverage.bzl", "coverage_java_test")
load(":functions.bzl", "create_java_compiler_args_srcs", "explicit_target")
load(":maven.bzl", "maven_pom")
load(":utils.bzl", "singlejar")
load(":lint.bzl", "lint_test")

def kotlin_compile(ctx, name, srcs, deps, friends, out, jre = []):
    """Runs kotlinc on the given source files.

    Args:
        ctx:      the analysis context
        name:     the name of the module being compiled
        srcs:     a list of Java and Kotlin source files
        deps:     a depset of compile-time jar dependencies
        friends:  a list of friend jars (allowing access to 'internal' members)
        jre:      a list of jars to put on the bootclasspath, *instead* of the
                    default JRE determined by kotlinc
        out:      the output jar file

    Expects that ctx.files._kotlinc is defined.

    Note: kotlinc only compiles Kotlin, not Java. So if there are Java
    sources, then you will also need to run javac after this action.
    """
    deps = depset(direct = jre, transitive = [deps])
    src_paths = [src.path for src in srcs]
    args, option_files = \
        create_java_compiler_args_srcs(ctx, src_paths, out, deps.to_list())

    args += ["--module_name", name]
    for friend in friends:
        args += ["--friend_dir", friend.path]
    if jre:
        args += ["--no-jdk"]

    ctx.actions.run(
        inputs = depset(direct = srcs + option_files, transitive = [deps]),
        outputs = [out],
        mnemonic = "kotlinc",
        arguments = args,
        executable = ctx.executable._kotlinc,
    )

def _kotlin_jar_impl(ctx):
    # TODO: We would prefer to use transitive_compile_time_jars instead of
    # transitive_runtime_deps, but currently there are issues when mixing Kotlin
    # and ijars. (ijar strips needed Kotlin metadata and inline method bodies.)
    # Note that transitive_runtime_deps excludes neverlink dependencies,
    # so we try to get those back by adding ctx.files.deps directly. Fixing
    # these issues and adding ijar/hjar support could significantly improve
    # build times.
    compile_deps = depset(
        direct = ctx.files.deps + ctx.files._kotlin_stdlib,
        transitive = [
            dep[JavaInfo].transitive_runtime_deps
            for dep in ctx.attr.deps
            if JavaInfo in dep
        ],
    )
    kotlin_compile(
        ctx = ctx,
        name = ctx.attr.module_name,
        srcs = ctx.files.srcs,
        deps = compile_deps,
        friends = ctx.files.friends,
        out = ctx.outputs.output_jar,
    )

_kotlin_jar = rule(
    attrs = {
        "srcs": attr.label_list(
            allow_empty = False,
            allow_files = True,
        ),
        "friends": attr.label_list(
            allow_files = True,
        ),
        "deps": attr.label_list(
            mandatory = False,
            allow_files = [".jar"],
        ),
        "module_name": attr.string(),
        "_kotlinc": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:kotlinc"),
            allow_files = True,
        ),
        "_kotlin_stdlib": attr.label(
            default = Label("//prebuilts/tools/common/kotlin-plugin-ij:Kotlin/kotlinc/lib/kotlin-stdlib"),
            allow_files = True,
        ),
    },
    outputs = {
        "output_jar": "lib%{name}.jar",
    },
    implementation = _kotlin_jar_impl,
)

def kotlin_library(
        name,
        srcs,
        javacopts = None,
        resources = [],
        resource_strip_prefix = None,
        deps = [],
        runtime_deps = [],
        bundled_deps = [],
        friends = [],
        pom = None,
        exclusions = None,
        visibility = None,
        jar_name = None,
        testonly = None,
        lint_baseline = None,
        lint_classpath = [],
        module_name = None):
    """Compiles a library jar from Java and Kotlin sources"""
    kotlins = [src for src in srcs if src.endswith(".kt")]
    javas = [src for src in srcs if src.endswith(".java")]

    if not kotlins and not javas:
        fail("No sources found for kotlin_library " + name)

    targets = []
    kdeps = []
    if kotlins:
        kotlin_name = name + ".kotlin"
        targets += [kotlin_name]
        kdeps += [":lib" + kotlin_name + ".jar"]
        _kotlin_jar(
            name = kotlin_name,
            srcs = srcs,
            deps = deps + bundled_deps,
            friends = friends,
            visibility = visibility,
            testonly = testonly,
            module_name = module_name,
        )

    java_name = name + ".java"
    resources_with_notice = native.glob(["NOTICE", "LICENSE"]) + resources if pom else resources
    if javas or resources_with_notice:
        targets += [java_name]
        native.java_library(
            name = java_name,
            srcs = javas,
            javacopts = javacopts if javas else None,
            resources = resources_with_notice,
            resource_strip_prefix = resource_strip_prefix,
            deps = (kdeps + deps + bundled_deps) if javas else None,
            runtime_deps = runtime_deps,
            resource_jars = bundled_deps,
            visibility = visibility,
            testonly = testonly,
        )

    singlejar(
        name = name,
        jar_name = jar_name,
        runtime_deps = deps + ["//prebuilts/tools/common/kotlin-plugin-ij:Kotlin/kotlinc/lib/kotlin-stdlib"],
        jars = [":lib" + target + ".jar" for target in targets],
        visibility = visibility,
        testonly = testonly,
    )

    if pom:
        maven_pom(
            name = name + "_maven",
            deps = [explicit_target(dep) + "_maven" for dep in deps if not dep.endswith("_neverlink")],
            exclusions = exclusions,
            library = name,
            visibility = visibility,
            source = pom,
        )

    lint_srcs = javas + kotlins
    if lint_baseline:
        if not lint_srcs:
            fail("lint_baseline set for iml_module that has no sources")
        lint_test(
            name = name + "_lint_test",
            srcs = lint_srcs,
            baseline = lint_baseline,
            deps = deps + bundled_deps + lint_classpath,
            custom_rules = ["//tools/base/lint:studio-checks.lint-rules.jar"],
            tags = ["no_windows"],
        )

def kotlin_test(name, srcs, deps = [], runtime_deps = [], friends = [], visibility = None, **kwargs):
    kotlin_library(
        name = name + ".testlib",
        srcs = srcs,
        deps = deps,
        testonly = 1,
        runtime_deps = runtime_deps,
        jar_name = name + ".jar",
        visibility = visibility,
        friends = friends,
    )

    coverage_java_test(
        name = name + ".test",
        runtime_deps = [
            ":" + name + ".testlib",
        ] + runtime_deps,
        visibility = visibility,
        **kwargs
    )

    native.test_suite(
        name = name,
        tests = [name + ".test"],
    )
