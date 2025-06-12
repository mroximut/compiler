package edu.kit.kastel.vads.compiler.ir.node;

public final class BitwiseAndNode extends BinaryOperationNode {
    public BitwiseAndNode(Block block, Node left, Node right) {
        super(block, left, right);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BitwiseAndNode && super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
} 