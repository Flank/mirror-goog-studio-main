#!/usr/bin/python
import json
import sys
import numpy

wall = dict()
end = dict()

for path in sys.argv[1:]:
    print ("Processing file: " + path)
    data = open(path).read()
    events = json.loads(data)
    data = dict()
    min_ts = None
    for event in events:
        if "ts" in event:
            ts = long(event["ts"])
            if not min_ts or min_ts > ts:
                min_ts = ts

    for event in events:
        if "pid" in event:
            pid = event["pid"]
            tid = event["tid"]
            ph = event["ph"]
            if ph == "B":
                tids = data.setdefault(pid, dict())
                evs = tids.setdefault(tid, [])
                evs.append(event)
            elif ph == "E":
                last = data[pid][tid].pop()
                wall.setdefault(last["name"],[]).append(long(event["ts"]) - long(last["ts"]))
                end.setdefault(last["name"],[]).append(long(event["ts"]) - min_ts)

print("On a total of " + str(len(sys.argv[1:])) + " files:")
for k,v in wall.items():
    l = len(v)
    avg = numpy.mean(v)
    med = numpy.median(v)
    print (k + ": #" + str(l) + " Avg: " + str(avg) + " Med: " + str(med) + " EndAvg: " + str(numpy.mean(end[k])))
