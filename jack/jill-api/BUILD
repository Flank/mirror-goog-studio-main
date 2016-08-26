java_library(
    name = "jill-api",
    srcs = glob(["src/main/java/**"]),
    visibility = ["//visibility:public"],
    deps = [":com.google.code.findbugs_jsr305_neverlink"],
)

# Compile-time only dependency on the JSR 305 annotations.
java_library(
    name = "com.google.code.findbugs_jsr305_neverlink",
    neverlink = 1,
    exports = ["//tools/base/third_party:com.google.code.findbugs_jsr305"],
)
