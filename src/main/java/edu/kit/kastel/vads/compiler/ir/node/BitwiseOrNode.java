package edu.kit.kastel.vads.compiler.ir.node;

public final class BitwiseOrNode extends BinaryOperationNode {
    public BitwiseOrNode(Block block, Node left, Node right) {
        super(block, left, right);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BitwiseOrNode && super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
} 