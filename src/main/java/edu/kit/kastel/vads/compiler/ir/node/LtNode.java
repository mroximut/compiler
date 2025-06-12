package edu.kit.kastel.vads.compiler.ir.node;

public final class LtNode extends BinaryOperationNode {
    public LtNode(Block block, Node left, Node right) {
        super(block, left, right);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LtNode && super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
} 