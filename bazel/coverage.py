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
      with open(workspace + "/bazel-testlogs/" + test + "/coverage.dat") as f:
        fh.write(f.read())

if __name__ == '__main__':
  main()
