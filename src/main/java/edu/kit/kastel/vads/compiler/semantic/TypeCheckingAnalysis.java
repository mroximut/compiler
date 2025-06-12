package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.*;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;
import edu.kit.kastel.vads.compiler.parser.type.Type;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;

public class TypeCheckingAnalysis implements Visitor<Namespace<Type>, Type> {

    @Override
    public Type visit(BinaryOperationTree binaryOperationTree, Namespace<Type> data) {
        Type lhsType = binaryOperationTree.lhs().accept(this, data);
        Type rhsType = binaryOperationTree.rhs().accept(this, data);
        
        if (lhsType != BasicType.INT || rhsType != BasicType.INT) {
            throw new SemanticException("Binary operation requires integer operands");
        }
        
        return BasicType.INT;
    }

    @Override
    public Type visit(LiteralTree literalTree, Namespace<Type> data) {
        return BasicType.INT;
    }

    @Override
    public Type visit(UnaryOperationTree unaryOperationTree, Namespace<Type> data) {
        Type exprType = unaryOperationTree.expression().accept(this, data);

        switch (unaryOperationTree.operatorType()) {
            case MINUS -> {
                if (exprType != BasicType.INT) {
                    throw new SemanticException("Negation requires integer operand");
                }
                return BasicType.INT;
            }
            case BITWISE_NOT -> {
                if (exprType != BasicType.INT) {
                    throw new SemanticException("Bitwise NOT requires integer operand");
                }
                return BasicType.INT;
            }
            case LOGICAL_NOT -> {
                if (exprType != BasicType.BOOL) {
                    throw new SemanticException("Logical NOT requires boolean operand");
                }
                return BasicType.BOOL;
            }
            default -> throw new SemanticException("Unknown unary operator");
        }
    }

    @Override
    public Type visit(BooleanLiteralTree booleanLiteralTree, Namespace<Type> data) {
        return BasicType.BOOL;
    }

    @Override
    public Type visit(IdentExpressionTree identExpressionTree, Namespace<Type> data) {
        Type type = data.get(identExpressionTree.name());
        if (type == null) {
            throw new SemanticException("Variable not declared: " + identExpressionTree.name().name());
        }
        return type;
    }

    @Override
    public Type visit(DeclarationTree declarationTree, Namespace<Type> data) {
        Type declaredType = declarationTree.type().accept(this, data);
        if (declarationTree.initializer() != null) {
            Type initType = declarationTree.initializer().accept(this, data);
            if (initType != declaredType) {
                throw new SemanticException("Type mismatch in variable initialization");
            }
        }
        data.put(declarationTree.name(), declaredType, (existing, replacement) -> replacement);
        return declaredType;
    }

    @Override
    public Type visit(ReturnTree returnTree, Namespace<Type> data) {
        if (returnTree.expression() != null) {
            return returnTree.expression().accept(this, data);
        }
        return BasicType.INT;
    }

    @Override
    public Type visit(AssignmentTree assignmentTree, Namespace<Type> data) {
        Type lValueType = assignmentTree.lValue().accept(this, data);
        Type exprType = assignmentTree.expression().accept(this, data);
        if (lValueType != exprType) {
            throw new SemanticException("Type mismatch in assignment");
        }
        return lValueType;
    }

    @Override
    public Type visit(BlockTree blockTree, Namespace<Type> data) {
        for (var statement : blockTree.statements()) {
            statement.accept(this, data);
        }
        return BasicType.INT;
    }

    @Override
    public Type visit(FunctionTree functionTree, Namespace<Type> data) {
        functionTree.body().accept(this, data);
        return BasicType.INT;
    }

    @Override
    public Type visit(LValueIdentTree lValueIdentTree, Namespace<Type> data) {
        Type type = data.get(lValueIdentTree.name());
        if (type == null) {
            throw new SemanticException("Variable not declared: " + lValueIdentTree.name().name());
        }
        return type;
    }

    @Override
    public Type visit(NameTree nameTree, Namespace<Type> data) {
        return BasicType.INT;
    }

    @Override
    public Type visit(ProgramTree programTree, Namespace<Type> data) {
        for (var tree : programTree.topLevelTrees()) {
            tree.accept(this, data);
        }
        return BasicType.INT;
    }

    @Override
    public Type visit(TypeTree typeTree, Namespace<Type> data) {
        return BasicType.INT;
    }
} 