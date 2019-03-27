# Creates a filegroup for a given platform directory in the Android SDK.
#
# It excludes files that are not necessary for testing
# and take up space in the sandbox.
def platform_filegroup(name, visibility = ["//visibility:public"]):
    native.filegroup(
        name = name,
        srcs = sdk_glob(
            include = [name + "/**"],
            exclude = [
                name + "/skins/**",
            ],
        ),
        visibility = visibility,
    )
    build_only_name = name + "_build_only"
    native.filegroup(
        name = build_only_name,
        srcs = sdk_glob(
            include = [name + "/**"],
            exclude = [
                name + "/skins/**",
                name + "/data/res/**",
            ],
        ),
        visibility = visibility,
    )

# A glob that only includes files from the current platform Android SDK.
def sdk_glob(include, exclude = []):
    return select({
        "//tools/base/bazel:darwin": _sdk_glob("darwin", include, exclude),
        "//tools/base/bazel:windows": _sdk_glob("windows", include, exclude),
        "//conditions:default": _sdk_glob("linux", include, exclude),
    })

def _sdk_glob(platform, include, exclude):
    return native.glob(
        include = [platform + "/" + name for name in include],
        exclude = [platform + "/" + name for name in exclude],
    )

# A path to a file from the current platform Android SDK.
def sdk_path(paths):
    return select({
        "//tools/base/bazel:host_darwin": ["darwin/" + path for path in paths],
        "//tools/base/bazel:host_windows": ["windows/" + path for path in paths],
        "//conditions:default": ["linux/" + path for path in paths],
    })

def expand_template_impl(ctx):
    ctx.actions.expand_template(
        template = ctx.file.template,
        output = ctx.outputs.out,
        substitutions = ctx.attr.substitutions,
    )

expand_template = rule(
    implementation = expand_template_impl,
    attrs = {
        "template": attr.label(mandatory = True, allow_single_file = True),
        "substitutions": attr.string_dict(mandatory = True),
        "out": attr.output(mandatory = True),
    },
)

def tool_start_script(name, platform, command_name, main_class_name, jars, default_jvm_opts):
    is_windows = platform == "win"
    sep = ";" if is_windows else ":"
    lib_path = "%APP_HOME%\\lib\\" if is_windows else "$APP_HOME/lib/"
    jar_names = [jar[jar.find(":") + 1:] for jar in jars]
    expand_template(
        name = name,
        template = "//tools/base/bazel/sdk/resources:" + platform + "_start_script",
        out = platform + "/" + command_name + (".bat" if is_windows else ""),
        substitutions = {
            "${COMMAND_NAME}": command_name,
            "${DEFAULT_JVM_OPTS}": default_jvm_opts,
            "${MAIN_CLASS}": main_class_name,
            "${JARS}": sep.join([lib_path + jar for jar in jar_names]),
            "${PLATFORM}": platform,
            "${COMMAND_UPPER}": command_name.upper(),
        },
    )
