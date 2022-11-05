abstract class Expr {
    interface Visitor<T> {
        fun visitVariable(expr: Variable): T
        fun visitNumber(expr: NumberLiteral): T
        fun visitBinOp(expr: BinOp): T
        fun visitUnaryOp(expr: UnaryOp): T
        fun visitString(expr: StringLiteral): T
    }
    abstract fun <T> accept(visitor: Visitor<T>): T
}

abstract class Stmt {
    interface Visitor {
        fun visitPrintStmt(stmt: PrintStmt): Unit
        fun visitIfStmt(stmt: IfStmt): Unit
        fun visitGotoStmt(stmt: GotoStmt): Unit
        fun visitInputStmt(stmt: InputStmt): Unit
        fun visitLetStmt(stmt: LetStmt): Unit
        fun visitGosubStmt(stmt: GosubStmt): Unit
        fun visitReturnStmt(stmt: ReturnStmt): Unit
        fun visitEndStmt(stmt: EndStmt): Unit
        fun visitProgramStmt(stmt: ProgramStmt): Unit
        fun visitLineStmt(stmt: LineStmt): Unit
        fun visitMultiStmt(stmt: MultiStmt): Unit
    }
    abstract fun accept(visitor: Visitor)
}

data class Variable(val name: String) : Expr() {
    override fun <T> accept(visitor: Visitor<T>): T {
        return visitor.visitVariable(this)
    }
}

data class NumberLiteral(val value: Int) : Expr() {
    override fun <T> accept(visitor: Visitor<T>): T {
        return visitor.visitNumber(this)
    }
}

data class BinOp(val left: Expr, val op: Token, val right: Expr) : Expr() {
    override fun <T> accept(visitor: Visitor<T>): T {
        return visitor.visitBinOp(this)
    }
}

data class UnaryOp(val op: Token, val expr: Expr) : Expr() {
    override fun <T> accept(visitor: Visitor<T>): T {
        return expr.accept(visitor)
    }
}

data class StringLiteral(val value: String) : Expr() {
    override fun <T> accept(visitor: Visitor<T>): T {
        return visitor.visitString(this)
    }
}

data class PrintStmt(val exprs: MutableList<Expr>) : Stmt() {
    override fun accept(visitor: Visitor) {
        visitor.visitPrintStmt(this)
    }
}

data class IfStmt(val condition: Expr, val thenBranch: Stmt) : Stmt() {
    override fun accept(visitor: Visitor) {
        visitor.visitIfStmt(this)
    }
}

data class MultiStmt(val stmts: MutableList<Stmt>) : Stmt() {
    override fun accept(visitor: Visitor) {
        visitor.visitMultiStmt(this)
    }
}

data class GotoStmt(val line: Expr) : Stmt() {
    override fun accept(visitor: Visitor) {
        visitor.visitGotoStmt(this)
    }
}

data class InputStmt(val vars: List<Expr>) : Stmt() {
    override fun accept(visitor: Visitor) {
        visitor.visitInputStmt(this)
    }
}

data class LetStmt(val name: String, val expr: Expr) : Stmt() {
    override fun accept(visitor: Visitor) {
        visitor.visitLetStmt(this)
    }
}

data class GosubStmt(val line: Expr) : Stmt() {
    override fun accept(visitor: Visitor) {
        visitor.visitGosubStmt(this)
    }
}

class ReturnStmt : Stmt() {
    override fun accept(visitor: Visitor) {
        visitor.visitReturnStmt(this)
    }
}

class EndStmt : Stmt() {
    override fun accept(visitor: Visitor) {
        visitor.visitEndStmt(this)
    }
}

data class ProgramStmt(val statements: MutableList<Stmt>) : Stmt() {
    override fun accept(visitor: Visitor) {
        visitor.visitProgramStmt(this)
    }
}

data class LineStmt(val line: Int, val statement: MultiStmt) : Stmt() {
    override fun accept(visitor: Visitor) {
        visitor.visitLineStmt(this)
    }
}