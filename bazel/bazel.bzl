
def _kotlin_jar_impl(ctx):

  class_jar = ctx.outputs.class_jar
  build_output = class_jar.path


  all_deps = set(ctx.files.deps)
  for this_dep in ctx.attr.deps:
    if hasattr(this_dep, "java"):
      all_deps += this_dep.java.transitive_runtime_deps


  cmd = "rm -f %s\n" % build_output

  # Compile all files in srcs with kotlinc
  cmd += "/bin/bash " + ctx.file._kotlinc.path + " %s -d %s %s\n" % (
      "-cp " + ":".join([dep.path for dep in ctx.files.deps]) if len(ctx.files.deps) != 0 else "",
      build_output,
      " ".join([src.path for src in ctx.files.srcs]),
  )

  # Execute the command
  ctx.action(
      inputs = (
          ctx.files.srcs
        + ctx.files.deps
        + ctx.files._kotlinc
        + ctx.files._kotlin_sdk
        + ctx.files._jdk
          ),
      outputs = [class_jar],
      mnemonic = "kotlinc",
      command = "set -e;" + cmd,
      use_default_shell_env = True,
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
            executable=True,
            default=Label("//tools/base/bazel:kotlin/bin/kotlinc"),
            single_file=True,
            allow_files=True),
        "_kotlin_sdk": attr.label(
            default=Label("//tools/base/bazel:kotlin-sdk"),
        ),
        "_jdk": attr.label(
            default = Label("//tools/defaults:jdk"),
        ),
    },
    outputs = {
        "class_jar": "lib%{name}.jar",
    },
    implementation = _kotlin_jar_impl,
)

def kotlin_library(name, srcs=[], deps=[], visibility=[], **kwargs):
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
        deps = deps + ["lib" + name + ".kotlin.jar", "//tools/base/bazel:kotlin/lib/kotlin-runtime.jar"],
        **kwargs
    )
    jars = ["lib" + name + ".java.jar"]

  native.java_import(
      name = name,
      jars = ["lib" + name + ".kotlin.jar"] + jars,
      deps = deps,
      visibility = visibility,
    )


def _groovy_jar_impl(ctx):
  class_jar = ctx.outputs.class_jar
  build_output = class_jar.path + ".build_output"

  all_deps = set(ctx.files.deps)
  for this_dep in ctx.attr.deps:
    if hasattr(this_dep, "java"):
      all_deps += this_dep.java.transitive_runtime_deps

  cmd = "rm -rf %s\n" % build_output
  cmd += "mkdir -p %s\n" % build_output

  first = True;
  for src in ctx.files.srcs:
    # The directory might not contain any groovy or java files, so when sandboxed the directory wouldn't even exist.
    cmd += "if [ -d " + src.path + " ]; then find " + src.path + " -iname \"*.groovy\" -o -iname \"*.java\"; fi " + ("> " if first else ">> ") + "%s/class_list\n" % build_output
    first = False;

  # encoding cannot be pased using -Fencoding as -F *always* adds a "-" so it ends up like -encoding -utf8 and fails.
  cmd += "JAVA_OPTS=\"-Dfile.encoding=UTF8\" " + ctx.file._groovyc.path + " %s -j -d %s %s\n" % (
      "-cp " + ":".join([dep.path for dep in all_deps]) if len(all_deps) != 0 else "",
      build_output,
      "@%s/class_list" % build_output
  )
  # TODO: We should not be using groovy to compile the java classes.
  cmd += "jar cf " + class_jar.path + " -C " + build_output + " .\n"

  cmd += "rm -rf %s" % build_output

  # Execute the command
  ctx.action(
      inputs = (
          ctx.files.src_files
          + list(all_deps)
          + ctx.files._groovysdk
          + ctx.files._jdk
          ),
      outputs = [class_jar],
      mnemonic = "Groovyc",
      command = "set -e;" + cmd,
      use_default_shell_env = True,
  )

_groovy_jar = rule(
    attrs = {
        "srcs": attr.label_list(
            non_empty = True,
            allow_files = True,
        ),
        "src_files": attr.label_list(
            non_empty = True,
            allow_files = True,
        ),
        "deps": attr.label_list(
            mandatory = False,
            allow_files = FileType([".jar"]),
        ),
        "_groovyc": attr.label(
            executable=True,
            default=Label("//tools/base/bazel:groovy/bin/groovyc"),
            single_file=True,
            allow_files=True),
        "_groovysdk": attr.label(
            default = Label("//tools/base/bazel:groovy-sdk"),
        ),
        "_jdk": attr.label(
            default = Label("//tools/defaults:jdk"),
        ),
    },
    outputs = {
        "class_jar": "lib%{name}.jar",
    },
    implementation = _groovy_jar_impl,
)

def groovy_library(name, srcs=[], deps=[], visibility=[], resources=[], **kwargs):
  _groovy_jar(
      name = name + "-groovy",
      srcs = srcs,
      src_files = native.glob([src + "/**/*.java" for src in srcs] + [src + "/**/*.groovy" for src in srcs]),
      deps = deps,
  )
  native.java_library(
      name = name + "-res",
      resources = resources,
      visibility = visibility,
      **kwargs
  )
  native.java_import(
      name = name,
      jars = ["lib" + name + "-groovy.jar", "lib" + name + "-res.jar"],
      visibility = visibility,
  )

def kotlin_groovy_library(name, srcs=[], deps=[], visibility=[], **kwargs):
  _kotlin_jar(
      name = name + ".kotlin",
      srcs = srcs,
      deps = deps,
  )

  _groovy_jar(
      name = name + ".groovy",
      srcs = srcs,
      src_files = native.glob([src + "/**/*.java" for src in srcs] + [src + "/**/*.groovy" for src in srcs]),
      deps = deps + ["//tools/base/bazel:kotlin/lib/kotlin-runtime.jar", "lib" + name + ".kotlin.jar"],
  )

  native.java_library(
      name = name + "-res",
      visibility = visibility,
      **kwargs
   )

  native.java_import(
      name = name,
      jars = ["lib" + name + ".kotlin.jar", "lib" + name + ".groovy.jar", "lib" + name + "-res.jar"],
      visibility = visibility,
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

