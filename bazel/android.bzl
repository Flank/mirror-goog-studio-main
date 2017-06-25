def _multi_cpu_zip_impl(ctx):
  inputs = []
  for cpu, deps in ctx.split_attr.deps.items():
    if not cpu:
      # --fat_apk_cpu wasn't used, so the dependencies were compiled using the
      # cpu value from --cpu, so use that as the directory name.
      cpu = ctx.fragments.cpp.cpu
    for dep in deps:
      for f in dep.data_runfiles.files:
        inputs.append((cpu, f))

  # If two targets in deps share the same files (e.g. in the data attribute)
  # they would be included mulitple times in the same path in the zip, so
  # dedupe the files.
  deduped_inputs = set(inputs)
  zipper_args = ["c", ctx.outputs.zip.path]
  for cpu, file in deduped_inputs:
    zipper_args.append("%s/%s=%s" % (cpu, file.short_path, file.path))

  ctx.action(
    inputs=[f for cpu, f in deduped_inputs],
    outputs=[ctx.outputs.zip],
    executable=ctx.executable._zipper,
    arguments=zipper_args,
    progress_message="Creating zip...",
    mnemonic="zipper",
  )

multi_cpu_zip = rule(
  implementation = _multi_cpu_zip_impl,
  attrs = {
    "deps": attr.label_list(cfg = android_common.multi_cpu_configuration),
    "$zipper": attr.label(default = Label("@bazel_tools//tools/zip:zipper"), cfg = "host", executable=True),
  },
  outputs = {"zip": "%{name}.zip"},
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

ANDROID_COPTS = _select_android(["-fPIC"], [])

ANDROID_LINKOPTS = _select_android([
    "-llog",
    "-lm",
    "-ldl",
    "-pie",
    "-s",
    "-Wl,--gc-sections",
    "-Wl,--exclude-libs,ALL"], [])

