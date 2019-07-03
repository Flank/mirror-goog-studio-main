"""Converts Jacoco XML to LCOV.

- Reads Jacoco XML from stdin
- Resolves classes to sourcefiles
- Writes LCOV tracefile to stdout
"""
import itertools as IT
import os
import sys
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
      './tools/base/build-system/integration-test/test-projects',
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
      './tools/vendor/google/android-apk/testData',
      './tools/vendor/google/android-apk/testSrc',
      './tools/vendor/google/android-ndk/testData',
      './tools/vendor/google/android-ndk/testSrc',
      # generated
      './tools/adt/idea/android-lang-databinding/gen',
      './tools/adt/idea/android-lang/gen',
      './tools/adt/idea/artwork/gen',
      './tools/adt/idea/smali/gen',
      # external
      './tools/base/jobb',
      './tools/base/zipflinger',
      './tools/dx',
      './tools/external',
      './tools/idea',
      './tools/sherpa',
      './tools/studio',
      './tools/swing-testing',
      './tools/swt',
      './tools/vendor/adt_infra_internal',
      './tools/vendor/galvsoft',
      './tools/vendor/google/aapt2-prebuilt',
      './tools/vendor/google/adrt',
      './tools/vendor/google/CloudEndpoints',
      './tools/vendor/google/cpp-integration-tests',
      './tools/vendor/google/docs',
      './tools/vendor/google/dogfood',
      './tools/vendor/google/firebase'
      './tools/vendor/google/games'
      './tools/vendor/google/GooglePlayLog'
      './tools/vendor/google/installer'
      './tools/vendor/google/JenkinsPresubmit'
      './tools/vendor/google/layoutlib-prebuilt'
      './tools/vendor/google/lldb-integration-tests'
      './tools/vendor/google/real-world-integration-test'
      './tools/vendor/google/scripts'
      './tools/vendor/google/testing'
      './tools/vendor/google/TranslationPluginForEclipse'
      './tools/vendor/google/url-assistant'
      './tools/vendor/google/WinLauncher2'
      './tools/vendor/google3',
      './tools/vendor/intel',
      './tools/vendor/intellij',
  ]
  # file extensions we care about
  ends = [
      '.java',
      '.kt',
  ]
  
  # we're using a filename -> directory path because it makes resolving to
  # sourcefiles very fast
  filetree = {}
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

  for pkg_name in data:
    for sfile_name in data[pkg_name]:
      filepath = os.path.join(data[pkg_name][sfile_name]['path'], pkg_name,
                              sfile_name)
      sys.stdout.write('SF:{}\n'.format(filepath))
      for line_num in data[pkg_name][sfile_name]['instrumented']:
        # format is DA:{line number},{number of hits}, but we don't care about
        # detailed hit numbers so just use 1 for covered lines
        sys.stdout.write('DA:{},{}\n'.format(
            line_num, int(line_num in data[pkg_name][sfile_name]['covered'])))
      sys.stdout.write('end_of_record\n')

if __name__ == '__main__':
  main()
