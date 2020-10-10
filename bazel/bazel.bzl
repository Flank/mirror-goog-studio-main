load(":coverage.bzl", "coverage_baseline", "coverage_java_test")
load(":functions.bzl", "create_java_compiler_args_srcs", "create_option_file", "explicit_target", "label_workspace_path", "workspace_path")
load(":groovy.bzl", "groovy_impl")
load(":kotlin.bzl", "kotlin_compile")
load(":lint.bzl", "lint_test")
load(":merge_archives.bzl", "create_manifest_argfile", "run_singlejar")
load("@bazel_tools//tools/jdk:toolchain_utils.bzl", "find_java_runtime_toolchain", "find_java_toolchain")

# This is a custom implementation of label "tags".
# A label of the form:
#   "//package/directory:rule[tag1, tag2]"
# Gets split up into a tuple containing the label, and the array of tags:
#   ("//package/directory:rule", ["tag1", "tag2"])
# Returns the split up tuple.
def _get_label_and_tags(label):
    if not label.endswith("]"):
        return label, []
    rfind = label.rfind("[")
    if rfind == -1:
        print("Malformed tagged label: " + label)
        return label, []
    return label[:rfind], [tag.strip() for tag in label[rfind + 1:-1].split(",")]

# Returns the paths of the given files relative to any of the roots. Were
# files is a list of File objects, and roots is a list strings represnting
# paths of directories relative to the package
# If a file is not found to be inside any of the given roots, it is ignored.
def relative_paths(ctx, files, roots):
    package_prefixes = ctx.attr.package_prefixes
    translated_package_prefixes = {root: prefix.replace(".", "/") for (root, prefix) in package_prefixes.items()}

    paths = []
    for file in files:
        for root in roots:
            path = label_workspace_path(ctx.label) + "/" + root
            if file.path.startswith(path):
                relpath = file.path[len(path) + 1:]
                if root in translated_package_prefixes:
                    relpath = translated_package_prefixes[root] + "/" + relpath
                paths.append((relpath, file))
    return paths

def resources_impl(ctx, name, roots, resources, resources_jar):
    rel_paths = relative_paths(ctx, resources, roots)
    plugin = None
    for k, v in rel_paths:
        if k == "META-INF/plugin.xml":
            plugin = v
    zipper_args = ["c", resources_jar.path]
    zipper_files = "".join([k + "=" + v.path + "\n" for k, v in relative_paths(ctx, resources, roots)])
    zipper_list = create_option_file(ctx, name + ".res.lst", zipper_files)
    zipper_args += ["@" + zipper_list.path]
    ctx.actions.run(
        inputs = resources + [zipper_list],
        outputs = [resources_jar],
        executable = ctx.executable._zipper,
        arguments = zipper_args,
        progress_message = "Creating resources zip...",
        mnemonic = "zipper",
    )
    return plugin

def accumulate_provider(provider, deps, runtime, compile_time):
    deps += [provider]
    runtime = depset(transitive = [runtime, provider.transitive_runtime_jars])
    compile_time = depset(transitive = [compile_time, provider.full_compile_jars])
    return deps, runtime, compile_time

