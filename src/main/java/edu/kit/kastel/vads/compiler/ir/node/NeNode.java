package edu.kit.kastel.vads.compiler.ir.node;

public final class NeNode extends BinaryOperationNode {
    public NeNode(Block block, Node left, Node right) {
        super(block, left, right);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof NeNode && super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
} 