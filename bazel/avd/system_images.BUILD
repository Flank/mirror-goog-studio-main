# BUILD file referenced by the system-image http_archive rule in the top level
# WORKSPACE file.

filegroup(
    name = "x86-android-28-images",
    srcs = [
        "kernel-ranchu-64",
        "ramdisk.img",
        "data/misc/apns/apns-conf.xml",
        "data/misc/wifi/WifiConfigStore.xml",
        "encryptionkey.img",
        "system.img",
        "source.properties",
        "vendor.img",
        "userdata.img",
        "build.prop",
        "advancedFeatures.ini",
        "NOTICE.txt",
    ],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "x86_64-android-29-images",
    srcs = [
        "kernel-ranchu",
        "ramdisk.img",
        "data/misc/apns/apns-conf.xml",
        "data/misc/wifi/WifiConfigStore.xml",
        "encryptionkey.img",
        "system.img",
        "source.properties",
        "vendor.img",
        "userdata.img",
        "build.prop",
        "advancedFeatures.ini",
        "NOTICE.txt",
        "VerifiedBootParams.textproto",
    ],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "x86_64-android-TiramisuPrivacySandbox-images",
    srcs = [
        "advancedFeatures.ini",
        "build.prop",
        "data/local.prop",
        "data/misc/apns/apns-conf.xml",
        "data/misc/gceconfigs/gpu.config",
        "data/misc/emulator/version.txt",
        "data/misc/emulator/config/radioconfig.xml",
        "data/misc/modem_simulator/iccprofile_for_sim0.xml",
        "data/misc/modem_simulator/iccprofile_for_carrierapitests.xml",
        "data/misc/modem_simulator/etc/modem_simulator/files/numeric_operator.xml",
        "encryptionkey.img",
        "kernel-ranchu",
        "NOTICE.txt",
        "ramdisk.img",
        "source.properties",
        "system.img",
        "userdata.img",
        "vendor.img",
        "VerifiedBootParams.textproto",
    ],
    visibility = ["//visibility:public"],
)
