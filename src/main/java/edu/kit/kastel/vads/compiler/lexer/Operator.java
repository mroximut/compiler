package edu.kit.kastel.vads.compiler.lexer;

import edu.kit.kastel.vads.compiler.Span;

public record Operator(OperatorType type, Span span) implements Token {

    @Override
    public boolean isOperator(OperatorType operatorType) {
        return type() == operatorType;
    }

    @Override
    public String asString() {
        return type().toString();
    }

    public enum OperatorType {
        // Assignment operators
        ASSIGN("="),
        ASSIGN_PLUS("+="),
        ASSIGN_MINUS("-="),
        ASSIGN_MUL("*="),
        ASSIGN_DIV("/="),
        ASSIGN_MOD("%="),
        ASSIGN_AND("&="),
        ASSIGN_OR("|="),
        ASSIGN_XOR("^="),
        ASSIGN_SHL("<<="),
        ASSIGN_SHR(">>="),

        // Arithmetic operators
        PLUS("+"),
        MINUS("-"),
        MUL("*"),
        DIV("/"),
        MOD("%"),

        // Bitwise operators
        BITWISE_AND("&"),
        BITWISE_OR("|"),
        BITWISE_XOR("^"),
        BITWISE_NOT("~"),
        SHL("<<"),
        SHR(">>"),

        // Logical operators
        LOGICAL_AND("&&"),
        LOGICAL_OR("||"),
        LOGICAL_NOT("!"),

        // Comparison operators
        LT("<"),
        LE("<="),
        GT(">"),
        GE(">="),
        EQ("=="),
        NE("!="),

        // Ternary operator
        QUESTION("?"),
        COLON(":");

        private final String value;

        OperatorType(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }
    }
}
