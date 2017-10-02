-printusage Test2_usage.txt
-printmapping Test2_mapping.txt
-printseeds Test2_seeds.txt

-keep class Test2 {
  *;
}

-keepattributes *Annotation*


-keep class SomeAnnotation

-keep class TestSubclass {
  *;
}