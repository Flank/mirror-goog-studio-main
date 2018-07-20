load(":functions.bzl", "create_java_compiler_args")

def _jasmin_jar_impl(ctx):
  class_jar = ctx.outputs.class_jar
  args, option_files = create_java_compiler_args(ctx, class_jar, [])

  ctx.action(
    inputs = ctx.files.srcs + option_files,
    outputs = [class_jar],
    mnemonic = "jasmin",
    arguments = args,
    executable = ctx.executable._jasmin,
  )

_jasmin_jar = rule(
    attrs = {
        "srcs": attr.label_list(
            non_empty = True,
            allow_files = True,
        ),
        "_jasmin": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:jasmin-compiler"),
            allow_files = True,
        ),
    },
    outputs = {
        "class_jar": "lib%{name}.jar",
    },
    implementation = _jasmin_jar_impl,
)

def jasmin_library(name, srcs=None, visibility=None):
  jar_name = "_" + name
  _jasmin_jar(
      name = jar_name,
      srcs = srcs,
  )
  native.java_import(
      name = name,
      jars = [":" + jar_name],
  )
