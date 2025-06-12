package edu.kit.kastel.vads.compiler.ir.node;

public final class LeNode extends BinaryOperationNode {
    public LeNode(Block block, Node left, Node right) {
        super(block, left, right);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LeNode && super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
} 