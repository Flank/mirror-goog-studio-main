#!/bin/bash
declare -r test_input="tools/base/bazel/native/matryoshka/a.out"
rm ${TEST_TMPDIR}/*.out
cp $test_input ${TEST_TMPDIR}
cd ${TEST_TMPDIR}
./a.out a1.out || exit 1
chmod u+x a1.out || exit 2
./a1.out a2.out || exit 3

# We should only generate a1.out. There will be no a2.out extracted.
if [ -f a2.out ]; then exit 4; fi
echo "PASS"
