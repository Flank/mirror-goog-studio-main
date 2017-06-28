def singlejar(name, jars, jar_name=None, **kwargs):
  jar_name = jar_name if jar_name else "lib" + name + ".jar"
  native.genrule(
    name = name + ".genrule",
    srcs = jars,
    outs = [jar_name],
    tools = ["//tools/base/bazel:singlejar"],
    cmd = "$(location //tools/base/bazel:singlejar) --jvm_flag=-Xmx1g $@ $(SRCS)",
  )

  native.java_import(
    name = name,
    jars = [jar_name],
    **kwargs
  )

def _fileset_impl(ctx):
  srcs = set(order="compile")
  for src in ctx.attr.srcs:
    srcs += src.files

  remap = {}
  for a, b in ctx.attr.maps.items():
    if a.startswith("//"):
      key = a[2:].replace(':', '/')
    else:
      key = ctx.label.package + "/" + a
    remap[key] = b

  cmd = ""
  for f in ctx.files.srcs:
    # Use short_path, which for outputs of other rules doesn't include "bazel-out" etc.
    if f.short_path in remap:
      dest = remap[f.short_path]
      fd = ctx.new_file(dest)
      cmd += "mkdir -p " + fd.dirname + "\n"
      cmd += "cp -f '" + f.path + "' '" + fd.path + "'\n"

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
      outs = outs
    )

  native.filegroup(
    name = name,
    srcs = outs + rem,
    **kwargs
  )

# Usage:
# java_jarjar(
#     name = <the name of the rule. The output of the rule will be ${name}.jar.
#     srcs = <a list of all the jars to jarjar and include into the output jar>
#     rules = <the rule file to apply>
# )
#
# TODO: This rule is using anarres jarjar which doesn't produce stable zips (timestamps)
# jarjar is available in bazel but the current version is old and uses ASM4, so no Java8
# will migrate to it when it's fixed.
def java_jarjar(name, rules, srcs=[], visibility=None):
  native.genrule(
      name = name,
      srcs = srcs + [rules],
      outs = [name + ".jar"],
      tools = ["//tools/base/bazel:jarjar"],
      cmd = ("$(location //tools/base/bazel:jarjar) --rules " +
             "$(location " + rules + ") " +
             " ".join(["$(location " + src + ")" for src in srcs]) + " " +
             "--output '$@'"),
      visibility = visibility,
  )

# Creates a filegroup for a given platform directory in the SDK.
#
# It excludes files that are not necessary for testing and take up space in the sandbox.
def platform_filegroup(name, visibility = ["//visibility:public"]):
  pattern = "*/" + name

  native.filegroup(
      name = name,
      srcs = native.glob(
          include = [pattern + "/**"],
          exclude = [
              pattern + "/skins/**",
          ]
      ),
      visibility = visibility
   )

def merged_properties(name, srcs, mappings, visibility=None):
  native.genrule(
      name = name,
      srcs = srcs,
      outs = [name + ".properties"],
      tools = ["//tools/base/bazel:properties_merger"],
      visibility = visibility,
      cmd = ("$(location //tools/base/bazel:properties_merger) " +
             " ".join(["--mapping " + m for m in mappings]) + " " +
             " ".join(["--input $(location " + src + ") " for src in srcs]) + " " +
             "--output '$@'")
  )

def srcjar(name, java_library, visibility=None):
  implicit_jar = ':lib' + java_library[1:] + '-src.jar'
  native.genrule(
      name = name,
      srcs = [implicit_jar],
      outs = [java_library[1:] + ".srcjar"],
      visibility = visibility,
      cmd = "cp $(location " + implicit_jar + ") $@"
  )

def _archive_impl(ctx):
  inputs = []
  zipper_args = ["c", ctx.outputs.out.path]
  for dep, target in ctx.attr.deps.items():
    file = dep.files.to_list()[0]
    name = "%s/%s=%s" % (target, file.basename, file.path)
    zipper_args.append(name)
    inputs += [file]

  ctx.action(
    inputs=inputs,
    outputs=[ctx.outputs.out],
    executable=ctx.executable._zipper,
    arguments=zipper_args,
    progress_message="Creating archive...",
    mnemonic="archiver",
  )

archive = rule(
  implementation = _archive_impl,
  attrs = {
    "deps": attr.label_keyed_string_dict(
        non_empty = True,
        allow_files = True,
    ),
    "$zipper": attr.label(default = Label("@bazel_tools//tools/zip:zipper"), cfg = "host", executable=True),
  },
  outputs = {"out": "%{name}.jar"},
)

