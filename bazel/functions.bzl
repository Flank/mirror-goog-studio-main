# Helper functions for implementing Bazel rules.

def create_option_file(ctx, name, content):
  """ Create the command line options file """
  options_file = ctx.new_file(name)
  ctx.file_action(output=options_file, content=content)
  return options_file

def create_java_compiler_args_srcs(ctx, srcs, path, deps):
  return create_java_compiler_args_srcs_deps(
    ctx,
    srcs,
    path,
    ":".join([dep.path for dep in deps]))

def create_java_compiler_args_srcs_deps(ctx, srcs, jar, deps):
  args = []
  option_files = []

  # Classpath
  if deps:
    cp_file = create_option_file(ctx, jar.basename + ".cp", deps)
    option_files += [cp_file]
    args += ["-cp", "@" + cp_file.path]

  # Output
  args += ["-o", jar.path]

  # Source files
  srcs_lines =  "\n".join(srcs)
  source_file = create_option_file(ctx, jar.basename + ".lst", srcs_lines)
  option_files += [source_file]
  args += ["@" + source_file.path]

  return (args, option_files)

def create_java_compiler_args(ctx, path, deps):
  return create_java_compiler_args_srcs(
      ctx,
      [src.path for src in ctx.files.srcs],
      path,
      deps)

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
  return label_workspace_path(Label("//" + path, relative_to_caller_repository=True))
