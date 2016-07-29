
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

def kotlin_library(name, srcs=[], deps=[], exports=[], visibility=[], **kwargs):
  """Rule analagous to java_library that accepts .kt sources instead of
  .java sources. The result is wrapped in a java_import so that java rules may
  depend on it.
  """
  _kotlin_jar(
      name = name + ".kotlin",
      srcs = srcs,
      deps = deps,
  )

  javas = native.glob([src + "/**/*.java" for src in srcs])
  jars = [];

  if javas:
    native.java_library(
        name = name + ".java",
        srcs = javas,
        deps = deps + ["lib" + name + ".kotlin.jar", "//tools/base/bazel:kotlin-runtime"],
        **kwargs
    )
    jars = ["lib" + name + ".java.jar"]

  native.java_import(
      name = name,
      jars = ["lib" + name + ".kotlin.jar"] + jars,
      deps = deps,
      visibility = visibility,
      exports = exports,
    )


def _groovy_jar_impl(ctx):
  all_deps = set(ctx.files.deps)
  for this_dep in ctx.attr.deps:
    if hasattr(this_dep, "java"):
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

def groovy_library(name, srcs=[], deps=[], visibility=[], resources=[], javacopts=[], exports=[], **kwargs):
  groovies = []
  for src in srcs:
     gsrc = native.glob([src + "/**/*.groovy"]);
     groovies += gsrc;

  groovies = native.glob([src + "/**/*.groovy" for src in srcs]);
  native.genrule(
    name = name + ".stub",
    srcs = groovies,
    outs = [ name + ".stub.jar" ],
    cmd = "./$(location //tools/base/bazel:groovy_stub_gen) -o $(@D)/" + name + ".stub.jar $(SRCS);",
    tools = ["//tools/base/bazel:groovy_stub_gen"],
  )

  native.java_library(
      name = name + "-java",
      srcs = native.glob([src + "/**/*.java" for src in srcs]),
      javacopts = javacopts + ["-sourcepath $(GENDIR)/$(location :" + name + ".stub)", "-implicit:none"],
      resources = resources,
      visibility = visibility,
      deps = deps + [":" + name + ".stub"],
      **kwargs
  )

  _groovy_jar(
      name = name + "-groovy",
      srcs = native.glob([src + "/**/*.groovy" for src in srcs]),
      deps = deps + [":" + name + "-java"],
  )

  native.java_import(
      name = name,
      jars = ["lib" + name + "-groovy.jar", "lib" + name + "-java.jar"],
      visibility = visibility,
      exports = exports
  )

def kotlin_groovy_library(name, srcs=[], deps=[], exports=[], visibility=[], **kwargs):
  _kotlin_jar(
      name = name + ".kotlin",
      srcs = srcs,
      deps = deps,
  )

  groovy_library(
    name = name + ".groovy",
    srcs = srcs,
    deps = deps + ["//tools/base/bazel:kotlin-runtime", "lib" + name + ".kotlin.jar"],
    visibility = visibility,
    **kwargs
    )

  native.java_import(
      name = name,
      jars = ["lib" + name + ".kotlin.jar", "lib" + name + ".groovy-groovy.jar", "lib" + name + ".groovy-java.jar"],
      visibility = visibility,
      exports = exports,
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
