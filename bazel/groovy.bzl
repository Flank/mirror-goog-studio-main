load(":functions.bzl", "create_java_compiler_args")

def _groovy_jar_impl(ctx):
  all_deps = set(ctx.files.deps)
  for this_dep in ctx.attr.deps:
    if hasattr(this_dep, "java"):
      # Groovy needs the class to be loadable so it cannot work with ijars and needs the full jars.
      all_deps += this_dep.java.transitive_runtime_deps

  class_jar = ctx.outputs.class_jar

  args, option_files = create_java_compiler_args(ctx, class_jar.path,
                                                 all_deps)

  cmd = ctx.executable._groovy.path + " " + " ".join(args)
  ctx.action(
      inputs = [ctx.executable._groovy] + ctx.files.srcs + list(all_deps) + option_files,
      outputs = [class_jar],
      mnemonic = "groovyc",
      command = cmd
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

def groovy_stubs(name, srcs=[]):
  native.genrule(
    name = name,
    srcs = srcs,
    outs = [name + ".jar"],
    cmd = "./$(location //tools/base/bazel:groovy_stub_gen) -o $(@D)/" + name + ".jar $(SRCS);",
    tools = ["//tools/base/bazel:groovy_stub_gen"],
  )