def _iml_module_jar_impl(
        ctx,
        name,
        roots,
        java_srcs,
        kotlin_srcs,
        groovy_srcs,
        form_srcs,
        resources,
        manifests,
        res_zips,
        output_jar,
        java_deps,
        form_deps,
        exports,
        friends,
        module_name,
        transitive_compile_time_jars,
        transitive_runtime_jars):
    jars = []
    sourcepath = []
    forms = []

    java_jar = ctx.actions.declare_file(name + ".java.jar") if java_srcs else None
    kotlin_jar = ctx.actions.declare_file(name + ".kotlin.jar") if kotlin_srcs else None
    groovy_jar = ctx.actions.declare_file(name + ".groovy.jar") if groovy_srcs else None

    # Groovy
    if groovy_srcs:
        groovy_deps = [java_jar] if java_jar else []
        groovy_deps += [kotlin_jar] if kotlin_jar else []
        groovy_deps += transitive_runtime_jars.to_list()
        stub_jar = ctx.actions.declare_file(name + ".groovy_stubs.src.jar")
        groovy_impl(ctx, roots, groovy_srcs, groovy_deps, transitive_runtime_jars, groovy_jar, stub_jar)
        sourcepath += [stub_jar]
        jars += [groovy_jar]

    # Kotlin
    kotlin_providers = []
    if kotlin_srcs:
        kotlin_compile(
            ctx = ctx,
            name = module_name,
            srcs = java_srcs + kotlin_srcs,
            deps = transitive_compile_time_jars,
            friends = friends,
            out = kotlin_jar,
            jre = ctx.files._bootclasspath,
        )
        kotlin_providers += [JavaInfo(
            output_jar = kotlin_jar,
            compile_jar = kotlin_jar,
        )]
        jars += [kotlin_jar]

    # Resources.
    plugin = None
    if resources:
        resources_jar = ctx.actions.declare_file(name + ".res.jar")
        plugin = resources_impl(ctx, name, roots, resources, resources_jar)
        jars += [resources_jar]
    if res_zips:
        jars += res_zips

    # Java
    if java_srcs:
        compiled_java = ctx.actions.declare_file(name + ".pjava.jar") if form_srcs else java_jar
        formc_input_jars = [compiled_java] + ([kotlin_jar] if kotlin_jar else [])
        java_toolchain = find_java_toolchain(ctx, ctx.attr._java_toolchain)

        java_provider = java_common.compile(
            ctx,
            source_files = java_srcs,
            output = compiled_java,
            deps = java_deps + kotlin_providers,
            javac_opts = java_common.default_javac_opts(java_toolchain = java_toolchain) + ctx.attr.javacopts,
            java_toolchain = java_toolchain,
            host_javabase = find_java_runtime_toolchain(ctx, ctx.attr._host_javabase),
            sourcepath = sourcepath,
        )

        # Forms
        if form_srcs:
            forms += relative_paths(ctx, form_srcs, roots)

            # Note: we explicitly include the bootclasspath from the current Java toolchain with
            # the classpath, because extracting it at runtime, when we are running in the
            # FormCompiler JVM, is not portable across JDKs (and made much harder on JDK9+).

            option_flags, option_files = create_java_compiler_args_srcs(
                ctx,
                [form.path for form in form_srcs] + [k + "=" + v.path for k, v in form_deps] + [f.path for f in formc_input_jars],
                java_jar,
                transitive_runtime_jars.to_list() + java_toolchain.bootclasspath.to_list(),
            )
            args = ctx.actions.args()
            args.add_all(option_flags)

            # To support persistent workers, arguments must come from a param file..
            args.use_param_file("@%s", use_always = True)
            args.set_param_file_format("multiline")

            ctx.actions.run(
                inputs = [v for _, v in form_deps] + form_srcs + formc_input_jars + option_files +
                         transitive_runtime_jars.to_list() + java_toolchain.bootclasspath.to_list(),
                outputs = [java_jar],
                mnemonic = "formc",
                arguments = [args],
                executable = ctx.executable._formc,
                execution_requirements = {"supports-workers": "1"},
            )

        jars += [java_jar]

    if form_srcs and not java_srcs:
        fail("Forms only supported with java sources")

    manifest_argfile = None
    if ctx.files.manifests:
        manifest_argfile = create_manifest_argfile(ctx, name + ".manifest.lst", ctx.files.manifests)

    run_singlejar(
        ctx = ctx,
        jars = jars,
        out = output_jar,
        manifest_lines = ["@" + manifest_argfile.path] if manifest_argfile else [],
        extra_inputs = [manifest_argfile] if manifest_argfile else [],
        allow_duplicates = True,  # TODO: Ideally we could be more strict here.
    )

    providers = []
    providers = [JavaInfo(
        output_jar = output_jar,
        compile_jar = output_jar,
        deps = java_deps,
    )]
    providers += exports

    return java_common.merge(providers), forms, plugin

