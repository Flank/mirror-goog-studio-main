load("//tools/base/bazel:android.bzl", "dex_library")

def perf_test(name, srcs, test_app, deps = []):
    native.genrule(
      name = name + "_transform-app_java",
      srcs = [test_app + "_java"],
      outs = [name + "_transform-app_java.jar"],
      cmd = select({
          "//tools/base/bazel:darwin": "cp ./$< ./$@",
          "//tools/base/bazel:windows": "cp ./$< ./$@",
          "//conditions:default": "$(location //tools/base/profiler/tests/perf-test:profilers-transform-main) ./$< ./$@",
      }),
      executable = 1,
      tools = select({
          "//tools/base/bazel:darwin": [],
          "//tools/base/bazel:windows": [],
          "//conditions:default": [
               "//tools/base/profiler/tests/perf-test:profilers-transform-main",
          ],}),
    )
    native.genrule(
      name = name + "_transform-app",
      srcs = [":" + name + "_transform-app_java"],
      outs = [name + "_transform.jar"],
      cmd = "$(location //prebuilts/studio/sdk:dx-preview) --dex --output=./$@ ./$<",
      tools = ["//prebuilts/studio/sdk:dx-preview"],
    )

    native.java_test(
      name = name,
      runtime_deps = [
          "//tools/base/profiler/tests/perf-test:art-runner",
          "//tools/base/profiler:netty-grpc-jar",
          "//tools/base/profiler/native/agent:libsupportjni.so",
          "//tools/base/testutils:tools.testutils",
      ],
      deps = deps + [
          "//tools/base/profiler/tests/perf-test:profiler-service",
          "//tools/base/common:studio.common",
          "//tools/base/profiler:studio-profiler-grpc-1.0-jarjar",
          "//tools/base/profiler/app:perfa",
          "//tools/base/profiler/native/perfa:libperfa.so",
          "//tools/base/profiler/native/perfd",
          "//tools/base/profiler/tests/android-mock:android-mock-dex",
          "//tools/base/profiler/tests/app-launcher:app-launcher-dex",
          "//tools/base/third_party:junit_junit",
          ":" + name + "_transform-app",
          test_app,
      ],
      jvm_flags = [
              "-Dtest.suite.jar=AllTest.jar",
              "-Dperfd.location=$(location //tools/base/profiler/native/perfd)",
              "-Dart.location=/prebuilts/tools/linux-x86_64/art/bin/art",
              "-Dagent.location=/tools/base/profiler/native/agent",
              "-Dperfa.dex.location=$(location //tools/base/profiler/tests/app-launcher:app-launcher-dex)",
              "-Dandroid-mock.dex.location=$(location //tools/base/profiler/tests/android-mock:android-mock-dex)",
              "-Dinstrumented.app.dex.location=$(location :" + name + "_transform-app)",
              "-Dart.deps.location=prebuilts/tools/linux-x86_64/art/framework/",
              "-Dart.boot.location=prebuilts/tools/linux-x86_64/art/framework/x86_64/",
              "-Dprofiler.service.location=$(location //tools/base/profiler/tests/perf-test:profiler-service)",
              "-Dperfa.dir.location=/tools/base/profiler/native/perfa",
              "-Dperfa.location=$(location //tools/base/profiler/native/perfa:libperfa.so)",
              "-Dperfa.jar.location=$(location //tools/base/profiler/app:perfa)",
              "-Djvmti.app.dex.location=$(location " + test_app + ")",
          ],
      shard_count = 1,
      test_class = "com.android.testutils.JarTestSuite",
      visibility = ["//visibility:public"],
      size = "small",
      data = [
        ":" + name + "_transform-app",
        test_app,
        "//tools/base/profiler/tests/perf-test:art-runner"
      ],
      srcs = select({
        "//tools/base/bazel:darwin": ["//tools/base/bazel/test:NoOpTest.java"],
        "//tools/base/bazel:windows": ["//tools/base/bazel/test:NoOpTest.java"],
        "//conditions:default": srcs}),
    )
