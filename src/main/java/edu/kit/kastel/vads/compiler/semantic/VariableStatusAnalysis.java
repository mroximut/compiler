// package edu.kit.kastel.vads.compiler.semantic;

// import edu.kit.kastel.vads.compiler.lexer.Operator;
// import edu.kit.kastel.vads.compiler.parser.ast.*;
// import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
// import edu.kit.kastel.vads.compiler.parser.visitor.Unit;

// import org.jspecify.annotations.Nullable;

// import java.util.Locale;

// /// Checks that variables are
// /// - declared before assignment
// /// - not declared twice
// /// - not initialized twice
// /// - assigned before referenced
// class VariableStatusAnalysis implements NoOpVisitor<Namespace<VariableStatusAnalysis.VariableStatus>> {


//     enum VariableStatus {
//         DECLARED,
//         INITIALIZED;

//         @Override
//         public String toString() {
//             return name().toLowerCase(Locale.ROOT);
//         }
//     }

//     @Override
//     public Unit visit(AssignmentTree assignmentTree, Namespace<VariableStatus> data) {
//         assignmentTree.lValue().accept(this, data);
//         assignmentTree.expression().accept(this, data);

//         switch (assignmentTree.lValue()) {
//             case LValueIdentTree(var name) -> {
//                 VariableStatus status = data.get(name);
//                 if (assignmentTree.operator().type() == Operator.OperatorType.ASSIGN) {
//                     checkDeclared(name, status);
//                 } else {
//                     if (!data.isAllDefined()) checkInitialized(name, status);
//                 }
//                 if (status != VariableStatus.INITIALIZED) {
//                     // only update when needed, reassignment is totally fine
//                     updateStatus(data, VariableStatus.INITIALIZED, name);
//                 }
//             }
//         }
//         return Unit.INSTANCE;
//     }

//     private static void checkDeclared(NameTree name, @Nullable VariableStatus status) {
//         if (status == null) {
//             throw new SemanticException("Variable " + name + " must be declared before assignment");
//         }
//     }

//     private static void checkInitialized(NameTree name, @Nullable VariableStatus status) {
//         if (status == null || status == VariableStatus.DECLARED) {
//             throw new SemanticException("Variable " + name + " must be initialized before use");
//         }
//     }

//     private static void checkUndeclared(NameTree name, @Nullable VariableStatus status) {
//         if (status != null) {
//             throw new SemanticException("Variable " + name + " is already declared");
//         }
//     }

//     @Override
//     public Unit visit(DeclarationTree declarationTree, Namespace<VariableStatus> data) {
//         declarationTree.type().accept(this, data);
//         declarationTree.name().accept(this, data);  
//         if (declarationTree.initializer() != null) {
//             declarationTree.initializer().accept(this, data);
//         }

//         checkUndeclared(declarationTree.name(), data.get(declarationTree.name()));
//         VariableStatus status = declarationTree.initializer() == null
//             ? VariableStatus.DECLARED
//             : VariableStatus.INITIALIZED;
//         updateStatus(data, status, declarationTree.name());
//         return Unit.INSTANCE;
//     }

//     private static void updateStatus(Namespace<VariableStatus> data, VariableStatus status, NameTree name) {
//         VariableStatus existing = data.get(name);
//         if (existing != null && existing.ordinal() >= status.ordinal()) {
//             throw new SemanticException("variable is already " + existing + ". Cannot be " + status + " here.");
//         }
//         data.put(name, status, (_, replacement) -> replacement);
//         // data.put(name, status, (existing, replacement) -> {
//         //     if (existing.ordinal() >= replacement.ordinal()) {
//         //         throw new SemanticException("variable is already " + existing + ". Cannot be " + replacement + " here.");
//         //     }
//         //     return replacement;
//         // });
//     }

//     @Override
//     public Unit visit(IdentExpressionTree identExpressionTree, Namespace<VariableStatus> data) {
//         identExpressionTree.name().accept(this, data);
//         VariableStatus status = data.get(identExpressionTree.name());
//         checkInitialized(identExpressionTree.name(), status);
//         return Unit.INSTANCE;
//     }



