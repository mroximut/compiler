package edu.kit.kastel.vads.compiler.parser.symbol;

record FuncIdentName(String identifier) implements Name {
    @Override
    public String asString() {
        return identifier();
    }
}
