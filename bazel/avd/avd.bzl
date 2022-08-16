# A rule that declares all the files needed to run an Android emulator.
# Targets that depend on this rule can then launch the emulator using
# the script that is generated by this rule.
def _avd_impl(ctx):
    # Look for the source.properties file in the system image to get information
    # about the image from.
    source_properties_path = None
    system_image_files = ctx.attr.image[DefaultInfo].files.to_list()
    for system_image_file in system_image_files:
        if system_image_file.basename == "source.properties":
            source_properties_path = system_image_file.path
            break
    if source_properties_path == None:
        fail("Supplied system image does not contain a source.properties file")

    executable = ctx.outputs.executable
    ctx.actions.expand_template(
        template = ctx.file._template,
        output = executable,
        is_executable = True,
        substitutions = {
            "%source_properties_path%": source_properties_path,
        },
    )

    runfiles = ctx.runfiles(files = [executable] +
                                    ctx.files.emulator +
                                    ctx.files.platform +
                                    ctx.files._platform_tools +
                                    ctx.files.image)
    return [DefaultInfo(runfiles = runfiles)]

avd = rule(
    implementation = _avd_impl,
    attrs = {
        "_template": attr.label(
            default = "//tools/base/bazel/avd:emulator_launcher.sh.template",
            allow_single_file = True,
        ),
        "emulator": attr.label(
            default = "//prebuilts/studio/sdk:emulator",
        ),
        "_platform_tools": attr.label(
            default = "//prebuilts/studio/sdk:platform-tools",
        ),
        "image": attr.label(
            default = "@system_image_android-29_default_x86_64//:x86_64-android-29-images",
        ),
        "platform": attr.label(
            default = "//prebuilts/studio/sdk:platforms/latest",
        ),
    },
    executable = True,
)
