import os


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
  workspace = find_workspace(os.path.realpath(__file__))
  tests = find_tests(workspace)
  for test in tests:
    os.remove(workspace + "/bazel-testlogs/" + test + "/coverage.dat")

if __name__ == '__main__':
  main()
