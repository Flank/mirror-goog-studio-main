java_binary(
    name = "profileV1",
    srcs = [
        "src/com/android/signflinger/ProfileV1.java",
    ],
    data = [
        "resources/signflinger.profile",
        "//tools/base/signflinger:test_keys_and_certs",
    ],
    jvm_flags = [
        "-javaagent:$(location //tools/base/tracer:trace_agent)=$(location resources/signflinger.profile)",
    ],
    main_class = "com.android.signflinger.ProfileV1",
    deps = [
        "//tools/apksig",
        "//tools/base/signflinger",
        "//tools/base/signflinger:sign_utils",
        "//tools/base/tracer:tools.tracer",
        "//tools/base/tracer:trace_agent",
        "//tools/base/zipflinger",
        "//tools/base/zipflinger/tools:profiler_lib",
    ],
)
