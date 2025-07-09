package edu.kit.kastel.vads.compiler.ir;

import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.DivNode;
import edu.kit.kastel.vads.compiler.ir.node.ModNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.optimize.Optimizer;
import edu.kit.kastel.vads.compiler.ir.util.DebugInfo;
import edu.kit.kastel.vads.compiler.ir.util.DebugInfoHelper;
import edu.kit.kastel.vads.compiler.parser.ast.AssignmentTree;
import edu.kit.kastel.vads.compiler.parser.ast.BinaryOperationTree;
import edu.kit.kastel.vads.compiler.parser.ast.BlockTree;
import edu.kit.kastel.vads.compiler.parser.ast.BooleanLiteralTree;
import edu.kit.kastel.vads.compiler.parser.ast.BreakTree;
import edu.kit.kastel.vads.compiler.parser.ast.ContinueTree;
import edu.kit.kastel.vads.compiler.parser.ast.DeclarationTree;
import edu.kit.kastel.vads.compiler.parser.ast.ForTree;
import edu.kit.kastel.vads.compiler.parser.ast.FunctionTree;
import edu.kit.kastel.vads.compiler.parser.ast.IdentExpressionTree;
import edu.kit.kastel.vads.compiler.parser.ast.IfTree;
import edu.kit.kastel.vads.compiler.parser.ast.LValueIdentTree;
import edu.kit.kastel.vads.compiler.parser.ast.LiteralTree;
import edu.kit.kastel.vads.compiler.parser.ast.NameTree;
import edu.kit.kastel.vads.compiler.parser.ast.NoOpTree;
import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.ast.ReturnTree;
import edu.kit.kastel.vads.compiler.parser.ast.StatementTree;
import edu.kit.kastel.vads.compiler.parser.ast.Tree;
import edu.kit.kastel.vads.compiler.parser.ast.TypeTree;
import edu.kit.kastel.vads.compiler.parser.ast.UnaryOperationTree;
import edu.kit.kastel.vads.compiler.parser.ast.WhileTree;
import edu.kit.kastel.vads.compiler.parser.ast.TernaryTree;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;
import edu.kit.kastel.vads.compiler.ir.node.Phi;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.BinaryOperator;

/// SSA translation as described in
/// [`Simple and Efficient Construction of Static Single Assignment Form`](https://compilers.cs.uni-saarland.de/papers/bbhlmz13cc.pdf).
///
/// This implementation also tracks side effect edges that can be used to avoid reordering of operations that cannot be
/// reordered.
///
/// We recommend to read the paper to better understand the mechanics implemented here.
public class SsaTranslation {
    private final FunctionTree function;
    private final GraphConstructor constructor;

    public SsaTranslation(FunctionTree function, Optimizer optimizer) {
        this.function = function;
        this.constructor = new GraphConstructor(optimizer, function.name().name().asString());
    }

    public IrGraph translate() {
        var visitor = new SsaTranslationVisitor();
        this.function.accept(visitor, this);
        return this.constructor.graph();
    }

    private void writeVariable(Name variable, Block block, Node value) {
        this.constructor.writeVariable(variable, block, value);
    }

    private Node readVariable(Name variable, Block block) {
        return this.constructor.readVariable(variable, block);
    }

    private Block currentBlock() {
        return this.constructor.currentBlock();
    }

    private static class SsaTranslationVisitor implements Visitor<SsaTranslation, Optional<Node>> {

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private static final Optional<Node> NOT_AN_EXPRESSION = Optional.empty();

        private final Deque<DebugInfo> debugStack = new ArrayDeque<>();
        private final Deque<Block> loopHeadersAndIncrs = new ArrayDeque<>();
        private final Deque<Block> loopExits = new ArrayDeque<>();

        private void pushSpan(Tree tree) {
            this.debugStack.push(DebugInfoHelper.getDebugInfo());
            DebugInfoHelper.setDebugInfo(new DebugInfo.SourceInfo(tree.span()));
        }

