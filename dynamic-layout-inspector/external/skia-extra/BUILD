config_setting(
    name = "windows",
    values = {"host_cpu": "x64_windows"},
    visibility = ["//visibility:public"],
)

cc_library(
    name = "skia_includes",
    includes = ["."] + select({
        "windows": ["win"],
        "//conditions:default": [],
    }),
    visibility = ["//visibility:public"],
)
