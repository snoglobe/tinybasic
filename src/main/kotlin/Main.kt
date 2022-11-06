import java.nio.file.Files
import java.nio.file.Path

class ByteClassLoader(parent: ClassLoader?) : ClassLoader(parent) {
    private val byteDataMap: HashMap<String, ByteArray> = HashMap()
    fun loadDataInBytes(byteData: ByteArray, resourcesName: String) {
        byteDataMap[resourcesName] = byteData
    }

    public override fun findClass(className: String): Class<*> {
        if (byteDataMap.isEmpty()) throw ClassNotFoundException("byte data is empty")
        val filePath = className.replace("\\.".toRegex(), "/")
        val extractedBytes = byteDataMap[filePath] ?: throw ClassNotFoundException("Cannot find $filePath in bytes")
        return defineClass(className, extractedBytes, 0, extractedBytes.size)
    }
}

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        val input = Files.readString(Path.of(args[0]))
        val scanner = Scanner(input).scanTokens()
        val parser = Parser(scanner)
        val compiler = Compiler(parser.program())
        val out = if (args.size > 1)
            if(!args[1].endsWith(".class"))
                args[1] + ".class"
            else
                args[1]
        else
            args[0].substringBeforeLast('.') + ".class"
        compiler.compile(out)
    } else {
        outer@ while (true) {
            val lines = mutableListOf<String>()
            while (true) {
                print("] ")
                val line = readLine() ?: break@outer
                if (line.lowercase() == "run") break
                if (line.lowercase() == "end") break@outer
                if (line.lowercase() == "list") {
                    lines.forEach { println(it) }
                    continue
                }
                lines.add(line)
            }
            val input = lines.joinToString("\n")
            val scanner = Scanner(input).scanTokens()
            val parser = Parser(scanner)
            val compiler = Compiler(parser.program())
            val loader = ByteClassLoader(null)
            loader.loadDataInBytes(compiler.result(), "Program")
            val mainClass = loader.findClass("Program")
            val mainMethod = mainClass.getMethod("main", Array<String>::class.java)
            mainMethod.invoke(null, Array(0) {""})
        }
    }
}