load(":functions.bzl", "create_java_compiler_args")

def _groovy_jar_impl(ctx):
  all_deps = depset(ctx.files.deps)
  for this_dep in ctx.attr.deps:
    if hasattr(this_dep, "java"):
      # Groovy needs the class to be loadable so it cannot work with ijars and needs the full jars.
      all_deps += this_dep.java.transitive_runtime_deps

  class_jar = ctx.outputs.class_jar

  args, option_files = create_java_compiler_args(ctx, class_jar.path,
                                                 all_deps)

  ctx.action(
      inputs = ctx.files.srcs + list(all_deps) + option_files,
      outputs = [class_jar],
      mnemonic = "groovyc",
      arguments = args,
      executable = ctx.executable._groovy
  )

groovy_jar = rule(
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
            cfg = "host",
            default = Label("//tools/base/bazel:groovyc"),
            allow_files = True,
        ),
    },
    outputs = {
        "class_jar": "lib%{name}.jar",
    },
    implementation = _groovy_jar_impl,
)

def _groovy_srcs_list_impl(ctx):
  file_list = ctx.outputs.file_list

  ctx.file_action(
    output = file_list,
    content = "\n".join([src.path for src in ctx.files.srcs]),
  )

_groovy_srcs_list = rule(
    attrs = {
        "srcs": attr.label_list(
            non_empty = True,
            allow_files = True,
        ),
    },
    outputs = {
        "file_list": "lst%{name}.lst",
    },
    implementation = _groovy_srcs_list_impl,
)


def groovy_stubs(name, srcs=[]):
  _groovy_srcs_list(
    name = name + "_srcs",
    srcs = srcs,
  )

  native.genrule(
    name = name + ".genrule",
    srcs = srcs + [":" + name + "_srcs"],
    outs = [name + ".jar"],
    cmd = "./$(location //tools/base/bazel:groovy_stub_gen) -o $(@D)/" + name + ".jar @$(location :" + name + "_srcs)",
    tools = ["//tools/base/bazel:groovy_stub_gen"],
  )

  native.java_import(
    name = name,
    jars = [name + ".jar"],
  )
