#!kotlinc

import java.io.File
import java.util.Locale
import java.util.regex.Pattern
import kotlin.system.exitProcess

FixLinks().run(args)

// Fixes the internal html links within a rasterized Markdeep document to
// remove some absolute paths etc, make chapter links point to anchors
// within the HTML document instead of pointing off to the separate .md.html
// files, etc.
class FixLinks {
    fun run(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println("Usage: <file.html> [, file2.html, file3.html, ...]")
            exitProcess(1)
        }
        for (path in args) {
            val file = File(path)
            if (!file.isFile) {
                System.err.println("${file.canonicalFile} does not exist")
                exitProcess(1)
            }
            process(file)
        }
    }

    private fun process(file: File) {
        var source = file.readText()
        source = source.replace("api-guide.md.html", "api-guide.html")
        source = source.replace("user-guide.md.html", "user-guide.html")
        source = source.replace("api-guide/../usage/", "usage/")
        source = source.replace("usage/../api-guide/", "api-guide/")

        val absolutePathPattern = Pattern.compile("file:.*tools/base/lint/docs/")
        val linkPattern = Pattern.compile("\"([^\"]+)/(.+)\\.md\\.(html)\"")

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
                val start = matcher.start(1)
                val end = matcher.end(3)
                val anchorFile = File(file.parentFile, matcher.group().removeSurrounding("\""))
                if (!anchorFile.exists()) {
                    System.err.println("Unexpected link reference $anchorFile does not exist" )
                    exitProcess(1)
                }
                val anchorFileContents = anchorFile.readText()
                val anchorHeadingIndex = anchorFileContents.indexOf(("\n# "))
                if (anchorHeadingIndex == -1) {
                    offset = start
                    continue
                }
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
