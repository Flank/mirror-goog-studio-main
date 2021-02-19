load("//tools/base/bazel:maven.bzl", "maven_java_library")

java_library(
    name = "jaxb-inheritance-plugin",
    srcs = glob(["src/main/java/**"]),
    resources = glob(["src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.sun.activation_javax.activation",
        "//tools/base/third_party:org.glassfish.jaxb_jaxb-xjc",
    ],
)
