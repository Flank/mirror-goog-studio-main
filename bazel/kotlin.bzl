load(":functions.bzl", "create_java_compiler_args_srcs")
load(":functions.bzl", "explicit_target")
load(":maven.bzl", "maven_pom")
load(":utils.bzl", "singlejar")

def _kotlin_jar_impl(ctx):

  class_jar = ctx.outputs.class_jar

  all_deps = set(ctx.files.deps)
  all_deps += set(ctx.files._kotlin)
  for this_dep in ctx.attr.deps:
    if hasattr(this_dep, "java"):
      all_deps += this_dep.java.transitive_runtime_deps

  merged = [src.path for src in ctx.files.srcs]
  if ctx.attr.package_prefixes:
    merged = [ a + ":" + b if b else a for (a,b) in zip(merged, ctx.attr.package_prefixes)]
  content = "\n".join(merged)

  args, option_files = create_java_compiler_args_srcs(ctx, content, class_jar.path,
                                                 all_deps)


  ctx.action(
    inputs = ctx.files.srcs + list(all_deps) + option_files,
    outputs = [class_jar],
    mnemonic = "kotlinc",
    arguments = args,
    executable = ctx.executable._kotlinc,
  )

kotlin_jar = rule(
    attrs = {
        "srcs": attr.label_list(
            non_empty = True,
            allow_files = True,
        ),
        "package_prefixes": attr.string_list(),
        "deps": attr.label_list(
            mandatory = False,
            allow_files = FileType([".jar"]),
        ),
        "_kotlinc": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:kotlinc"),
            allow_files = True,
        ),
        "_kotlin": attr.label(
            default = Label("//tools/base/bazel:kotlin-runtime"),
            allow_files = True,
        ),
    },
    outputs = {
        "class_jar": "lib%{name}.jar",
    },
    implementation = _kotlin_jar_impl,
)

def kotlin_java_library(name, java_dir, kotlin_dir, resources_dir, deps, pom=None, visibility = None):
  java_srcs = native.glob([java_dir + "/**/*.java"])
  kotlin_srcs = native.glob([kotlin_dir + "/**/*.kt"])
  resources = native.glob([resources_dir + "/**"])

  kotlin_name = name + ".kotlin"
  kotlin_jar(
      name = kotlin_name,
      # kotlinc cannot take individual *.java files, just the directory.
      srcs = kotlin_srcs + [java_dir],
      deps = deps,
  )

  java_name = name + ".java"
  native.java_library(
      name = java_name,
      srcs = java_srcs,
      resources = resources,
      deps = [":lib" + kotlin_name + ".jar"] + deps,
  )

  single_name = name + ".single"
  singlejar(
      name = single_name,
      srcs = [":lib" + target + ".jar" for target in (java_name, kotlin_name)]
  )

  native.java_import(
      name = name,
      jars = [":" + single_name],
      visibility = visibility,
  )

  if pom:
    maven_pom(
      name = name + "_maven",
      deps = [explicit_target(dep) + "_maven" for dep in deps if not dep.endswith("_neverlink")],
      library = name,
      visibility = visibility,
      source = pom,
    )
