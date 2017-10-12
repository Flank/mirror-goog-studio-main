# Helper functions for implementing Bazel rules.

def create_option_file(ctx, name, content):
  """ Create the command line options file """
  options_file = ctx.new_file(name)
  ctx.file_action(output=options_file, content=content)
  return options_file

def create_java_compiler_args_srcs(ctx, srcs, jar, deps):
  args = []
  option_files = []

  # Classpath
  if deps:
    cp_file = create_option_file(ctx, jar.basename + ".cp",
      ":".join([dep.path for dep in deps]))
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