def _iml_module_impl(ctx):
    names = [iml.basename[:-4] for iml in ctx.files.iml_files if iml.basename.endswith(".iml")]

    transitive_data = depset()
    java_deps = []
    form_deps = []

    transitive_runtime_jars = depset(order = "preorder")
    transitive_compile_time_jars = depset(order = "preorder")

    for this_dep in ctx.attr.deps:
        if hasattr(this_dep, "module"):
            transitive_data = depset(transitive = [transitive_data, this_dep.module.transitive_data])
            form_deps += this_dep.module.forms
        elif DefaultInfo in this_dep:
            transitive_data = depset(transitive = [transitive_data, this_dep[DefaultInfo].default_runfiles.files])

        if java_common.provider in this_dep:
            java_deps, transitive_runtime_jars, transitive_compile_time_jars = accumulate_provider(
                this_dep[java_common.provider],
                java_deps,
                transitive_runtime_jars,
                transitive_compile_time_jars,
            )

    test_java_deps = []
    test_form_deps = []
    transitive_test_runtime_jars = depset([ctx.outputs.production_jar], order = "preorder")
    transitive_test_compile_time_jars = depset([ctx.outputs.production_jar], order = "preorder")

    for this_dep in ctx.attr.test_deps:
        if JavaInfo in this_dep:
            test_java_deps, transitive_test_runtime_jars, transitive_test_compile_time_jars = accumulate_provider(
                this_dep[JavaInfo],
                test_java_deps,
                transitive_test_runtime_jars,
                transitive_test_compile_time_jars,
            )
        if hasattr(this_dep, "module"):
            transitive_data = depset(transitive = [transitive_data, this_dep.module.transitive_data])
            test_form_deps += this_dep.module.test_forms
            test_java_deps, transitive_test_runtime_jars, transitive_test_compile_time_jars = accumulate_provider(
                this_dep.module.test_provider,
                test_java_deps,
                transitive_test_runtime_jars,
                transitive_test_compile_time_jars,
            )

    exports = []
    test_exports = []
    for export in ctx.attr.exports:
        if JavaInfo in export:
            exports += [export[JavaInfo]]
        if hasattr(export, "module"):
            test_exports += [export.module.test_provider]

    module_jars = ctx.outputs.production_jar
    module_runtime = transitive_runtime_jars

    transitive_data = depset(ctx.files.iml_files + ctx.files.data, transitive = [transitive_data])

    # If multiple modules we use the label, otherwise use the exact module name
    module_name = names[0] if len(names) == 1 else ctx.label.name
    main_provider, main_forms, plugin_xml = _iml_module_jar_impl(
        ctx,
        ctx.label.name,
        ctx.attr.roots,
        ctx.files.java_srcs,
        ctx.files.kotlin_srcs,
        ctx.files.groovy_srcs,
        ctx.files.form_srcs,
        ctx.files.resources,
        ctx.attr.manifests,
        ctx.files.res_zips,
        ctx.outputs.production_jar,
        java_deps,
        form_deps,
        exports,
        [],
        module_name,
        transitive_compile_time_jars,
        transitive_runtime_jars,
    )

    test_provider, test_forms, _ = _iml_module_jar_impl(
        ctx,
        ctx.label.name + "_test",
        ctx.attr.test_roots,
        ctx.files.java_test_srcs,
        ctx.files.kotlin_test_srcs,
        ctx.files.groovy_test_srcs,
        ctx.files.form_test_srcs,
        ctx.files.test_resources,
        [],
        [],
        ctx.outputs.test_jar,
        [main_provider] + test_java_deps,
        test_form_deps,
        exports + test_exports,
        [ctx.outputs.production_jar] + ctx.files.test_friends,
        module_name,
        transitive_test_compile_time_jars,
        transitive_test_runtime_jars,
    )

    return struct(
        module = struct(
            bundled_deps = ctx.files.bundled_deps,
            module_jars = module_jars,
            module_runtime = module_runtime,
            transitive_data = transitive_data,
            forms = main_forms,
            test_forms = test_forms,
            java_deps = java_deps,
            test_provider = test_provider,
            main_provider = main_provider,
            names = names,
            plugin = plugin_xml,
        ),
        providers = [main_provider],
    )

