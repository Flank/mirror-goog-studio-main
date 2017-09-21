load(":functions.bzl", "create_java_compiler_args_srcs")

def groovy_impl(ctx, srcs, groovy_deps, groovy_jar, stub_jar):
  stub_args =["-o"] + [stub_jar.path] + [groovy.path for groovy in srcs]
  ctx.action(
      inputs = srcs,
      outputs = [stub_jar],
      mnemonic = "groovystub",
      arguments = stub_args,
      executable = ctx.executable._groovystub
  )

  args, option_files = create_java_compiler_args_srcs(
      ctx,
      [src.path for src in srcs],
      groovy_jar.path,
      groovy_deps)

  ctx.action(
      inputs = srcs + groovy_deps + option_files,
      outputs = [groovy_jar],
      mnemonic = "groovyc",
      arguments = args,
      executable = ctx.executable._groovyc
  )
