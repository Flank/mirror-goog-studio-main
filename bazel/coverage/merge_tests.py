"""Flattens LCOV across tests

- stdin: Input LCOV tracefile
- stdout: LCOV tracefile with tests merged (each file appears only once)
"""
import sys

def main():
    lcov_in = {} # map[test][file][line] = covered
    current_file = None
    current_test = None
    for line in sys.stdin:
        line = line.strip()
        if line[:3] == "TN:":
            current_test = line[3:]
            if current_test not in lcov_in:
                lcov_in[current_test] = {}
        elif line[:3] == "SF:":
            current_file = line[3:]
            lcov_in[current_test][current_file] = {}
        elif line[:3] == "DA:":
            [num, hit] = line[3:].split(",")
            lcov_in[current_test][current_file][int(num)] = hit != "0" # convert to bool
        else:
            pass

    lcov_out = {} # map[file][line] = covered
    for t in lcov_in:
        for f in lcov_in[t]:
            if f not in lcov_out:
                lcov_out[f] = {}
            for l in lcov_in[t][f]:
                if l in lcov_out[f]:
                    lcov_out[f][l] |= lcov_in[t][f][l]
                else:
                    lcov_out[f][l] = lcov_in[t][f][l]

    for f in sorted(lcov_out):
        sys.stdout.write('SF:{}\n'.format(f))
        for l in sorted(lcov_out[f]):
            sys.stdout.write('DA:{},{}\n'.format(l, int(lcov_out[f][l])))
        sys.stdout.write('end_of_record\n')

if __name__ == '__main__':
    main()
