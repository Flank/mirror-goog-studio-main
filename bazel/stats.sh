readonly TARGETS="//tools/... + //prebuilts/studio/... + //prebuilts/tools/..."
readonly TESTS="tests(${TARGETS})"
readonly MANUAL="attr(\"tags\", \"manual\", ${TESTS})"
readonly ENABLED="${TESTS} except ${MANUAL}"
readonly DISABLED_ON_WINDOWS="attr(\"tags\", \"no(_test)?_windows\", ${ENABLED})"
readonly ENABLED_ON_WINDOWS="${ENABLED} except ${DISABLED_ON_WINDOWS}"

readonly DISABLED_ON_MAC="attr(\"tags\", \"no(_test)?_mac\", ${ENABLED})"
readonly ENABLED_ON_MAC="${ENABLED} except ${DISABLED_ON_MAC}"

readonly ENABLED_N=$(bazel query "${ENABLED}" | wc -l)
readonly ENABLED_ON_WINDOWS_N=$(bazel query "${ENABLED_ON_WINDOWS}" | wc -l)
readonly PERCENT_ON_WINDOWS=$((${ENABLED_ON_WINDOWS_N} * 100 / ${ENABLED_N}))
readonly ENABLED_ON_MAC_N=$(bazel query "${ENABLED_ON_MAC}" | wc -l)
readonly PERCENT_ON_MAC=$((${ENABLED_ON_MAC_N} * 100 / ${ENABLED_N}))

echo "Targets: $(bazel query "${TARGETS}" | wc -l)"
echo "Test targets: $(bazel query "${TESTS}" | wc -l)"
echo "Disabled tests: $(bazel query "${MANUAL}" | wc -l)"
echo "Enabled tests: ${ENABLED_N}"
echo "Enabled tests (on windows): ${ENABLED_ON_WINDOWS_N} (${PERCENT_ON_WINDOWS}%)"
echo "Enabled tests (on mac): ${ENABLED_ON_MAC_N} (${PERCENT_ON_MAC}%)"

for arg in $@; do
  if [ "$arg" = "--list_no_windows" ]; then
    # List of tests that don't run on windows
    bazel query "${DISABLED_ON_WINDOWS}"
  fi
done


