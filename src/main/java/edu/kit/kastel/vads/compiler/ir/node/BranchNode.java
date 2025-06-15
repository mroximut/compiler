package edu.kit.kastel.vads.compiler.ir.node;

public final class BranchNode extends Node {
    public BranchNode(Block block, Node condition, Block trueBlock, Block falseBlock) {
        super(block, condition);
        this.condition = condition;
        this.trueBlock = trueBlock;
        this.falseBlock = falseBlock;
    }

    private final Node condition;
    private final Block trueBlock;
    private final Block falseBlock;  

    public Node condition() {
        return this.condition;
    }

    public Block trueBlock() {
        return trueBlock;
    }

    public Block falseBlock() {
        return falseBlock;
    }
} 