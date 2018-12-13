# make SDK system-images and emulator binaries from
# local installations of the Android SDK available to
# tests to patch in to the prebuilts in individual Bazel
# sandboxes

def _external_sdk(repository_ctx):
    android_sdk_path = repository_ctx.attr.path
    rule_name = repository_ctx.name
    target_path = repository_ctx.path("")
    copy_script = repository_ctx.path(Label("//tools/base/bazel:qa_emu_setup.py"))

    build_file_contents = """
filegroup(
    name = "emulator",
    srcs = glob([
        "emulator/**"
    ]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "system-images",
    srcs = glob([
        "system-images/**"
    ]),
    visibility = ["//visibility:public"],
)"""
    repository_ctx.file("BUILD", content = build_file_contents, executable = False)
    script_env = {}
    if android_sdk_path != "":
        script_env["QA_ANDROID_SDK_ROOT"] = android_sdk_path

    my_exec_result = repository_ctx.execute(
        [
            "python",
            copy_script,
            target_path,
        ],
        environment = script_env,
        quiet = False,
    )

    if my_exec_result.return_code != 0:
        fail("Unable to set up emulator and system images external SDK package")

setup_external_sdk = repository_rule(
    implementation = _external_sdk,
    local = True,
    environ = [
        "QA_ANDROID_SDK_ROOT",
    ],
    attrs = {
        "path": attr.string(),
    },
)
