package edu.kit.kastel.vads.compiler.ir.node;

public final class EqNode extends BinaryOperationNode {
    public EqNode(Block block, Node left, Node right) {
        super(block, left, right);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EqNode && super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
} 