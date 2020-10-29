# Helper functions for implementing Bazel rules.

# Prefer using ctx.actions.args() instead; see
# https://docs.bazel.build/versions/master/skylark/lib/Args.html
def create_option_file(ctx, name, content):
    """ Create the command line options file """
    options_file = ctx.actions.declare_file(name)
    ctx.actions.write(output = options_file, content = content)
    return options_file

# Adds an explict target-name part if label doesn't have it.
def explicit_target(label):
    return label if ":" in label else label + ":" + label.rsplit("/", 1)[-1]

# Converts label package to a path relative to the execroot.
def label_workspace_path(label):
    if label.workspace_root != "":
        return label.workspace_root + "/" + label.package
    return label.package

# Converts a relative path to be relative to the execroot.
def workspace_path(path):
    return label_workspace_path(Label("//" + path, relative_to_caller_repository = True))
