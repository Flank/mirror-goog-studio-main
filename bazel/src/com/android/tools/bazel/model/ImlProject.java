package com.android.tools.bazel.model;

import com.android.tools.bazel.parser.ast.CallExpression;
import com.android.tools.bazel.parser.ast.CallStatement;
import java.io.IOException;

public class ImlProject extends BazelRule {

    public ImlProject(Package pkg, String name) {
        super(pkg, name);
    }

    @Override
    public void update() throws IOException {
        CallStatement statement = getCallStatement("iml_project", name);
        if (getLoad(statement) == null) {
            addLoad("//tools/base/bazel:bazel.bzl", statement);
        }

        CallExpression call = statement.getCall();
        call.setArgument("modules", dependencies);
        call.addElementToList("tags", "managed");
    }

    public void addModule(ImlModule rule) {
        addDependency(rule, false);
    }
}
