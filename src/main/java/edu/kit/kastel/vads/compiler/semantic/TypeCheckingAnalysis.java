package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.lexer.Operator.OperatorType;
import edu.kit.kastel.vads.compiler.parser.ast.*;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;
import edu.kit.kastel.vads.compiler.parser.type.Type;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;

public class TypeCheckingAnalysis implements Visitor<Namespace<Type>, Type> {

    @Override
    public Type visit(BinaryOperationTree binaryOperationTree, Namespace<Type> data) {
        Type lhsType = binaryOperationTree.lhs().accept(this, data);
        Type rhsType = binaryOperationTree.rhs().accept(this, data);

        // Handle logical operators
        if (binaryOperationTree.operatorType() == OperatorType.LOGICAL_AND || 
            binaryOperationTree.operatorType() == OperatorType.LOGICAL_OR) {
            if (lhsType != BasicType.BOOL || rhsType != BasicType.BOOL) {
                throw new SemanticException("Logical operation requires boolean operands");
            }
            return BasicType.BOOL;
        }

        // Handle relational operators
        if (binaryOperationTree.operatorType() == OperatorType.LT ||
            binaryOperationTree.operatorType() == OperatorType.LE ||
            binaryOperationTree.operatorType() == OperatorType.GT ||
            binaryOperationTree.operatorType() == OperatorType.GE) {
            if (lhsType != BasicType.INT || rhsType != BasicType.INT) {
                throw new SemanticException("Relational operation requires integer operands");
            }
            return BasicType.BOOL;
        }

        // Handle equality operators
        if (binaryOperationTree.operatorType() == OperatorType.EQ ||
            binaryOperationTree.operatorType() == OperatorType.NE) {
            if (lhsType != rhsType) {
                throw new SemanticException("Equality operation requires operands of the same type");
            }
            return BasicType.BOOL;
        }
        
        // Handle arithmetic operators
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
            case MINUS, BITWISE_NOT -> {
                if (exprType != BasicType.INT) {
                    throw new SemanticException("Unary operation requires integer operand");
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
        
        // Check if variable is already declared in the current scope only
        if (data.get(declarationTree.name()) != null) {
            throw new SemanticException("Variable already declared in this scope: " + declarationTree.name().name());
        }

        // Add variable to current scope
        data.put(declarationTree.name(), declaredType, (_, replacement) -> replacement);

        // Check initializer if present
        if (declarationTree.initializer() != null) {
            Type initType = declarationTree.initializer().accept(this, data);
            if (initType != declaredType) {
                throw new SemanticException("Type mismatch in variable initialization");
            }
        }

        return declaredType;
    }

    @Override
    public Type visit(ReturnTree returnTree, Namespace<Type> data) {
        if (returnTree.expression() != null) {
            Type returnType = returnTree.expression().accept(this, data);
            if (returnType != BasicType.INT) {
                throw new SemanticException("Return statement must return an integer");
            }
            return returnType;
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
        // Create a new scope that inherits from the parent scope
        Namespace<Type> blockScope = new Namespace<>(data);
        
        // Process all statements in the block
        for (var statement : blockTree.statements()) {
            statement.accept(this, blockScope);
        }
        
        return BasicType.INT;
    }

    @Override
    public Type visit(FunctionTree functionTree, Namespace<Type> data) {
        // Create a new scope that inherits from the parent scope
        Namespace<Type> functionScope = new Namespace<>(data);
        
        // Process the function body
        functionTree.body().accept(this, functionScope);
        
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
        // Create a new scope for the program (no parent scope needed)
        Namespace<Type> programScope = new Namespace<>();
        
        // Process all top-level trees
        for (var tree : programTree.topLevelTrees()) {
            tree.accept(this, programScope);
        }
        
        return BasicType.INT;
    }

    @Override
    public Type visit(TypeTree typeTree, Namespace<Type> data) {
        return typeTree.type();
    }

    @Override
    public Type visit(NoOpTree noOpTree, Namespace<Type> data) {
        return BasicType.INT;
    }

    @Override
    public Type visit(TernaryTree ternaryTree, Namespace<Type> data) {
        Type conditionType = ternaryTree.condition().accept(this, data);
        if (conditionType != BasicType.BOOL) {
            throw new SemanticException("Ternary condition must be boolean");
        }

        Type thenType = ternaryTree.trueExpr().accept(this, data);
        Type elseType = ternaryTree.falseExpr().accept(this, data);

        if (thenType != elseType) {
            throw new SemanticException("Ternary branches must have the same type");
        }

        return thenType;
    }

    @Override
    public Type visit(WhileTree whileTree, Namespace<Type> data) {
        Type conditionType = whileTree.condition().accept(this, data);
        if (conditionType != BasicType.BOOL) {
            throw new SemanticException("While condition must be boolean");
        }

        // Create a new scope that inherits from the parent scope
        Namespace<Type> whileScope = new Namespace<>(data);
        whileTree.body().accept(this, whileScope);
        
        return BasicType.INT;
    }

    @Override
    public Type visit(IfTree ifTree, Namespace<Type> data) {
        Type conditionType = ifTree.condition().accept(this, data);
        if (conditionType != BasicType.BOOL) {
            throw new SemanticException("If condition must be boolean");
        }

        // Create new scopes that inherit from the parent scope
        Namespace<Type> thenScope = new Namespace<>(data);
        ifTree.thenBranch().accept(this, thenScope);
        
        if (ifTree.elseBranch() != null) {
            Namespace<Type> elseScope = new Namespace<>(data);
            ifTree.elseBranch().accept(this, elseScope);
        }
        
        return BasicType.INT;
    }

    @Override
    public Type visit(ForTree forTree, Namespace<Type> data) {
        // Create a new scope that inherits from the parent scope
        Namespace<Type> forScope = new Namespace<>(data);
        
        // Handle initialization in the for loop's scope
        forTree.initializer().accept(this, forScope);

        // Check condition in the for loop's scope
        Type conditionType = forTree.condition().accept(this, forScope);
        if (conditionType != BasicType.BOOL) {
            throw new SemanticException("For condition must be boolean");
        }

        // Handle body in the for loop's scope
        forTree.body().accept(this, forScope);

        // Handle increment in the for loop's scope
       
        forTree.increment().accept(this, forScope);
        

        return BasicType.INT;
    }

    @Override
    public Type visit(ContinueTree continueTree, Namespace<Type> data) {
        return BasicType.INT;
    }

    @Override
    public Type visit(BreakTree breakTree, Namespace<Type> data) {
        return BasicType.INT;
    }
} 