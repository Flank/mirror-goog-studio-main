def _jni_library_impl(ctx):
  inputs = []
  for cpu, deps in ctx.split_attr.deps.items():
    if not cpu:
      # --fat_apk_cpu wasn't used, so the dependencies were compiled using the
      # cpu value from --cpu, so use that as the directory name.
      cpu = ctx.fragments.cpp.cpu
    for dep in deps:
      for f in dep.files:
        inputs.append((cpu, f))

  # If two targets in deps share the same files (e.g. in the data attribute)
  # they would be included mulitple times in the same path in the zip, so
  # dedupe the files.
  deduped_inputs = set(inputs)
  zipper_args = ["c", ctx.outputs.zip.path]
  for cpu, file in deduped_inputs:
    # "lib/" is the JNI directory looked for in android
    target = "lib/%s/%s" % (cpu, file.basename)
    # Using bazel stripping convention
    # https://docs.bazel.build/versions/master/be/c-cpp.html#cc_binary
    if target.endswith(".stripped"):
      target = target[:-9]
    name = target + "=" + file.path
    zipper_args.append(name)

  ctx.action(
    inputs=[f for cpu, f in deduped_inputs],
    outputs=[ctx.outputs.zip],
    executable=ctx.executable._zipper,
    arguments=zipper_args,
    progress_message="Creating zip...",
    mnemonic="zipper",
  )

def _android_cc_binary_impl(ctx):
  for cpu, binary in ctx.split_attr.binary.items():
    name = ctx.attr.filename
    file = binary.files.to_list()[0]
    for out in ctx.outputs.outs:
      if out.path.endswith(cpu + "/" + name):
        ctx.action(
          mnemonic = "SplitCp",
          inputs = [file],
          outputs = [out],
          command = "cp " + file.path  + " " + out.path
        )

_android_cc_binary = rule(
  implementation = _android_cc_binary_impl,
  attrs = {
    "filename": attr.string(),
    "binary": attr.label(
        cfg = android_common.multi_cpu_configuration,
        allow_files = True,
    ),
    "abis": attr.string_list(),
    "outs": attr.output_list(),
  },
  fragments = ["cpp"],
)

def android_cc_binary(name, binary, abis, filename, **kwargs):
  outs = [];
  for abi in abis:
      outs += [name + "/" + abi + "/" + filename]
  _android_cc_binary(
    name=name,
    abis=abis,
    filename=filename,
    binary=binary,
    outs=outs,
    **kwargs
  )

jni_library = rule(
  implementation = _jni_library_impl,
  attrs = {
    "deps": attr.label_list(
        cfg = android_common.multi_cpu_configuration,
        allow_files = True,
    ),
    "$zipper": attr.label(default = Label("@bazel_tools//tools/zip:zipper"), cfg = "host", executable=True),
  },
  outputs = {"zip": "%{name}.jar"},
  fragments = ["cpp"],
)

def _select_android(android, default):
  return select({
        "//tools/base/bazel:android_cpu_x86": android,
        "//tools/base/bazel:android_cpu_x86_64": android,
        "//tools/base/bazel:android_cpu_arm": android,
        "//tools/base/bazel:android_cpu_arm_64": android,
        "//conditions:default": default,
        })

def dex_library(name, jars=[], visibility=[]):
  native.genrule(
    name = name,
    srcs = jars,
    outs = [name + ".jar"],
    cmd = "$(location //prebuilts/studio/sdk:dx-preview) --dex --output=./$@ ./$<",
    tools = ["//prebuilts/studio/sdk:dx-preview"],
  )

ANDROID_COPTS = _select_android([
    "-fPIC",
  ], [])

ANDROID_LINKOPTS = _select_android([
    "-llog",
    "-lm",
    "-ldl",
    "-pie",
    "-Wl,--gc-sections",
    "-Wl,--as-needed",
    "-fuse-ld=gold",
    "-Wl,--icf=safe",
  ], [])

