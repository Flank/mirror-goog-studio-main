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
          ]
      ),
      visibility = visibility
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
      visibility = visibility
  )

# A glob that only includes files from the current platform Android SDK.
def sdk_glob(include, exclude=[]):
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
         "//tools/base/bazel:darwin": ["darwin/" + path for path in paths],
         "//tools/base/bazel:windows": ["windows/" + path for path in paths],
         "//conditions:default": ["linux/" + path for path in paths],
    })