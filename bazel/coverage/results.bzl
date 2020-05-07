## Users should never need to load this file outside the toplevel WORKSPACE.
## It sets up a bazel repo in bazel-testlogs and constructs a build graph for
## generating test target lcov tracefiles from test coverage data outputs.
## Those tracefiles will eventually be used by coverage report rules in @cov//

# Create the bazel repo in bazel-testlogs
# Its BUILD file only constructs the result processing graph
def setup_testlogs_loop_repo():
    native.new_local_repository(
        name = "results",
        path = "bazel-testlogs",
        build_file_content = """
load("@cov//:results.bzl", "construct_result_processing_graph")
construct_result_processing_graph()
""",
    )

jacoco_cli = "@//prebuilts/tools/common/jacoco:cli"

# Reconstruct test, split, and shard information from filepaths
# Unsharded, unsplit tests look like /<test> and reported as ret[test] = None
# Sharded, unsplit tests look like <test>/shard_X_of_Y and reported as ret[test] = Y
# Unsharded test splits look like <test>__<split> and reported as ret[test][split] = None
# Sharded test splits look like <test>__<split>/shard_X_of_Y and reported as ret[test][split] = Y
def test_shard_split_dict():
    ret = {}
    for path in native.glob(["**/test.outputs/outputs.zip"]):
        segs = path.split("/")[:-2]  # strip /test.outputs/outputs.zip
        if "shard" in segs[-1]:  # last segment has form 'shard_X_of_Y'
            shard_count = int(segs[-1].split("_")[3])  # extract Y
            if "__" in segs[-2]:  # this is a split test
                test_name, split_name = segs[-2].split("__")
                k = "/".join(segs[:-2]) + "/" + test_name
                if k not in ret:
                    ret[k] = {}
                ret[k][split_name] = shard_count
            else:
                k = "/".join(segs[:-1])  # key is the test name (i.e. sans shard info)
                ret[k] = shard_count
        elif "__" in segs[-1]:  # this is a split test
            test_name, split_name = segs[-1].split("__")
            k = "/".join(segs[:-1]) + "/" + test_name
            if k not in ret:
                ret[k] = {}
            ret[k][split_name] = None
        else:
            k = "/".join(segs)  # key is the test name
            ret[k] = None
    return ret

def coverage_class_jar(test):
    target_formatted = ":".join(test.rsplit("/", 1))
    incl = [
        "com/android/*.class",
        "org/jetbrains/android/*.class",
        "org/jetbrains/kotlin/android/*.class",
    ]
    excl = [
        "com/android/aapt/*.class",
        "com/android/i18n/*.class",
        "com/android/internal/*.class",
        "com/android/tools/r8/*.class",
    ]
    native.genrule(
        name = "{}.CovClsJar".format(test),
        srcs = ["@//{}_deploy.jar".format(target_formatted)],
        outs = ["{}/coverage.jar".format(test)],
        # zip -U copies from one zip to another
        # We use it to extract the classes we care about for coverage
        cmd = "zip -q -U $< --out $@ {incl} -x {excl}".format(
            incl = " ".join(incl),
            excl = " ".join(excl),
        ),
    )

def extract_exec_files(path):
    native.genrule(
        name = "{}.JacocoExec".format(path),
        srcs = ["{}/test.outputs/outputs.zip".format(path)],
        outs = ["{}/jacoco.exec".format(path)],
        # Unzipping multiple .exec files to a pipe is equivalent
        # to unzipping each and then using jacoco to merge them.
        # This method allows us to blindly support tests that produce
        # multiple exec files (e.g. gradle integration tests).
        cmd = "unzip -p $< *.exec >$@",
    )

def jacoco_exec_file(test, shards):
    if shards:
        for s in range(1, shards + 1):
            extract_exec_files("{}/shard_{}_of_{}".format(test, s, shards))

        # merge the shard execs into a single exec
        native.genrule(
            name = "{}.JacocoExec".format(test),
            tools = [jacoco_cli],
            srcs = ["{}/shard_{}_of_{}.JacocoExec".format(test, s, shards) for s in range(1, shards + 1)],
            outs = ["{}/jacoco.exec".format(test)],
            cmd = "$(location {cli}) merge --quiet $(SRCS) --destfile $@".format(cli = jacoco_cli),
        )
    else:  # unsharded test
        extract_exec_files(test)

def jacoco_xml_report(test):
    native.genrule(
        name = "{}.JacocoXML".format(test),
        tools = [jacoco_cli],
        srcs = [
            "{}.JacocoExec".format(test),
            "{}.CovClsJar".format(test),
        ],
        outs = ["{}/jacoco.xml".format(test)],
        cmd = "$(location {cli}) report --quiet $(location {exc}) --classfiles $(location {jar}) --xml $@".format(
            cli = jacoco_cli,
            exc = "{}.JacocoExec".format(test),
            jar = "{}.CovClsJar".format(test),
        ),
    )

def lcov_tracefile(test):
    native.genrule(
        name = "{}.LCOVTracefile".format(test),
        tools = ["@cov//:jacoco_xml_to_lcov"],
        srcs = ["{}.JacocoXML".format(test)],
        outs = ["{}/lcov".format(test)],
        local = True,  # jacoco_xml_to_lcov walks the filesystem
        visibility = ["@cov//:__pkg__"],
        cmd = "python $(location @cov//:jacoco_xml_to_lcov) {} <$< >$@".format(test),
    )

def test_target_pipeline(test, shards):
    coverage_class_jar(test)
    jacoco_exec_file(test, shards)
    jacoco_xml_report(test)
    lcov_tracefile(test)

def construct_result_processing_graph():
    ts = test_shard_split_dict()
    for k in ts:
        if type(ts[k]) == "dict":
            for s in ts[k]:
                test_target_pipeline("{}__{}".format(k, s), ts[k][s])
            native.genrule(
                name = "{}.LCOVTracefile".format(k),
                tools = ["@cov//:merge_lcov"],
                srcs = ["{}__{}.LCOVTracefile".format(k, s) for s in ts[k]],
                outs = ["{}/lcov".format(k)],
                visibility = ["@cov//:__pkg__"],
                cmd = "python $(location @cov//:merge_lcov) $(SRCS) >$@",
            )
        else:
            test_target_pipeline(k, ts[k])
