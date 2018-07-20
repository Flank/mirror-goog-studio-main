load(":functions.bzl", "create_java_compiler_args_srcs", "explicit_target", "create_option_file", "label_workspace_path", "workspace_path")
load(":groovy.bzl", "groovy_impl")
load(":kotlin.bzl", "kotlin_impl")
load(":utils.bzl", "fileset", "java_jarjar", "singlejar")

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
  return label[:rfind], [tag.strip() for tag in label[rfind+1:-1].split(",")]

# Returns the paths of the given files relative to any of the roots. Were
# files is a list of File objects, and roots is a list strings represnting
# paths of directories relative to the package
# If a file is not found to be inside any of the given roots, it is ignored.
def relative_paths(ctx, files, roots):
  paths = []
  for file in files:
    for root in roots:
      path = label_workspace_path(ctx.label) + "/" + root
      if file.path.startswith(path):
        paths += [(file.path[len(path) + 1:], file)]
  return paths

def resources_impl(ctx, name, roots, resources, resources_jar):
  zipper_args = ["c", resources_jar.path]
  zipper_files = "".join([k + "=" + v.path + "\n" for k,v in relative_paths(ctx, resources, roots)])
  zipper_list = create_option_file(ctx, name + ".res.lst", zipper_files)
  zipper_args += ["@" + zipper_list.path]
  ctx.action(
    inputs = resources + [zipper_list],
    outputs = [resources_jar],
    executable = ctx.executable._zipper,
    arguments = zipper_args,
    progress_message = "Creating zip...",
    mnemonic = "zipper",
  )

def accumulate_provider(provider, deps, runtime, compile_time):
  deps += [provider]
  runtime += provider.transitive_runtime_jars
  compile_time += provider.transitive_compile_time_jars
  return deps, runtime, compile_time

