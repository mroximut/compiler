package edu.kit.kastel.vads.compiler.parser.ast;

import java.util.List;

import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;

public record FunctionCallTree(NameTree name, List<ExpressionTree> arguments) implements StatementTree, ExpressionTree {
    @Override
    public Span span() {
        return new Span.SimpleSpan(name().span().start(), arguments.get(arguments.size() - 1).span().end());
    }

    @Override
    public <T, R> R accept(Visitor<T, R> visitor, T data) {
        return visitor.visit(this, data);
    }
}
