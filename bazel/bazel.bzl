load(":functions.bzl", "create_java_compiler_args", "explicit_target")
load(":groovy.bzl", "groovy_jar", "groovy_stubs")
load(":kotlin.bzl", "kotlin_jar")
load(":utils.bzl", "fileset", "java_jarjar", "singlejar")

def _form_jar_impl(ctx):
  all_deps = set(ctx.files.deps)
  for this_dep in ctx.attr.deps:
    if hasattr(this_dep, "java"):
      # All the transitive dependencies are needed to compile forms
      all_deps += this_dep.java.transitive_runtime_deps

  class_jar = ctx.outputs.class_jar

  args, option_files = create_java_compiler_args(ctx, class_jar.path,
                                                 all_deps)

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
    back_deps=[]):

  # Create the explicit lists of main, tests and exports the same way IntelliJ does
  main_deps = []
  test_deps = [":" + name]
  test_exports = []
  for dep in deps:
    label, tags = _get_label_and_tags(dep)
    if "test" not in tags:
      main_deps += [label]
    new_test_deps = [label]
    if "module" in tags:
      new_test_deps += [explicit_target(label) + "_testlib"]
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

