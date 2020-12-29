## Users should never need to load this file outside the toplevel WORKSPACE.
## It sets up a bazel repo in bazel-testlogs and constructs a build graph for
## generating test target Jacoco execfiles from test coverage data outputs.
## Those execfiles will eventually be used by coverage report rules in @cov//

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

def extract_exec_files(path):
    native.genrule(
        name = "{}.JacocoExec".format(path),
        srcs = ["{}/test.outputs/outputs.zip".format(path)],
        outs = ["{}/jacoco.exec".format(path)],
        visibility = ["@cov//:__pkg__"],
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
            visibility = ["@cov//:__pkg__"],
            cmd = "$(location {cli}) merge --quiet $(SRCS) --destfile $@".format(cli = jacoco_cli),
        )
    else:  # unsharded test
        extract_exec_files(test)

def test_target_pipeline(test, shards):
    jacoco_exec_file(test, shards)

def construct_result_processing_graph():
    ts = test_shard_split_dict()
    for k in ts:
        if type(ts[k]) == "dict":
            for s in ts[k]:
                test_target_pipeline("{}__{}".format(k, s), ts[k][s])
            native.genrule(
                name = "{}.JacocoExec".format(k),
                tools = [jacoco_cli],
                srcs = ["{}__{}.JacocoExec".format(k, s) for s in ts[k]],
                outs = ["{}/jacoco.exec".format(k)],
                visibility = ["@cov//:__pkg__"],
                cmd = "$(location {cli}) merge --quiet $(SRCS) --destfile $@".format(cli = jacoco_cli),
            )
        else:
            test_target_pipeline(k, ts[k])
