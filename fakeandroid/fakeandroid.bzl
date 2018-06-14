def fake_android_test(name, srcs, deps = [], data = [], runtime_deps = [], tags = [], size = "small", jvm_flags = []):
    native.java_test(
      name = name,
      runtime_deps = runtime_deps + [
          "//tools/base/fakeandroid:art-runner",
          "//tools/base/testutils:tools.testutils",
      ],
      deps = deps + [
          "//tools/base/common:studio.android.sdktools.common",
          "//tools/base/fakeandroid:app-launcher-dex",
          "//tools/base/fakeandroid:android-mock-dex",
          "//tools/base/third_party:junit_junit",
      ],
      jvm_flags = jvm_flags + [
              "-Dtest.suite.jar=" + name + ".jar",
              "-Dart.location=/prebuilts/tools/linux-x86_64/art/bin/art",
              "-Dperfa.dex.location=$(location //tools/base/fakeandroid:app-launcher-dex)",
              "-Dandroid-mock.dex.location=$(location //tools/base/fakeandroid:android-mock-dex)",
              "-Dart.deps.location=prebuilts/tools/linux-x86_64/art/framework/",
              "-Dart.boot.location=prebuilts/tools/linux-x86_64/art/framework/x86_64/",
              "-Dart.lib64.location=prebuilts/tools/linux-x86_64/art/lib64",
          ],
      shard_count = 1,
      test_class = "com.android.testutils.JarTestSuite",
      visibility = ["//visibility:public"],
      size = size,
      data = data + [
        "//tools/base/fakeandroid:art-runner",
      ],
      srcs = select({
        "//tools/base/bazel:darwin": ["//tools/base/bazel/test:NoOpTest.java"],
        "//tools/base/bazel:windows": ["//tools/base/bazel/test:NoOpTest.java"],
        "//conditions:default": srcs}),
      tags = list(depset(tags + ["no_windows", "no_mac"])),
    )
