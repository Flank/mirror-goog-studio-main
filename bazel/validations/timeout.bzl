"""Aspect to validate that only the specified targets use eternal timeout.

When bazel is invoked with this aspect attached, it validates that only
the allowlisted targets below can use the eternal timeout.
"""
APPROVED_ETERNAL_TESTS = []

FAILURE_MESSAGE = """Test target {} has timeout set to eternal.
We do not want any new target with eternal timeout (b/162943254).
If this is intentional, contact android-devtools-infra@ to relax the restriction on the target."""

IGNORE_TAG = ["perfgate"]

def _has_intersect(this, other):
    for item in this:
        if item in other:
            return True
    return False

def _no_eternal_tests_impl(target, ctx):
    if ctx.rule.kind.endswith("_test"):
        if ctx.rule.attr.timeout == "eternal" and str(ctx.label) not in APPROVED_ETERNAL_TESTS:
            if not _has_intersect(IGNORE_TAG, ctx.rule.attr.tags):
                fail(FAILURE_MESSAGE.format(str(ctx.label)))
    return []

no_eternal_tests = aspect(
    implementation = _no_eternal_tests_impl,
    attr_aspects = [],
)
