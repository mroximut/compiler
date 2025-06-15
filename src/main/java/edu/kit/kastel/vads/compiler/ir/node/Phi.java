package edu.kit.kastel.vads.compiler.ir.node;

public final class Phi extends Node {

    private boolean sideEffectPhi = false;

    public Phi(Block block) {
        super(block);
    }

    public void appendOperand(Node node) {
        addPredecessor(node);
    }

    public void setSideEffectPhi() {
        this.sideEffectPhi = true;
    }

    public boolean isSideEffectPhi() {
        return this.sideEffectPhi;
    }
}
