def _maven_artifact_impl(ctx):
  direct_deps = set()
  transitive = set([ctx.outputs.pom])
  jars = set([jar.class_jar for jar in ctx.attr.artifact.java.outputs.jars])
  packages = {ctx.outputs.pom : jars}

  for label in ctx.attr.deps:
    direct_deps = direct_deps | [label.maven.pom]
    transitive = transitive | label.maven.transitive_deps
    jars = jars | label.maven.transitive_jars
    packages += label.maven.packages


  ctx.action(
      mnemonic = "GenPom",
      inputs = list(direct_deps),
      outputs = [ctx.outputs.pom],
      arguments = [ctx.outputs.pom.path, ctx.attr.info] + [dep.path for dep in list(direct_deps)],
      executable = ctx.executable._pom,
  )
  return struct(maven = struct(
    pom = ctx.outputs.pom,
    transitive_deps = transitive,
    transitive_jars = jars,
    packages = packages,
  ))


maven_artifact = rule(
   implementation = _maven_artifact_impl,
   attrs = {
     "deps": attr.label_list(),
     "artifact": attr.label(
          allow_files = True
      ),
     "info" : attr.string(),
     "_pom": attr.label(
         executable = True,
         cfg = "host",
         default = Label("//tools/base/bazel:pom_modifier"),
         allow_files = True),
   },
   outputs = {
     "pom": "%{name}.pom",
   },
)

# A java library that can be used in a maven_repo rule.
#
# Usage:
# maven_java_library(
#     name = "name",
#     deps = A list of maven_java_library or maven_java_import dependencies
#     # all java_library attriutes
#     info = A maven coordinate for this artifact as a string
# )
def maven_java_library(name, deps=[], maven_info=None, **kwargs):
  native.java_library(
    name = name,
    deps = deps,
    **kwargs
  )

  maven_artifact(
    name = name + "_maven",
    deps = [dep + "_maven" for dep in deps],
    artifact = name,
    info = maven_info,
  )

def _maven_repo_impl(ctx):
  inputs = []
  for artifact in ctx.attr.artifacts:
    for pom, jars in artifact.maven.packages.items():
      inputs += [pom] + list(jars)

  # Execute the command
  ctx.action(
    inputs = inputs,
    outputs = [ctx.outputs.repo],
    mnemonic = "mavenrepo",
    executable = ctx.executable._repo,
    arguments = [ctx.outputs.repo.path] + [file.path for file in inputs],
  )

_maven_repo = rule(
   implementation = _maven_repo_impl,
   attrs = {
       "artifacts" : attr.label_list(),
        "_repo": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:repo_builder"),
            allow_files = True),
   },
   outputs = {
     "repo": "%{name}_repo.zip",
   },
)

# Creates a maven repo with the given artifacts and all their transitive
# dependencies.
#
# Usage:
# maven_repo(
#     name = The name of the rule. The output of the rule will be ${name}.zip.
#     artifacts = A list of all maven_java_libraries to add to the repo.
# )
def maven_repo(artifacts=[], **kwargs):
    _maven_repo(
      artifacts = [artifact + "_maven" for artifact in artifacts],
      **kwargs
    )