_iml_module_ = rule(
    attrs = {
        "iml_files": attr.label_list(
            allow_files = True,
            allow_empty = False,
            mandatory = True,
        ),
        "java_srcs": attr.label_list(allow_files = True),
        "kotlin_srcs": attr.label_list(allow_files = True),
        "groovy_srcs": attr.label_list(allow_files = True),
        "form_srcs": attr.label_list(allow_files = True),
        "java_test_srcs": attr.label_list(allow_files = True),
        "kotlin_test_srcs": attr.label_list(allow_files = True),
        "groovy_test_srcs": attr.label_list(allow_files = True),
        "form_test_srcs": attr.label_list(allow_files = True),
        "javacopts": attr.string_list(),
        "resources": attr.label_list(allow_files = True),
        "manifests": attr.label_list(allow_files = True),
        "res_zips": attr.label_list(allow_files = True),
        "test_resources": attr.label_list(allow_files = True),
        "package_prefixes": attr.string_dict(),
        "test_class": attr.string(),
        "exports": attr.label_list(),
        "roots": attr.string_list(),
        "test_roots": attr.string_list(),
        "deps": attr.label_list(),
        "test_deps": attr.label_list(),
        "test_friends": attr.label_list(),
        "data": attr.label_list(allow_files = True),
        "test_data": attr.label_list(allow_files = True),
        "bundled_deps": attr.label_list(allow_files = True),
        "_java_toolchain": attr.label(default = Label("@bazel_tools//tools/jdk:current_java_toolchain")),
        "_host_javabase": attr.label(default = Label("@bazel_tools//tools/jdk:current_host_java_runtime")),
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
        "_kotlinc": attr.label(
            default = Label("//tools/base/bazel:kotlinc"),
            cfg = "host",
            executable = True,
        ),
        "_bootclasspath": attr.label(
            default = Label("@bazel_tools//tools/jdk:platformclasspath"),
            cfg = "host",
        ),
        "_kotlin": attr.label(
            default = Label("//prebuilts/tools/common/kotlin-plugin-ij:Kotlin/kotlinc/lib/kotlin-stdlib"),
            allow_files = True,
        ),
        "_groovyc": attr.label(
            default = Label("//tools/base/bazel:groovyc"),
            cfg = "host",
            executable = True,
        ),
        "_groovystub": attr.label(
            default = Label("//tools/base/bazel:groovy_stub_gen"),
            cfg = "host",
            executable = True,
        ),
        "_formc": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:formc"),
            allow_files = True,
        ),
    },
    fragments = ["java"],
    outputs = {
        "production_jar": "%{name}.jar",
        "test_jar": "%{name}_test.jar",
    },
    implementation = _iml_module_impl,
)

def _iml_runtime_impl(ctx):
    providers = [ctx.attr.iml_module.module.main_provider]
    module_runtime = depset()
    transitive_data = depset()
    names = []
    module_jars = None

    for dep in ctx.attr.runtime_deps:
        if JavaInfo in dep:
            providers += [dep[JavaInfo]]
        if hasattr(dep, "runtime_info"):
            fail("runtime should not depend on runtime")
        if hasattr(dep, "module"):
            module_runtime = depset(transitive = [module_runtime, dep.module.module_runtime])
            module_jars = dep.module.module_jars
            transitive_data = depset(transitive = [transitive_data, dep.module.transitive_data])
            names = dep.module.names

    combined_provider = java_common.merge(providers)

    # for name in ctx.attr.iml_module.module.names:
    module_runtime = depset(transitive = [module_runtime, combined_provider.transitive_runtime_jars])

    return struct(
        providers = [combined_provider],
        runtime_info = struct(
            module_runtime = module_runtime,
            module_jars = module_jars,
            transitive_data = transitive_data,
            names = names,
        ),
    )

