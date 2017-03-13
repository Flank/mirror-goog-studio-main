
import re
import os
import glob
import sys
import subprocess
import argparse

# test command line arguments
argParser = argparse.ArgumentParser(description = 'dexter end-to-end test driver')
argParser.add_argument('-cmd', required = True)
args = argParser.parse_args()

# the bazel sandbox test data root
data_root = 'tools/base/profiler/native/testdata/dex'

# list of test cases
# ( <test_name> : { <test_case_config> } )
test_cases = {
  'map'             : { 'args' : '-m', 'input' : '*.dex' },
  'stats'           : { 'args' : '-s', 'input' : '*.dex' },
  'asm'             : { 'args' : '-d', 'input' : '*.dex' },
  'hello_stats'     : { 'args' : '-s -e Hello', 'input' : 'hello.dex' },
  'am_stats'        : { 'args' : '-s -e android.app.ActivityManager', 'input' : 'large.dex' },
}

# run a shell command and returns the stdout content
def Run(cmd):
  return subprocess.Popen(
    args = cmd,
    shell = True,
    stdout = subprocess.PIPE,
    stderr = subprocess.STDOUT).communicate()[0]

# for each test_case, run dexter over the specified input (ex. *.dex)
#
# the expected ('golden') output has the same base name as the input .dex,
# for example (test_name = 'map') :
#
#    'hello.dex' -> 'hello.map.expected'
#
for test_name, test_config in sorted(test_cases.iteritems()):
  input_files = glob.glob(os.path.join(data_root, test_config['input']))

  for input in input_files:
    # run dexter with the test arguments
    cmd = '%s %s %s' % (args.cmd, test_config['args'], input)
    actual_output = Run(cmd)

    # build the expected filename
    expected_filename = re.sub(r'\.dex', ('.%s.expected' % test_name), input)

    # compare the actual output with the expected output
    with open(expected_filename) as f:
      if actual_output != f.read():
        print('expected output mismatch (%s)' % os.path.basename(expected_filename))
        exit(1)
