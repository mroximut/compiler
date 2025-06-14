package edu.kit.kastel.vads.compiler.ir.node;

public final class JumpNode extends Node {
    public JumpNode(Block from, Block to) {
        super(from);
        this.from = from;
        this.to = to;
    }

    private final Block to;
    private final Block from;

    public Block from() {
        return from;
    }

    public Block to() {
        return to;
    }

    @Override
    protected String info() {
        return " from " + from.toString() + " to " + to.toString();
    }
} 