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
            "//tools/base/deploy/agent/native:agent_server",
            "//tools/base/deploy/agent/native:libswap.so",
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

            # Agent server for communcation with the agent.
            "-Dswap.server.location=$(location //tools/base/deploy/agent/native:agent_server)",

            # APKs for testing the DexArchiveComparator
            "-Dapk1.location=$(location //tools/base/deploy/test/data/apk1:apk)",
            "-Dapk2.location=$(location //tools/base/deploy/test/data/apk2:apk)",
        ],
        deps = [
            "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.4.0:jar",
            "//tools/base/bazel:langtools",
            "//tools/base/deploy/deployer:tools.deployer",
            "//tools/base/deploy/proto:deploy_java_proto",
            "//tools/base/fakeandroid",
            "//tools/idea/.idea/libraries:JUnit4",
        ],
    )
