
method Target.<init>() : void
{
	.src "entryHooks.java"
	.line 10
	.prologue_end
	.line 10
	    0| move-object v0, v2
	.local v0, "this", Target
	    1| move-object v1, v0
	    2| invoke-direct {v1}, java.lang.Object.<init>
	    5| return-void
}

method Target.main(java.lang.String[]) : void
{
	.params "?"
	.src "entryHooks.java"
	.line 14
	.prologue_end
	.line 14
	    0| move-object v0, v3
	.local v0, "args", java.lang.String[]
	    1| sget-object v1, java.lang.System.out
	    3| const-string v2, "Hello, world!"
	    5| invoke-virtual {v1,v2}, java.io.PrintStream.println
	.line 15
	    8| invoke-static {}, Target.test
	.line 16
	   11| sget-object v1, java.lang.System.out
	   13| const-string v2, "Good bye!"
	   15| invoke-virtual {v1,v2}, java.io.PrintStream.println
	.line 17
	   18| return-void
}

method Target.test() : void
{
	.src "entryHooks.java"
	.line 21
	.prologue_end
	.line 21
	    0| new-instance v1, Target
	    2| move-object v4, v1
	    3| move-object v1, v4
	    4| move-object v2, v4
	    5| invoke-direct {v2}, Target.<init>
	    8| move-object v0, v1
	.line 22
	.local v0, "obj", Target
	    9| move-object v1, v0
	   10| const/16 v2, #+123 (0x0000007b | 0.000000)
	   12| const-string v3, "Testing..."
	   14| invoke-virtual {v1,v2,v3}, Target.foo
	.line 23
	   17| return-void
}

method Target.foo(int, java.lang.String) : void
{
	.params "?", "?"
	.src "entryHooks.java"
	.line 27
	.prologue_end
	.line 27
	    0| move-object v0, v10
	.local v0, "this", Target
	    1| move v1, v11
	.local v1, "x", int
	    2| move-object v2, v12
	.local v2, "msg", java.lang.String
	    3| sget-object v3, java.lang.System.out
	    5| const-string v4, "foo(%d, %s)\n"
	    7| const/4 v5, #+2 (0x00000002 | 0.000000)
	    8| new-array v5, v5, java.lang.Object[]
	   10| move-object v9, v5
	   11| move-object v5, v9
	   12| move-object v6, v9
	   13| const/4 v7, #+0 (0x00000000 | 0.000000)
	   14| move v8, v1
	   15| invoke-static {v8}, java.lang.Integer.valueOf
	   18| move-result-object v8
	   19| aput-object v8, v6, v7
	   21| move-object v9, v5
	   22| move-object v5, v9
	   23| move-object v6, v9
	   24| const/4 v7, #+1 (0x00000001 | 0.000000)
	   25| move-object v8, v2
	   26| aput-object v8, v6, v7
	   28| invoke-virtual {v3,v4,v5}, java.io.PrintStream.printf
	   31| move-result-object v3
	.line 28
	   32| return-void
}

method Tracer.<init>() : void
{
	.src "entryHooks.java"
	.line 2
	.prologue_end
	.line 2
	    0| move-object v0, v2
	.local v0, "this", Tracer
	    1| move-object v1, v0
	    2| invoke-direct {v1}, java.lang.Object.<init>
	    5| return-void
}

method Tracer.onEntry(java.lang.String) : void
{
	.params "?"
	.src "entryHooks.java"
	.line 6
	.prologue_end
	.line 6
	    0| move-object v0, v5
	.local v0, "methodName", java.lang.String
	    1| sget-object v1, java.lang.System.out
	    3| new-instance v2, java.lang.StringBuilder
	    5| move-object v4, v2
	    6| move-object v2, v4
	    7| move-object v3, v4
	    8| invoke-direct {v3}, java.lang.StringBuilder.<init>
	   11| const-string v3, "OnEntry("
	   13| invoke-virtual {v2,v3}, java.lang.StringBuilder.append
	   16| move-result-object v2
	   17| move-object v3, v0
	   18| invoke-virtual {v2,v3}, java.lang.StringBuilder.append
	   21| move-result-object v2
	   22| const-string v3, ")"
	   24| invoke-virtual {v2,v3}, java.lang.StringBuilder.append
	   27| move-result-object v2
	   28| invoke-virtual {v2}, java.lang.StringBuilder.toString
	   31| move-result-object v2
	   32| invoke-virtual {v1,v2}, java.io.PrintStream.println
	.line 7
	   35| return-void
}
