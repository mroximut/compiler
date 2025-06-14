package edu.kit.kastel.vads.compiler.ir.node;

public final class EqNode extends BinaryOperationNode {
    public EqNode(Block block, Node left, Node right) {
        super(block, left, right);
    }
} 