_iml_runtime = rule(
    attrs = {
        "iml_module": attr.label(),
        "runtime_deps": attr.label_list(),
    },
    fragments = ["java"],
    implementation = _iml_runtime_impl,
)

def _iml_test_module_impl(ctx):
    providers = [ctx.attr.iml_module.module.test_provider]
    for dep in ctx.attr.runtime_deps:
        if JavaInfo in dep:
            providers += [dep[JavaInfo]]
        if hasattr(dep, "module"):
            providers += [dep.module.test_provider]
    java_info = java_common.merge(providers)
    runfiles = ctx.runfiles(transitive_files = ctx.attr.iml_module.module.transitive_data)
    return [
        java_info,
        DefaultInfo(runfiles = runfiles),
    ]

_iml_test_module_ = rule(
    attrs = {
        "iml_module": attr.label(),
        "runtime_deps": attr.label_list(),
    },
    fragments = ["java"],
    implementation = _iml_test_module_impl,
)

# Macro implementation of an iml_module "rule".
# This rule corresponds to the building artifacts needed to build an IntelliJ module.
# Instantiating this rule looks similar to an .iml definition:
#
# iml_module(
#     # The name of the module used to generate the rules
#     name = "module_name",
#     # A list of directories containing the sources (.iml use directories as oposed to files)
#     srcs = ["src/main/java"],
#     # The directories with the test sources.
#     test_srcs = ["src/test/java"],
#     # The directories with the production resources
#     resources = ["src/main/resources"]
#     # A dict indicating test targets to create, each one running a subset of tests
#     # designated by `test_filter` which matches a package name or FQCN. If a test
#     # target does not define a `test_filter`, it will run the set of tests that
#     # excludes all the other filters. If a test target defines a `test_filter` which
#     # is a subset of another test filter, the test target will exclude those tests.
#     # For example:
#     #  `{"A": {"test_filter": "x.y"}, "B": {"test_filter": "x.y.z"}}`
#     # Split test target A will automatically exclude "x.y.z".
#     # Targets may specify the following common attributes: `data`, `shard_count`, and
#     # `tags`. For definitions of these attributes, see
#     # https://docs.bazel.build/versions/master/be/common-definitions.html
#     split_test_targets = {},
#     # Designates the test target with the common Flaky attribute.
#     # Tests marked Flaky will be attempted a total of 3 times, until a passing
#     # run is achieved or fail.
#     test_flaky = True,
#     # Specifies the number of parallel shards to run the test.
#     # See https://docs.bazel.build/versions/master/be/common-definitions.html#test.shard_count.
#     # Mutually exclusive with test_target_shards.
#     test_shard_count = 1,
#     # The directories with the test resources
#     test_resources = ["src/test/resources"],
#     # A tag enhanced list of dependency tags. These dependencies can contain a
#     # list of tags after the label. The supported tags are:
#     #     module: It treats the dependency as a module dependency, making
#     #             production and test sources depend on each other correctly.
#     #     test:   This dependency is included and available for test sources only.
#     deps = [
#         "//path/to/module:name[module]",
#         "//path/to/libs:junit-4.12[test]",
#         "//a/module/only/needed/in/tests:name[module, test]",
#         "//a/standard/java/dependency:dep",
#     ],
# )
#
# This macro generates the following rules visible to the user:
#
# "module_name": A Java library compiled from the production sources
# "module_name_testlib": A Java library compiled from the test sources
# "module_name_tests": A java test rule that runs the tests found in "libmodule_name_testlib.jar",
#   or a test_suite containing all split_test_targets.
# "module_name_tests__split-name": A java test rule running a split subset of tests if using
#   split_test_targets.
def iml_module(
        name,
        srcs = [],
        package_prefixes = {},
        project = "",
        test_srcs = [],
        exclude = [],
        resources = [],
        manifests = [],
        res_zips = [],
        test_resources = [],
        deps = [],
        runtime_deps = [],
        test_runtime_deps = [],
        manual_test_runtime_deps = [],
        test_friends = [],
        visibility = [],
        exports = [],
        plugins = [],
        javacopts = [],
        javacopts_from_jps = [],
        test_data = [],
        test_flaky = False,
        test_timeout = "moderate",
        test_class = "com.android.testutils.JarTestSuite",
        test_shard_count = None,
        split_test_targets = None,
        tags = None,
        test_tags = None,
        back_target = 0,
        iml_files = None,
        data = [],
        bundle_data = [],
        test_main_class = None,
        lint_baseline = None,
        lint_timeout = None,
        back_deps = [],
        bundled_deps = [],
        exec_properties = {}):
    prod_deps = []
    test_deps = []
    for dep in deps:
        label, label_tags = _get_label_and_tags(dep)
        if "test" not in label_tags:
            prod_deps += [label]
        test_deps += [label]

    srcs = split_srcs(srcs, resources, exclude)
    split_test_srcs = split_srcs(test_srcs, test_resources, exclude)
    _iml_module_(
        name = name,
        tags = tags,
        visibility = visibility,
        java_srcs = srcs.javas,
        kotlin_srcs = srcs.kotlins,
        groovy_srcs = srcs.groovies,
        form_srcs = srcs.forms,
        resources = srcs.resources,
        manifests = srcs.manifests,
        res_zips = res_zips,
        roots = srcs.roots,
        java_test_srcs = split_test_srcs.javas,
        kotlin_test_srcs = split_test_srcs.kotlins,
        groovy_test_srcs = split_test_srcs.groovies,
        form_test_srcs = split_test_srcs.forms,
        test_resources = split_test_srcs.resources,
        test_roots = split_test_srcs.roots,
        package_prefixes = package_prefixes,
        javacopts = javacopts + javacopts_from_jps,
        iml_files = iml_files,
        exports = exports,
        deps = prod_deps + ["//tools/base/bazel:langtools"],
        test_deps = test_deps + ["//tools/base/bazel:langtools"],
        test_friends = test_friends,
        data = data + bundle_data,
        test_data = test_data,
        test_class = test_class,
        bundled_deps = bundled_deps,
    )

    _iml_runtime(
        name = name + "_runtime",
        tags = tags,
        iml_module = ":" + name,
        runtime_deps = runtime_deps + [":" + name],
        visibility = visibility,
    )

    if srcs.javas + srcs.kotlins:
        coverage_baseline(
            name = name,
            srcs = srcs.javas + srcs.kotlins,
            jar = name + ".jar",
            tags = tags,
        )

    # Only add test utils to other than itself.
    test_utils = [] if name == "studio.testutils" else ["//tools/base/testutils:studio.android.sdktools.testutils"]
    _iml_test_module_(
        name = name + "_testlib",
        tags = tags,
        iml_module = ":" + name,
        testonly = True,
        visibility = visibility,
        runtime_deps = runtime_deps + test_runtime_deps + [
            ":" + name + "_runtime",
            "//tools/base/bazel:langtools",
        ] + test_utils,
    )

    lint_srcs = srcs.javas + srcs.kotlins
    if lint_baseline:
        if not lint_srcs:
            fail("lint_baseline set for iml_module that has no sources")

        kwargs = {}
        if lint_timeout:
            kwargs["timeout"] = lint_timeout

        lint_tags = tags if tags else []
        if "no_windows" not in lint_tags:
            lint_tags += ["no_windows"]

        lint_test(
            name = name + "_lint_test",
            srcs = lint_srcs,
            baseline = lint_baseline,
            deps = prod_deps,
            custom_rules = ["//tools/base/lint:studio-checks.lint-rules.jar"],
            external_annotations = ["//tools/base/external-annotations:annotations.zip"],
            tags = lint_tags,
            **kwargs
        )
    elif lint_timeout:
        fail("lint_timeout set for iml_module that doesn't use lint (set lint_baseline to enable lint)")

    if not test_srcs:
        return
    if split_test_targets and test_flaky:
        fail("must use the Flaky attribute per split_test_target")
    if split_test_targets and test_shard_count:
        fail("test_shard_count and split_test_targets should not both be specified")
    test_tags = tags + test_tags if tags and test_tags else (tags if tags else test_tags)
    if split_test_targets:
        _gen_split_tests(
            name = name,
            split_test_targets = split_test_targets,
            test_tags = test_tags,
            test_data = test_data,
            runtime_deps = manual_test_runtime_deps + [":" + name + "_testlib"],
            timeout = test_timeout,
            jvm_flags = ["-Dtest.suite.jar=" + name + "_test.jar"],
            test_class = test_class,
            visibility = visibility,
            main_class = test_main_class,
            exec_properties = exec_properties,
        )
    else:
        coverage_java_test(
            name = name + "_tests",
            tags = test_tags,
            runtime_deps = manual_test_runtime_deps + [":" + name + "_testlib"],
            flaky = test_flaky,
            timeout = test_timeout,
            shard_count = test_shard_count,
            data = test_data,
            jvm_flags = ["-Dtest.suite.jar=" + name + "_test.jar"],
            test_class = test_class,
            visibility = visibility,
            main_class = test_main_class,
            exec_properties = exec_properties,
        )

