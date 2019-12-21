java_binary(
    name = "profilers-transform-main",
    testonly = True,
    srcs = [
        "src/com/android/tools/profiler/transform/ProfilerTransformMain.java",
    ],
    main_class = "com.android.tools.profiler.transform.ProfilerTransformMain",
    resources = ["profiler.properties"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/base/profiler/transform"],
    deps = [
        "//tools/base/profiler/transform",
        "//tools/base/testutils:studio.android.sdktools.testutils_testlib",
    ],
)
