package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.BlockTree;
import edu.kit.kastel.vads.compiler.parser.ast.FunctionTree;
import edu.kit.kastel.vads.compiler.parser.ast.IfTree;
import edu.kit.kastel.vads.compiler.parser.ast.ReturnTree;
import edu.kit.kastel.vads.compiler.parser.ast.StatementTree;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;
import java.util.List;

class ReturnAnalysis implements NoOpVisitor<List<StatementTree>> {

    @Override
    public Unit visit(ReturnTree returnTree, List<StatementTree> data) {
        data.add(returnTree);
        return NoOpVisitor.super.visit(returnTree, data);
    }

    @Override
    public Unit visit(BlockTree blockTree, List<StatementTree> data) {
        for (StatementTree statement : blockTree.statements()) {
            if (data.contains(statement)) {
                data.add(blockTree);
                break;
            }
        }
        return NoOpVisitor.super.visit(blockTree, data);
    }

    @Override
    public Unit visit(IfTree ifTree, List<StatementTree> data) {
        if (data.contains(ifTree.thenBranch())) {
            if (ifTree.elseBranch() != null && data.contains(ifTree.elseBranch())) {
                data.add(ifTree);
            }
        }
        return NoOpVisitor.super.visit(ifTree, data);
    }

    @Override
    public Unit visit(FunctionTree functionTree, List<StatementTree> data) {
        if (!data.contains(functionTree.body())) {
            throw new SemanticException("function " + functionTree.name() + " does not return");
        }
        data.clear();
        return NoOpVisitor.super.visit(functionTree, data);
    }
}