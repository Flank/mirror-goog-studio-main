function fail {
   echo $1
   exit 1
}

EXPECTED=$(cat <<- EOF
>main
><init>
>simple
>twoReturns
<twoReturns.1
>twoReturns
<twoReturns.2
<itCatches
>itCatches
>nestedCatches
>throw1
>catch1
>finally1
>catch2
>finally2
<nestedCatches
>itThrows
>callsAThrow
>itThrows
><init>1
<<init>1
<main
EOF
)

if [ -f report.json ]; then
  fail "Report should not exist before execution"
fi
# Run the file without instrumentation
OUT=$(tools/base/tracer/trace_test)

if [ "$OUT" != "$EXPECTED" ]; then
  fail "Expected output does not match"
fi

if [ -f report.json ]; then
  fail "Report should not exist after running with no agent"
fi

# Now run with:
OUT=$(tools/base/tracer/trace_test --jvm_flag=-javaagent:tools/base/tracer/trace_agent.jar=tools/base/tracer/agent/testSrc/com/android/tools/tracer/test.profile)
if [ ! -f report.json ]; then
  fail "Report with profile should exist"
fi

EXPECTED_JSON=$(cat <<EOF
[
"MainTest.simple"},
""},
"MainTest.twoReturns"},
""},
"MainTest.twoReturns"},
""},
"MainTest.itCatches"},
""},
"MainTest.nestedCatches"},
""},
"MainTest.itThrows"},
""},
"MainTest.callsAThrow"},
"MainTest.itThrows"},
""},
""},
"Other.<init>"},
""},
"PkgClass.<init>"},
""},
"manual trace"},
""},
"custom events"},
"custom"},
""},
""},
""},
EOF
)
JSON=$(cat report.json | sed "s/.*\"name\" : //")

if [ "$EXPECTED_JSON" != "$JSON" ]; then
  echo $JSON
  fail "Expected report does not match"
fi

if [ "$OUT" != "$EXPECTED" ]; then
  fail "Expected output with agent does not match"
fi

# Now run with no profile
OUT=$(tools/base/tracer/trace_test --jvm_flag=-javaagent:tools/base/tracer/trace_agent.jar)

if [ "$OUT" != "$EXPECTED" ]; then
  fail "Expected output with agent and no profile does not match"
fi
