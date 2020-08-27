"""A tool to read values of bazel status files."""
import argparse
import sys


def stat(arg):
  with open(arg.src) as f:
    ret = {}
    for line in f.read().splitlines():
      parts = line.split(" ", 2)
      ret[parts[0]] = parts[1]

  with open(arg.dst, "w") as out:
    out.write(ret[arg.key] + "\n")


def main(argv):
  parser = argparse.ArgumentParser()
  parser.add_argument("--src", help="The status file")
  parser.add_argument("--dst", help="The target file")
  parser.add_argument("--key", help="The key to extract")
  stat(parser.parse_args(argv))


if __name__ == "__main__":
  main(sys.argv[1:])
