"""Converts Jacoco XMl to LCOV.

- Resolves classes to sourcefiles
- Identifies classes without sourcefiles
- Identifies uninstrumented sourcefiles
- Reports packages and classes with most uncovered lines
"""
import itertools as IT
import os
import xml.etree.ElementTree as ET


def main():
  # directory subtrees omitted from consideration
  skip = [
      # tests
      './tools/adt/idea/adt-ui-model/testSrc',
      './tools/adt/idea/adt-ui/src/test',
      './tools/adt/idea/android-adb/testSrc',
      './tools/adt/idea/android-common/test',
      './tools/adt/idea/android-debuggers/testSrc',
      './tools/adt/idea/android-kotlin/android-extensions-idea/testData',
      './tools/adt/idea/android-kotlin/android-extensions-idea/tests',
      './tools/adt/idea/android-kotlin/android-extensions-jps/testData',
      './tools/adt/idea/android-kotlin/idea-android/testData',
      './tools/adt/idea/android-kotlin/idea-android/tests',
      './tools/adt/idea/android-lang-databinding/testSrc',
      './tools/adt/idea/android-lang/testSrc',
      './tools/adt/idea/android-layout-inspector/testSrc',
      './tools/adt/idea/android-test-framework/testSrc',
      './tools/adt/idea/android-uitests/testData',
      './tools/adt/idea/android-uitests/testSrc',
      './tools/adt/idea/android/testData',
      './tools/adt/idea/android/testSrc',
      './tools/adt/idea/apkanalyzer/testSrc',
      './tools/adt/idea/assistant/testSrc',
      './tools/adt/idea/build-common/testSrc',
      './tools/adt/idea/connection-assistant/testSrc',
      './tools/adt/idea/databinding/testData',
      './tools/adt/idea/databinding/testSrc',
      './tools/adt/idea/deploy/testSrc',
      './tools/adt/idea/designer/testData',
      './tools/adt/idea/designer/testSrc',
      './tools/adt/idea/jps-plugin/testData',
      './tools/adt/idea/jps-plugin/testSrc',
      './tools/adt/idea/kotlin-integration/testData',
      './tools/adt/idea/kotlin-integration/testSrc',
      './tools/adt/idea/native-symbolizer/testSrc',
      './tools/adt/idea/observable-ui/testSrc',
      './tools/adt/idea/observable/testSrc',
      './tools/adt/idea/transport-database/testSrc',
      './tools/adt/idea/profilers-android/testSrc',
      './tools/adt/idea/profilers-ui/testSrc',
      './tools/adt/idea/profilers/testSrc',
      './tools/adt/idea/project-system-gradle/testSrc',
      './tools/adt/idea/project-system/testSrc',
      './tools/adt/idea/sdk-updates/testSrc',
      './tools/adt/idea/smali/testSrc',
      './tools/adt/idea/swingp/testSrc',
      './tools/adt/idea/uitest-framework-bazel/testSrc',
      './tools/adt/idea/uitest-framework-gradle/testSrc',
      './tools/adt/idea/uitest-framework/testSrc',
      './tools/adt/idea/whats-new-assistant/testSrc',
      './tools/adt/idea/wizard-model/testSrc',
      './tools/analytics-library/crash/src/test',
      './tools/analytics-library/publisher/src/test',
      './tools/analytics-library/shared/src/test',
      './tools/analytics-library/testing/src/test',
      './tools/analytics-library/tracker/src/test',
      './tools/apksig/src/test',
      './tools/apkzlib/src/test',
      './tools/base/apkparser/analyzer/src/test',
      './tools/base/apkparser/binary-resources/src/test',
      './tools/base/apkparser/cli/src/test',
      './tools/base/bazel/test',
      './tools/base/bazel/testSrc',
      './tools/base/build-system/builder-model/src/test',
      './tools/base/build-system/builder-test-api/src/test',
      './tools/base/build-system/builder/src/test',
      './tools/base/build-system/gradle-api/src/test',
      './tools/base/build-system/gradle-core/src/test',
      './tools/base/build-system/gradle-experimental/src/test',
      './tools/base/build-system/integration-test/application/src/test',
      './tools/base/build-system/integration-test/databinding/src/test',
      './tools/base/build-system/integration-test/framework/src/test',
      './tools/base/build-system/integration-test/java-library-model-builder/src/test',
      './tools/base/build-system/integration-test/test-projects',
      './tools/base/build-system/java-lib-plugin/java-lib-model-builder/src/test',
      './tools/base/build-system/manifest-merger/src/test',
      './tools/base/build-system/profile/src/test',
      './tools/base/common/src/test',
      './tools/base/ddmlib/src/test',
      './tools/base/deploy/deployer/src/test',
      './tools/base/deploy/test',
      './tools/base/devicelib/src/test',
      './tools/base/draw9patch/src/test',
      './tools/base/fakeadbserver/src/test',
      './tools/base/flags/src/test',
      './tools/base/instant-run/instant-run-client/src/test',
      './tools/base/instant-run/instant-run-runtime/src/test',
      './tools/base/layoutinspector/testSrc',
      './tools/base/layoutlib-api/sample/testproject',
      './tools/base/layoutlib-api/src/test',
      './tools/base/lint/libs/lint-gradle/src/test',
      './tools/base/lint/libs/lint-tests/src/test',
      './tools/base/ninepatch/src/test',
      './tools/base/perf-logger/src/test',
      './tools/base/perflib/src/test',
      './tools/base/pixelprobe/src/test',
      './tools/base/profiler/app/supportlib/src/test',
      './tools/base/profiler/integration-tests',
      './tools/base/profiler/tests',
      './tools/base/repository/src/test',
      './tools/base/sdk-common/src/test',
      './tools/base/sdklib/src/test',
      './tools/base/testutils/src/test',
      './tools/base/tracer/agent/testSrc',
      './tools/base/usb-devices/testSrc',
      './tools/base/vector-drawable-tool/src/test',
      './tools/buildSrc/src/test',
      './tools/data-binding/compilationTests/src/test',
      './tools/data-binding/compiler/src/test',
      './tools/data-binding/compilerCommon/src/test',
      './tools/data-binding/exec/src/test',
      './tools/data-binding/extensions-support/library/src/androidTest',
      './tools/data-binding/extensions/library/src/androidTest',
      './tools/data-binding/integration-tests-support',
      './tools/data-binding/integration-tests',
      './tools/data-binding/samples',
      './tools/dx/dalvik/dx/junit-tests',
      './tools/dx/dalvik/dx/tests',
      # generated
      './tools/adt/idea/android-lang-databinding/gen',
      './tools/adt/idea/android-lang/gen',
      './tools/adt/idea/artwork/gen',
      './tools/adt/idea/smali/gen',
      # external
      './tools/base/jobb',
      './tools/external',
      './tools/idea',
      './tools/sherpa',
      './tools/studio',
      './tools/swing-testing',
      './tools/swt',
      './tools/vendor',
  ]
  # file extensions we care about
  ends = [
      '.java',
      '.kt',
  ]
  print 'create a filename -> dirpath map of ./tools'
  # we're using a filename -> directory path because it makes resolving to
  # sourcefiles very fast
  filetree = {}
  pathset = set()
  # walk down the source file tree
  for (dirpath, _, filenames) in os.walk('./tools'):
    # ignore skipped directories
    if any(IT.imap(dirpath.startswith, skip)):
      continue
    # otherwise add files to the map
    for name in filenames:
      if not any(IT.imap(name.endswith, ends)):
        continue
      if name in filetree:
        filetree[name].add(dirpath)
      else:
        filetree[name] = set([dirpath])
      pathset.add(os.path.join(dirpath, name))

  print 'parse jacoco xml report'
  root = ET.parse(
      './out/agent-coverage/tools/base/coverage_report/report.xml').getroot()

  print 'resolve report package/file combos to directory paths'
  # we're going to build a nest map like package -> (file -> path/coverage info)
  # this makes it easy to do package or file level sorting for output
  data = {}
  matchedset = set()
  fakeset = set()
  for pkg in root.iter('package'):
    pkg_name = pkg.get('name')
    for sfile in pkg.iter('sourcefile'):
      sfile_name = sfile.get('name')
      if sfile_name in filetree:
        # if there's a path with the package/class as a suffix, then that's the
        # real sourcefile
        matches = list(
            IT.ifilter(lambda path: path.endswith(pkg_name),
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
          matchedset.add(os.path.join(path, pkg_name, sfile_name))
        else:
          fakeset.add(os.path.join(pkg_name, sfile_name))

  print 'compute unmatched real paths'
  unmatched = pathset - matchedset
  missing = open('./out/missing', 'w')
  for path in sorted(unmatched):
    missing.write('{}\n'.format(path))

  print 'write fake package/class list'
  fake = open('./out/fake', 'w')
  for path in sorted(fakeset):
    fake.write('{}\n'.format(path))

  print 'write to lcov file'
  lcov = open('./out/lcov', 'w')
  for pkg_name in data:
    for sfile_name in data[pkg_name]:
      filepath = os.path.join(data[pkg_name][sfile_name]['path'], pkg_name,
                              sfile_name)
      lcov.write('SF:{}\n'.format(filepath))
      for line_num in data[pkg_name][sfile_name]['instrumented']:
        # format is DA:{line number},{number of hits}, but we don't care about
        # detailed hit numbers so just use 1 for covered lines
        lcov.write('DA:{},{}\n'.format(
            line_num, int(line_num in data[pkg_name][sfile_name]['covered'])))
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
        'instrumented': inst,
        'covered': cov,
        'paths': list(paths),
    }

  print 'write worst (most uncovered lines) report'
  # packages + classes by uncovered lines
  worst = open('./out/worst', 'w')
  # packages only
  worst_no_files = open('./out/worstNoFiles', 'w')
  worst.write('uncovered lines - cov% : (package @ [paths])|(file @ path)\n')
  worst.write('path to file = path/package/file rooted at WORKSPACE\n')
  worst.write('NB: omits files and packages with zero uncovered lines\n')
  worst_no_files.write('uncovered lines - cov% : package @ [paths]\n')
  worst_no_files.write('path to package = path/package rooted at WORKSPACE\n')
  worst_no_files.write('NB: omits packages with zero uncovered lines\n')
  for pkg in sorted(
      pkg_cov,
      key=lambda p: pkg_cov[p]['instrumented'] - pkg_cov[p]['covered'],
      reverse=True):
    cov = pkg_cov[pkg]['covered']
    inst = pkg_cov[pkg]['instrumented']
    if inst - cov == 0:  # no reason to list fully covered packages
      continue
    percent = round(100 * float(cov) / float(inst), 1)
    worst.write('{} - {}% : {} @ {}\n'.format(inst - cov, percent, pkg,
                                              pkg_cov[pkg]['paths']))
    worst_no_files.write('{} - {}% : {} @ {}\n'.format(inst - cov, percent, pkg,
                                                       pkg_cov[pkg]['paths']))

    def uncovered_line_count(file_cov):
      return len(file_cov['instrumented']) - len(file_cov['covered'])

    for sfile in sorted(
        data[pkg],
        key=lambda f: uncovered_line_count(data[pkg][f]),
        reverse=True):
      cov = len(data[pkg][sfile]['covered'])
      inst = len(data[pkg][sfile]['instrumented'])
      if inst - cov == 0:
        continue
      percent = round(100 * float(cov) / float(inst), 1)
      worst.write('    {} - {}% : {} @ {}\n'.format(inst - cov, percent, sfile,
                                                    data[pkg][sfile]['path']))


if __name__ == '__main__':
  main()
