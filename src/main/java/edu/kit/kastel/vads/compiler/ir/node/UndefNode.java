package edu.kit.kastel.vads.compiler.ir.node;

public final class UndefNode extends Node {
    public UndefNode(Block block) {
        super(block);
    }

    @Override
    protected String info() {
        return "undef";
    }
}
