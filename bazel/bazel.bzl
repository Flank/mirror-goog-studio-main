load(":coverage.bzl", "coverage_baseline", "coverage_java_test")
load(":functions.bzl", "create_option_file", "explicit_target", "label_workspace_path", "workspace_path")
load(":kotlin.bzl", "kotlin_compile")
load(":kotlin.bzl", "test_kotlin_use_ir")
load(":lint.bzl", "lint_test")
load(":merge_archives.bzl", "run_singlejar")
load("@bazel_tools//tools/jdk:toolchain_utils.bzl", "find_java_runtime_toolchain", "find_java_toolchain")

ImlModuleInfo = provider(
    doc = "Info produced by the iml_module rule.",
    fields = [
        "module_jars",
        "forms",
        "test_forms",
        "java_deps",
        "test_provider",
        "main_provider",
        "module_deps",
        "plugin_deps",
        "external_deps",
        "jvm_target",
        "names",
    ],
)

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

def _iml_module_jar_impl(
        ctx,
        name,
        roots,
        java_srcs,
        kotlin_srcs,
        form_srcs,
        resources,
        res_zips,
        output_jar,
        java_deps,
        java_runtime_deps,
        form_deps,
        exports,
        friend_jars,
        module_name,
        use_ijar):
    jars = []
    ijars = []
    sourcepath = []
    forms = []

    java_jar = ctx.actions.declare_file(name + ".java.jar") if java_srcs else None
    kotlin_jar = ctx.actions.declare_file(name + ".kotlin.jar") if kotlin_srcs else None
    kotlin_ijar = ctx.actions.declare_file(name + ".kotlin-ijar.jar") if kotlin_srcs and use_ijar else None
    full_ijar = ctx.actions.declare_file(name + ".merged-ijar-iml.jar") if use_ijar else None

    # Compiler args and JVM target.
    java_toolchain = find_java_toolchain(ctx, ctx.attr._java_toolchain)
    jvm_target = ctx.attr.jvm_target if ctx.attr.jvm_target else java_toolchain.target_version
    javac_opts = java_common.default_javac_opts(java_toolchain = java_toolchain) + ctx.attr.javacopts
    kotlinc_opts = list(ctx.attr.kotlinc_opts)
    if jvm_target == "8":
        javac_opts += ["--release", "8"]
        kotlinc_opts += ["-jvm-target", "1.8"]
        kt_java_runtime = ctx.attr._kt_java_runtime_8[java_common.JavaRuntimeInfo]
    elif jvm_target == "11":
        # Ideally we use "--release 11" for javac too, but that is incompatible with "--add-exports".
        kotlinc_opts += ["-jvm-target", "11"]
        kt_java_runtime = ctx.attr._kt_java_runtime_11[java_common.JavaRuntimeInfo]
    else:
        fail("JVM target " + jvm_target + " is not currently supported in iml_module")

    # Kotlin
    kotlin_providers = []
    if kotlin_srcs:
        kotlin_providers += [kotlin_compile(
            ctx = ctx,
            name = module_name,
            srcs = java_srcs + kotlin_srcs,
            deps = java_deps,
            friend_jars = friend_jars,
            out = kotlin_jar,
            out_ijar = kotlin_ijar,
            java_runtime = kt_java_runtime,
            kotlinc_opts = kotlinc_opts,
            transitive_classpath = False,  # Matches JPS.
        )]
        jars += [kotlin_jar]
        if use_ijar:
            ijars += [kotlin_ijar]

    # Resources.
    if resources:
        resources_jar = ctx.actions.declare_file(name + ".res.jar")
        resources_impl(ctx, name, roots, resources, resources_jar)
        jars += [resources_jar]
    if res_zips:
        jars += res_zips

    # Java
    if java_srcs:
        compiled_java = ctx.actions.declare_file(name + ".pjava.jar") if form_srcs else java_jar
        formc_input_jars = [compiled_java] + ([kotlin_jar] if kotlin_jar else [])

        java_provider = java_common.compile(
            ctx,
            source_files = java_srcs,
            output = compiled_java,
            deps = java_deps + kotlin_providers,
            javac_opts = javac_opts,
            java_toolchain = java_toolchain,
            sourcepath = sourcepath,
            # TODO(b/216385876) After updating to Bazel 5.0, use enable_compile_jar_action = use_ijar,
        )
        if use_ijar:
            # Note: we exclude formc output from ijars, since formc does not generate APIs used downstream.
            ijars += java_provider.compile_jars.to_list()

        # Forms
        if form_srcs:
            forms += relative_paths(ctx, form_srcs, roots)

            # formc requires full compile jars (no ijars/hjars).
            form_dep_jars = depset(transitive = [
                java_common.make_non_strict(dep).full_compile_jars
                for dep in java_deps
            ])

            # Note: we explicitly include the bootclasspath from the current Java toolchain with
            # the classpath, because extracting it at runtime, when we are running in the
            # FormCompiler JVM, is not portable across JDKs (and made much harder on JDK9+).
            form_classpath = depset(transitive = [form_dep_jars, java_toolchain.bootclasspath])

            args = ctx.actions.args()
            args.add_joined("-cp", form_classpath, join_with = ":")
            args.add("-o", java_jar)
            args.add_all(form_srcs)
            args.add_all([k + "=" + v.path for k, v in form_deps])
            args.add_all(formc_input_jars)

            # To support persistent workers, arguments must come from a param file..
            args.use_param_file("@%s", use_always = True)
            args.set_param_file_format("multiline")

            ctx.actions.run(
                inputs = depset(
                    direct = [v for _, v in form_deps] + form_srcs + formc_input_jars,
                    transitive = [form_classpath],
                ),
                outputs = [java_jar],
                mnemonic = "formc",
                arguments = [args],
                executable = ctx.executable._formc,
                execution_requirements = {"supports-multiplex-workers": "1"},
            )

        jars += [java_jar]

    if form_srcs and not java_srcs:
        fail("Forms only supported with java sources")

    run_singlejar(
        ctx = ctx,
        jars = jars,
        out = output_jar,
        allow_duplicates = True,  # TODO: Ideally we could be more strict here.
    )

    if use_ijar:
        run_singlejar(
            ctx = ctx,
            jars = ijars,
            out = full_ijar,
            allow_duplicates = True,
        )

    providers = []
    providers = [JavaInfo(
        output_jar = output_jar,
        compile_jar = full_ijar or output_jar,
        deps = java_deps,
        runtime_deps = java_runtime_deps,
    )]
    providers += exports

    return java_common.merge(providers), forms

