import TokenType.*

data class Token(var type: TokenType, var lexeme: String, var literal: Any?, var position: Pair<Int, Int>)

enum class TokenType {
    STRING, NUMBER, LESS_THAN, GREATER_THAN, LESS_EQUAL, GREATER_EQUAL, EQUAL, PLUS, MINUS, TIMES, DIVIDE, LPAREN, RPAREN,
    PRINT, IF, GOTO, INPUT, LET, GOSUB, RETURN, END, VAR, COMMA, THEN, NOTEQUAL,
    EOF
}

class Scanner(var text: String) {
    private var start = 0
    private var current = 0
    private var line = 0

    var keywords = mapOf(
        "print" to PRINT,
        "PRINT" to PRINT,
        "if" to IF,
        "IF" to IF,
        "goto" to GOTO,
        "GOTO" to GOTO,
        "input" to INPUT,
        "INPUT" to INPUT,
        "let" to LET,
        "LET" to LET,
        "gosub" to GOSUB,
        "GOSUB" to GOSUB,
        "return" to RETURN,
        "RETURN" to RETURN,
        "end" to END,
        "END" to END,
        "then" to THEN,
        "THEN" to THEN,
    )

    private var tokens = mutableListOf<Token>()

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }
        tokens.add(Token(EOF, "", null, Pair(line, current)))
        return tokens
    }

    private fun isAtEnd(): Boolean {
        return current >= text.length
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (text[current] != expected) return false
        current++
        return true
    }

    private fun scanToken() {
        when (val c = advance()) {
            ' ', '\r', '\t' -> {
                // ignore
            }
            '\n' -> {
                line++
            }
            '"' -> {
                string()
            }
            '+' -> {
                addToken(PLUS)
            }
            '-' -> {
                addToken(MINUS)
            }
            '*' -> {
                addToken(TIMES)
            }
            '/' -> {
                addToken(DIVIDE)
            }
            '<' -> {
                if(match('>')) {
                    addToken(NOTEQUAL)
                } else if(match('=')) {
                    addToken(LESS_EQUAL)
                } else {
                    addToken(LESS_THAN)
                }
            }
            '>' -> {
                if (match('=')) {
                    addToken(GREATER_EQUAL)
                } else {
                    addToken(GREATER_THAN)
                }
            }
            '=' -> {
                addToken(EQUAL)
            }
            ',' -> {
                addToken(COMMA)
            }
            '(' -> {
                addToken(LPAREN)
            }
            ')' -> {
                addToken(RPAREN)
            }
            else -> {
                when {
                    isDigit(c) -> number()
                    isAlpha(c) -> identifier()
                    else -> {
                        error("Unexpected character.")
                    }
                }
            }
        }
    }

    private fun isDigit(c: Char): Boolean {
        return c in '0'..'9'
    }

    private fun isAlpha(c: Char): Boolean {
        return c in 'a'..'z' || c in 'A'..'Z' || c == '_'
    }

    private fun peek(): Char {
        return if (isAtEnd()) (0).toChar() else text[current]
    }

    private fun advance(): Char {
        current++
        return text[current - 1]
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') {
                line++
            }
            advance()
        }
        if (isAtEnd()) {
            error("Unterminated string.")
        }
        advance()
        addToken(STRING, text.substring(start + 1, current - 1))
    }

    private fun number() {
        while (peek().isDigit()) {
            advance()
        }
        addToken(NUMBER, text.substring(start, current).toInt())
    }

    private fun addToken(type: TokenType) {
        addToken(type, null)
    }

    private fun addToken(type: TokenType, literal: Any?) {
        addToken(type, literal, Pair(line, current))
    }

    private fun addToken(type: TokenType, literal: Any?, position: Pair<Int, Int>) {
        tokens.add(Token(type, text.substring(start, current), literal, position))
    }

    private fun isAlphaNumeric(c: Char): Boolean {
        return isAlpha(c) || isDigit(c)
    }

    private fun identifier() {
        while (isAlphaNumeric(peek())) {
            advance()
        }
        val text = text.substring(start, current)
        val type = keywords[text] ?: VAR
        addToken(type)
    }
}