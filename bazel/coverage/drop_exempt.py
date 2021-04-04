"""Drops exempt regions from an LCOV tracefile

- Arg 1: path to the exemption marker file
- Stdin: an LCOV tracefile
- Stdout: the LCOV tracefile with exempt regions dropped
"""
import sys

def main():
    exempt_marker_path = sys.argv[1]

    # parse exempt line markers from marker file
    isStartMark = {} # file -> line w/ marker -> is a start marker
    with open(exempt_marker_path) as fh:
        for l in fh:
            # lines look like: <file path>:<line num>:<@coverage marker>:<on|off followed by anything>
            [filePath, lineNum, _, tag] = l.strip().split(":", 3)
            if filePath not in isStartMark:
                isStartMark[filePath] = {}
            lineNum = int(lineNum)

            if tag.startswith("off"): # tries to open an exempt block
                isStartMark[filePath][lineNum] = True
            elif tag.startswith("on"): # tries to close an exempt block
                isStartMark[filePath][lineNum] = False
            else: # malformed so skip it
                continue

    # find well formed exempt ranges from markers
    exempt = {} # file -> [(start, end)]
    for f in isStartMark: # for each file
        exempt[f] = [] # init range list
        ls = sorted(isStartMark[f]) # get lines in order
        for (s,e) in zip(ls, ls[1:]): # and iterate over adjacent pairs
            # if it's a well formed range then record it
            if isStartMark[f][s] and not isStartMark[f][e]:
                exempt[f].append((s,e))

    # parse LCOV from stdin
    cov = {} # test -> file -> line -> covered
    curTest = None
    curFile = None
    for line in sys.stdin:
        line = line.strip()
        if line.startswith("TN:"): # test name
            curTest = line[3:]
            if curTest not in cov:
                cov[curTest] = {}
        elif line.startswith("SF:"): # source file
            curFile = line[3:]
            cov[curTest][curFile] = {}
        elif line.startswith("DA:"): # line data
            [num, hit] = line[3:].split(",")
            cov[curTest][curFile][int(num)] = hit != "0" # convert to bool
        else:
            pass

    # delete exempt line ranges from coverage
    for f in exempt:
        for (s,e) in exempt[f]:
            for l in range(s, e+1):
                for t in cov:
                    if f in cov[t] and l in cov[t][f]:
                        del cov[t][f][l]

    # write LCOV to stdout
    for t in sorted(cov):
        for f in sorted(cov[t]):
            sys.stdout.write('TN:{}\n'.format(t))
            sys.stdout.write('SF:{}\n'.format(f))
            for l in sorted(cov[t][f]):
                sys.stdout.write('DA:{},{}\n'.format(l, int(cov[t][f][l])))
            sys.stdout.write('end_of_record\n')

if __name__ == '__main__':
    main()
