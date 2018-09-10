load("//tools/base/bazel:bazel.bzl", "fileset")

def coverage_report(
    name,
    production_java_libraries,
    exclude_patterns=[],
    tags=[]):

    # keep in sync with CoverageReportGenerator.java
    collection_binary_name = name + "_collection_binary"

    production_source_and_classes = name + "_source_and_classes"

    fileset(
        name = production_source_and_classes,
        srcs = [
            collection_binary_name + "_deploy.jar",
            collection_binary_name + "_deploy-src.jar",
        ],
        mappings = {
                        collection_binary_name + "_deploy.jar": "classes.jar",
                                                collection_binary_name + "_deploy-src.jar": "sources.jar",
        },
    )

    native.java_binary(
        name = collection_binary_name,
        main_class = "fake_do_not_execute",
        runtime_deps = production_java_libraries,
    )

    native.java_binary(
        name = name,
        main_class = "com.android.tools.coverage.CoverageReportGenerator",
        classpath_resources = [ production_source_and_classes, ],
        runtime_deps = ["//tools/base/bazel:coverage_report_generator"],
        tags = tags + ["agent_coverage_report"],
   )