def _gen_split_tests(
        name,
        split_test_targets,
        test_tags = None,
        test_data = None,
        timeout = None,
        **kwargs):
    """Generates split test targets.

    A new test target is generated for each split_test_target, a test_suite containing all
    split targets, and a test target which does not perform any splitting. The non-split target is
    only to be used for local development with the bazel `--test_filter` flag, since this flag
    does not work on split test targets. The test_suite will only contain split tests which do not
    use the 'manual' tag.

    Args:
        name: The base name of the test.
        split_test_targets: A dict of names to split_test_target definitions.
        test_tags: optional list of tags to include for test targets.
        test_data: optional list of data to include for test targets.
        timeout: optional timeout that applies to this split test only (overriding target level).
    """

    # create a _tests__all target for local development with all test sources
    # primarily useful if users want to specify a --test_filter themselves
    coverage_java_test(
        name = name + "_tests__all",
        data = test_data + _get_unique_split_data(split_test_targets),
        tags = ["manual"],
        **kwargs
    )
    split_tests = []
    for split_name in split_test_targets:
        test_name = name + "_tests__" + split_name
        split_target = split_test_targets[split_name]
        shard_count = split_target.get("shard_count")
        tags = split_target.get("tags", default = [])
        data = split_target.get("data", default = [])
        split_timeout = split_target.get("timeout", default = timeout)
        flaky = split_target.get("flaky")
        if "manual" not in tags:
            split_tests.append(test_name)
        if test_data:
            data += test_data
        if test_tags:
            tags += test_tags

        args = _gen_split_test_args(split_name, split_test_targets)

        coverage_java_test(
            name = test_name,
            shard_count = shard_count,
            timeout = split_timeout,
            flaky = flaky,
            data = data,
            tags = tags,
            args = args,
            **kwargs
        )
    native.test_suite(
        name = name + "_tests",
        tags = ["manual"] if "manual" in test_tags else [],
        tests = split_tests,
    )

