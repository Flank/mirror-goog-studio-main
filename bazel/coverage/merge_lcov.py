"""Merges LCOV tracefiles

- Arguments are paths of input tracefiles
- Merged tracefile is written to stdout
"""
import sys

def main():
    inputs = sys.argv[1:]

    data = {} # map[test][file][line] = covered

    for path in inputs:
        with open(path) as fh:
            contents = fh.read()
            tn = None
            sf = None
            for line in contents.split('\n'):
                line = line.strip()
                if line[:3] == "TN:":
                    tn = line[3:]
                    if tn not in data:
                        data[tn] = {}
                elif line[:3] == "SF:":
                    sf = line[3:]
                    if sf not in data[tn]:
                            data[tn][sf] = {}
                elif line[:3] == "DA:":
                    [num, hit] = line[3:].split(",")
                    num = int(num)
                    hit = hit != "0" # convert to bool
                    if num not in data[tn][sf]:
                        data[tn][sf][num] = hit
                    else:
                        data[tn][sf][num] |= hit

    for t in sorted(data):
        for s in sorted(data[t]):
            if len(data[t][s]) == 0: # skip files with no instrumented lines
                continue
            sys.stdout.write("TN:{}\n".format(t))
            sys.stdout.write("SF:{}\n".format(s))
            for l in sorted(data[t][s]):
                sys.stdout.write("DA:{},{}\n".format(l, int(data[t][s][l])))
            sys.stdout.write('end_of_record\n')

if __name__ == '__main__':
    main()
