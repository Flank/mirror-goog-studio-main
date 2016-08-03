
def _kotlin_jar_impl(ctx):

  class_jar = ctx.outputs.class_jar
  build_output = class_jar.path


  all_deps = set(ctx.files.deps)
  all_deps += set(ctx.files._kotlin)
  for this_dep in ctx.attr.deps:
    if hasattr(this_dep, "java"):
      all_deps += this_dep.java.transitive_runtime_deps

  args = []
  if ctx.files.deps:
    args = ["-cp", ":".join([dep.path for dep in all_deps])]
  args += ["-d", build_output]
  args += [src.path for src in ctx.files.srcs]

  # Execute the command
  ctx.action(
    inputs = (ctx.files.srcs + list(all_deps)),
    outputs = [class_jar],
    mnemonic = "kotlinc",
    executable = ctx.executable._kotlinc,
    arguments = args,
  )

_kotlin_jar = rule(
    attrs = {
        "srcs": attr.label_list(
            non_empty = True,
            allow_files = True,
        ),
        "deps": attr.label_list(
            mandatory = False,
            allow_files = FileType([".jar"]),
        ),
        "_kotlinc": attr.label(
            executable = True,
            default = Label("//tools/base/bazel:kotlinc"),
            allow_files = True),
        "_kotlin": attr.label(
            default = Label("//tools/base/bazel:kotlin-runtime"),
            allow_files = True),
    },
    outputs = {
        "class_jar": "lib%{name}.jar",
    },
    implementation = _kotlin_jar_impl,
)

def _groovy_jar_impl(ctx):
  all_deps = set(ctx.files.deps)
  for this_dep in ctx.attr.deps:
    if hasattr(this_dep, "java"):
      # Groovy needs the class to be loadable so it cannot work with ijars and needs the full jars.
      all_deps += this_dep.java.transitive_runtime_deps

  class_jar = ctx.outputs.class_jar
  args = ["-o", class_jar.path, "-cp", ":".join([dep.path for dep in all_deps])] + [src.path for src in ctx.files.srcs]

  # Execute the command
  ctx.action(
      inputs = (ctx.files.srcs + list(all_deps)),
      outputs = [class_jar],
      mnemonic = "groovyc",
      executable = ctx.executable._groovy,
      arguments = args,
  )

_groovy_jar = rule(
    attrs = {
        "srcs": attr.label_list(
            non_empty = True,
            allow_files = True,
        ),
        "deps": attr.label_list(
            mandatory = False,
            allow_files = FileType([".jar"]),
        ),
        "_groovy": attr.label(
            executable = True,
            default = Label("//tools/base/bazel:groovyc"),
            allow_files = True),
    },
    outputs = {
        "class_jar": "lib%{name}.jar",
    },
    implementation = _groovy_jar_impl,
)

def _fileset_impl(ctx):
  srcs = set(order="compile")
  for src in ctx.attr.srcs:
    srcs += src.files

  remap = {}
  for a, b in ctx.attr.maps.items():
    remap[ ctx.label.package + "/" + a ] = b

  cmd = ""
  for f in ctx.files.srcs:
    if f.path in remap:
      dest = remap[f.path]
      fd = ctx.new_file(dest)
      cmd += "mkdir -p " + fd.dirname + "\n"
      cmd += "cp -f " + f.path + " " + fd.path + "\n"

  script = ctx.new_file(ctx.label.name + ".cmd.sh")
  ctx.file_action(output = script, content = cmd)

  # Execute the command
  ctx.action(
    inputs = (
      ctx.files.srcs +
      [script]
    ),
    outputs = ctx.outputs.outs,
    mnemonic = "fileset",
    command = "set -e; sh " + script.path,
    use_default_shell_env = True,
  )


_fileset = rule(
    executable=False,
    attrs = {
      "srcs": attr.label_list(allow_files=True),
      "maps": attr.string_dict(mandatory=True, non_empty=True),
      "outs": attr.output_list(mandatory=True, non_empty=True),
    },
    implementation = _fileset_impl,
)

def fileset(name, srcs=[], mappings={}, **kwargs):
  outs = []
  maps = {}
  rem = []
  for src in srcs:
    done = False
    for prefix, destination in mappings.items():
      if src.startswith(prefix):
        f = destination + src[len(prefix):];
        maps[src] = f
        outs += [f]
        done = True
    if not done:
      rem += [src]

  if outs:
    _fileset(
      name = name + ".map",
      srcs = srcs,
      maps = maps,
            outs = outs)

  native.filegroup(
    name = name,
    srcs = outs + rem
  )