def _get_unique_split_data(split_test_targets):
    """Returns all split_test_target 'data' dependencies without duplicates."""
    data = []
    for split_name in split_test_targets:
        split_data = split_test_targets[split_name].get("data", default = [])
        [data.append(d) for d in split_data if d not in data]
    return data

def _gen_split_test_args(split_name, split_test_targets):
    """Generates the args for a split test target.

    Args:
        split_name: The name of the split_test_target to generate args for.
        split_test_targets: All the defined split_test_targets.
    Returns:
        The test args with --test_filter and --test_exclude_filter defined
        based on the test_filter given to each split_test_target.
    """
    args = []
    split_target = split_test_targets[split_name]
    test_filter = split_target.get("test_filter")
    _validate_split_test_filter(test_filter)
    if test_filter:
        args.append("--test_filter='(" + test_filter + ")'")

    excludes = _gen_split_test_excludes(split_name, split_test_targets)
    if excludes:
        args.append("--test_exclude_filter='(" + "|".join(excludes) + ")'")
    return args

def _validate_split_test_filter(test_filter):
    """Validates the test_filter matches a package or FQCN format."""
    if not test_filter:
        return
    if test_filter.startswith("."):
        # Allow trailing packages, e.g. ".gradle", which could for example match
        # against "test.subpackage1.gradle" AND "test.subpackage2.gradle"
        test_filter = test_filter[1:]
    for split in test_filter.split("."):
        if not (split.isalnum()):
            fail("invalid test_filter '%s'. Must be package name or FQCN" % test_filter)

