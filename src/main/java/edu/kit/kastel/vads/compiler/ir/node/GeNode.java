package edu.kit.kastel.vads.compiler.ir.node;

public final class GeNode extends BinaryOperationNode {
    public GeNode(Block block, Node left, Node right) {
        super(block, left, right);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof GeNode && super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
} 