def _iml_resources(name, resources, srcs):
  res_exclude = ["**/*.java", "**/*.kt", "**/*.groovy", "**/.DS_Store"]
  # Temporarily remove file names not supported by bazel
  # https://github.com/bazelbuild/bazel/issues/167
  res_exclude += ["**/* *", "**/*$*"]

  iml_resource_dirs = resources + srcs
  iml_resources = native.glob(
        include = [r + "/**/*" for r in iml_resource_dirs],
        exclude = res_exclude)

  if iml_resources:
    mappings = {}
    for d in iml_resource_dirs:
      mappings[d] = name + ".res.root"
    fileset(
      name = name + ".res",
      srcs = iml_resources,
      mappings = mappings
    )
    return [":" + name + ".res"]
  else:
    return []

def _groovy_stubs(name, srcs=[]):
  native.genrule(
    name = name,
    srcs = srcs,
    outs = [name + ".jar"],
    cmd = "./$(location //tools/base/bazel:groovy_stub_gen) -o $(@D)/" + name + ".jar $(SRCS);",
    tools = ["//tools/base/bazel:groovy_stub_gen"],
  )

def _iml_library(name, srcs=[], exclude=[], deps=[], exports=[], visibility=[], javacopts=[], **kwargs):

  kotlins = native.glob([src + "/**/*.kt" for src in srcs], exclude=exclude)
  groovies = native.glob([src + "/**/*.groovy" for src in srcs], exclude=exclude)
  javas = native.glob([src + "/**/*.java" for src in srcs], exclude=exclude)
  jars = []

  if kotlins:
    _kotlin_jar(
        name = name + ".kotlins",
        srcs = srcs,
        deps = deps,
    )
    deps += ["//tools/base/bazel:kotlin-runtime", "lib" + name + ".kotlins.jar"]
    jars += ["lib" + name + ".kotlins.jar"]

  if groovies:
    stub = name + ".groovies.stubs"
    _groovy_stubs(
        name = stub,
        srcs = groovies
    )
    _groovy_jar(
        name = name + ".groovies",
        srcs = groovies,
        deps = ["@local_jdk//:langtools-neverlink"] + deps + [":" + name + ".javas"],
    )
    jars += ["lib" + name + ".groovies.jar"]
    deps += [":" + stub]
    javacopts += ["-sourcepath $(GENDIR)/$(location :" + stub + ")", "-implicit:none"]

  native.java_library(
    name = name + ".javas",
    srcs = javas,
    javacopts = javacopts,
    visibility = visibility,
    deps = None if not javas else deps + ["@local_jdk//:langtools-neverlink"],
    exports = exports,
    **kwargs
  )
  jars += ["lib" + name + ".javas.jar"]

  native.java_import(
    name = name,
    jars = jars,
    visibility = visibility,
    exports = [":" + name + ".javas"],
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
def iml_module(name, srcs=[], test_srcs=[], exclude=[], resources=[], test_resources=[], deps=[],
    visibility=[], exports=[], javacopts=[], back_target=0, back_deps=[]):

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
      new_test_deps += [label + "_testlib"]
    test_deps += new_test_deps
    if label in exports:
      test_exports += new_test_deps

  _iml_library(
    name = name,
    srcs = srcs,
    exclude = exclude,
    exports = exports,
    deps = main_deps,
    javacopts = javacopts,
    resource_strip_prefix = PACKAGE_NAME + "/" + name + ".res.root",
    resources = _iml_resources(name, resources, srcs),
    visibility = visibility,
  )

  _iml_library(
    name = name + "_testlib",
    srcs = test_srcs,
    deps = test_deps,
    visibility = visibility,
    exports = test_exports,
    resource_strip_prefix = PACKAGE_NAME + "/" + name + "_testlib.res.root",
    resources = _iml_resources(name + "_testlib", test_resources, test_srcs),
    javacopts = javacopts,
  )

  native.java_test(
    name = name + "_tests",
    runtime_deps = [
      ":" + name + "_testlib",
      "//tools/base/testutils:testutils",
    ],
    #TODO support tests from all the three jars
    jvm_flags = ["-Dtest.suite.jar=" + name + "_testlib.javas.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    visibility = ["//visibility:public"],
  )