def _gen_split_test_excludes(split_name, split_test_targets):
    """Generates a list of test exclude filters.

    These are used to exclude tests from running when other split_test_targets define
    a 'test_filter' that is a subset of another test_filter. e.g.,
    {
      "A": {"test_filter": "com.bar"},
      "B": {"test_filter": "com.bar.MyTest"},
    }
    The split_target A will generate an excludes ["com.bar.MyTest"], and
    split_target B will generate no excludes.

    If a split_test_target has no 'test_filter', it will generate a
    list of excludes based on all the other test filters.

    Args:
        split_name: The name of the split_test_target to generate excludes for.
        split_test_targets: All the defined split_test_targets.
    Returns:
        A list of exclude filters based on the 'test_filter' of other
        split_test_targets.
    """
    split_target = split_test_targets[split_name]
    test_filter = split_target.get("test_filter")
    excludes = []
    for other_split_name in split_test_targets:
        # pass over the split_test_target we're generating excludes for
        if split_name == other_split_name:
            continue

        other = split_test_targets[other_split_name]
        other_test_filter = other.get("test_filter")
        if not other_test_filter:
            if not test_filter:
                fail("Cannot have more than one split_test_targets without a 'test_filter'.")
            continue

        # empty test filter, always exclude other test filters
        if not test_filter:
            excludes.append(other_test_filter)
            continue

        if other_test_filter.startswith(test_filter):
            excludes.append(other_test_filter)

    return excludes

def split_srcs(src_dirs, res_dirs, exclude):
    roots = src_dirs + res_dirs
    exts = ["java", "kt", "groovy", "DS_Store", "form", "flex", "MF"]
    excludes = []
    for root in roots:
        excludes += [root + "/**/*." + ext for ext in exts]

    resources = native.glob(
        include = [src + "/**" for src in roots],
        exclude = excludes,
    )
    javas = native.glob([src + "/**/*.java" for src in src_dirs], exclude)
    kotlins = native.glob([src + "/**/*.kt" for src in src_dirs], exclude)
    groovies = native.glob([src + "/**/*.groovy" for src in src_dirs], exclude)
    forms = native.glob([src + "/**/*.form" for src in src_dirs], exclude)
    manifests = native.glob([src + "/**/*.MF" for src in src_dirs], exclude)
    return struct(
        roots = roots,
        resources = resources,
        javas = javas,
        kotlins = kotlins,
        groovies = groovies,
        forms = forms,
        manifests = manifests,
    )
