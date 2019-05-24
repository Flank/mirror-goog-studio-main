load("//:coverage.bzl", "coverage_report")

# This should be a py_binary, but due to a Bazel bug
# using a py_binary as a tool in a genrule doesn't work
py_library(
    name = "jacoco_xml_to_lcov",
    srcs = ["jacoco_xml_to_lcov.py"],
    visibility = ["//visibility:public"],
)

# This should be a py_binary, but due to a Bazel bug
# using py_binary as a tool in a genrule doesn't work
py_library(
    name = "merge_lcov",
    srcs = ["merge_lcov.py"],
)

# This is just an example report for testing during development
# run tests: bazel test --define agent_coverage=true --config=remote -- @cov//:example:suite
# make lcov: bazel build --config=remote -- @cov//:example.lcov
coverage_report(
    name = "example",
    tests = [
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.tests_tests",  # 5 shard 1 exec
        "//tools/base/build-system/integration-test/application:automatic-tests",  # 8 shard 2 exec
        "//tools/base/build-system/integration-test/native:AbiPureSplits",  # 1 shard 2 exec
        "//tools/base/lint/libs/lint-tests:tests.test",  # 1 shard 1 exec
    ],
)
