package edu.kit.kastel.vads.compiler.ir.node;

public final class BitwiseXorNode extends BinaryOperationNode {
    public BitwiseXorNode(Block block, Node left, Node right) {
        super(block, left, right);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BitwiseXorNode && super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
} 