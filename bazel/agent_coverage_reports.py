#!/usr/bin/env python3
import os
import argparse
import subprocess
import tempfile


def find_workspace(path):
  """Finds enclosing bazel WORKSPACE directory to |path|."""
  if os.path.isfile(os.path.join(path, 'WORKSPACE')):
    return path
  else:
    parent = os.path.dirname(path)
    return None if parent == path else find_workspace(parent)


def bazel_query(bazel, query):
  print('  bazel query {}'.format(query))
  return subprocess.check_output([bazel, 'query', query]).decode('utf-8').splitlines()


def main():
  """Finds all bazel jacoco agent coverage reports and generates them."""
  parser = argparse.ArgumentParser(description='Generate coverage reports.')
  parser.add_argument('--bazel_testlogs', default='bazel-testlogs', help='location of the testlogs dir to analyze')
  args = parser.parse_args()
  test_logs_dir = args.bazel_testlogs

  workspace = find_workspace(os.path.realpath(__file__))

  bazel = workspace + '/tools/base/bazel/bazel'

  universe = '(//tools/... - //tools/adt/idea/android-uitests/...)'

  print('Finding all possible coverage reports...')

  coverage_targets = bazel_query(
      bazel, 'attr("tags","agent_coverage_report",kind(java_binary, ' + universe
      + '))')
  if not coverage_targets:
    print('No coverage targets found')
    return

  print('found targets:')
  for coverage_target in coverage_targets:
    print(' {}'.format(coverage_target))

  targets_map = {target: tempfile.NamedTemporaryFile(mode='w+t') for target in coverage_targets}

  for (coverage_target, production_targets_file) in targets_map.items():
    print('Finding test targets for report ' + coverage_target)
    # find the tests for the production code that this coverage report relates to
    production_targets = bazel_query(
        bazel,
        'kind(test, rdeps(' + universe + ', deps(' + coverage_target + ')))')
    for target in production_targets:
      production_targets_file.write(target)
      production_targets_file.write('\n')
    print(' ... found {} targets'.format(len(production_targets)))


  for (coverage_target, production_targets_file) in targets_map.items():
    report_name = coverage_target[2:].replace(':', '/')
    # generate the coverage report
    subprocess.check_call(
        [bazel, 'run', coverage_target, '--config=remote', '--', report_name, production_targets_file.name, test_logs_dir])
  print('Done.')


if __name__ == '__main__':
  main()
