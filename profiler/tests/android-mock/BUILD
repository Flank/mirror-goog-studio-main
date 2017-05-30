genrule(
    name = "android-mock-dex",
    srcs = [":libandroid-mock.jar"],
    outs = ["android-mock.dex"],
    cmd = "$(location //prebuilts/studio/sdk:dx-preview) --dex --output=./$@ ./$<",
    tools = [
        "//prebuilts/studio/sdk:dx-preview",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "android-mock",
    srcs = glob(["**/*.java"]),
    neverlink = 1,
    visibility = ["//visibility:public"],
)
