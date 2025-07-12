package edu.kit.kastel.vads.compiler.parser.type;

import java.util.List;
import java.util.stream.Collectors;

public record FunctionType(Type returnType, List<Type> parameters) implements Type {
    @Override
    public String asString() {
        return "function(" + parameters.stream().map(Type::asString).collect(Collectors.joining(", ")) + ") -> " + returnType.asString();
    }
}
