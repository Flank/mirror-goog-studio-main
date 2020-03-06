java_binary(
    name = "verify",
    srcs = [
        "src/com/android/zipflinger/Verify.java",
    ],
    main_class = "com.android.zipflinger.Verify",
    deps = [
        "//tools/base/zipflinger",
    ],
)

java_binary(
    name = "map",
    srcs = [
        "src/com/android/zipflinger/Mapper.java",
    ],
    main_class = "com.android.zipflinger.Mapper",
    deps = [
        "//tools/base/zipflinger",
    ],
)

# Debug tool to create zip files directly with zipflinger
java_binary(
    name = "zip",
    srcs = [
        "src/com/android/zipflinger/ZipCreator.java",
    ],
    main_class = "com.android.zipflinger.ZipCreator",
    deps = [
        "//tools/base/zipflinger",
    ],
)

java_library(
    name = "profiler_lib",
    srcs = [
        "src/com/android/zipflinger/ApkMaker.java",
        "src/com/android/zipflinger/Profiler.java",
    ],
    visibility = [
        "//tools/base/signflinger/tools:__subpackages__",
        "//tools/base/zipflinger/tools:__subpackages__",
    ],
    deps = [
        "//tools/base/zipflinger",
    ],
)

java_binary(
    name = "profileAdd",
    srcs = [
        "src/com/android/zipflinger/ProfileAdd.java",
    ],
    data = [
        "resources/zipflinger.profile",
    ],
    jvm_flags = [
        "-javaagent:$(location //tools/base/tracer:trace_agent)=$(location resources/zipflinger.profile)",
    ],
    main_class = "com.android.zipflinger.ProfileAdd",
    deps = [
        ":profiler_lib",
        "//tools/base/tracer:tools.tracer",
        "//tools/base/tracer:trace_agent",
        "//tools/base/zipflinger",
    ],
)

java_binary(
    name = "profileCreate",
    srcs = [
        "src/com/android/zipflinger/ProfileCreate.java",
    ],
    data = [
        "resources/zipflinger.profile",
    ],
    jvm_flags = [
        "-javaagent:$(location //tools/base/tracer:trace_agent)=$(location resources/zipflinger.profile)",
    ],
    main_class = "com.android.zipflinger.ProfileCreate",
    deps = [
        ":profiler_lib",
        "//tools/base/tracer:tools.tracer",
        "//tools/base/tracer:trace_agent",
        "//tools/base/zipflinger",
    ],
)

java_binary(
    name = "profileCompressor",
    srcs = [
        "src/com/android/zipflinger/ProfileCompressor.java",
    ],
    data = [
        "resources/zipflinger.profile",
    ],
    jvm_flags = [
        "-javaagent:$(location //tools/base/tracer:trace_agent)=$(location resources/zipflinger.profile)",
    ],
    main_class = "com.android.zipflinger.ProfileCompressor",
    deps = [
        ":profiler_lib",
        "//tools/base/tracer:tools.tracer",
        "//tools/base/tracer:trace_agent",
        "//tools/base/zipflinger",
    ],
)
