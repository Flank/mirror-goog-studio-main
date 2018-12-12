import os
import xml.etree.ElementTree as ET
import itertools as IT

def main():
  # directory subtrees omitted from consideration
  skip = [
      './tools/base/sdk-common/src/test', # actually a test directory that wasn't filtered
  ]
  print 'create a filename -> dirpath map of ./tools'
  # we're using a filename -> directory path because it makes resolving to
  # sourcefiles very fast
  filetree = {}
  # walk down the source file tree
  for (dirpath, _, filenames) in os.walk('./tools'):
    # ignore skipped directories
    if any(IT.imap(lambda sp: dirpath.startswith(sp), skip)):
      continue
    # otherwise add files to the map
    for name in filenames:
      if name in filetree:
        filetree[name].add(dirpath)
      else:
        filetree[name] = set([dirpath])

  print 'parse jacoco xml report'
  root = ET.parse('./out/agent-coverage/tools/base/coverage_report/report.xml').getroot()

  print 'resolve report package/file combos to directory paths'
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
        matches = list(IT.ifilter(lambda path: path.endswith(pkg_name), filetree[sfile_name]))
        if matches:
          # the directory path upto the package portion
          path = matches[0][0:matches[0].find(pkg_name)]
          # the list of instrumented line numbers
          instrumented = [int(line.get('nr')) for line in sfile.iter('line')]
          # the set of covered line numbers
          covered = set(int(line.get('nr')) for line in sfile.iter('line') if line.get('ci') != '0')
          if pkg_name not in data:
            data[pkg_name] = {}
          data[pkg_name][sfile_name] = {
              'path' : path,
              'instrumented' : instrumented,
              'covered' : covered,
          }

  print 'write to lcov file'
  lcov = open('./out/lcov', 'w')
  for pkg_name in data:
    for sfile_name in data[pkg_name]:
      filepath = os.path.join(data[pkg_name][sfile_name]['path'], pkg_name, sfile_name)
      lcov.write('SF:{}\n'.format(filepath))
      for line_num in data[pkg_name][sfile_name]['instrumented']:
        # format is DA:{line number},{number of hits}, but we don't care about
        # detailed hit numbers so just use 1 for covered lines
        lcov.write('DA:{},{}\n'.format(line_num, int(line_num in data[pkg_name][sfile_name]['covered'])))
      lcov.write('end_of_record\n')

  print 'aggregate package level coverage'
  # we need to aggregate data to the package level for reports
  pkg_cov = {}
  for pkg in data:
    inst = 0
    cov = 0
    paths = set()
    for sfile in data[pkg]:
      inst += len(data[pkg][sfile]['instrumented'])
      cov += len(data[pkg][sfile]['covered'])
      paths.add(data[pkg][sfile]['path'])
    pkg_cov[pkg] = {
        'instrumented' : inst,
        'covered' : cov,
        'paths' : list(paths),
    }

  print 'write worst (most uncovered lines) report'
  # packages + classes by uncovered lines
  worst = open('./out/worst', 'w')
  # packages only
  worstNoFiles = open('./out/worstNoFiles', 'w')
  worst.write('uncovered lines - cov% : (package @ [paths])|(file @ path)\n')
  worst.write('path to file = path/package/file rooted at WORKSPACE\n')
  worst.write('NB: omits files and packages with zero uncovered lines\n')
  worstNoFiles.write('uncovered lines - cov% : package @ [paths]\n')
  worstNoFiles.write('path to package = path/package rooted at WORKSPACE\n')
  worstNoFiles.write('NB: omits packages with zero uncovered lines\n')
  for pkg in sorted(pkg_cov, key=lambda p: pkg_cov[p]['instrumented'] - pkg_cov[p]['covered'], reverse=True):
    cov = pkg_cov[pkg]['covered']
    inst = pkg_cov[pkg]['instrumented']
    if inst - cov == 0: # no reason to list fully covered packages
      continue
    percent = round(100 * float(cov) / float(inst), 1)
    worst.write('{} - {}% : {} @ {}\n'.format(inst - cov, percent, pkg, pkg_cov[pkg]['paths']))
    worstNoFiles.write('{} - {}% : {} @ {}\n'.format(inst - cov, percent, pkg, pkg_cov[pkg]['paths']))
    for sfile in sorted(data[pkg], key=lambda f: len(data[pkg][f]['instrumented']) - len(data[pkg][f]['covered']), reverse=True):
      cov = len(data[pkg][sfile]['covered'])
      inst = len(data[pkg][sfile]['instrumented'])
      if inst - cov == 0:
        continue
      percent = round(100 * float(cov) / float(inst), 1)
      worst.write('    {} - {}% : {} @ {}\n'.format(inst - cov, percent, sfile, data[pkg][sfile]['path']))

if __name__ == '__main__':
  main()
