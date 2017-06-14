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
    inputs = ctx.files.inputs + list(all_deps) + option_files,
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
        "inputs": attr.label_list(
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

def kotlin_library(name, srcs, javacopts=None, resources=[], deps=[], bundled_deps=[], pom=None, visibility=None, jar_name=None, **kwargs):
  kotlins = native.glob([src + "/**/*.kt" for src in srcs])
  javas = native.glob([src + "/**/*.java" for src in srcs])

  if not kotlins and not javas:
    print("No sources found for kotlin_library " + name)


  targets = []
  kdeps = []
  if kotlins:
    kotlin_name = name + ".kotlin"
    targets += [kotlin_name]
    kdeps += [":lib" + kotlin_name + ".jar"]
    kotlin_jar(
        name = kotlin_name,
        srcs = srcs,
        inputs = kotlins + javas,
        deps = deps + bundled_deps,
        visibility = visibility,
    )

  java_name = name + ".java"
  if javas:
    targets += [java_name]
    native.java_library(
        name = java_name,
        srcs = javas,
        javacopts = javacopts,
        resources = resources,
        deps = kdeps + deps + bundled_deps,
        resource_jars = bundled_deps,
        visibility = visibility,
        **kwargs
    )

  singlejar(
      name = name,
      jar_name = jar_name,
      runtime_deps = deps + ["//tools/base/bazel:kotlin-runtime"],
      jars = [":lib" + target + ".jar" for target in targets],
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


def kotlin_test(name, srcs, deps=[], runtime_deps=[], visibility=None, **kwargs):
  kotlin_library(
    name = name + ".testlib",
    srcs = srcs,
    deps = deps,
    runtime_deps = runtime_deps,
    jar_name = name + ".jar",
    visibility = visibility,
  )

  native.java_test(
      name = name + ".test",
      runtime_deps = [
          ":" + name + ".testlib",
      ],
      **kwargs
  )

  native.test_suite(
      name = name,
      tests = [name + ".test"],
  )
