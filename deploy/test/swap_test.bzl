load("//tools/base/fakeandroid:fakeandroid.bzl", "fake_android_test")

def swap_test(name, srcs):
    fake_android_test(
        name = name,
        size = "medium",
        srcs = srcs + native.glob(
            include = ["java/com/android/tools/deploy/swapper/*.java"],
            exclude = ["java/com/android/tools/deploy/swapper/*Test.java"],
        ),
        data = [
            ":test-app",
            "//tools/base/deploy/agent/native:agent_server",
            "//tools/base/deploy/agent/native:libswap.so",
            "//tools/base/deploy/test/apk1:apk",
            "//tools/base/deploy/test/apk2:apk",
        ],
        jvm_flags = [
            # Location of the inital test app.
            "-Dapp.dex.location=$(location :test-app)",

            # Location of the dex files to be swapped in.
            "-Dapp.swap.dex.location=$(location :test-app-swap)",

            # JVMTI Agent for the host.
            "-Dswap.agent.location=$(location //tools/base/deploy/agent/native:libswap.so)",

            # Agent server for communcation with the agent.
            "-Dswap.server.location=$(location //tools/base/deploy/agent/native:agent_server)",

            # APKs for testing the DexArchiveComparator
            "-Dapk1.location=$(location //tools/base/deploy/test/apk1:apk)",
            "-Dapk2.location=$(location //tools/base/deploy/test/apk2:apk)",
        ],
        deps = [
            ":test-app",
            ":test-app-swap",
            "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.4.0:jar",
            "//tools/base/bazel:langtools",
            "//tools/base/deploy/deployer:tools.deployer",
            "//tools/base/deploy/proto:deploy_java_proto",
            "//tools/base/deploy/test/apk1:apk",
            "//tools/base/deploy/test/apk2:apk",
            "//tools/base/fakeandroid",
            "//tools/idea/.idea/libraries:JUnit4",
        ],
    )
