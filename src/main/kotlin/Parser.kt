import TokenType.*

class Parser(var tokens: List<Token>) {
    private var current = 0
    private var currentLine = LineStmt(0, MultiStmt(mutableListOf()))

    private fun peek(): TokenType {
        return tokens[current].type
    }

    private fun peek(type: TokenType): Boolean {
        return tokens[current].type == type
    }

    private fun eat(type: TokenType): Token {
        if (peek() == type) {
            current++
            return tokens[current - 1]
        } else {
            throw Exception("Expected $type, got ${tokens[current].type} at line ${tokens[current].position.first}.")
        }
    }

    fun program(): ProgramStmt {
        val programStmt = ProgramStmt(mutableListOf())
        while (peek() != EOF) {
            programStmt.statements.add(line())
        }
        return programStmt
    }

    private fun line(): LineStmt {
        return if (peek(NUMBER)) {
            val lineNumber = eat(NUMBER).literal as Int
            currentLine = LineStmt(lineNumber, MultiStmt(mutableListOf(statement())))
            currentLine
        } else {
            currentLine.statement.stmts.add(statement())
            currentLine
        }
    }

    private fun statement(): Stmt {
        return when (peek()) {
            PRINT -> printStatement()
            LET -> letStatement()
            IF -> ifStatement()
            GOTO -> gotoStatement()
            GOSUB -> gosubStatement()
            INPUT -> inputStatement()
            RETURN -> returnStatement()
            END -> endStatement()
            VAR -> {
                val name = eat(VAR).lexeme
                eat(EQUAL)
                val value = expr()
                LetStmt(name, value)
            }
            else -> throw Exception("Expected statement, got ${tokens[current].type}")
        }
    }

    private fun printStatement(): PrintStmt {
        eat(PRINT)
        val expr = exprlist()
        return PrintStmt(expr)
    }

    private fun letStatement(): LetStmt {
        eat(LET)
        val name = eat(VAR).lexeme
        eat(EQUAL)
        val expr = expr()
        return LetStmt(name, expr)
    }

    private fun ifStatement(): IfStmt {
        eat(IF)
        val left = expr()
        val op = eat(peek())
        val right = expr()
        eat(THEN)
        val expr = BinOp(left, op, right)
        val stmt = statement()
        return IfStmt(expr, stmt)
    }

    private fun gotoStatement(): GotoStmt {
        eat(GOTO)
        val lineNumber = expr()
        return GotoStmt(lineNumber)
    }

    private fun gosubStatement(): GosubStmt {
        eat(GOSUB)
        val lineNumber = expr()
        return GosubStmt(lineNumber)
    }

    private fun returnStatement(): ReturnStmt {
        eat(RETURN)
        return ReturnStmt()
    }

    private fun inputStatement(): InputStmt {
        eat(INPUT)
        val vars = varlist()
        return InputStmt(vars)
    }

    private fun endStatement(): EndStmt {
        eat(END)
        return EndStmt()
    }

    private fun expr(): Expr {
        var expr = term()
        while (peek(PLUS) || peek(MINUS)) {
            val op = eat(peek())
            val right = term()
            expr = BinOp(expr, op, right)
        }
        return expr
    }

    private fun term(): Expr {
        var left = factor()
        while (peek(TIMES) || peek(DIVIDE)) {
            val op = eat(peek())
            val right = factor()
            left = BinOp(left, op, right)
        }
        return left
    }

    private fun factor(): Expr {
        return when (peek()) {
            MINUS -> {
                val op = eat(MINUS)
                val expr = factor()
                UnaryOp(op, expr)
            }
            NUMBER -> NumberLiteral(eat(NUMBER).literal as Int)
            LPAREN -> {
                eat(LPAREN)
                val expr = expr()
                eat(RPAREN)
                expr
            }
            else -> vara()
        }
    }

    private fun vara(): Expr {
        return when (peek()) {
            VAR -> Variable(eat(VAR).lexeme)
            STRING -> StringLiteral(eat(STRING).literal as String)
            else -> {throw Exception("Expected variable or string, got ${tokens[current].type} at line ${tokens[current].position.first}.")}
        }
    }

    private fun varlist(): List<Expr> {
        val vars = mutableListOf<Expr>()
        while (peek(VAR) || peek(STRING)) {
            vars.add(vara())
            if (peek(COMMA)) {
                eat(COMMA)
            }
        }
        return vars
    }

    private fun exprlist(): MutableList<Expr> {
        val expr = if (peek(LPAREN)) {
            eat(LPAREN)
            expr().also { eat(RPAREN) }
        } else {
            expr()
        }
        val exprs = mutableListOf<Expr>()
        exprs.add(expr)
        while (peek() == COMMA) {
            eat(COMMA)
            exprs.add(expr())
        }
        return exprs
    }
}