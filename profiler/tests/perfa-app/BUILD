genrule(
    name = "perfa-app-dex",
    srcs = [":perfa-app_deploy.jar"],
    outs = ["perfa-app.dex"],
    cmd = "$(location //prebuilts/studio/sdk:dx-preview) --dex --output=./$@ ./$<",
    tools = [
        "//prebuilts/studio/sdk:dx-preview",
    ],
    visibility = ["//visibility:public"],
)

java_binary(
    name = "perfa-app",
    srcs = glob(["**/*.java"]),
    main_class = "com.android.tools.profiler.Main",
    deps = [
        "//tools/base/profiler/app:studio-profiler",
    ],
)
