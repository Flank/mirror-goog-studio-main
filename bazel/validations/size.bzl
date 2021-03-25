"""Aspect that provides the data_size output group.

The data_size output group contains a file, ${targetLabel}.datasize.txt,
which lists disk usage in KB and label for each data dependency. These files
are only included in bazel output for test rules, since tests are typically
the largest offenders of including too much runtime data.

If a target depends on 10 or more input targets (not a bazel rule dependency),
those inputs are aggregated into a single line item.
"""

DiskUsageInfo = provider(
    fields = {
        "size_file": "A file containing disk usage in kilobytes",
    },
)

def _calculate_disk_usage(ctx, label, files, out):
    """ Calculates the disk usage (in KB) and writes to out. """
    ctx.actions.run_shell(
        outputs = [out],
        tools = files,
        progress_message = "calculating disk usage of %s" % label,
        execution_requirements = {"no-remote-exec": "1"},
        mnemonic = "DuDataDepSize",
        # This command writes: $targetLabel\n$diskUsage
        # $diskUsage =
        # * du -L --max-depth=0 : Calculate disk usage for target files (-L follows symlinks)
        # * cut -f 1            : Take only the first column from `du` (disk usage)
        command = "echo -e \"%s\n$(du -L --max-depth=0 | cut -f 1)\" > '%s'" % (label, out.path),
    )
    pass

def _data_size_impl(target, ctx):
    out_file = ctx.actions.declare_file("%s.datasize.txt" % target.label.name)

    if hasattr(ctx.rule.attr, "data") and ctx.rule.attr.data:
        # Collect all .datasize files from data dependencies
        size_files = []

        # If there are 10 or more file input targets, group them into a single
        # .disksize file. This is to avoid the DuDataDepSummary failing from
        # having too long of an argument list.
        num_file_targets = len([d for d in ctx.rule.attr.data if DiskUsageInfo not in d])
        group_input_files = num_file_targets > 9
        input_file_targets = []

        for data in ctx.rule.attr.data:
            if DiskUsageInfo in data:
                size_files.append(data[DiskUsageInfo].size_file)
            elif group_input_files:
                input_file_targets.append(data)
            else:
                # If we get an 'input file target' as a data dependency,
                # calculate the disk usage for that input file. Input file
                # targets point to a file in a bazel package, and do not
                # belong to any rule. Because of this, the DiskUsageInfo
                # provider is not available.
                size_file = ctx.actions.declare_file(
                    "%s-%s.datasize.txt" % (target.label.name, data.label.name),
                )
                _calculate_disk_usage(ctx, data.label, data.files, size_file)
                size_files.append(size_file)

        if input_file_targets:
            # calculate disk usage for grouped input file targets
            input_depset = depset(transitive = [target.files for target in input_file_targets])
            size_file = ctx.actions.declare_file("%s-grouped-inputs.datasize.txt" % target.label.name)
            label = "(%s grouped input file targets)" % len(input_file_targets)
            _calculate_disk_usage(ctx, label, input_depset, size_file)
            size_files.append(size_file)

        # Aggregate .datasize files into a new .datasize summary for the
        # current target. The output of the new .datasize file is:
        #   $targetLabel
        #   $diskUsage1 $dataTargetLabel1
        #   $diskUsage2 $dataTargetLabel2
        #   ...
        ctx.actions.run_shell(
            outputs = [out_file],
            tools = size_files,
            progress_message = "aggregating disk usage for %s" % target.label,
            execution_requirements = {"no-remote-exec": "1"},
            mnemonic = "DuDataDepSummary",
            # Write $targetLabel, then for each .datasize source input, calculate and
            # append $totalDiskUsage\t$dataTargetLabel to the output file.
            command = "{} && for f in $SRCS; do echo -e \"$({})\t$({})\" >> '{}'; done".format(
                # Write the target label
                "echo '%s' > '%s'" % (target.label, out_file.path),
                # $totalDiskUsage =
                # * tail -n +2 $f : Strip the first line from .datasize file (target label)
                # * cut -f 1      : Select the first column values (disk size in KB)
                # * paste -s -d+  : Merge the rows using '+' as a delimiter ($row1 + $row2)
                # * bc            : Calculate the expression (sum of all rows)
                "tail -n +2 $f | cut -f 1 | paste -s -d+ | bc",
                # $dataTargetLabel =
                # * head -n1 $f   : Take the first line from .datasize file (target label)
                "head -n1 $f",
                out_file.path,
            ),
            env = {
                "SRCS": " ".join([f.path for f in size_files]),
            },
        )
    else:
        _calculate_disk_usage(ctx, target.label, target.files, out_file)

    providers = [DiskUsageInfo(size_file = out_file)]

    # Only include .datasize files in bazel output for test rules
    if ctx.rule.kind.endswith("_test"):
        providers.append(OutputGroupInfo(data_size = depset([out_file])))
    return providers

data_size = aspect(
    implementation = _data_size_impl,
    doc = "Calculates the the disk usage (in kilobytes) for data deps",
    # Some rules may depend on a predeclared output, instead of the rule that
    # generates the output. Because Aspects do not apply to output files, we
    # do not have access to the DiskUsageInfo provider. Instead of this aspect
    # looking at output file targets, it will look at the generating rule.
    # e.g., //tools/data-binding:data_binding_runtime.zip output file
    # will apply to //tools/data-binding:runtimeLibraries.
    apply_to_generating_rules = True,
    provides = [DiskUsageInfo],
    attr_aspects = ["data"],
)
"""Calculates the disk size used by a target's data dependencies.

The data_size output group will contain a .disksize file, per target.
For targets with 'data', this file will be a list of disk usage (in KB) and
label for each 'data' dependency. Targets without any data dependencies
will only contain their disk usage. The .disksize file will always contain
the label of the target it represents on the first line.
"""
