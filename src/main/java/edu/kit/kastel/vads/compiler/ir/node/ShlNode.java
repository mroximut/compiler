package edu.kit.kastel.vads.compiler.ir.node;

public final class ShlNode extends BinaryOperationNode {
    public ShlNode(Block block, Node left, Node right) {
        super(block, left, right);
    }

    @Override
    public boolean equals(Object obj) {
        return commutativeEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return commutativeHashCode(this);
    }
} 