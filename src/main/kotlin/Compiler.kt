import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import TokenType.*
import org.objectweb.asm.Label
import java.io.File

enum class Type {
    Int,
    String,
    None
}

class Compiler(private val ast: ProgramStmt) : Expr.Visitor<Unit>, Stmt.Visitor {
    private var className = "Program"
    private val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    private val methodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "main",
        "([Ljava/lang/String;)V",
        null, null)

    private val variables = mutableMapOf<String, Int>()
    private val types     = mutableMapOf<String, Type>()
    private val mapped    = mutableListOf<String>()

    private val labels    = mutableMapOf<Int, Label>()

    private val codeLabel = Label()
    private val endLabel  = Label()

    private var currentLine = 0

    private fun resolveVariables(stmt: Stmt) {
        when(stmt) {
            is LineStmt -> {
                resolveVariables(stmt.statement)
            }
            is MultiStmt -> {
                stmt.stmts.forEach { resolveVariables(it) }
            }
            is LetStmt -> {
                if (variables[stmt.name] == null) {
                    variables[stmt.name] = variables.size
                }
                types[stmt.name] = when(stmt.expr) {
                    is NumberLiteral -> Type.Int
                    is StringLiteral -> Type.String
                    is Variable ->
                        if(types.containsKey(stmt.expr.name)) {
                            types[stmt.expr.name]!!
                        } else
                            Type.None
                    is BinOp -> Type.Int
                    is UnaryOp -> Type.Int
                    else -> null!!
                }
            }
            is IfStmt -> {
                resolveVariables(stmt.thenBranch)
            }
            is InputStmt -> {
                for(variable in stmt.vars) {
                    if (variable is Variable) {
                        if (variables[variable.name] == null) {
                            variables[variable.name] = variables.size
                        }
                        types[variable.name] = Type.Int
                    }
                }
            }
            is ProgramStmt -> {
                stmt.statements.forEach { resolveVariables(it) }
            }
        }

        for (variable in variables) {
            if(variable.key in mapped) {
                continue
            }
            when(types[variable.key]) {
                Type.Int -> {
                    methodVisitor.visitLocalVariable(variable.key, "I", null, codeLabel, endLabel, variable.value)
                    methodVisitor.visitInsn(Opcodes.ICONST_0)
                    methodVisitor.visitVarInsn(Opcodes.ISTORE, variable.value)
                    mapped.add(variable.key)
                }
                Type.String -> {
                    methodVisitor.visitLocalVariable(variable.key, "Ljava/lang/String;", null, codeLabel, endLabel, variable.value)
                    methodVisitor.visitLdcInsn("")
                    methodVisitor.visitVarInsn(Opcodes.ASTORE, variable.value)
                    mapped.add(variable.key)
                }
                else -> {}
            }
        }
    }

    fun compile(out: String) {
        className = out.substringBefore(".class")
        beginCompile(ast)
        ast.accept(this)
        endCompile()

        val bytes = classWriter.toByteArray()
        val file = File(out)
        file.writeBytes(bytes)
    }

    private fun beginCompile(ast: ProgramStmt) {
        classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "lineNumber", "I", null, 1)
        classWriter.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "stack", "Ljava/util/Stack;", null, null)

        variables["*"] = 0
        types["*"] = Type.Int

        // Fix out of order line numbers
        ast.statements.sortBy { if (it is LineStmt) it.line else null }

        // generate the labels
        ast.statements.forEach {
            if(it is LineStmt) {
                labels[it.line] = Label()
            }
        }

        do {
            resolveVariables(ast)
        }
        while (types.values.contains(Type.None))

        // static initializer
        val clinit = classWriter.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
        clinit.visitCode()

        currentLine = labels.keys.first()
        clinit.visitLdcInsn(currentLine)
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, className, "lineNumber", "I")

        clinit.visitTypeInsn(Opcodes.NEW, "java/util/Stack")
        clinit.visitInsn(Opcodes.DUP)
        clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/Stack", "<init>", "()V", false)
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, className, "stack", "Ljava/util/Stack;")

        clinit.visitInsn(Opcodes.RETURN)
        clinit.visitMaxs(1, 0)

        clinit.visitEnd()

        methodVisitor.visitCode()
    }

    private fun endCompile() {
        methodVisitor.visitInsn(Opcodes.RETURN)
        methodVisitor.visitMaxs(255, 255)
        methodVisitor.visitEnd()
        classWriter.visitEnd()
    }

    fun result(): ByteArray {
        beginCompile(ast)
        ast.accept(this)
        endCompile()
        return classWriter.toByteArray()
    }

    override fun visitVariable(expr: Variable) {
        // check if variable is defined
        if (variables[expr.name] == null) {
            throw RuntimeException("Undefined variable '${expr.name}'.")
        }
        when (types[expr.name]!!) {
            Type.Int -> {
                methodVisitor.visitVarInsn(Opcodes.ILOAD, variables[expr.name]!!)
            }
            Type.String -> {
                methodVisitor.visitVarInsn(Opcodes.ALOAD, variables[expr.name]!!)
            }
            else -> {null!!}
        }
    }

    override fun visitNumber(expr: NumberLiteral) {
        methodVisitor.visitLdcInsn(expr.value)
    }

    private fun generateComparison(opCode: Int) {
        val label = Label()
        methodVisitor.visitJumpInsn(opCode, label)
        methodVisitor.visitInsn(Opcodes.ICONST_0)
        val end = Label()
        methodVisitor.visitJumpInsn(Opcodes.GOTO, end)
        methodVisitor.visitLabel(label)
        methodVisitor.visitInsn(Opcodes.ICONST_1)
        methodVisitor.visitLabel(end)
    }

    override fun visitBinOp(expr: BinOp) {
        expr.left.accept(this)
        expr.right.accept(this)
        when (expr.op.type) {
            PLUS -> methodVisitor.visitInsn(Opcodes.IADD)
            MINUS -> methodVisitor.visitInsn(Opcodes.ISUB)
            TIMES -> methodVisitor.visitInsn(Opcodes.IMUL)
            DIVIDE -> methodVisitor.visitInsn(Opcodes.IDIV)
            EQUAL -> {
                generateComparison(Opcodes.IF_ICMPEQ)
            }
            NOTEQUAL -> {
                generateComparison(Opcodes.IF_ICMPNE)
            }
            LESS_THAN -> {
                generateComparison(Opcodes.IF_ICMPLT)
            }
            LESS_EQUAL -> {
                generateComparison(Opcodes.IF_ICMPLE)
            }
            GREATER_THAN -> {
                generateComparison(Opcodes.IF_ICMPGT)
            }
            GREATER_EQUAL -> {
                generateComparison(Opcodes.IF_ICMPGE)
            }
            else -> throw RuntimeException("Unknown operator type ${expr.op.type}.")
        }
    }

    override fun visitUnaryOp(expr: UnaryOp) {
        expr.expr.accept(this)
        when (expr.op.type) {
            MINUS -> methodVisitor.visitInsn(Opcodes.INEG)
            else -> throw RuntimeException("Unknown operator type ${expr.op.type}.")
        }
    }

    override fun visitString(expr: StringLiteral) {
        methodVisitor.visitLdcInsn(expr.value)
    }

    override fun visitPrintStmt(stmt: PrintStmt) {
        for(expr in stmt.exprs) {
            methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
            expr.accept(this)
            when (expr) {
                is NumberLiteral -> {
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(I)V", false)
                }
                is StringLiteral -> {
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false)
                }
                is Variable -> {
                    when (types[expr.name]!!) {
                        Type.Int -> {
                            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(I)V", false)
                        }
                        Type.String -> {
                            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false)
                        }
                        else -> {null!!}
                    }
                }
                is BinOp -> {
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(I)V", false)
                }
            }
        }
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "()V", false)
    }

    override fun visitIfStmt(stmt: IfStmt) {
        val endLabel = Label()
        stmt.condition.accept(this)
        methodVisitor.visitJumpInsn(Opcodes.IFEQ, endLabel)
        stmt.thenBranch.accept(this)
        methodVisitor.visitLabel(endLabel)
    }

    override fun visitGotoStmt(stmt: GotoStmt) {
        stmt.line.accept(this)
        methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, className, "lineNumber", "I")
        methodVisitor.visitJumpInsn(Opcodes.GOTO, codeLabel)
    }

    override fun visitInputStmt(stmt: InputStmt) {
        for (variable in stmt.vars) {
            if (variables[(variable as Variable).name] == null) {
                throw RuntimeException("Undefined variable '${variable.name}'.")
            }
            methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/Scanner")
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;")
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Scanner", "nextInt", "()I", false)
            methodVisitor.visitVarInsn(Opcodes.ISTORE, variables[variable.name]!!)
        }
    }

    override fun visitLetStmt(stmt: LetStmt) {
        stmt.expr.accept(this)
        when (types[stmt.name]!!) {
            Type.Int -> {
                methodVisitor.visitVarInsn(Opcodes.ISTORE, variables[stmt.name]!!)
            }
            Type.String -> {
                methodVisitor.visitVarInsn(Opcodes.ASTORE, variables[stmt.name]!!)
            }
            else -> {null!!}
        }
    }

    override fun visitGosubStmt(stmt: GosubStmt) {
        var nextLine = -1

        // get next key from map
        for (key in labels.keys) {
            if (key > currentLine) {
                nextLine = key
                break
            }
        }

        // push current line to stack
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, className, "stack", "Ljava/util/Stack;")
        methodVisitor.visitLdcInsn(nextLine)
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Stack", "push", "(Ljava/lang/Object;)Ljava/lang/Object;", false)
        methodVisitor.visitInsn(Opcodes.POP)

        // set current line to stmt.line
        stmt.line.accept(this)
        methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, className, "lineNumber", "I")

        methodVisitor.visitJumpInsn(Opcodes.GOTO, codeLabel)
    }

    override fun visitReturnStmt(stmt: ReturnStmt) {
        // pop line from stack
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, className, "stack", "Ljava/util/Stack;")
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Stack", "pop", "()Ljava/lang/Object;", false)
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer")
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false)
        methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, className, "lineNumber", "I")
        methodVisitor.visitJumpInsn(Opcodes.GOTO, codeLabel)
    }

    override fun visitEndStmt(stmt: EndStmt) {
        methodVisitor.visitInsn(Opcodes.RETURN)
    }

    override fun visitProgramStmt(stmt: ProgramStmt) {
        val defaultLabel = Label()
        methodVisitor.visitLabel(codeLabel)

        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, className, "lineNumber", "I")

        val lineToLabel = mutableMapOf<Int, Label>()
        for (line in stmt.statements)
            if (line is LineStmt) {
                if(lineToLabel.containsKey(line.line))
                    continue
                lineToLabel[line.line] = labels[line.line]!!
            }

        methodVisitor.visitLookupSwitchInsn(
            defaultLabel,
            lineToLabel.keys.toIntArray(),
            lineToLabel.values.toTypedArray()
        )
        // generate the code
        stmt.statements.forEach {
            it.accept(this)
        }
        methodVisitor.visitLabel(defaultLabel)
        methodVisitor.visitLabel(endLabel)
    }

    override fun visitLineStmt(stmt: LineStmt) {
        currentLine = stmt.line
        methodVisitor.visitLabel(labels[stmt.line]!!)

        methodVisitor.visitLineNumber(stmt.line, labels[stmt.line]!!)
        stmt.statement.accept(this)

        var nextLine = -1

        // get next key from map
        for (key in labels.keys) {
            if (key > stmt.line) {
                nextLine = key
                break
            }
        }

        // set line number to next line
        labels[nextLine].let {
            if (it != null) {
                methodVisitor.visitLdcInsn(nextLine)
                methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, className, "lineNumber", "I")
                methodVisitor.visitJumpInsn(Opcodes.GOTO, codeLabel)
            } else {
                methodVisitor.visitInsn(Opcodes.RETURN)
            }
        }
    }

    override fun visitMultiStmt(stmt: MultiStmt) {
        if(stmt.stmts.any { (it is GotoStmt || it is GosubStmt || it is ReturnStmt)
                    && stmt.stmts.last() != it }) {
            println(stmt.stmts.last())
            throw RuntimeException("Goto, Gosub and Return statements must be the last statement in a line.")
        }
        stmt.stmts.forEach {
            it.accept(this)
        }
    }

}