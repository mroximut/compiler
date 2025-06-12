package edu.kit.kastel.vads.compiler.ir.node;

public final class JumpNode extends Node {
    public JumpNode(Block from, Block to) {
        super(from);
        this.to = to;
    }

    private final Block to;

    public Block to() {
        return to;
    }
} 