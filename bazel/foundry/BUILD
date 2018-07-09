# This test is run explicitely on the foundry bot as will not pass on
# a standard linux machine, because validates a very custom /dev/random condition.
java_test(
    name = "test",
    srcs = ["FoundryTest.java"],
    data = ["//prebuilts/studio/sdk:build-tools/latest"],
    jvm_flags = ["-Djava.security.debug=all"],
    tags = ["manual"],
    test_class = "FoundryTest",
)
