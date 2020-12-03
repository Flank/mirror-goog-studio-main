genrule(
    name = "sites",
    outs = [
        "sites.h",
        "sites.cc",
        "Sites.java",
    ],
    cmd = """
       $(location :generator) \
           loc_h=$(location :sites.h) \
           loc_c=$(location :sites.cc) \
           loc_java=$(location :Sites.java) \
    """,
    tools = [
        ":generator",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "java_sites",
    srcs = [":Sites.java"],
    visibility = ["//visibility:public"],
)

java_binary(
    name = "generator",
    srcs = ["src/com/android/tools/SitesGenerator.java"],
    main_class = "com.android.tools.SitesGenerator",
)

java_test(
    name = "testSites",
    size = "small",
    srcs = [
        "tests/src/java/com/android/tools/TestSites.java",
        ":Sites.java",
    ],
    jvm_flags = ["-Dtest.suite.jar=testSites.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)
