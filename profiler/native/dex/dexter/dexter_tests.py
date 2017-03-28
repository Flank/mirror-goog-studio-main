
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
  'map'             : { 'args' : '-m', 'input' : ['*.dex'] },
  'stats'           : { 'args' : '-s', 'input' : ['*.dex'] },
  'asm'             : { 'args' : '-d', 'input' : ['*.dex'] },
  'hello_stats'     : { 'args' : '-s -e Hello', 'input' : ['hello.dex'] },
  'am_stats'        : { 'args' : '-s -e android.app.ActivityManager', 'input' : ['large.dex'] },
  'rewrite'         : { 'args' : '-d -x full_rewrite', 'input' : ['*.dex'] },
  'entry_hook'      : { 'args' : '-d -x stress_entry_hook', 'input' : [
                          'entry_hooks.dex', 'hello.dex', 'medium.dex', 'min.dex' ] },
}

# run a shell command and returns the stdout content
def Run(cmd, stdin_content=None):
  return subprocess.Popen(
    args = cmd,
    shell = True,
    stdin = subprocess.PIPE,
    stdout = subprocess.PIPE,
    stderr = subprocess.STDOUT).communicate(input = stdin_content)[0]

failures = 0

# for each test_case, run dexter over the specified input (ex. *.dex)
#
# the expected ('golden') output has the same base name as the input .dex,
# for example (test_name = 'map') :
#
#    'hello.dex' -> 'expected/hello.map'
#
for test_name, test_config in sorted(test_cases.iteritems()):
  for input_pattern in test_config['input']:
    input_files = glob.glob(os.path.join(data_root, input_pattern))

    for input in input_files:
      # run dexter with the test arguments
      cmd = '%s %s %s' % (args.cmd, test_config['args'], input)
      actual_output = Run(cmd)

      # build the expected filename
      expected_filename = re.sub(r'\.dex', ('.%s' % test_name), os.path.basename(input))
      expected_filename = os.path.join(data_root, 'expected', expected_filename)

      # compare the actual output with the expected output
      cmp_output = Run('diff "%s" -' % expected_filename, actual_output)
      if cmp_output:
        print('\nFAILED: expected output mismatch (%s)' % os.path.basename(expected_filename))
        print(cmp_output)
        failures = failures + 1
      else:
        print('ok: output matching (%s)' % os.path.basename(expected_filename))

if failures != 0:
  print('\nSUMMARY: %d failure(s)\n' % failures)
  exit(1)
