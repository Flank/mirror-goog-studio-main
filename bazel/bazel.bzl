load(":functions.bzl", "create_java_compiler_args", "explicit_target")
load(":groovy.bzl", "groovy_jar", "groovy_stubs")
load(":kotlin.bzl", "kotlin_jar")
load(":utils.bzl", "fileset", "java_jarjar", "singlejar")

def _form_jar_impl(ctx):
  all_deps = depset(ctx.files.deps)
  for this_dep in ctx.attr.deps:
    if hasattr(this_dep, "java"):
      # All the transitive dependencies are needed to compile forms
      all_deps += this_dep.java.transitive_runtime_deps

  class_jar = ctx.outputs.class_jar

  args, option_files = create_java_compiler_args(ctx, class_jar.path, all_deps)

  ctx.action(
      inputs = ctx.files.srcs + list(all_deps) + option_files,
      outputs = [class_jar],
      mnemonic = "formc",
      arguments = args,
      executable = ctx.executable._formc,
  )

_form_jar = rule(
    attrs = {
        "srcs": attr.label_list(
            non_empty = True,
            allow_files = True,
        ),
        "deps": attr.label_list(
            mandatory = False,
            allow_files = FileType([".jar"]),
        ),
        "_formc": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:formc"),
            allow_files = True),
    },
    outputs = {
        "class_jar": "lib%{name}.jar",
    },
    implementation = _form_jar_impl,
)

def _iml_resources(name, resources, srcs):
  iml_resource_dirs = resources + srcs
  res_exclude = ["**/*.java", "**/*.kt", "**/*.groovy", "**/.DS_Store"]
  res_exclude = res_exclude + [ d + "/META-INF/MANIFEST.MF" for d in iml_resource_dirs]

  iml_resources = native.glob(
        include = [r + "/**/*" for r in iml_resource_dirs],
        exclude = res_exclude)

  if iml_resources:
    mappings = {}
    for d in iml_resource_dirs:
      mappings[d + "/"] = name + ".res.root/"
    fileset(
      name = name + ".res",
      srcs = iml_resources,
      mappings = mappings
    )
    return [":" + name + ".res"]
  else:
    return []


