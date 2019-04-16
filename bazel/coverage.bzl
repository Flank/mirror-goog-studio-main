def coverage_java_test(name, coverage = True, data = [], jvm_flags = [], tags = [], **kwargs):
    jacoco_jvm_agent = "//prebuilts/tools/common/m2/repository/org/jacoco/org.jacoco.agent/0.8.2:runtime-jar"

    jacoco_jvm_flag = "-javaagent:$(location " + jacoco_jvm_agent + ")=destfile=$$TEST_UNDECLARED_OUTPUTS_DIR/coverage/" + name + "_tests_jacoco.exec,inclnolocationclasses=true"

    if tags == None:
        tags = []

    if not coverage:
        native.java_test(
            name = name,
            data = data,
            jvm_flags = jvm_flags,
            tags = tags,
            **kwargs
        )
    else:
        native.java_test(
            name = name,
            data = data + select({
                "//tools/base/bazel:agent_coverage": [jacoco_jvm_agent],
                "//conditions:default": [],
            }),
            jvm_flags = jvm_flags + select({
                "//tools/base/bazel:agent_coverage": [jacoco_jvm_flag],
                "//conditions:default": [],
            }),
            tags = tags + ["coverage-test"],
            **kwargs
        )
