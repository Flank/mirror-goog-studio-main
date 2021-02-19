def xsd_to_java(name, package, xsd, src_location, episode = None, binding_deps = [], custom_binding = None, catalog = None, deps = []):
    realepisode = episode if episode else name + "-noepisode"
    srcs = ["//tools/base/repository:root", "//tools/base/repository:src/main/resources/xsd/global.xjb", xsd] + binding_deps + deps
    cmd = "$(location //tools/base/repository:xjc)"
    if catalog:
        catalog_location = "$(location " + catalog + ")"
        srcs += [catalog]
        cmd += " -catalog " + catalog_location
    cmd += " -episode " + "$(location " + realepisode + ")"
    if custom_binding:
        cmd += " -b $(location " + custom_binding + ")"
        srcs += [custom_binding]
    for binding in binding_deps:
        cmd += " -b $(execpath " + binding + ")"
    cmd += " -p " + package
    cmd += " -extension -Xandroid-inheritance"
    cmd += " -b tools/base/repository/src/main/resources/xsd/global.xjb -no-header"
    cmd += " -d `cat $(location //tools/base/repository:root)`/" + src_location
    cmd += " $(rootpath " + xsd + ")"
    for dep in deps:
        cmd += " -cp $(location " + dep + ")"
    outs = [realepisode]

    native.genrule(
        visibility = ["//visibility:public"],
        local = 1,
        srcs = srcs,
        name = name,
        outs = outs,
        cmd = cmd,
        tools = ["//tools/base/repository:xjc"],
        tags = ["manual"],
    )
