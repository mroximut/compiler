package edu.kit.kastel.vads.compiler.ir.node;

public final class BitwiseNotNode extends Node {
    public BitwiseNotNode(Block block, Node operand) {
        super(block, operand);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BitwiseNotNode not)) {
            return false;
        }
        return block() == not.block() && predecessor(0) == not.predecessor(0);
    }

    @Override
    public int hashCode() {
        return block().hashCode() * 31 + predecessor(0).hashCode();
    }
} 