def _iml_library(name, srcs=[], package_prefixes=[], res_zips=[], exclude=[], deps=[], exports=[], visibility=[], javacopts=[], **kwargs):

  kotlins = native.glob([src + "/**/*.kt" for src in srcs], exclude=exclude)
  groovies = native.glob([src + "/**/*.groovy" for src in srcs], exclude=exclude)
  javas = native.glob([src + "/**/*.java" for src in srcs], exclude=exclude)
  forms = native.glob([src + "/**/*.form" for src in srcs], exclude=exclude)

  jars = []

  if kotlins:
    kotlin_jar(
        name = name + ".kotlins",
        srcs = srcs,
        package_prefixes = package_prefixes,
        inputs = kotlins + javas,
        deps = ["@local_jdk//:langtools-neverlink"] + deps,
    )
    deps += ["//tools/base/bazel:kotlin-runtime", "lib" + name + ".kotlins.jar"]
    jars += ["lib" + name + ".kotlins.jar"]

  if groovies:
    stub = name + ".groovies.stubs"
    groovy_stubs(
        name = stub,
        srcs = groovies
    )
    groovy_jar(
        name = name + ".groovies",
        srcs = groovies,
        deps = ["@local_jdk//:langtools-neverlink"] + deps + [":" + name + ".javas"],
    )
    jars += ["lib" + name + ".groovies.jar"]
    deps += [":" + stub]
    javacopts += ["-sourcepath $(GENDIR)/$(location :" + stub + ")", "-implicit:none"]

  native.java_library(
    name = name + ".javas" if not forms else name + ".pjavas",
    srcs = javas,
    javacopts = javacopts,
    deps = None if not javas else deps + ["@local_jdk//:langtools-neverlink"],
    **kwargs
  )
  if forms:
    _form_jar(
      name = name + ".javas",
      srcs = ["lib" + name + ".pjavas.jar"] + forms,
      deps = deps,
    )
  jars += ["lib" + name + ".javas.jar"]

  singlejar(
      name = name,
      jars = jars + res_zips,
      runtime_deps = deps,
      exports = exports,
      visibility = visibility,
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
  return label[:rfind], [tag.strip() for tag in label[rfind+1:-1].split(",")]


def _iml_module_impl(ctx):
  names = [iml.basename[:-4] for iml in ctx.files.iml_files if iml.basename.endswith(".iml")]

  module_jars = dict()
  module_runtime = dict()
  transitive_runtime_deps = depset()
  transitive_data = depset()

  for this_dep in ctx.attr.deps:
    if hasattr(this_dep, "java"):
      transitive_runtime_deps += this_dep.java.transitive_runtime_deps
    if hasattr(this_dep, "module"):
      transitive_runtime_deps += this_dep.module.transitive_runtime_deps
      transitive_data += this_dep.module.transitive_data
      module_jars.update(this_dep.module.module_jars)
      module_runtime.update(this_dep.module.module_runtime)

  for name in names:
    module_jars[name] = ctx.file.production_jar
    module_runtime[name] = transitive_runtime_deps
  transitive_data += depset(ctx.files.iml_files + ctx.files.data)
  transitive_runtime_deps += depset([ctx.file.production_jar])


  return struct(
    module = struct(
      module_jars = module_jars,
      module_runtime = module_runtime,
      transitive_runtime_deps = transitive_runtime_deps,
      transitive_data = transitive_data,
    )
  )

_iml_module = rule(
    attrs = {
      "iml_files" : attr.label_list(
        allow_files = True,
        allow_empty = False,
        mandatory = True,
      ),
      "production_jar" : attr.label(
        allow_files = True,
        single_file = True,
      ),
      "deps" : attr.label_list(
      ),
      "data" : attr.label_list(
        allow_files = True,
      ),
    },
    implementation = _iml_module_impl,
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
    back_deps=[]):

  # Create the explicit lists of main, tests and exports the same way IntelliJ does
  main_deps = []
  test_deps = [":" + name]
  test_exports = []
  module_deps = []
  for dep in deps:
    label, tags = _get_label_and_tags(dep)
    if "test" not in tags:
      main_deps += [label]
    new_test_deps = [label]
    if "module" in tags:
      new_test_deps += [explicit_target(label) + "_testlib"]
      module_deps += [explicit_target(label)]
    test_deps += new_test_deps
    if label in exports:
      test_exports += new_test_deps

  prefixes = [package_prefixes[src] if src in package_prefixes else "" for src in srcs]
  _iml_library(
    name = name,
    srcs = srcs,
    package_prefixes = prefixes,
    exclude = exclude,
    exports = exports,
    deps = main_deps,
    javacopts = javacopts,
    plugins = plugins,
    resource_strip_prefix = PACKAGE_NAME + "/" + name + ".res.root",
    resources = _iml_resources(name, resources, srcs),
    res_zips = res_zips,
    visibility = visibility,
  )

  test_prefixes = [package_prefixes[src] if src in package_prefixes else "" for src in test_srcs]
  _iml_library(
    name = name + "_testlib",
    srcs = test_srcs,
    package_prefixes = test_prefixes,
    deps = test_deps,
    visibility = visibility,
    exports = test_exports,
    resource_strip_prefix = PACKAGE_NAME + "/" + name + "_testlib.res.root",
    resources = _iml_resources(name + "_testlib", test_resources, test_srcs),
    javacopts = javacopts,
  )

  if test_srcs:
    native.java_test(
      name = name + "_tests",
      runtime_deps = test_runtime_deps + [
        ":" + name + "_testlib",
        "//tools/base/testutils:studio.testutils",
        "//tools/base/bazel:langtools",
      ],
      timeout = test_timeout,
      shard_count = test_shard_count,
      data = test_data,
      jvm_flags = ["-Dtest.suite.jar=lib" + name + "_testlib.jar"],
      tags = test_tags,
      test_class = test_class,
      visibility = ["//visibility:public"],
    )

  _iml_module(
    name = name + "_module",
    visibility = ["//visibility:public"],
    iml_files = iml_files,
    production_jar = name,
    deps = [dep + "_module" for dep in module_deps] + list(depset(test_deps + main_deps + runtime_deps)),
    data = bundle_data
  )

def _iml_project_impl(ctx):
  imls = []
  deps = []
  inputs = []

  module_jars = dict()
  module_runtime = dict()
  transitive_data = depset()
  transitive_runtime_deps = depset()
  for dep in ctx.attr.modules:
    if hasattr(dep, "module"):
      module_jars.update(dep.module.module_jars)
      module_runtime.update(dep.module.module_runtime)
      transitive_data += dep.module.transitive_data
      transitive_runtime_deps += dep.module.transitive_runtime_deps

  text = ""
  for name, files in module_runtime.items():
    text += name + ": " + module_jars[name].path
    for file in files:
      text += ":" + file.path
    text += "\n"


  module_info = ctx.new_file(ctx.label.name + ".module_info")
  ctx.file_action(
    output = module_info,
    content = text
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
          "--module_info", module_info.path]

  ctx.action(
    mnemonic = "Ant",
    inputs = [ctx.file.build, module_info] + ctx.files.data + list(transitive_data + transitive_runtime_deps),
    outputs = outs,
    executable = ctx.executable.ant,
    arguments = args,
  )

_iml_project = rule(
    attrs = {
        "modules": attr.label_list(
            non_empty = True,
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

def iml_project(name,modules=[], **kwargs):
  # TODO Once iml_modules can be more than just java_imports we can make this part of the rule
  normalized_modules = []
  for module in modules:
    if ':' not in module:
      module = module + ":" + module[module.rfind('/') + 1:]
    normalized_modules += [module + "_module"]

  _iml_project(
    name = name,
    modules = normalized_modules,
    **kwargs
  )
