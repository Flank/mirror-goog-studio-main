#!kotlin

// Perform misc text cleanup in the .md.html files:
// remove trailing spaces, remove repeated blank
// lines, and switch from straight quotes to smart
// quotes

import java.io.File
import kotlin.system.exitProcess

Main().run(args)

class Main {
    private var modified = 0
    private var stripDuplicateNewlines = false
    private var smartQuotes = false

    fun run(args: Array<String>) {
        if (args.isEmpty() || args[0] == "--help") {
            println("Usage: fixspace [--strip-duplicate-newlines] [--smart-quotes] <files>")
            exitProcess(0)
        }
        val files = mutableListOf<File>()
        for (arg in args) {
            when {
                arg == "--strip-duplicate-newlines" -> {
                    stripDuplicateNewlines = true
                }
                arg == "--smart-quotes" -> {
                    smartQuotes = true
                }
                arg.startsWith("--") -> {
                    println("Unknown option $arg")
                    exitProcess(-1)
                }
                else -> {
                    val file = File(arg).canonicalFile
                    if (!file.exists()) {
                        System.err.println("$file does not exist")
                        exitProcess(-1)
                    }
                    files.add(file)
                }
            }
        }

        for (file in files) {
            fix(file)
        }

        println("Formatted $modified files")
    }

    private fun fix(file: File) {
        val name = file.name
        if (name == ".git" || name.endsWith(".class") || name.endsWith(".png") || name.endsWith(".db")) {
            return
        }
        if (name.endsWith(".min.js") || name == "node_modules") {
            return
        }
        if (name.endsWith(".html") && !name.endsWith(".md.html")) {
            return
        }
        if (file.isDirectory) {
            file.listFiles()?.forEach { fix(it) }
        } else if (file.isFile) {
            reformat(file)
        }
    }

    private fun reformat(file: File) {

        val text = file.readText()
        val sb = StringBuilder()
        for (line in text.lines()) {
            if (addSeparator(sb)) {
                sb.append('\n')
            }
            sb.append(line.stripTrailing().replace("\t", "    "))
        }
        var formatted = sb.toString()

        if (smartQuotes && (file.name.endsWith(".md") || file.name.endsWith(".html") ||
                    file.name.endsWith(".txt"))) {
            formatted = fixQuotes(formatted)
        }
        if (formatted != text) {
            modified++
            file.writeText(formatted)
        }
    }

    private fun fixQuotes(formatted: String): String {
        var inCode = false
        var inQuote = false
        var column = 0
        var inPre = false
        val sb = StringBuilder(formatted.length)
        var skipLine = false
        var lineno = 1
        for (i in formatted.indices) {
            var c = formatted[i]

            if (c == '`' && (column > 0 || !formatted.startsWith("```", i)) && !inPre) {
                inCode = !inCode
            } else if (column == 0) {
                if (formatted.startsWith("<style", i) ||
                   formatted.startsWith("<meta", i) ||
                        formatted.startsWith("<!-- Markdeep:", i)) {
                    skipLine = true
                } else if (c == '`' && formatted.startsWith("```", i)) {
                    inPre = !inPre
                } else if (c == '~' && formatted.startsWith("~~~", i)) {
                    inPre = !inPre
                }
            } else if (!inPre && !inCode && !skipLine && c == '"') { // || c == '“UAST”')
                inQuote = !inQuote
                if (inQuote) {
                    c = '\u201C'
                } else {
                    c = '\u201D'
                }
            } else if (c == '\u201C') {
                // already converted; make sure we don't get confused if there's a mismatch between
                // straight quotes and smart quotes
                inQuote = true
            } else if (c == '\u201D') {
                inQuote = false
            }
            if (c == '\n') {
                column = 0
                skipLine = false
                lineno++
            } else {
                column++
            }
            sb.append(c)
        }
        return sb.toString()
    }

    private fun addSeparator(sb: StringBuilder): Boolean {
        if (sb.isEmpty()) {
            return false
        }

        if (!stripDuplicateNewlines) {
            return true
        }

        if (sb.length < 2) {
            return true
        }

        return sb[sb.length - 1] != '\n' ||
                sb[sb.length - 2] != '\n'
    }
}
