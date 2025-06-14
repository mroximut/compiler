package edu.kit.kastel.vads.compiler.ir;

import edu.kit.kastel.vads.compiler.ir.node.AddNode;
import edu.kit.kastel.vads.compiler.ir.node.BitwiseAndNode;
import edu.kit.kastel.vads.compiler.ir.node.BitwiseNotNode;
import edu.kit.kastel.vads.compiler.ir.node.BitwiseOrNode;
import edu.kit.kastel.vads.compiler.ir.node.BitwiseXorNode;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.BranchNode;
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
import edu.kit.kastel.vads.compiler.ir.node.DivNode;
import edu.kit.kastel.vads.compiler.ir.node.EqNode;
import edu.kit.kastel.vads.compiler.ir.node.GeNode;
import edu.kit.kastel.vads.compiler.ir.node.GtNode;
import edu.kit.kastel.vads.compiler.ir.node.JumpNode;
import edu.kit.kastel.vads.compiler.ir.node.LeNode;
import edu.kit.kastel.vads.compiler.ir.node.LogicalNotNode;
import edu.kit.kastel.vads.compiler.ir.node.LtNode;
import edu.kit.kastel.vads.compiler.ir.node.ModNode;
import edu.kit.kastel.vads.compiler.ir.node.MulNode;
import edu.kit.kastel.vads.compiler.ir.node.NeNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.Phi;
import edu.kit.kastel.vads.compiler.ir.node.ProjNode;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;
import edu.kit.kastel.vads.compiler.ir.node.ShlNode;
import edu.kit.kastel.vads.compiler.ir.node.ShrNode;
import edu.kit.kastel.vads.compiler.ir.node.StartNode;
import edu.kit.kastel.vads.compiler.ir.node.SubNode;
import edu.kit.kastel.vads.compiler.ir.node.UndefNode;
import edu.kit.kastel.vads.compiler.ir.optimize.Optimizer;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class GraphConstructor {

    private final Optimizer optimizer;
    private final IrGraph graph;
    private final Map<Name, Map<Block, Node>> currentDef = new HashMap<>();
    private final Map<Block, Map<Name, Phi>> incompletePhis = new HashMap<>();
    private final Map<Block, Node> currentSideEffect = new HashMap<>();
    private final Map<Block, Phi> incompleteSideEffectPhis = new HashMap<>();
    private final Set<Block> sealedBlocks = new HashSet<>();
    private Block currentBlock;
    private int blockCounter = 0;

    public GraphConstructor(Optimizer optimizer, String name) {
        this.optimizer = optimizer;
        this.graph = new IrGraph(name);
        this.currentBlock = this.graph.startBlock();
        // the start block never gets any more predecessors
        sealBlock(this.currentBlock);
    }

    public Node newStart() {
        assert currentBlock() == this.graph.startBlock() : "start must be in start block";
        return new StartNode(currentBlock());
    }

    public Node newAdd(Node left, Node right) {
        return this.optimizer.transform(new AddNode(currentBlock(), left, right));
    }
    public Node newSub(Node left, Node right) {
        return this.optimizer.transform(new SubNode(currentBlock(), left, right));
    }

    public Node newMul(Node left, Node right) {
        return this.optimizer.transform(new MulNode(currentBlock(), left, right));
    }

    public Node newDiv(Node left, Node right) {
        return this.optimizer.transform(new DivNode(currentBlock(), left, right, readCurrentSideEffect()));
    }

    public Node newMod(Node left, Node right) {
        return this.optimizer.transform(new ModNode(currentBlock(), left, right, readCurrentSideEffect()));
    }

    public Node newBitwiseNot(Node operand) {
        return this.optimizer.transform(new BitwiseNotNode(currentBlock(), operand));
    }

    public Node newLogicalNot(Node operand) {
        return this.optimizer.transform(new LogicalNotNode(currentBlock(), operand));
    }

    public Node newShl(Node left, Node right) {
        return this.optimizer.transform(new ShlNode(currentBlock(), left, right));
    }

    public Node newShr(Node left, Node right) {
        return this.optimizer.transform(new ShrNode(currentBlock(), left, right));
    }

    public Node newReturn(Node result) {
        return new ReturnNode(currentBlock(), readCurrentSideEffect(), result);
    }

    public Node newConstInt(int value) {
        // always move const into start block, this allows better deduplication
        // and resultingly in better value numbering
        return this.optimizer.transform(new ConstIntNode(this.graph.startBlock(), value));
    }

    public Node newSideEffectProj(Node node) {
        return new ProjNode(currentBlock(), node, ProjNode.SimpleProjectionInfo.SIDE_EFFECT);
    }

    public Node newResultProj(Node node) {
        return new ProjNode(currentBlock(), node, ProjNode.SimpleProjectionInfo.RESULT);
    }

    public Block currentBlock() {
        return this.currentBlock;
    }

    public Phi newPhi() {
        // don't transform phi directly, it is not ready yet
        return new Phi(currentBlock());
    }

    public IrGraph graph() {
        return this.graph;
    }

    void writeVariable(Name variable, Block block, Node value) {
        this.currentDef.computeIfAbsent(variable, _ -> new HashMap<>()).put(block, value);
    }

    Node readVariable(Name variable, Block block) {
        Node node = this.currentDef.getOrDefault(variable, Map.of()).get(block);
        if (node != null) {
            return node;
        }
        return readVariableRecursive(variable, block);
    }


    private Node readVariableRecursive(Name variable, Block block) {
        Node val;
        if (!this.sealedBlocks.contains(block)) {
            val = new Phi(block);
            this.incompletePhis.computeIfAbsent(block, _ -> new HashMap<>()).put(variable, (Phi) val);
        } else if (block.predecessors().size() == 1) {
            val = readVariable(variable, block.predecessors().getFirst().block());
        } else {
            val = new Phi(block);
            writeVariable(variable, block, val);
            val = addPhiOperands(variable, (Phi) val);
        }
        writeVariable(variable, block, val);
        return val;
    }

    Node addPhiOperands(Name variable, Phi phi) {
        for (Node pred : phi.block().predecessors()) {
            phi.appendOperand(readVariable(variable, pred.block()));
        }
        return tryRemoveTrivialPhi(phi);
    }

    Node tryRemoveTrivialPhi(Phi phi) {
        Node same = null;
        for (Node op : phi.predecessors()) {
            if (op == same || op == phi) {
                continue; // unique value or self-reference
            }
            if (same != null) {
                return phi; // the phi merges at least two values: not trivial
            }
            same = op;
        }

        if (same == null) {
            same = this.newUndef(); // phi is unreachable or in the start block
        }

        // Remember all users except the phi itself
        Set<Node> users = new HashSet<>(phi.graph().successors(phi));
        users.remove(phi);

        // Reroute all uses of phi to same and remove phi
        for (Node use : users) {
            for (int i = 0; i < use.predecessors().size(); i++) {
                if (use.predecessor(i) == phi) {
                    use.setPredecessor(i, same);
                }
            }
        }

        // Try to recursively remove all phi users, which might have become trivial
        for (Node use : users) {
            if (use instanceof Phi) {
                tryRemoveTrivialPhi((Phi) use);
            }
        }
        return same;
    }

    void sealBlock(Block block) {
        for (Map.Entry<Name, Phi> entry : this.incompletePhis.getOrDefault(block, Map.of()).entrySet()) {
            addPhiOperands(entry.getKey(), entry.getValue());
        }
        Phi sideEffectPhi = this.incompleteSideEffectPhis.get(block);
        if (sideEffectPhi != null) {
            addPhiOperands(sideEffectPhi);
        }
        this.sealedBlocks.add(block);
    }

    public void writeCurrentSideEffect(Node node) {
        writeSideEffect(currentBlock(), node);
    }

    private void writeSideEffect(Block block, Node node) {
        this.currentSideEffect.put(block, node);
    }

    public Node readCurrentSideEffect() {
        return readSideEffect(currentBlock());
    }

    private Node readSideEffect(Block block) {
        Node node = this.currentSideEffect.get(block);
        if (node != null) {
            return node;
        }
        return readSideEffectRecursive(block);
    }

    private Node readSideEffectRecursive(Block block) {
        Node val;
        if (!this.sealedBlocks.contains(block)) {
            val = new Phi(block);
            Phi old = this.incompleteSideEffectPhis.put(block, (Phi) val);
            assert old == null : "double readSideEffectRecursive for " + block;
        } else if (block.predecessors().size() == 1) {
            val = readSideEffect(block.predecessors().getFirst().block());
        } else {
            val = new Phi(block);
            writeSideEffect(block, val);
            val = addPhiOperands((Phi) val);
        }
        writeSideEffect(block, val);
        return val;
    }

    Node addPhiOperands(Phi phi) {
        for (Node pred : phi.block().predecessors()) {
            phi.appendOperand(readSideEffect(pred.block()));
        }
        return tryRemoveTrivialPhi(phi);
    }

    public Node newLt(Node left, Node right) {
        return this.optimizer.transform(new LtNode(this.currentBlock, left, right));
    }

    public Node newLe(Node left, Node right) {
        return this.optimizer.transform(new LeNode(this.currentBlock, left, right));
    }

    public Node newGt(Node left, Node right) {
        return this.optimizer.transform(new GtNode(this.currentBlock, left, right));
    }

    public Node newGe(Node left, Node right) {
        return this.optimizer.transform(new GeNode(this.currentBlock, left, right));
    }

    public Node newEq(Node left, Node right) {
        return this.optimizer.transform(new EqNode(this.currentBlock, left, right));
    }

    public Node newNe(Node left, Node right) {
        return this.optimizer.transform(new NeNode(this.currentBlock, left, right));
    }

    public Node newBitwiseAnd(Node left, Node right) {
        return this.optimizer.transform(new BitwiseAndNode(this.currentBlock, left, right));
    }

    public Node newBitwiseXor(Node left, Node right) {
        return this.optimizer.transform(new BitwiseXorNode(this.currentBlock, left, right));
    }

    public Node newBitwiseOr(Node left, Node right) {
        return this.optimizer.transform(new BitwiseOrNode(this.currentBlock, left, right));
    }

    public Block newBlock() {
        return new Block(this.graph, "block" + (blockCounter++));
    }

    public Node newUndef() {
        return new UndefNode(this.currentBlock);
    }

    public void newBranch(Block block, Node condition, Block trueBlock, Block falseBlock) {
        BranchNode branch = new BranchNode(block, condition, trueBlock, falseBlock);
        trueBlock.addPredecessor(branch);
        falseBlock.addPredecessor(branch);
    }

    public void newJump(Block from, Block to) {
        JumpNode jump = new JumpNode(from, to);
        to.addPredecessor(jump);
    }

    public void setCurrentBlock(Block block) {
        this.currentBlock = block;
    }

}
