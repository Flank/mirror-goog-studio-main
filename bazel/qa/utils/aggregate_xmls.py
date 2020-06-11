#!/usr/bin/python3
"""Aggregate testlogs XMLs into a single XML file

This scripts takes a directory as an input, collects all XML files that exist
in the directory tree and aggregates results for testsuites and testcases that
are present within. The tests that are run multiple times are indexed with a
numeric suffix counter. Aggregated result is written to a file.

Example:
  $ ./aggregate_xmls.py --testlogs_dir=/mydir/testlogs --output_file=out.xml

Arguments:
  --testlogs_dir: directory where test logs are located. ex: <bazel-testlogs>

  --output_file: name of the XML file where the aggregated results will be
      written to. (default: aggregated_results.xml)
"""

import argparse
import os
import xml.etree.ElementTree as ET

def merge_xmls(filelist):
  root = ET.Element('testsuites')
  classcounter = {}
  testcounter = {}
  for filename in filelist:
    data = ET.parse(filename).getroot()
    for child in data.findall('testsuite'):
      for subchild in child:
        if subchild.tag != "testcase":
          continue
        else:
          classname = child.attrib['name']
          testname = subchild.attrib["name"]
          if classname not in classcounter:
            classcounter[classname] = 0
          else:
            classcounter[classname] += 1
          if testname not in testcounter:
            testcounter[testname] = 0
          else:
            testcounter[testname] += 1
          child.attrib["name"] = child.attrib["name"] \
                                  + "-" + str(classcounter[classname])
          subchild.attrib["name"] = subchild.attrib["name"] \
                                  + "-" + str(classcounter[classname])
          subchild.attrib["classname"] = subchild.attrib["classname"] \
                                  + "-" + str(testcounter[testname])
          root.append(child)
          break
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
