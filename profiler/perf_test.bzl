load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/fakeandroid:fakeandroid.bzl", "fake_android_test")

def perf_test(name, srcs, test_app, deps = [], tags = None, size = "small"):
    native.genrule(
      name = name + "_transform-app_java",
      srcs = [test_app + "_java_deploy.jar"],
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
      tags = tags,
    )

    dex_library(
      name = name + "_transform-app",
      jars = [":" + name + "_transform-app_java"],
    )

    fake_android_test(
      name = name,
      srcs = srcs,
      deps = deps + [
          "//tools/base/profiler/tests/perf-test:profiler-service",
          "//tools/base/profiler:studio-profiler-grpc-1.0-jarjar",
          "//tools/base/profiler/app:perfa",
          "//tools/base/profiler/native/perfa:libperfa.so",
          "//tools/base/profiler/native/perfd",
          ":" + name + "_transform-app",
          test_app,
      ],
      runtime_deps = select({
          "//tools/base/bazel:darwin": [],
          "//tools/base/bazel:windows": [],
          "//conditions:default": [
              ":profiler-service",
              "//tools/base/profiler:netty-grpc-jar",
              "//tools/base/profiler/native/agent:libsupportjni.so",
              "//tools/base/profiler/app:perfa",
              "//tools/base/profiler/app:perfa_java",
              "//tools/base/profiler/app:perfa_okhttp",
              "//tools/base/profiler/native/perfa:libperfa.so",
          ],
      }),
      tags = tags,
      size = size,
      jvm_flags = [
              "-Dperfd.location=$(location //tools/base/profiler/native/perfd)",
              "-Dagent.location=/tools/base/profiler/native/agent",
              "-Dprofiler.service.location=$(location //tools/base/profiler/tests/perf-test:profiler-service)",
              "-Dperfa.dir.location=/tools/base/profiler/native/perfa",
              "-Dperfa.location=$(location //tools/base/profiler/native/perfa:libperfa.so)",
              "-Dperfa.jar.location=$(location //tools/base/profiler/app:perfa)",
              "-Dnative.lib.location=$(location " + test_app + ").dirname",
              # Non transformed app should be used here.
              "-Djvmti.app.dex.location=$(location " + test_app + ")",
              # Transformed app should be used here. This way we can test our support for both O+ and pre-O devices.
              "-Dinstrumented.app.dex.location=$(location :" + name + "_transform-app)",
      ],
      data = select({
          "//tools/base/bazel:darwin": [],
          "//tools/base/bazel:windows": [],
          "//conditions:default": [
              ":profiler-service",
              "//tools/base/profiler/app:perfa",
              "//tools/base/profiler/app:perfa_java",
              "//tools/base/profiler/app:perfa_okhttp",
              "//tools/base/profiler/native/perfa:libperfa.so",
          ],
      })
    )
