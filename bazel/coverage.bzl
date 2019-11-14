def coverage_java_test(name, coverage = True, data = [], jvm_flags = [], tags = [], visibility = None, test_excluded_packages = {}, **kwargs):
    jacoco_jvm_agent = "//prebuilts/tools/common/jacoco:agent"

    jacoco_jvm_flag = "-javaagent:$(location " + jacoco_jvm_agent + ")=destfile=$$TEST_UNDECLARED_OUTPUTS_DIR/coverage/" + name + "_tests_jacoco.exec,inclnolocationclasses=true"

    # Map the dictionary of packages (platform -> ["a", "b", "c"]) to a dictionary
    # of (platform -> ["-Dtest.excluded.packages=a,b,c"]), which can be used as argument
    # to a select() function call.
    test_jvm_flags = {}
    for k, v in test_excluded_packages.items():
        test_jvm_flags[k] = ["-Dtest.excluded.packages=" + ",".join(v)]
    if len(test_jvm_flags) == 0:
        test_jvm_flags = {"//conditions:default": []}

    if tags == None:
        tags = []

    if not coverage:
        native.java_test(
            name = name,
            data = data,
            jvm_flags = jvm_flags + select(test_jvm_flags),
            tags = tags,
            visibility = visibility,
            **kwargs
        )
    else:
        # the test needs to be visible to the results workspace it can use the deploy jar
        if visibility == None:
            visibility = ["@results//:__pkg__"]
        elif "//visibility:public" not in visibility:
            visibility += ["@results//:__pkg__"]

        native.java_test(
            name = name,
            data = data + select({
                "//tools/base/bazel:agent_coverage": [jacoco_jvm_agent],
                "//conditions:default": [],
            }),
            jvm_flags = jvm_flags + select(test_jvm_flags) + select({
                "//tools/base/bazel:agent_coverage": [jacoco_jvm_flag],
                "//conditions:default": [],
            }),
            tags = tags + ["coverage-test"],
            visibility = visibility,
            **kwargs
        )
