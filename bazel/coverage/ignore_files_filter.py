"""Filters out ignored source files

- Reads a list of files from stdin
- Writes a list of unignored files to stdout
"""
import sys


def main():
  # directory subtrees omitted from consideration
  skip = [
      # tests
      'tools/base/bazel/test',
      'tools/base/build-system/integration-test/databinding/src/test',
      'tools/base/pixelprobe/src/test',
      'tools/base/profiler/tests',
      # generated
      'tools/adt/idea/android-lang-databinding/gen',
      'tools/adt/idea/android-lang/gen',
      'tools/adt/idea/artwork/gen',
      'tools/adt/idea/smali/gen',
      # external
      'tools/base/zipflinger',
      'tools/idea',
      'tools/studio',
      'tools/swing-testing',
      'tools/vendor/adt_infra_internal',
      'tools/vendor/google/firebase',
      'tools/vendor/google/real-world-integration-test',
      'tools/vendor/google/url-assistant',
      'tools/vendor/intellij',
  ]
  # file extensions we care about
  ends = [
      '.java',
      '.kt',
  ]

  for path in sys.stdin:
    path = path.strip()
    # ignore skipped directories
    if any(map(path.startswith, skip)):
      continue
    # ignore non-source files
    if not any(map(path.endswith, ends)):
      continue
    sys.stdout.write(path + "\n")

if __name__ == '__main__':
  main()
