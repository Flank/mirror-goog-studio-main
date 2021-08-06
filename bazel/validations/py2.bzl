"""Aspect to validate that python targets do not run with Python 2.

When bazel is invoked with this aspect attached, it validates that
py_binary and py_test targets are not set to run with Python 2.
"""

FAILURE_MESSAGE = """Target {} has python_version set to PY2.
Python 2 is deprecated and should no longer be used, please switch to Python 3 instead."""

def _no_py2_targets_impl(target, ctx):
    if ctx.rule.kind in ["py_binary", "py_test"]:
        if ctx.rule.attr.python_version == "PY2":
            fail(FAILURE_MESSAGE.format(str(ctx.label)))
    return []

no_py2_targets = aspect(
    implementation = _no_py2_targets_impl,
    attr_aspects = [],
)
