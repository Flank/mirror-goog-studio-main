"""Merges LCOV tracefiles

- Arguments are paths of input tracefiles
- Merged tracefile is written to stdout
"""
import sys

def main():
    inputs = sys.argv[1:]

    data = {}

    for path in inputs:
        with open(path) as fh:
            contents = fh.read()
            for record in contents.split("end_of_record\n"):
                if "SF:" not in record:
                    continue
                # path has a fix 38 character long prefix and a 5 character long
                # suffix to it, which is being sliced to get the test name
                # for example, if path is
                # bazel-genfiles/external/results/tools/adt/idea/adt-ui/intellij.android.adt.ui_tests/lcov
                # test name will be
                # tools/adt/idea/adt-ui/intellij.android.adt.ui_tests
                sys.stdout.write('TN:{}\n'.format((path[38:])[:-5]))
                sys.stdout.write(record)
                sys.stdout.write('end_of_record\n')

if __name__ == '__main__':
    main()