        private void popSpan() {
            DebugInfoHelper.setDebugInfo(this.debugStack.pop());
        }

        @Override
        public Optional<Node> visit(AssignmentTree assignmentTree, SsaTranslation data) {
            pushSpan(assignmentTree);
            BinaryOperator<Node> desugar = switch (assignmentTree.operator().type()) {
                case ASSIGN_MINUS -> data.constructor::newSub;
                case ASSIGN_PLUS -> data.constructor::newAdd;
                case ASSIGN_MUL -> data.constructor::newMul;
                case ASSIGN_DIV -> (lhs, rhs) -> projResultDivMod(data, data.constructor.newDiv(lhs, rhs));
                case ASSIGN_MOD -> (lhs, rhs) -> projResultDivMod(data, data.constructor.newMod(lhs, rhs));
                case ASSIGN_AND -> (lhs, rhs) -> data.constructor.newBitwiseAnd(lhs, rhs);
                case ASSIGN_OR -> (lhs, rhs) -> data.constructor.newBitwiseOr(lhs, rhs);
                case ASSIGN_XOR -> (lhs, rhs) -> data.constructor.newBitwiseXor(lhs, rhs);
                case ASSIGN_SHL -> (lhs, rhs) -> data.constructor.newShl(lhs, rhs);
                case ASSIGN_SHR -> (lhs, rhs) -> data.constructor.newShr(lhs, rhs);
                case ASSIGN -> null;
                default ->
                    throw new IllegalArgumentException("not an assignment operator " + assignmentTree.operator());
            };

            switch (assignmentTree.lValue()) {
                case LValueIdentTree(var name) -> {
                    Node rhs = assignmentTree.expression().accept(this, data).orElseThrow();
                    if (desugar != null) {
                        rhs = desugar.apply(data.readVariable(name.name(), data.currentBlock()), rhs);
                    }
                    data.writeVariable(name.name(), data.currentBlock(), rhs);
                }
            }
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(BinaryOperationTree binaryOperationTree, SsaTranslation data) {
            pushSpan(binaryOperationTree);

            Node resLogical = switch (binaryOperationTree.operatorType()) {
                case LOGICAL_OR -> new TernaryTree(
                    binaryOperationTree.lhs(),
                    new BooleanLiteralTree(true, binaryOperationTree.span()),
                    binaryOperationTree.rhs(),
                    binaryOperationTree.span()
                ).accept(this, data).orElseThrow();
                case LOGICAL_AND -> new TernaryTree(
                    binaryOperationTree.lhs(),
                    binaryOperationTree.rhs(),
                    new BooleanLiteralTree(false, binaryOperationTree.span()),
                    binaryOperationTree.span()
                ).accept(this, data).orElseThrow();
                default -> null;
            };

            if (resLogical != null) {
                popSpan();
                return Optional.of(resLogical);
            }

            Node lhs = binaryOperationTree.lhs().accept(this, data).orElseThrow();
            Node rhs = binaryOperationTree.rhs().accept(this, data).orElseThrow();
            Node res = switch (binaryOperationTree.operatorType()) {
                case MINUS -> data.constructor.newSub(lhs, rhs);
                case PLUS -> data.constructor.newAdd(lhs, rhs);
                case MUL -> data.constructor.newMul(lhs, rhs);
                case DIV -> projResultDivMod(data, data.constructor.newDiv(lhs, rhs));
                case MOD -> projResultDivMod(data, data.constructor.newMod(lhs, rhs));
                case SHL -> data.constructor.newShl(lhs, rhs);
                case SHR -> data.constructor.newShr(lhs, rhs);
                case LT -> data.constructor.newLt(lhs, rhs);
                case LE -> data.constructor.newLe(lhs, rhs);
                case GT -> data.constructor.newGt(lhs, rhs);
                case GE -> data.constructor.newGe(lhs, rhs);
                case EQ -> data.constructor.newEq(lhs, rhs);
                case NE -> data.constructor.newNe(lhs, rhs);
                case BITWISE_AND -> data.constructor.newBitwiseAnd(lhs, rhs);
                case BITWISE_XOR -> data.constructor.newBitwiseXor(lhs, rhs);
                case BITWISE_OR -> data.constructor.newBitwiseOr(lhs, rhs);

                default ->
                    throw new IllegalArgumentException("not a binary expression operator " + binaryOperationTree.operatorType());
            };
            popSpan();
            return Optional.of(res);
        }

        @Override
        public Optional<Node> visit(NoOpTree noOpTree, SsaTranslation data) {
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(BlockTree blockTree, SsaTranslation data) {
            pushSpan(blockTree);
            for (StatementTree statement : blockTree.statements()) {
                statement.accept(this, data);
                // skip everything after a return in a block
                if (statement instanceof ReturnTree || statement instanceof BreakTree || statement instanceof ContinueTree) {
                    break;
                }
            }
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(DeclarationTree declarationTree, SsaTranslation data) {
            pushSpan(declarationTree);
            if (declarationTree.initializer() != null) {
                Node rhs = declarationTree.initializer().accept(this, data).orElseThrow();
                data.writeVariable(declarationTree.name().name(), data.currentBlock(), rhs);
            }
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(FunctionTree functionTree, SsaTranslation data) {
            pushSpan(functionTree);
            Node start = data.constructor.newStart();
            data.constructor.writeCurrentSideEffect(data.constructor.newSideEffectProj(start));
            functionTree.body().accept(this, data);
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(IdentExpressionTree identExpressionTree, SsaTranslation data) {
            pushSpan(identExpressionTree);
            Node value = data.readVariable(identExpressionTree.name().name(), data.currentBlock());
            popSpan();
            return Optional.of(value);
        }

        @Override
        public Optional<Node> visit(LiteralTree literalTree, SsaTranslation data) {
            pushSpan(literalTree);
            Node node = data.constructor.newConstInt((int) literalTree.parseValue().orElseThrow());
            popSpan();
            return Optional.of(node);
        }

        @Override
        public Optional<Node> visit(LValueIdentTree lValueIdentTree, SsaTranslation data) {
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(NameTree nameTree, SsaTranslation data) {
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(UnaryOperationTree unaryOperationTree, SsaTranslation data) {
            pushSpan(unaryOperationTree);
            Node node = unaryOperationTree.expression().accept(this, data).orElseThrow();
            Node res = switch (unaryOperationTree.operatorType()) {
                case MINUS -> data.constructor.newSub(data.constructor.newConstInt(0), node);
                case BITWISE_NOT -> data.constructor.newBitwiseNot(node);
                case LOGICAL_NOT -> data.constructor.newLogicalNot(node);
                default -> throw new IllegalArgumentException("Unknown unary operator: " + unaryOperationTree.operatorType());
            };
            popSpan();
            return Optional.of(res);
        }

        @Override
        public Optional<Node> visit(BooleanLiteralTree booleanLiteralTree, SsaTranslation data) {
            pushSpan(booleanLiteralTree);
            Node node = data.constructor.newConstInt(booleanLiteralTree.value() ? 1 : 0);
            popSpan();
            return Optional.of(node);
        }

        @Override
        public Optional<Node> visit(ProgramTree programTree, SsaTranslation data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Node> visit(ReturnTree returnTree, SsaTranslation data) {
            pushSpan(returnTree);
            Node node = returnTree.expression().accept(this, data).orElseThrow();
            Node ret = data.constructor.newReturn(node);
            data.constructor.graph().endBlock().addPredecessor(ret);
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(TypeTree typeTree, SsaTranslation data) {
            throw new UnsupportedOperationException();
        }

        private Node projResultDivMod(SsaTranslation data, Node divMod) {
            // make sure we actually have a div or a mod, as optimizations could
            // have changed it to something else already
            if (!(divMod instanceof DivNode || divMod instanceof ModNode)) {
                return divMod;
            }
            Node projSideEffect = data.constructor.newSideEffectProj(divMod);
            data.constructor.writeCurrentSideEffect(projSideEffect);
            return data.constructor.newResultProj(divMod);
        }

        @Override
        public Optional<Node> visit(BreakTree breakTree, SsaTranslation data) {
            pushSpan(breakTree);
            if (loopExits.isEmpty()) {
                throw new IllegalStateException("break statement outside of loop");
            }
            data.constructor.newJump(data.currentBlock(), loopExits.peek());
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(ContinueTree continueTree, SsaTranslation data) {
            pushSpan(continueTree);
            if (loopHeadersAndIncrs.isEmpty()) {
                throw new IllegalStateException("continue statement outside of loop");
            }
            data.constructor.newJump(data.currentBlock(), loopHeadersAndIncrs.peek());
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(ForTree forTree, SsaTranslation data) {
            pushSpan(forTree);
            
            forTree.initializer().accept(this, data);
            // new WhileTree(forTree.condition(), 
            //               new BlockTree(List.of(forTree.body(), forTree.increment()), forTree.span()), 
            //               forTree.span()).accept(this, data);

            // Create blocks for the while loop structure
            Block headerBlock = data.constructor.newBlock();  // Block containing condition check
            Block bodyBlock = data.constructor.newBlock();    // Block containing loop body
            Block exitBlock = data.constructor.newBlock();    // Block for after the loop
            Block incrementBlock = data.constructor.newBlock();    // Block for the increment

            // Track loop blocks
            loopHeadersAndIncrs.push(incrementBlock);
            loopExits.push(exitBlock);

            // Jump from current block to header block where condition is checked
            data.constructor.newJump(data.currentBlock(), headerBlock);

            // Set current block to header and evaluate the condition
            data.constructor.setCurrentBlock(headerBlock);
            Node condition = forTree.condition().accept(this, data).orElseThrow();

            // Create branch based on condition - if true go to body, if false exit loop
            data.constructor.newBranch(data.currentBlock(), condition, bodyBlock, exitBlock);

            // Process the loop body
            data.constructor.setCurrentBlock(bodyBlock);
            data.constructor.sealBlock(bodyBlock);
            forTree.body().accept(this, data);

            if (!returnsBreaksContinues(forTree.body())) {
                data.constructor.newJump(data.currentBlock(), incrementBlock);
            }
            data.constructor.setCurrentBlock(incrementBlock);
            forTree.increment().accept(this, data);
            data.constructor.sealBlock(incrementBlock);
            data.constructor.newJump(data.currentBlock(), headerBlock);
            data.constructor.sealBlock(headerBlock);

            // Continue with exit block after loop
            data.constructor.setCurrentBlock(exitBlock);
            data.constructor.sealBlock(exitBlock);

            // Pop loop tracking
            loopHeadersAndIncrs.pop();
            loopExits.pop();

            popSpan();
            return NOT_AN_EXPRESSION;
            
        }

        @Override
        public Optional<Node> visit(IfTree ifTree, SsaTranslation data) {
            pushSpan(ifTree);
            // Create blocks for then, else, and merge
            Block thenBlock = data.constructor.newBlock();
            Block mergeBlock = data.constructor.newBlock();
            Block elseBlock = ifTree.elseBranch() != null ? data.constructor.newBlock() : mergeBlock;
            
            // Evaluate condition in current block
            Node condition = ifTree.condition().accept(this, data).orElseThrow();
            
            // Create branch node
            data.constructor.newBranch(data.currentBlock(), condition, thenBlock, elseBlock);
            
            // Process then branch
            data.constructor.setCurrentBlock(thenBlock);
            data.constructor.sealBlock(thenBlock);
            ifTree.thenBranch().accept(this, data);
            
            if (!returnsBreaksContinues(ifTree.thenBranch())) {
                data.constructor.newJump(data.currentBlock(), mergeBlock);
            }
            
            // Process else branch if it exists
            if (ifTree.elseBranch() != null) {
                data.constructor.setCurrentBlock(elseBlock);
                data.constructor.sealBlock(elseBlock);
                ifTree.elseBranch().accept(this, data);
                if (!returnsBreaksContinues(ifTree.elseBranch())) {
                    data.constructor.newJump(data.currentBlock(), mergeBlock);
                }
            }
            
            // Continue with merge block
            data.constructor.setCurrentBlock(mergeBlock);
            data.constructor.sealBlock(mergeBlock);
            
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(WhileTree whileTree, SsaTranslation data) {
            pushSpan(whileTree);
            
            // Create blocks for the while loop structure
            Block headerBlock = data.constructor.newBlock();  // Block containing condition check
            Block bodyBlock = data.constructor.newBlock();    // Block containing loop body
            Block exitBlock = data.constructor.newBlock();    // Block for after the loop

            // Track loop blocks
            loopHeadersAndIncrs.push(headerBlock);
            loopExits.push(exitBlock);

            // Jump from current block to header block where condition is checked
            data.constructor.newJump(data.currentBlock(), headerBlock);

            // Set current block to header and evaluate the condition
            data.constructor.setCurrentBlock(headerBlock);
            Node condition = whileTree.condition().accept(this, data).orElseThrow();

            // Create branch based on condition - if true go to body, if false exit loop
            data.constructor.newBranch(data.currentBlock(), condition, bodyBlock, exitBlock);

            // Process the loop body
            data.constructor.setCurrentBlock(bodyBlock);
            data.constructor.sealBlock(bodyBlock);
            whileTree.body().accept(this, data);

            if (!returnsBreaksContinues(whileTree.body())) {
                data.constructor.newJump(data.currentBlock(), headerBlock);
            }
            data.constructor.sealBlock(headerBlock);

            // Continue with exit block after loop
            data.constructor.setCurrentBlock(exitBlock);
            data.constructor.sealBlock(exitBlock);

            // Pop loop tracking
            loopHeadersAndIncrs.pop();
            loopExits.pop();

            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(TernaryTree ternaryTree, SsaTranslation data) {
            pushSpan(ternaryTree);
            
            // Create blocks for the ternary structure
            Block trueBlock = data.constructor.newBlock();
            Block falseBlock = data.constructor.newBlock();
            Block mergeBlock = data.constructor.newBlock();
            
            // Evaluate condition in current block
            Node condition = ternaryTree.condition().accept(this, data).orElseThrow();
            
            // Create branch node
            data.constructor.newBranch(data.currentBlock(), condition, trueBlock, falseBlock);
            //System.out.println("Creating branch from" + data.currentBlock().label() + " to " + trueBlock.label() + " and " + falseBlock.label());
            
            // Process true expression
            data.constructor.setCurrentBlock(trueBlock);
            data.constructor.sealBlock(trueBlock);
            Node trueValue = ternaryTree.trueExpr().accept(this, data).orElseThrow();
            data.constructor.newJump(data.currentBlock(), mergeBlock);
            
            // Process false expression
            data.constructor.setCurrentBlock(falseBlock);
            data.constructor.sealBlock(falseBlock);
            Node falseValue = ternaryTree.falseExpr().accept(this, data).orElseThrow();
            data.constructor.newJump(data.currentBlock(), mergeBlock);
            //System.out.println("Creating jump from" + falseBlock.label());
            
            // Continue with merge block
            data.constructor.setCurrentBlock(mergeBlock);
            data.constructor.sealBlock(mergeBlock);
            
            // Create phi node to merge the results
            Phi phi = data.constructor.newPhi();
            phi.appendOperand(trueValue);
            phi.appendOperand(falseValue);
            
            popSpan();
            return Optional.of(phi);
        }

        private boolean returnsBreaksContinues(StatementTree statement) {
            if (statement instanceof ReturnTree || statement instanceof BreakTree || statement instanceof ContinueTree) {
                return true;
            }
            if (statement instanceof BlockTree block) {
                for (StatementTree s : block.statements()) {
                    if (returnsBreaksContinues(s)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }


}
