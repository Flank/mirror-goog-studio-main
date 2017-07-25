genrule(
    name = "app-launcher-dex",
    srcs = [":app-launcher_deploy.jar"],
    outs = ["app-launcher.dex"],
    cmd = "$(location //prebuilts/studio/sdk:dx-preview) --dex --output=./$@ ./$<",
    tools = [
        "//prebuilts/studio/sdk:dx-preview",
    ],
    visibility = ["//visibility:public"],
)

java_binary(
    name = "app-launcher",
    srcs = ["src/com/android/tools/profiler/FakeAndroid.java"],
    main_class = "com.android.tools.profiler.FakeAndroid",
    deps = [
        "//tools/base/profiler/tests/android-mock",
    ],
)