def _iml_module_jar_impl(ctx,
    name,
    roots,
    java_srcs,
    kotlin_srcs,
    groovy_srcs,
    form_srcs,
    resources,
    output_jar,
    java_deps,
    form_deps,
    exports,
    friends,
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
    groovy_deps += list(transitive_runtime_jars)
    stub_jar = ctx.actions.declare_file(name + ".groovy_stubs.src.jar")
    groovy_impl(ctx, roots, groovy_srcs, groovy_deps, transitive_runtime_jars, groovy_jar, stub_jar)
    sourcepath += [stub_jar]
    jars += [groovy_jar]

  # Kotlin
  kotlin_providers = []
  if kotlin_srcs:
    kotlin_providers += [kotlin_impl(ctx, name, roots, java_srcs, kotlin_srcs,
        transitive_runtime_jars + transitive_compile_time_jars, ctx.attr.package_prefixes, kotlin_jar, friends)]

    jars += [kotlin_jar]

  # Java
  if java_srcs:
    compiled_java = ctx.actions.declare_file(name + ".pjava.jar") if form_srcs else java_jar
    formc_input_jars = [compiled_java] + ([kotlin_jar] if kotlin_jar else [])

    java_provider = java_common.compile(
      ctx,
      source_files = java_srcs,
      output = compiled_java,
      deps = java_deps + kotlin_providers,
      javac_opts = java_common.default_javac_opts(ctx, java_toolchain_attr = "_java_toolchain"),
      java_toolchain = ctx.attr._java_toolchain,
      host_javabase = ctx.attr._host_javabase,
      sourcepath = sourcepath,
    )
    # Forms
    if form_srcs:
      forms += relative_paths(ctx, form_srcs, roots)
      args, option_files = create_java_compiler_args_srcs(ctx,
        [form.path for form in form_srcs] + [k + "=" + v.path for k,v in form_deps] + [f.path for f in formc_input_jars],
        java_jar,
        transitive_runtime_jars)
      ctx.action(
          inputs = [v for _,v in form_deps] + form_srcs + formc_input_jars + option_files + transitive_runtime_jars.to_list(),
          outputs = [java_jar],
          mnemonic = "formc",
          arguments = args,
          executable = ctx.executable._formc,
      )

    jars += [java_jar]

  if form_srcs and not java_srcs:
    fail("Forms only supported with java sources")

  # Resources
  if resources:
    resources_jar = ctx.actions.declare_file(name + ".res.jar")
    resources_impl(ctx, name, roots, resources, resources_jar)
    jars += [resources_jar]

  ctx.action(
      inputs = jars,
      outputs = [output_jar],
      mnemonic = "module",
      arguments = [output_jar.path] + [jar.path for jar in jars],
      executable = ctx.executable._singlejar
  )

  providers = []
  providers += [java_common.create_provider(
      compile_time_jars = [output_jar],
      runtime_jars = [output_jar],
      transitive_compile_time_jars = [output_jar] + list(transitive_compile_time_jars),
      transitive_runtime_jars = [output_jar] + list(transitive_runtime_jars),
      use_ijar = False,
  )]
  providers += exports

  return java_common.merge(providers), forms

def _iml_module_impl(ctx):
  names = [iml.basename[:-4] for iml in ctx.files.iml_files if iml.basename.endswith(".iml")]

  transitive_data = depset()
  java_deps = []
  form_deps = []

  transitive_runtime_jars = depset(order = "preorder")
  transitive_compile_time_jars = depset(order = "preorder")

  for this_dep in ctx.attr.deps:
    if hasattr(this_dep, "module"):
      transitive_data += this_dep.module.transitive_data
      form_deps += this_dep.module.forms
    if java_common.provider in this_dep:
      java_deps, transitive_runtime_jars, transitive_compile_time_jars = accumulate_provider(
          this_dep[java_common.provider], java_deps, transitive_runtime_jars, transitive_compile_time_jars)

  test_java_deps = []
  test_form_deps = []
  transitive_test_runtime_jars = depset([ctx.outputs.production_jar], order = "preorder")
  transitive_test_compile_time_jars = depset([ctx.outputs.production_jar], order  = "preorder")

  for this_dep in ctx.attr.test_deps:
    if java_common.provider in this_dep:
      test_java_deps, transitive_test_runtime_jars, transitive_test_compile_time_jars = accumulate_provider(
          this_dep[java_common.provider], test_java_deps, transitive_test_runtime_jars, transitive_test_compile_time_jars)
    if hasattr(this_dep, "module"):
      transitive_data += this_dep.module.transitive_data
      test_form_deps += this_dep.module.test_forms
      test_java_deps, transitive_test_runtime_jars, transitive_test_compile_time_jars = accumulate_provider(
          this_dep.module.test_provider, test_java_deps, transitive_test_runtime_jars, transitive_test_compile_time_jars)

  exports = []
  test_exports = []
  for export in ctx.attr.exports:
    if java_common.provider in export:
      exports += [export[java_common.provider]]
    if hasattr(export, "module"):
      test_exports += [export.module.test_provider]

  module_jars = ctx.outputs.production_jar
  module_runtime = transitive_runtime_jars

  transitive_data += depset(ctx.files.iml_files + ctx.files.data)

  main_provider, main_forms = _iml_module_jar_impl(
    ctx,
    ctx.label.name,
    ctx.attr.roots,
    ctx.files.java_srcs,
    ctx.files.kotlin_srcs,
    ctx.files.groovy_srcs,
    ctx.files.form_srcs,
    ctx.files.resources,
    ctx.outputs.production_jar,
    java_deps,
    form_deps,
    exports,
    [],
    transitive_compile_time_jars,
    transitive_runtime_jars)

  test_provider, test_forms  = _iml_module_jar_impl(
    ctx,
    ctx.label.name + "_test",
    ctx.attr.test_roots,
    ctx.files.java_test_srcs,
    ctx.files.kotlin_test_srcs,
    ctx.files.groovy_test_srcs,
    ctx.files.form_test_srcs,
    ctx.files.test_resources,
    ctx.outputs.test_jar,
    [main_provider] + test_java_deps,
    test_form_deps,
    exports + test_exports,
    [ctx.outputs.production_jar],
    transitive_test_compile_time_jars,
    transitive_test_runtime_jars,
  )

  return struct(
    module = struct(
      module_jars = module_jars,
      module_runtime = module_runtime,
      transitive_data = transitive_data,
      forms = main_forms,
      test_forms = test_forms,
      java_deps = java_deps,
      test_provider = test_provider,
      main_provider = main_provider,
      names = names,
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
        "test_resources": attr.label_list(allow_files = True),
        "package_prefixes": attr.string_dict(),
        "test_class": attr.string(),
        "exports": attr.label_list(),
        "roots": attr.string_list(),
        "test_roots": attr.string_list(),
        "deps": attr.label_list(),
        "test_deps": attr.label_list(),
        "data": attr.label_list(allow_files = True),
        "test_data": attr.label_list(allow_files = True),
        "_java_toolchain": attr.label(default = Label("@bazel_tools//tools/jdk:toolchain")),
        "_host_javabase": attr.label(default = Label("@bazel_tools//tools/jdk:current_host_java_runtime")),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
        "_singlejar": attr.label(
            default = Label("//tools/base/bazel:singlejar"),
            cfg = "host",
            executable = True,
        ),
        "_kotlinc": attr.label(
            default = Label("//tools/base/bazel:kotlinc"),
            cfg = "host",
            executable = True,
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
    if java_common.provider in dep:
      providers += [dep[java_common.provider]]
    if hasattr(dep, "runtime_info"):
      fail("runtime should not depend on runtime")
    if hasattr(dep, "module"):
      module_runtime += dep.module.module_runtime
      module_jars = dep.module.module_jars
      transitive_data += dep.module.transitive_data
      names = dep.module.names

  combined_provider = java_common.merge(providers)
  # for name in ctx.attr.iml_module.module.names:
  module_runtime += combined_provider.transitive_runtime_jars

  return struct(
    providers = [combined_provider],
    runtime_info = struct(
      module_runtime = module_runtime,
      module_jars = module_jars,
      transitive_data = transitive_data,
      names = names,
    )
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
    if java_common.provider in dep:
      providers += [dep[java_common.provider]]
    if hasattr(dep, "module"):
      providers += [dep.module.test_provider]
  combined = java_common.merge(providers)
  return struct(
    providers = [combined],
  )

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
# "module_name_tests": A java test rule that runs the tests found in "libmodule_name_testlib.jar"
def iml_module(name,
    srcs=[],
    package_prefixes={},
    test_srcs=[],
    exclude=[],
    resources=[],
    res_zips=[],
    test_resources=[],
    deps=[],
    runtime_deps=[],
    test_runtime_deps=[],
    manual_test_runtime_deps=[],
    visibility=[],
    exports=[],
    plugins=[],
    javacopts=[],
    test_data=[],
    test_timeout="moderate",
    test_class="com.android.testutils.JarTestSuite",
    test_shard_count=None,
    tags=None,
    test_tags=None,
    back_target=0,
    iml_files=None,
    bundle_data=[],
    test_main_class = None,
    back_deps=[]):

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
    roots = srcs.roots,
    java_test_srcs = split_test_srcs.javas,
    kotlin_test_srcs = split_test_srcs.kotlins,
    groovy_test_srcs = split_test_srcs.groovies,
    form_test_srcs = split_test_srcs.forms,
    test_resources = split_test_srcs.resources,
    test_roots = split_test_srcs.roots,
    package_prefixes = package_prefixes,
    javacopts = javacopts,
    iml_files = iml_files,
    exports = exports,
    deps = prod_deps + ["//tools/base/bazel:langtools"],
    test_deps = test_deps + ["//tools/base/bazel:langtools"],
    data = bundle_data,
    test_data = test_data,
    test_class = test_class,
  )

  _iml_runtime(
    name = name + "_runtime",
    tags = tags,
    iml_module = ":" + name,
    runtime_deps = runtime_deps + [":" + name],
    visibility = visibility,
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

  test_tags = tags + test_tags if tags and test_tags else (tags if tags else test_tags)
  if test_srcs:
    native.java_test(
        name = name + "_tests",
        tags = test_tags,
        runtime_deps = manual_test_runtime_deps + [":" + name + "_testlib"],
        timeout = test_timeout,
        shard_count = test_shard_count,
        data = test_data,
        jvm_flags = ["-Dtest.suite.jar=" + name + "_test.jar"],
        test_class = test_class,
        visibility = visibility,
        main_class = test_main_class,
    )

def split_srcs(src_dirs, res_dirs, exclude):
  roots = src_dirs + res_dirs
  exts = ["java", "kt", "groovy", "DS_Store", "form", "flex"]
  excludes = []
  for root in roots:
    excludes += [ root + "/**/*." + ext for ext in exts]

  resources = native.glob(
      include = [src + "/**" for src in roots],
      exclude = excludes,
  )
  javas = native.glob([src + "/**/*.java" for src in src_dirs], exclude)
  kotlins = native.glob([src + "/**/*.kt" for src in src_dirs], exclude)
  groovies = native.glob([src + "/**/*.groovy" for src in src_dirs], exclude)
  forms = native.glob([src + "/**/*.form" for src in src_dirs], exclude)
  return struct(
      roots = roots,
      resources = resources,
      javas = javas,
      kotlins = kotlins,
      groovies = groovies,
      forms = forms,
    )

def _iml_project_impl(ctx):
  imls = []
  deps = []
  inputs = []

  module_jars = dict()
  module_runtime = dict()
  transitive_data = depset()

  for dep in ctx.attr.modules:
    if hasattr(dep, "runtime_info"):
      for name in dep.runtime_info.names:
        module_runtime[name] = dep.runtime_info.module_runtime
        module_jars[name] = dep.runtime_info.module_jars
      transitive_data += dep.runtime_info.transitive_data
    if hasattr(dep, "module"):
      fail("Don't depend on modules directly: " + str(dep.label))

  for dep in ctx.attr.libraries + ctx.attr.modules:
    if java_common.provider in dep:
      transitive_data += dep[java_common.provider].transitive_runtime_jars

  text = ""
  transitive_data += module_jars.values()
  for name, files in module_runtime.items():
    transitive_data += files
    text += name + ": " + module_jars[name].path
    for file in files:
      text += ":" + file.path
    text += "\n"

  module_info = ctx.new_file(ctx.label.name + ".module_info")
  ctx.file_action(
    output = module_info,
    content = text
  )

  artifacts = ""
  for dep in ctx.attr.artifacts:
    artifacts += dep.label.name + ": " + dep.artifact.path + "\n"
  artifact_info = ctx.new_file(ctx.label.name + ".artifact_info")
  ctx.file_action(
    output = artifact_info,
    content = artifacts
  )

  outs = [ctx.outputs.win, ctx.outputs.win32, ctx.outputs.mac, ctx.outputs.linux, ctx.outputs.output]

  args = ["--win", ctx.outputs.win.path,
          "--win32", ctx.outputs.win32.path,
          "--mac", ctx.outputs.mac.path,
          "--linux", ctx.outputs.linux.path,
          "--bin_dir", ctx.var["BINDIR"],
          "--gen_dir", ctx.var["GENDIR"],
          "--build", ctx.file.build.path,
          "--tmp", module_info.path + ".tmp",
          "--out", ctx.outputs.output.path,
          "--module_info", module_info.path,
          "--artifact_info", artifact_info.path]

  ctx.action(
    mnemonic = "Ant",
    inputs = [ctx.file.build, module_info, artifact_info] + ctx.files.data + ctx.files.artifacts + list(transitive_data),
    outputs = outs,
    executable = ctx.executable.ant,
    # We cannot enable this yet, because Mac's sandbox throws an error
    # execution_requirements = { "block-network" : "1" },
    arguments = args,
  )

_iml_project = rule(
    attrs = {
        "modules": attr.label_list(
            non_empty = True,
        ),
        "artifacts": attr.label_list(
        ),
        "libraries": attr.label_list(
        ),
        "data": attr.label_list(
            allow_files = True,
        ),
        "deps": attr.label_list(
        ),
        "ant": attr.label(
            executable = True,
            cfg = "host",
        ),
        "build": attr.label(
            allow_files = True,
            single_file = True,
        ),
    },
    outputs = {
        "win": "%{name}.win.zip",
        "win32": "%{name}.win32.zip",
        "mac": "%{name}.mac.zip",
        "linux": "%{name}.tar.gz",
        "output": "%{name}.log",
    },
    implementation = _iml_project_impl,
)

def normalize_label(label):
  if ':' not in label:
    label = label + ":" + label[label.rfind('/') + 1:]
  return label

def iml_project(name,modules=[], **kwargs):
  normalized_modules = [normalize_label(module) for module in modules]
  _iml_project(
    name = name,
    testonly = True,  # since "_testlib" entries are added programmatically to modules list here
    modules = [n + "_runtime" for n in normalized_modules] + [n + "_testlib" for n in normalized_modules],
    **kwargs
  )

def _iml_artifact_impl(ctx):
  jars = []

  if ctx.files.files:
    files_jar = ctx.actions.declare_file(ctx.label.name + ".files.jar")

    zipper_args = ["c", files_jar.path]
    zipper_files = "".join([d + "/" + f.basename + "=" + f.path + "\n" for d,f in zip(ctx.attr.dirs, ctx.files.files)])
    zipper_list = create_option_file(ctx, ctx.label.name + ".files.lst", zipper_files)
    zipper_args += ["@" + zipper_list.path]
    ctx.action(
      inputs = ctx.files.files + [zipper_list],
      outputs = [files_jar],
      executable = ctx.executable._zipper,
      arguments = zipper_args,
      progress_message = "Creating files artifact jar...",
      mnemonic = "zipper",
    )
    jars += [files_jar]
  for module in ctx.attr.modules:
    if java_common.provider in module:
      jars += list(module[java_common.provider].compile_jars)

  ctx.action(
      inputs = jars,
      outputs = [ctx.outputs.artifact],
      mnemonic = "artifact",
      arguments = [ctx.outputs.artifact.path] + [jar.path for jar in jars],
      executable = ctx.executable._singlejar
  )
  return struct(artifact = ctx.outputs.artifact)

_iml_artifact = rule(
    attrs = {
        "dirs": attr.string_list(),
        "files": attr.label_list(allow_files = True),
        "modules": attr.label_list(),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
        "_singlejar": attr.label(
            default = Label("//tools/base/bazel:singlejar"),
            cfg = "host",
            executable = True,
        ),
    },
    outputs = {
        "artifact": "%{name}.jar",
    },
    implementation = _iml_artifact_impl,
)

def iml_artifact(name, dirs={}, modules=[], **kwargs):

  # Reverse the map so we can use a label to string dict
  files = dict()
  for d, fs in dirs.items():
    for f in fs:
      files[f] = d

  dir_list = []
  file_list = []
  for d, fs in dirs.items():
    for f in fs:
      dir_list += [d]
      file_list += [f]

  _iml_artifact(
    name = name,
    dirs = dir_list,
    files = file_list,
    modules = modules,
    **kwargs
  )
