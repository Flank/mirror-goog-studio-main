load("//tools/base/transport/test-framework:transport_test.bzl", "transport_app")
load("//tools/base/bazel:utils.bzl", "flat_archive")

# Creates an APK to be deployed to fake android.
# In addition to dexing, this macro allows the inclusion of resources in the APK's META-INF, which
# is useful for testing certain App Inspection Service functionalities.
def app_inspection_app(name, srcs, resources = [], deps = []):
    transport_app(
        name = name + "_dexed",
        srcs = srcs,
        deps = deps,
    )

    # At this point, we have a jar file containing a classes.dex. We will need to unzip the dex and
    # rezip it with the provided resources.
    native.genrule(
        name = name + "_unzipped",
        srcs = [name + "_dexed.jar"],
        outs = [name + "_dexed/classes.dex"],
        tools = ["@bazel_tools//tools/zip:zipper"],
        cmd = "$(location @bazel_tools//tools/zip:zipper) x $< -d $(@D)",
    )

    mapping = {}
    for resource in resources:
        mapping[resource] = "META-INF"

    flat_archive(
        name = name,
        ext = "apk",
        deps = mapping,
        files = {name + "_dexed/classes.dex": "classes.dex"},
        visibility = ["//visibility:public"],
    )