//     @Override
//     public Unit visit(NoOpTree noOpTree, Namespace<VariableStatus> data) {
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(TernaryTree ternaryTree, Namespace<VariableStatus> data) {
//         ternaryTree.condition().accept(this, data);
//         ternaryTree.trueExpr().accept(this, data);
//         ternaryTree.falseExpr().accept(this, data);
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(WhileTree whileTree, Namespace<VariableStatus> data) {
//         Namespace<VariableStatus> whileScope = new Namespace<>(data);
//         whileTree.condition().accept(this, whileScope);
//         whileTree.body().accept(this, whileScope);
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(IfTree ifTree, Namespace<VariableStatus> data) {
//         ifTree.condition().accept(this, data);

//         Namespace<VariableStatus> thenScope = new Namespace<>(data);
//         ifTree.thenBranch().accept(this, thenScope);
//         if (ifTree.elseBranch() != null) {
//             Namespace<VariableStatus> elseScope = new Namespace<>(data);
//             ifTree.elseBranch().accept(this, elseScope);
//         }
//         return Unit.INSTANCE;
//     }



//     @Override
//     public Unit visit(TypeTree typeTree, Namespace<VariableStatus> data) {
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(UnaryOperationTree unaryOperationTree, Namespace<VariableStatus> data) {
//         unaryOperationTree.expression().accept(this, data);
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(ProgramTree programTree, Namespace<VariableStatus> data) {
//         for (var function : programTree.topLevelTrees()) {
//             function.accept(this, data);
//         }
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(NameTree nameTree, Namespace<VariableStatus> data) {
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(LiteralTree literalTree, Namespace<VariableStatus> data) {
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(LValueIdentTree lValueIdentTree, Namespace<VariableStatus> data) {
//         lValueIdentTree.name().accept(this, data);
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(FunctionTree functionTree, Namespace<VariableStatus> data) {
//         functionTree.returnType().accept(this, data);
//         functionTree.name().accept(this, data);
//         Namespace<VariableStatus> functionScope = new Namespace<>(data);
//         functionTree.body().accept(this, functionScope);
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(ForTree forTree, Namespace<VariableStatus> data) {
//         Namespace<VariableStatus> forScope = new Namespace<>(data);
//         forTree.initializer().accept(this, forScope);
//         forTree.condition().accept(this, forScope);
//         forTree.body().accept(this, forScope);
//         forTree.increment().accept(this, forScope);
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(BooleanLiteralTree booleanLiteralTree, Namespace<VariableStatus> data) {
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(BlockTree blockTree, Namespace<VariableStatus> data) {
//         Namespace<VariableStatus> blockScope = new Namespace<>(data);
//         for (var statement : blockTree.statements()) {
//             statement.accept(this, blockScope);
//         }
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(BinaryOperationTree binaryOperationTree, Namespace<VariableStatus> data) {
//         binaryOperationTree.lhs().accept(this, data);
//         binaryOperationTree.rhs().accept(this, data);
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(BreakTree breakTree, Namespace<VariableStatus> data) {
//         data.setAllDefined(true);
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(ContinueTree continueTree, Namespace<VariableStatus> data) {
//         data.setAllDefined(true);
//         return Unit.INSTANCE;
//     }

//     @Override
//     public Unit visit(ReturnTree returnTree, Namespace<VariableStatus> data) {
//         returnTree.expression().accept(this, data);
//         data.setAllDefined(true);
//         return Unit.INSTANCE;
//     }

// }


//// OLD VERSION

package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.lexer.Operator;
import edu.kit.kastel.vads.compiler.parser.ast.AssignmentTree;
import edu.kit.kastel.vads.compiler.parser.ast.DeclarationTree;
import edu.kit.kastel.vads.compiler.parser.ast.IdentExpressionTree;
import edu.kit.kastel.vads.compiler.parser.ast.LValueIdentTree;
import edu.kit.kastel.vads.compiler.parser.ast.NameTree;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/// Checks that variables are
/// - declared before assignment
/// - not declared twice
/// - not initialized twice
/// - assigned before referenced
class VariableStatusAnalysis implements NoOpVisitor<Namespace<VariableStatusAnalysis.VariableStatus>> {

    @Override
    public Unit visit(AssignmentTree assignmentTree, Namespace<VariableStatus> data) {
        switch (assignmentTree.lValue()) {
            case LValueIdentTree(var name) -> {
                VariableStatus status = data.get(name);
                if (assignmentTree.operator().type() == Operator.OperatorType.ASSIGN) {
                    checkDeclared(name, status);
                } else {
                    checkInitialized(name, status);
                }
                if (status != VariableStatus.INITIALIZED) {
                    // only update when needed, reassignment is totally fine
                    updateStatus(data, VariableStatus.INITIALIZED, name);
                }
            }
        }
        return NoOpVisitor.super.visit(assignmentTree, data);
    }

    private static void checkDeclared(NameTree name, @Nullable VariableStatus status) {
        if (status == null) {
            throw new SemanticException("Variable " + name + " must be declared before assignment");
        }
    }

    private static void checkInitialized(NameTree name, @Nullable VariableStatus status) {
        if (status == null || status == VariableStatus.DECLARED) {
            throw new SemanticException("Variable " + name + " must be initialized before use");
        }
    }

    private static void checkUndeclared(NameTree name, @Nullable VariableStatus status) {
        if (status != null) {
            throw new SemanticException("Variable " + name + " is already declared");
        }
    }

    @Override
    public Unit visit(DeclarationTree declarationTree, Namespace<VariableStatus> data) {
        checkUndeclared(declarationTree.name(), data.get(declarationTree.name()));
        VariableStatus status = declarationTree.initializer() == null
            ? VariableStatus.DECLARED
            : VariableStatus.INITIALIZED;
        updateStatus(data, status, declarationTree.name());
        return NoOpVisitor.super.visit(declarationTree, data);
    }

    private static void updateStatus(Namespace<VariableStatus> data, VariableStatus status, NameTree name) {
        data.put(name, status, (existing, replacement) -> {
            if (existing.ordinal() >= replacement.ordinal()) {
                throw new SemanticException("variable is already " + existing + ". Cannot be " + replacement + " here.");
            }
            return replacement;
        });
    }

    @Override
    public Unit visit(IdentExpressionTree identExpressionTree, Namespace<VariableStatus> data) {
        VariableStatus status = data.get(identExpressionTree.name());
        checkInitialized(identExpressionTree.name(), status);
        return NoOpVisitor.super.visit(identExpressionTree, data);
    }

    enum VariableStatus {
        DECLARED,
        INITIALIZED;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
