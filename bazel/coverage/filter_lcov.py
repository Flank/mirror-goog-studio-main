"""Filters LCOV tracefiles

- Arguments are filepath prefixes to keep (or drop if prefixed with '-')
    - A path is kept if it matches at least one include and no excludes
    - If no includes are specified then everything not excluded is included
- Input tracefile comes from stdin
- Output tracefiles goes to stdout
"""
import sys

def read_lcov():
    file_line_cov = {} # map[test][file][line] = covered
    current_sf = None
    current_tn = None
    for line in sys.stdin:
        line = line.strip()
        if line[:3] == "TN:":
            current_tn = line[3:]
            if current_tn not in file_line_cov:
                file_line_cov[current_tn] = {}
        elif line[:3] == "SF:":
            current_sf = line[3:]
            file_line_cov[current_tn][current_sf] = {}
        elif line[:3] == "DA:":
            [num, hit] = line[3:].split(",")
            file_line_cov[current_tn][current_sf][int(num)] = hit != "0" # convert to bool
        else:
            pass

    return file_line_cov

def is_excluded(path, excludes):
    # if no excludes are specified then this is skipped and nothing is excluded
    for e in excludes:
        if path.startswith(e):
            # matched an excluded prefix so finished with excludes
            return True
    return False

def is_included(path, includes):
    for i in includes:
        if path.startswith(i):
            # matched an included prefix so finished with includes
            return True
    return False

def write_lcov(filtered_cov):
    for tn in sorted(filtered_cov):
        for filepath in sorted(filtered_cov[tn]):
            sys.stdout.write('TN:{}\n'.format(tn))
            sys.stdout.write('SF:{}\n'.format(filepath))
            for line in sorted(filtered_cov[tn][filepath]):
                sys.stdout.write('DA:{},{}\n'.format(line, int(filtered_cov[tn][filepath][line])))
            sys.stdout.write('end_of_record\n')


def main():
    prefixes = sys.argv[1:]
    includes = [x for x in prefixes if not x.startswith("-")]
    excludes = [x[1:] for x in prefixes if x.startswith("-")]

    file_line_cov = read_lcov()

    after_excludes = {}
    for tn in file_line_cov:
        after_excludes[tn] = {f: file_line_cov[tn][f] for f in file_line_cov[tn] if not is_excluded(f, excludes)}

    filtered = after_excludes # by default include everything
    if len(includes) > 0: # but if there are explicit includes then only include those
        filtered = {}
        for tn in after_excludes:
            filtered[tn] = {f: after_excludes[tn][f] for f in after_excludes[tn] if is_included(f, includes)}

    write_lcov(filtered)

if __name__ == '__main__':
    main()
