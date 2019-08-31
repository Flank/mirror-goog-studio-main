"""Generate test/file list of a component
This script parses a lcov and generate a list of
test and files for the given component.

lcov format uses constants defined below
DA - line number
TN - test name
SF - source file
CN - component name
end_of_record - end of record for a file

- Argument is the name of component
- Input lcov for the component
- Output test/file list goes to stdout
"""
import sys

def main():
    test_list = set()
    file_list = set()
    for line in sys.stdin:
        line = line.strip()
        if line[:3] == "DA:" or line == "end_of_record":
            continue
        elif line[:3] == "TN:":
            if line[3:] not in test_list:
                test_list.add(line[3:])
        elif line[:3] == "SF:":
            if line[3:] not in file_list:
                file_list.add(line[3:])

    sys.stdout.write('CN:{}\n'.format(sys.argv[1]))
    for test in sorted(test_list):
        sys.stdout.write('TN:{}\n'.format(test))

    for fn in sorted(file_list):
        sys.stdout.write('SF:{}\n'.format(fn))

    sys.stdout.write('end_of_record\n')

if __name__ == '__main__':
    main()
