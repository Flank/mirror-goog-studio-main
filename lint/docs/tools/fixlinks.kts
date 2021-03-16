#!kotlinc

import java.io.File
import java.util.Locale
import java.util.regex.Pattern
import kotlin.system.exitProcess

FixLinks().run(arrayOf("../book.html"))

class FixLinks {
    fun run(args: Array<String>) {
        if (args.size != 1) {
            System.err.println("Usage: <file.html>")
            exitProcess(1)
        }
        val file = File(args[0])
        if (!file.isFile) {
            System.err.println("${file.canonicalFile} does not exist")
            exitProcess(1)
        }

        var source = file.readText()
        source = source.replace("api-guide/../usage/", "usage/")
        source = source.replace("usage/../api-guide/", "api-guide/")

        val absolutePathPattern = Pattern.compile("file:.*tools/base/lint/docs/")
        val linkPattern = Pattern.compile("\"(.+)/(.+)\\.md\\.(html)\"")

        var offset = 0
        while(true) {
            val matcher = absolutePathPattern.matcher(source)
            if (matcher.find(offset)) {
                source = source.substring(0, matcher.start()) + source.substring(matcher.end())
                offset = matcher.start()
            } else {
                break
            }
        }

        offset = 0
        while(true) {
            val matcher = linkPattern.matcher(source)
            if (matcher.find(offset)) {
                matcher.group(2)
                val start = matcher.start(1)
                val end = matcher.end(3)
                val anchorFile = File(file.parentFile, matcher.group().removeSurrounding("\""))
                val anchorFileContents = anchorFile.readText()
                val anchorHeadingIndex = anchorFileContents.indexOf(("\n# "))
                val anchorHeading = anchorFileContents.substring(anchorHeadingIndex,
                    anchorFileContents.indexOf('\n', anchorHeadingIndex + 1))
                val anchor = "#" + anchorHeading.filter { it.isLetter() || it == ':' }.toLowerCase(Locale.ROOT)
                source = source.substring(0, start) + anchor + source.substring(end)
                offset = start
                continue
            } else {
                break
            }
        }

        file.writeText(source)
    }
}

