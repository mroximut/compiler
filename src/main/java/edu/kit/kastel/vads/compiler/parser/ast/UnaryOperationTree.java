package edu.kit.kastel.vads.compiler.parser.ast;

import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.lexer.Operator.OperatorType;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;

public record UnaryOperationTree(
    ExpressionTree expression, OperatorType operatorType, Span operatorSpan
) implements ExpressionTree {

    @Override
    public Span span() {
        return operatorSpan.merge(expression().span());
    }

    @Override
    public <T, R> R accept(Visitor<T, R> visitor, T data) {
        return visitor.visit(this, data);
    }
} 