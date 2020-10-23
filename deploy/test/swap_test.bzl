load("//tools/base/fakeandroid:fakeandroid.bzl", "fake_android_test")

def swap_test(name, srcs):
    fake_android_test(
        name = name,
        size = "medium",
        srcs = srcs + native.glob(
            include = ["java/com/android/tools/deployer/*.java"],
            exclude = ["java/com/android/tools/deployer/*Test.java"],
        ),
        data = [
            ":original_dex",
            ":swapped_dex",
            "//tools/base/deploy/agent/native:libswap.so",
            "//tools/base/deploy/installer:install-server",
            "//tools/base/deploy/test/data/apk1:apk",
            "//tools/base/deploy/test/data/apk2:apk",
        ],
        jvm_flags = [
            # Location of the inital test app.
            "-Dapp.dex.location=$(location :original_dex)",

            # Location of the dex files to be swapped in.
            "-Dapp.swap.dex.location=$(location :swapped_dex)",

            # JVMTI Agent for the host.
            "-Dswap.agent.location=$(location //tools/base/deploy/agent/native:libswap.so)",

            # Install server for communcation with the agent.
            "-Dinstall.server.location=$(location //tools/base/deploy/installer:install-server)",

            # APKs for testing the DexArchiveComparator
            "-Dapk1.location=$(location //tools/base/deploy/test/data/apk1:apk)",
            "-Dapk2.location=$(location //tools/base/deploy/test/data/apk2:apk)",
        ],
        deps = [
            "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.4.0:jar",
            "//prebuilts/tools/common/m2/repository/junit/junit/4.12:jar",
            "//tools/base/bazel:langtools",
            "//tools/base/bazel:studio-proto",
            "//tools/base/deploy/deployer:tools.deployer",
            "//tools/base/deploy/proto:deploy_java_proto",
            "//tools/base/fakeandroid",
        ],
    )
