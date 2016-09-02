filegroup(
    name = "platforms/latest",
    srcs = [":platforms/android-24"],
    visibility = ["//visibility:public"],
)

# version-specific rule left private in hopes we can depend on platforms/latest instead
filegroup(
    name = "platforms/android-24",
    srcs = glob(
        include = ["*/platforms/android-24/**"],
    ),
)

filegroup(
    name = "platform-tools",
    srcs = glob(
        include = ["*/platform-tools/**"],
    ),
    visibility = ["//visibility:public"],
)
