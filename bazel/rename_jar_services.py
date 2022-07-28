#  Copyright (C) 2022 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import argparse
import fnmatch
import os
import re
import shutil
import sys
import zipfile

# This tool is meant to fix a jarjar deficiency where it doesn't properly rename services in the
# jar. It takes a jarjar output and the rules used to generate it and applies the shade (rename)
# rules to the META-INF/services folder.

def parse_args():
    parser = argparse.ArgumentParser(description='Process some integers.')
    parser.add_argument('-i', '--in', dest='jar_in', metavar='jar_in', nargs=1, required=True,
                        help='the input jar')
    parser.add_argument('-o', '--out', dest='jar_out', metavar='jar_out', nargs=1, required=True,
                        help='the output jar')
    parser.add_argument('-r', '--rules', dest='rules', metavar='rules', nargs=1, required=True,
                        help='the rules file')

    return parser.parse_args()

def to_regex(s):
    sentry = "__CAPTURING_STAR__"
    # fnmatch doesn't handle ** the way we want it to, so replace it with something else
    # and then switch it back with what we do want
    return fnmatch.translate(s.replace("**", sentry)).replace(sentry, "(.*)")

def build_replace(replace):
    regex = re.compile("(?s:([^@]*)(@[0-9]*))")

    matches = regex.findall(replace)

    # turn "com.example.@1.foo.@2" into "com.example.{}.foo.{}" and [0, 1] so we can do
    # "com.example.{}.foo.{}".format(*[groups(0), groups(1)])
    replacements = [int(i[1][1:])-1 for i in matches]
    formattable_string = "".join([i[0] + "{}" for i in matches])
    # if string has no replacements use original string
    if not formattable_string:
        formattable_string = replace

    return formattable_string, replacements

def unwrap_rules(rules_lines):
    ret = []
    for rule in rules_lines:
        rule = rule.strip()
        if not rule: continue
        rule_elements = rule.split(" ")
        rule_type = rule_elements[0]
        if rule_type == "zap": raise ValueError("zap operation not supported")
        elif rule_type != "rule": continue # we only care about shade rules
        # TODO: Maybe add zapping behavior to this
        match, replace = rule_elements[1], rule_elements[2]
        formattable_string, replacements = build_replace(replace)
        ret.append({
            # This is the rule type. Options are "rule" for shading,
            # "zap" for removing and "keep" for shrinking
            "rule_type": rule_type,
            # This is the original replacement string for this rule
            "match": match,
            # This is the original replacement string for this rule
            "replace": replace,
            # This is the regex to match the class name to be replaced
            "re_match": re.compile(to_regex(match)),
            # This is the formattable string. Ex: "com.example.{}.foo.{}"
            "formattable_string": formattable_string,
            # These are the replacement numbers @1, @2, etc., in [0, 1, ...] format.
            # They are ordered by the order they appear in the rule
            "replacements": replacements
        })
    return ret

def shade_name(name, rules):
    # Since we are dealing with filenames and individual class names, we only need to apply one rule
    for i in rules:
        match = i["re_match"].match(name)

        if match: # Apply this rule
            # First get the matching groups
            groups = match.groups()

            # Then format the replacement string with the groups and return
            return i["formattable_string"].format(*[groups[r] for r in i["replacements"]])

    # No matches made
    return name

def copy_entry(entry_name, src_archive, dst_archive):
    # Less memory intensive copy
    if sys.version_info >= (3, 6):
        with src_archive.open(entry_name) as src_file:
            with dst_archive.open(entry_name, mode="w") as dst_file:
                shutil.copyfileobj(src_file, dst_file)
    else:
        dst_archive.writestr(entry_name, src_archive.read(entry_name))

def shade_services(jar_file, rules_file, output, compression=zipfile.ZIP_STORED):
    rules = unwrap_rules(open(rules_file).readlines())
    with zipfile.ZipFile(jar_file, mode="r") as src_archive:
        with zipfile.ZipFile(output, mode="w", compression=compression) as dst_archive:
            # Copy every entry, shading service file names and content as needed
            for entry in src_archive.infolist():
                entry_name = entry.filename
                if entry_name.startswith("META-INF/services") and not entry.is_dir():
                    dir_name, base_name = os.path.split(entry_name)

                    # Calculate new entry name and content
                    shaded_entry_name = dir_name + "/" + shade_name(os.path.basename(base_name), rules)
                    entry_lines = src_archive.read(entry_name).decode(encoding="utf-8").split("\n")
                    shaded_lines = [shade_name(i, rules) for i in entry_lines]

                    # Write new content to new file name
                    dst_archive.writestr(shaded_entry_name, "\n".join(shaded_lines))
                else:
                    # Just copy the file
                    copy_entry(entry_name, src_archive, dst_archive)

def main():
    args = parse_args()
    shade_services(args.jar_in[0], args.rules[0], args.jar_out[0], zipfile.ZIP_DEFLATED)

if __name__ == '__main__':
    main()
