# A simple script that just keeps printing "yes", and never terminates
import sys

if (len(sys.argv) > 1):
    sys.stderr.write("Usage: yes")
    sys.exit(-1)

while True:
    print "yes"