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
                sf = record.split("SF:")[1].split("\n")[0]
                if sf not in data:
                    data[sf] = {}
                for line in record.split("\n"):
                    if line.startswith("DA:"):
                        [num, hit] = line[3:].split(",")
                        num = int(num)
                        hit = hit != "0" # convert to boolean
                        if num not in data[sf]:
                            data[sf][num] = hit
                        else:
                            data[sf][num] |= hit
    for f in sorted(data):
        sys.stdout.write('SF:{}\n'.format(f))
        for l in sorted(data[f]):
            sys.stdout.write('DA:{},{}\n'.format(l, int(data[f][l])))
        sys.stdout.write('end_of_record\n')

if __name__ == '__main__':
    main()
