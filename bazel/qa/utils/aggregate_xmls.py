#!/usr/bin/python3
"""Aggregate testlogs XMLs into a single XML file

This scripts takes a directory as an input, collects all XML files that exist
in the directory tree and aggregates results for testsuites and testcases that
are present within. Aggregated result is written to a file.

Example:
  $ ./aggregate_xmls.py --testlogs_dir=/mydir/testlogs --output_file=out.xml

Arguments:
  --testlogs_dir: directory where test logs are located. ex: <bazel-testlogs>

  --output_file: name of the XML file where the aggregated results will be
      written to. (default: aggregate_results.xml)
"""

import argparse
import os
import xml.etree.ElementTree as ET

def merge_xmls(filelist):
  root = ET.Element('testsuites')
  for filename in filelist:
    data = ET.parse(filename).getroot()
    for child in data:
      if child.find('testcase'):
        root.append(child)
  return root

def main():
  parser = argparse.ArgumentParser(description='Aggregate test results')
  parser.add_argument('--testlogs_dir', required=True)
  parser.add_argument('--output_file', default='aggregate_results.xml')
  args = parser.parse_args()

  filelist = []
  for dirname, subdir, filenames in os.walk(args.testlogs_dir):
    for filename in filenames:
      if filename.endswith('.xml'):
        filelist.append(dirname+"/"+filename)

  data = merge_xmls(filelist)
  ET.ElementTree(data).write(args.output_file)

if __name__ == '__main__':
  main()
