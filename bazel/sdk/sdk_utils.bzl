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
    runtime_jars = ctx.attr.java_binary[JavaInfo].transitive_runtime_jars
    jar_names = [calculate_jar_name_for_sdk_package(jar) for jar in runtime_jars]
    sep = ";" if ctx.attr.is_windows else ":"
    lib_path = "%APP_HOME%\\lib\\" if ctx.attr.is_windows else "$APP_HOME/lib/"
    jar_paths = sep.join([lib_path + jar for jar in jar_names])
    ctx.actions.expand_template(
        template = ctx.file.template,
        output = ctx.outputs.out,
        substitutions = ctx.attr.substitutions + {
            "${JARS}": jar_paths,
        },
    )

expand_template = rule(
    implementation = expand_template_impl,
    attrs = {
        "template": attr.label(mandatory = True, allow_single_file = True),
        "substitutions": attr.string_dict(mandatory = True),
        "java_binary": attr.label(mandatory = True),
        "is_windows": attr.bool(mandatory = True),
        "out": attr.output(mandatory = True),
    },
)

def tool_start_script(name, platform, command_name, main_class_name, java_binary, default_jvm_opts, visibility):
    is_windows = platform == "win"
    expand_template(
        name = name,
        visibility = visibility,
        template = "//tools/base/bazel/sdk/resources:" + platform + "_start_script",
        out = platform + "/" + command_name + (".bat" if is_windows else ""),
        substitutions = {
            "${COMMAND_NAME}": command_name,
            "${DEFAULT_JVM_OPTS}": default_jvm_opts,
            "${MAIN_CLASS}": main_class_name,
            "${PLATFORM}": platform,
            "${COMMAND_UPPER}": command_name.upper(),
        },
        java_binary = java_binary,
        is_windows = is_windows,
    )

sdk_jar_prefix_to_zip_location = {
    "prebuilts/tools/common/m2/repository/": "external/",
    "prebuilts/tools/common/": "external/",
    "tools/external/": "external/",
    "tools/base/": "",
    "tools/": "",
}

def calculate_jar_name_for_sdk_package(jar):
    path = jar.short_path
    path = path.replace("/libtools.", "/", maxsplit = 1)

    for prefix in sdk_jar_prefix_to_zip_location.keys():
        if path.startswith(prefix):
            return sdk_jar_prefix_to_zip_location[prefix] + path[len(prefix):]
    fail("Unknown path mapping for jar " + jar.path)