def merge_runfiles(deps):
    return depset(transitive = [
        dep[DefaultInfo].default_runfiles.files
        for dep in deps
        if dep[DefaultInfo].default_runfiles
    ])

def _iml_module_impl(ctx):
    names = [iml.basename[:-4] for iml in ctx.files.iml_files if iml.basename.endswith(".iml")]

    # Prod dependencies.
    java_deps = []
    form_deps = []
    for this_dep in ctx.attr.deps:
        if ImlModuleInfo in this_dep:
            form_deps += this_dep[ImlModuleInfo].forms
        if JavaInfo in this_dep:
            java_deps += [this_dep[JavaInfo]]
    module_deps = []
    plugin_deps = []
    external_deps = []
    for dep in ctx.attr.deps:
        if ImlModuleInfo in dep:
            module_deps += [dep]
            if ctx.attr.jvm_target:
                if not dep[ImlModuleInfo].jvm_target or int(dep[ImlModuleInfo].jvm_target) > int(ctx.attr.jvm_target):
                    fail("The module %s has a jvm_target of \"%s\", but depends on module %s with target \"%s\"" % (
                        ctx.attr.name,
                        ctx.attr.jvm_target,
                        dep[ImlModuleInfo].names[0],
                        dep[ImlModuleInfo].jvm_target,
                    ))
        elif hasattr(dep, "plugin_info"):
            plugin_deps += [dep]
        elif hasattr(dep, "platform_info"):
            pass
        else:
            external_deps += [dep]

    # Test dependencies (superset of prod).
    test_java_deps = []
    test_form_deps = []
    for this_dep in ctx.attr.test_deps:
        if JavaInfo in this_dep:
            test_java_deps += [this_dep[JavaInfo]]
        if ImlModuleInfo in this_dep:
            test_form_deps += this_dep[ImlModuleInfo].test_forms
            test_java_deps += [this_dep[ImlModuleInfo].test_provider]

    # Exports.
    exports = []
    test_exports = []
    for export in ctx.attr.exports:
        if JavaInfo in export:
            exports += [export[JavaInfo]]
        if ImlModuleInfo in export:
            test_exports += [export[ImlModuleInfo].test_provider]

    # Runfiles.
    # Note: the runfiles for test-only deps should technically not be in
    # the prod module, but it is simpler this way (and not very harmful).
    transitive_data = depset(
        direct = ctx.files.iml_files + ctx.files.data,
        transitive = [
            merge_runfiles(ctx.attr.deps),
            merge_runfiles(ctx.attr.test_deps),
            merge_runfiles(ctx.attr.runtime_deps),
        ],
    )
    runfiles = ctx.runfiles(transitive_files = transitive_data)

    # If multiple modules we use the label, otherwise use the exact module name
    module_name = names[0] if len(names) == 1 else ctx.label.name
    main_provider, main_forms = _iml_module_jar_impl(
        ctx = ctx,
        name = ctx.label.name,
        roots = ctx.attr.roots,
        java_srcs = ctx.files.java_srcs,
        kotlin_srcs = ctx.files.kotlin_srcs,
        form_srcs = ctx.files.form_srcs,
        resources = ctx.files.resources,
        res_zips = ctx.files.res_zips,
        output_jar = ctx.outputs.production_jar,
        java_deps = java_deps,
        java_runtime_deps = [dep[JavaInfo] for dep in ctx.attr.runtime_deps],
        form_deps = form_deps,
        exports = exports,
        friend_jars = [],
        module_name = module_name,
        use_ijar = True,
    )

    friend_jars = main_provider.compile_jars.to_list()
    for test_friend in ctx.attr.test_friends:
        friend_jars += test_friend[JavaInfo].compile_jars.to_list()

    test_provider, test_forms = _iml_module_jar_impl(
        ctx = ctx,
        name = ctx.label.name + "_test",
        roots = ctx.attr.test_roots,
        java_srcs = ctx.files.java_test_srcs,
        kotlin_srcs = ctx.files.kotlin_test_srcs,
        form_srcs = ctx.files.form_test_srcs,
        resources = ctx.files.test_resources,
        res_zips = [],
        output_jar = ctx.outputs.test_jar,
        java_deps = [main_provider] + test_java_deps,
        java_runtime_deps = [],  # Runtime deps already inherited from prod module.
        form_deps = test_form_deps,
        exports = exports + test_exports,
        friend_jars = friend_jars,
        module_name = module_name,
        use_ijar = False,
    )

    iml_module_info = ImlModuleInfo(
        module_jars = ctx.outputs.production_jar,
        forms = main_forms,
        test_forms = test_forms,
        java_deps = java_deps,
        test_provider = test_provider,
        main_provider = main_provider,
        module_deps = depset(direct = module_deps),
        plugin_deps = depset(direct = plugin_deps),
        external_deps = depset(direct = external_deps),
        jvm_target = ctx.attr.jvm_target,
        names = names,
    )

    return [
        iml_module_info,
        main_provider,
        DefaultInfo(runfiles = runfiles),
    ]

