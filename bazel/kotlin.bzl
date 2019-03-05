load(":coverage.bzl", "coverage_java_test")
load(":functions.bzl", "create_java_compiler_args_srcs", "explicit_target", "label_workspace_path", "workspace_path")
load(":maven.bzl", "maven_pom")
load(":utils.bzl", "singlejar")
load(":lint.bzl", "lint_test")

def kotlin_impl(ctx, name, roots, java_srcs, kotlin_srcs, kotlin_deps, package_prefixes, kotlin_jar, friends):
    merged = []
    for root in roots:
        if root in package_prefixes:
            root += ":" + package_prefixes[root]
        merged += [label_workspace_path(ctx.label) + "/" + root]

    kotlin_deps = list(kotlin_deps) + ctx.files._kotlin
    args, option_files = create_java_compiler_args_srcs(ctx, merged, kotlin_jar, ctx.files._bootclasspath + kotlin_deps)

    args += ["--module_name", name]
    for friend in friends:
        args += ["--friend_dir", friend.path]
    args += ["--no-jdk"]

    ctx.action(
        inputs = java_srcs + kotlin_srcs + option_files + kotlin_deps + friends + ctx.files._bootclasspath,
        outputs = [kotlin_jar],
        mnemonic = "kotlinc",
        arguments = args,
        executable = ctx.executable._kotlinc,
    )
    return java_common.create_provider(
        compile_time_jars = [kotlin_jar],
        runtime_jars = [kotlin_jar],
        use_ijar = False,
    )

def _kotlin_jar_impl(ctx):
    class_jar = ctx.outputs.class_jar

    all_deps = depset(ctx.files.deps)
    all_deps += depset(ctx.files._kotlin)
    for this_dep in ctx.attr.deps:
        if hasattr(this_dep, "java"):
            all_deps += this_dep.java.transitive_runtime_deps

    merged = [src.path for src in ctx.files.srcs]
    if ctx.attr.package_prefixes:
        merged = [a + ":" + b if b else a for (a, b) in zip(merged, ctx.attr.package_prefixes)]

    args, option_files = create_java_compiler_args_srcs(ctx, merged, class_jar, all_deps)

    for dir in ctx.files.friends:
        args += ["--friend_dir", dir.path]

    ctx.action(
        inputs = ctx.files.inputs + list(all_deps) + option_files + ctx.files.friends,
        outputs = [class_jar],
        mnemonic = "kotlinc",
        arguments = args,
        executable = ctx.executable._kotlinc,
    )

kotlin_jar = rule(
    attrs = {
        "srcs": attr.label_list(
            non_empty = True,
            allow_files = True,
        ),
        "inputs": attr.label_list(
            allow_files = True,
        ),
        "friends": attr.label_list(
            allow_files = True,
        ),
        "package_prefixes": attr.string_list(),
        "deps": attr.label_list(
            mandatory = False,
            allow_files = FileType([".jar"]),
        ),
        "_kotlinc": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:kotlinc"),
            allow_files = True,
        ),
        "_kotlin": attr.label(
            default = Label("//prebuilts/tools/common/kotlin-plugin-ij:Kotlin/kotlinc/lib/kotlin-stdlib"),
            allow_files = True,
        ),
    },
    outputs = {
        "class_jar": "lib%{name}.jar",
    },
    implementation = _kotlin_jar_impl,
)

def kotlin_library(
        name,
        srcs,
        java_srcs = [],
        javacopts = None,
        resources = [],
        deps = [],
        bundled_deps = [],
        friends = [],
        pom = None,
        exclusions = None,
        visibility = None,
        jar_name = None,
        testonly = None,
        lint_baseline = None,
        **kwargs):
    kotlins = native.glob([src + "/**/*.kt" for src in srcs])
    javas = native.glob([src + "/**/*.java" for src in srcs]) + java_srcs

    if not kotlins and not javas:
        print("No sources found for kotlin_library " + name)

    targets = []
    kdeps = []
    if kotlins:
        kotlin_name = name + ".kotlin"
        targets += [kotlin_name]
        kdeps += [":lib" + kotlin_name + ".jar"]
        kotlin_jar(
            name = kotlin_name,
            srcs = srcs,
            inputs = kotlins + javas,
            deps = deps + bundled_deps,
            friends = friends,
            visibility = visibility,
            testonly = testonly,
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
            deps = (kdeps + deps + bundled_deps) if javas else None,
            resource_jars = bundled_deps,
            visibility = visibility,
            testonly = testonly,
            **kwargs
        )

    singlejar(
        name = name,
        jar_name = jar_name,
        runtime_deps = deps + ["//prebuilts/tools/common/kotlin-plugin-ij:Kotlin/kotlinc/lib/kotlin-stdlib"],
        jars = [":lib" + target + ".jar" for target in targets],
        visibility = visibility,
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
    if lint_srcs and lint_baseline:
        lint_test(
            name = name + "_lint_test",
            srcs = lint_srcs,
            baseline = lint_baseline,
            deps = deps + bundled_deps,
            custom_rules = ["//tools/base/lint:studio-checks.lint-rules.jar"],
            tags = ["no_windows"],
        )

def kotlin_test(name, srcs, deps = [], runtime_deps = [], friends = [], coverage = False, visibility = None, **kwargs):
    kotlin_library(
        name = name + ".testlib",
        srcs = srcs,
        deps = deps,
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
        coverage = coverage,
        **kwargs
    )

    native.test_suite(
        name = name,
        tests = [name + ".test"],
    )
