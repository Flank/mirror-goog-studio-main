import os
import sys


def find_tests(workspace):
  """Finds generated coverage data files in bazel-testlogs directory."""
  tests = []
  logs = os.path.join(workspace, 'bazel-testlogs')
  for root, dirs, files in os.walk(logs):
    for file in files:
      if file == "coverage.dat":
        coverage = os.path.join(root, file)
        relpath = os.path.relpath(coverage, logs)
        tests += [os.path.dirname(relpath)]
  return tests


def find_workspace(path):
  """Finds enclosing bazel WORKSPACE directory to |path|."""
  if os.path.isfile(os.path.join(path, 'WORKSPACE')):
    return path
  else:
    parent = os.path.dirname(path)
    return None if parent == path else find_workspace(parent)


def main():
  if len(sys.argv) != 2:
    print('Usage: coverage.py output_file')
  workspace = find_workspace(os.path.realpath(__file__))
  tests = find_tests(workspace)
  output_path = sys.argv[1]
  with open(output_path, 'w') as fh:
    for test in tests:
      files = []
      with open(workspace + "/bazel-bin/" + test + ".instrumented_files") as f:
        files = f.read().splitlines()
      with open(workspace + "/bazel-testlogs/" + test + "/coverage.dat") as f:
        content = f.read().splitlines()
        for line in content:
          # Coverage format specifies a SF (Source File) line for each source.
          # SF:<absolute path to the source file>
          if line.startswith("SF:"):
            in_section = True
            found = None
            candidate = line[3:]
            for file in files:
              if file.endswith(candidate):
                found = file
                break
            if found:
              # We have a file for this section:
              fh.write("SF:" + workspace + "/" + found + '\n')
            else:
              print >>sys.stderr, "WARNING: Couldn't find instrumented file for {}".format(file)
          else:
            if found:
              fh.write(line + '\n')


if __name__ == '__main__':
  main()