_iml_module_ = rule(
    attrs = {
        "iml_files": attr.label_list(
            allow_files = True,
            allow_empty = False,
            mandatory = True,
        ),
        "java_srcs": attr.label_list(allow_files = True),
        "kotlin_srcs": attr.label_list(allow_files = True),
        "kotlin_use_compose": attr.bool(),
        "kotlin_use_ir": attr.bool(),
        "form_srcs": attr.label_list(allow_files = True),
        "java_test_srcs": attr.label_list(allow_files = True),
        "kotlin_test_srcs": attr.label_list(allow_files = True),
        "form_test_srcs": attr.label_list(allow_files = True),
        "jvm_target": attr.string(),
        "javacopts": attr.string_list(),
        "kotlinc_opts": attr.string_list(),
        "resources": attr.label_list(allow_files = True),
        "manifests": attr.label_list(allow_files = True),
        "res_zips": attr.label_list(allow_files = True),
        "test_resources": attr.label_list(allow_files = True),
        "package_prefixes": attr.string_dict(),
        "test_class": attr.string(),
        "exports": attr.label_list(providers = [[JavaInfo], [ImlModuleInfo]]),
        "roots": attr.string_list(),
        "test_roots": attr.string_list(),
        # TODO(b/218538628): Add proper support for native libs; right now they are effectively data deps.
        "deps": attr.label_list(providers = [[JavaInfo], [ImlModuleInfo], [CcInfo]]),
        "runtime_deps": attr.label_list(providers = [JavaInfo]),
        "test_deps": attr.label_list(providers = [[JavaInfo], [ImlModuleInfo], [CcInfo]]),
        "test_friends": attr.label_list(providers = [JavaInfo]),
        "data": attr.label_list(allow_files = True),
        "_java_toolchain": attr.label(default = Label("//prebuilts/studio/jdk:jdk11_toolchain_java11")),
        "_kt_java_runtime_8": attr.label(
            # We need this to be able to target JRE 8 in Kotlin, because
            # Kotlinc does not support the --release 8 Javac option.
            default = Label("//prebuilts/studio/jdk:jdk_runtime"),
            providers = [java_common.JavaRuntimeInfo],
            cfg = "exec",
        ),
        "_kt_java_runtime_11": attr.label(
            # We need this to be able to target JRE 11 in Kotlin, because
            # Kotlinc does not support the --release 11 Javac option.
            default = Label("//prebuilts/studio/jdk:jdk11_runtime"),
            providers = [java_common.JavaRuntimeInfo],
            cfg = "exec",
        ),
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
        "_compose_plugin": attr.label(
            default = Label("//prebuilts/tools/common/m2:compose-compiler-hosted"),
            cfg = "host",
            allow_single_file = [".jar"],
        ),
        "_jvm_abi_gen": attr.label(
            default = Label("//prebuilts/tools/common/m2:jvm-abi-gen-plugin"),
            cfg = "host",
            allow_single_file = [".jar"],
        ),
        "_kotlin": attr.label(
            default = Label("@maven//:org.jetbrains.kotlin.kotlin-stdlib"),
            allow_files = True,
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

def _iml_test_module_impl(ctx):
    runfiles = ctx.attr.iml_module[DefaultInfo].default_runfiles
    return [
        ctx.attr.iml_module[ImlModuleInfo].test_provider,  # JavaInfo.
        DefaultInfo(runfiles = runfiles),
    ]

_iml_test_module_ = rule(
    attrs = {
        "iml_module": attr.label(providers = [ImlModuleInfo]),
    },
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
        test_friends = [],
        visibility = [],
        exports = [],
        plugins = [],
        jvm_target = None,
        javacopts = [],
        javacopts_from_jps = [],
        enable_tests = True,
        test_data = [],
        test_flaky = False,
        test_jvm_flags = [],
        test_timeout = "moderate",
        test_class = "com.android.testutils.JarTestSuite",
        test_shard_count = None,
        split_test_targets = None,
        tags = None,
        test_tags = None,
        back_target = 0,
        iml_files = None,
        data = [],
        test_main_class = None,
        lint_baseline = None,
        lint_timeout = None,
        back_deps = [],
        exec_properties = {},
        kotlin_use_compose = False):
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
        kotlin_use_compose = kotlin_use_compose,
        kotlin_use_ir = test_kotlin_use_ir(),
        form_srcs = srcs.forms,
        resources = srcs.resources,
        res_zips = res_zips,
        roots = srcs.roots,
        java_test_srcs = split_test_srcs.javas,
        kotlin_test_srcs = split_test_srcs.kotlins,
        form_test_srcs = split_test_srcs.forms,
        test_resources = split_test_srcs.resources,
        test_roots = split_test_srcs.roots,
        package_prefixes = package_prefixes,
        jvm_target = jvm_target,
        javacopts = javacopts + javacopts_from_jps,
        iml_files = iml_files,
        exports = exports,
        deps = prod_deps,
        runtime_deps = runtime_deps,
        test_deps = test_deps,
        test_friends = test_friends,
        data = data,
        test_class = test_class,
    )

    if srcs.javas + srcs.kotlins:
        coverage_baseline(
            name = name,
            srcs = srcs.javas + srcs.kotlins,
            jar = name + ".jar",
            tags = tags,
        )

    _iml_test_module_(
        name = name + "_testlib",
        tags = tags,
        iml_module = ":" + name,
        testonly = True,
        visibility = visibility,
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

    # The default test_class (JarTestSuite) comes from testutils, so we add testutils as a runtime dep.
    test_utils = [] if name == "studio.android.sdktools.testutils" else ["//tools/base/testutils:studio.android.sdktools.testutils_testlib"]

    if split_test_targets and test_flaky:
        fail("must use the Flaky attribute per split_test_target")
    if split_test_targets and test_shard_count:
        fail("test_shard_count and split_test_targets should not both be specified")
    if enable_tests:
        test_tags = tags + test_tags if tags and test_tags else (tags if tags else test_tags)
        if split_test_targets:
            _gen_split_tests(
                name = name,
                split_test_targets = split_test_targets,
                test_tags = test_tags,
                test_data = test_data,
                runtime_deps = [":" + name + "_testlib"] + test_utils,
                timeout = test_timeout,
                jvm_flags = test_jvm_flags + ["-Dtest.suite.jar=" + name + "_test.jar"],
                test_class = test_class,
                visibility = visibility,
                main_class = test_main_class,
                exec_properties = exec_properties,
            )
        else:
            coverage_java_test(
                name = name + "_tests",
                tags = test_tags,
                runtime_deps = [":" + name + "_testlib"] + test_utils,
                flaky = test_flaky,
                timeout = test_timeout,
                shard_count = test_shard_count,
                data = test_data,
                jvm_flags = test_jvm_flags + ["-Dtest.suite.jar=" + name + "_test.jar"],
                test_class = test_class,
                visibility = visibility,
                main_class = test_main_class,
                exec_properties = exec_properties,
            )
    else:
        if test_tags:
            fail("enable_tests is False but test_tags was specified.")
        if test_data:
            fail("enable_tests is False but test_data was specified.")
        if test_flaky:
            fail("enable_tests is False but test_flaky was specified.")
        if test_jvm_flags:
            fail("enable_tests is False but test_jvm_flags was specified.")
        if test_shard_count:
            fail("enable_tests is False but test_shard_count was specified.")
        if split_test_targets:
            fail("enable_tests is False but split_test_targets was specified.")

def iml_test(
        name,
        module,
        runtime_deps = [],
        **kwargs):
    native.java_test(
        name = name,
        runtime_deps = runtime_deps + [module + "_testlib"],
        **kwargs
    )

def _gen_split_tests(
        name,
        split_test_targets,
        test_tags = None,
        test_data = None,
        timeout = None,
        exec_properties = None,
        jvm_flags = [],
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
        exec_properties = exec_properties,
        jvm_flags = jvm_flags,
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

        test_jvm_flags = []
        test_jvm_flags.extend(jvm_flags)
        test_jvm_flags.extend(_gen_split_test_jvm_flags(split_name, split_test_targets))
        test_exec_properties = split_target.get("exec_properties", default = exec_properties)

        coverage_java_test(
            name = test_name,
            shard_count = shard_count,
            timeout = split_timeout,
            flaky = flaky,
            data = data,
            tags = tags,
            exec_properties = test_exec_properties,
            jvm_flags = test_jvm_flags,
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

def _gen_split_test_jvm_flags(split_name, split_test_targets):
    """Generates jvm_flags for a split test target.

    Args:
        split_name: The name of the split_test_target to generate.
        split_test_targets: All the defined split_test_targets.
    Returns:
        The test jvm_flags with test_filter and test_exclude_filter defined
        based on the test_filter given to each split_test_target.
    """
    args = []
    jvm_flags = []
    split_target = split_test_targets[split_name]
    test_filter = split_target.get("test_filter")
    _validate_split_test_filter(test_filter)
    if test_filter:
        jvm_flags.append("-Dtest_filter=\"(" + test_filter + ")\"")

    excludes = _gen_split_test_excludes(split_name, split_test_targets)
    if excludes:
        jvm_flags.append("-Dtest_exclude_filter=\"(" + "|".join(excludes) + ")\"")
    return jvm_flags

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
    exts = ["java", "kt", "groovy", "DS_Store", "form", "flex"]
    excludes = []
    for root in roots:
        excludes += [root + "/**/*." + ext for ext in exts]

    resources = native.glob(
        include = [src + "/**" for src in roots],
        exclude = excludes,
    )
    groovies = native.glob([src + "/**/*.groovy" for src in src_dirs], exclude)
    if groovies:
        fail("Groovy is not supported")

    javas = native.glob([src + "/**/*.java" for src in src_dirs], exclude)
    kotlins = native.glob([src + "/**/*.kt" for src in src_dirs], exclude)
    forms = native.glob([src + "/**/*.form" for src in src_dirs], exclude)

    return struct(
        roots = roots,
        resources = resources,
        javas = javas,
        kotlins = kotlins,
        forms = forms,
    )
