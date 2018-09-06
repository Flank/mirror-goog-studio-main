def coverage_java_test(name, data=[], jvm_flags=[], **kwargs):

    jacoco_jvm_agent = "//prebuilts/tools/common/m2/repository/org/jacoco/org.jacoco.agent/0.8.2:runtime-jar"

    jacoco_jvm_flag = "-javaagent:$(location " + jacoco_jvm_agent + ")=destfile=$$TEST_UNDECLARED_OUTPUTS_DIR/coverage/" + name + "_tests_jacoco.exec,inclnolocationclasses=true"

    native.java_test(
        name = name,
        data =  select({
             "//tools/base/bazel:agent_coverage": data + [jacoco_jvm_agent],
             "//conditions:default": data,
        }),
        jvm_flags = select({
            "//tools/base/bazel:agent_coverage": jvm_flags +[jacoco_jvm_flag],
            "//conditions:default": jvm_flags,
        }),
        **kwargs
    )