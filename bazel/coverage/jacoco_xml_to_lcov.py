"""Converts Jacoco XML to LCOV.

- Reads Jacoco XML from stdin
- Resolves classes to sourcefiles
- Writes LCOV tracefile to stdout
"""
import os
import sys
import xml.etree.ElementTree as ET


def main():
  test_name = sys.argv[1]
  eligible_list_file = sys.argv[2]

  # we're using a filename -> directory path because it makes resolving to
  # sourcefiles very fast
  filetree = {}
  with open(eligible_list_file) as fh:
      for p in fh:
          p = p.strip()
          name = os.path.basename(p)
          dirpath = os.path.dirname(p)
          if name in filetree:
              filetree[name].add(dirpath)
          else:
              filetree[name] = set([dirpath])

  root = ET.parse(sys.stdin).getroot()

  # we're going to build a nest map like package -> (file -> path/coverage info)
  # this makes it easy to do package or file level sorting for output
  data = {}
  for pkg in root.iter('package'):
    pkg_name = pkg.get('name')
    for sfile in pkg.iter('sourcefile'):
      sfile_name = sfile.get('name')
      if sfile_name in filetree:
        # if there's a path with the package/class as a suffix, then that's the
        # real sourcefile
        matches = list(
            filter(lambda path: path.endswith(pkg_name),
                       filetree[sfile_name]))
        if matches:
          # the directory path upto the package portion
          path = matches[0][0:matches[0].find(pkg_name)]
          # the list of instrumented line numbers
          instrumented = [int(line.get('nr')) for line in sfile.iter('line')]
          # the set of covered line numbers
          covered = set(
              int(line.get('nr'))
              for line in sfile.iter('line')
              if line.get('ci') != '0')
          if pkg_name not in data:
            data[pkg_name] = {}
          data[pkg_name][sfile_name] = {
              'path': path,
              'instrumented': instrumented,
              'covered': covered,
          }

  for pkg_name in data:
    for sfile_name in data[pkg_name]:
      filepath = os.path.join(data[pkg_name][sfile_name]['path'], pkg_name,
                              sfile_name)
      sys.stdout.write('TN:{}\n'.format(test_name))
      sys.stdout.write('SF:{}\n'.format(filepath))
      for line_num in data[pkg_name][sfile_name]['instrumented']:
        # format is DA:{line number},{number of hits}, but we don't care about
        # detailed hit numbers so just use 1 for covered lines
        sys.stdout.write('DA:{},{}\n'.format(
            line_num, int(line_num in data[pkg_name][sfile_name]['covered'])))
      sys.stdout.write('end_of_record\n')

if __name__ == '__main__':
  main()
