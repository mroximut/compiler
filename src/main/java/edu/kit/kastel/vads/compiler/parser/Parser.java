package edu.kit.kastel.vads.compiler.parser;

import edu.kit.kastel.vads.compiler.lexer.Identifier;
import edu.kit.kastel.vads.compiler.lexer.Keyword;
import edu.kit.kastel.vads.compiler.lexer.KeywordType;
import edu.kit.kastel.vads.compiler.lexer.NumberLiteral;
import edu.kit.kastel.vads.compiler.lexer.Operator;
import edu.kit.kastel.vads.compiler.lexer.Operator.OperatorType;
import edu.kit.kastel.vads.compiler.lexer.Separator;
import edu.kit.kastel.vads.compiler.lexer.Separator.SeparatorType;
import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.lexer.Token;
import edu.kit.kastel.vads.compiler.parser.ast.AssignmentTree;
import edu.kit.kastel.vads.compiler.parser.ast.BinaryOperationTree;
import edu.kit.kastel.vads.compiler.parser.ast.BlockTree;
import edu.kit.kastel.vads.compiler.parser.ast.DeclarationTree;
import edu.kit.kastel.vads.compiler.parser.ast.ExpressionTree;
import edu.kit.kastel.vads.compiler.parser.ast.FunctionTree;
import edu.kit.kastel.vads.compiler.parser.ast.IdentExpressionTree;
import edu.kit.kastel.vads.compiler.parser.ast.LValueIdentTree;
import edu.kit.kastel.vads.compiler.parser.ast.LValueTree;
import edu.kit.kastel.vads.compiler.parser.ast.LiteralTree;
import edu.kit.kastel.vads.compiler.parser.ast.NameTree;
import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.ast.ReturnTree;
import edu.kit.kastel.vads.compiler.parser.ast.StatementTree;
import edu.kit.kastel.vads.compiler.parser.ast.TypeTree;
import edu.kit.kastel.vads.compiler.parser.ast.UnaryOperationTree;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;
import edu.kit.kastel.vads.compiler.parser.ast.BooleanLiteralTree;
import edu.kit.kastel.vads.compiler.parser.ast.IfTree;
import edu.kit.kastel.vads.compiler.parser.ast.WhileTree;
import edu.kit.kastel.vads.compiler.parser.ast.ForTree;
import edu.kit.kastel.vads.compiler.parser.ast.BreakTree;
import edu.kit.kastel.vads.compiler.parser.ast.ContinueTree;
import edu.kit.kastel.vads.compiler.parser.ast.TernaryTree;
import edu.kit.kastel.vads.compiler.parser.ast.NoOpTree;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final TokenSource tokenSource;

    public Parser(TokenSource tokenSource) {
        this.tokenSource = tokenSource;
    }

    public ProgramTree parseProgram() {
        ProgramTree programTree = new ProgramTree(List.of(parseFunction()));
        if (this.tokenSource.hasMore()) {
            throw new ParseException("expected end of input but got " + this.tokenSource.peek());
        }
        return programTree;
    }

    private FunctionTree parseFunction() {
        Keyword returnType = this.tokenSource.expectKeyword(KeywordType.INT);
        Identifier identifier = this.tokenSource.expectIdentifier();
        if (!identifier.value().equals("main")) {
            throw new ParseException("expected main function but got " + identifier);
        }
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
        BlockTree body = parseBlock();
        return new FunctionTree(
            new TypeTree(BasicType.INT, returnType.span()),
            name(identifier),
            body
        );
    }

    private BlockTree parseBlock() {
        Separator bodyOpen = this.tokenSource.expectSeparator(SeparatorType.BRACE_OPEN);
        List<StatementTree> statements = new ArrayList<>();
        while (!(this.tokenSource.peek() instanceof Separator sep && sep.type() == SeparatorType.BRACE_CLOSE)) {
            statements.add(parseStatement());
        }
        Separator bodyClose = this.tokenSource.expectSeparator(SeparatorType.BRACE_CLOSE);
        return new BlockTree(statements, bodyOpen.span().merge(bodyClose.span()));
    }

    private StatementTree parseStatement() {
        StatementTree statement;
        if (this.tokenSource.peek().isKeyword(KeywordType.IF)) {
            statement = parseIf();
        } else if (this.tokenSource.peek().isKeyword(KeywordType.WHILE)) {
            statement = parseWhile();
        } else if (this.tokenSource.peek().isKeyword(KeywordType.FOR)) {
            statement = parseFor();
        } else if (this.tokenSource.peek().isKeyword(KeywordType.BREAK)) {
            statement = parseBreak();
            this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        } else if (this.tokenSource.peek().isKeyword(KeywordType.CONTINUE)) {
            statement = parseContinue();
            this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        } else if (this.tokenSource.peek().isKeyword(KeywordType.INT) || this.tokenSource.peek().isKeyword(KeywordType.BOOL)) {
            statement = parseDeclaration();
            this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        } else if (this.tokenSource.peek().isKeyword(KeywordType.RETURN)) {
            statement = parseReturn();
            this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        } else {
            statement = parseSimple();
            this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        }
        return statement;
    }

    private StatementTree parseDeclaration() {
        BasicType type;
        if (this.tokenSource.peek().isKeyword(KeywordType.INT)) {
            this.tokenSource.expectKeyword(KeywordType.INT);
            type = BasicType.INT;
        } else if (this.tokenSource.peek().isKeyword(KeywordType.BOOL)) {
            this.tokenSource.expectKeyword(KeywordType.BOOL);
            type = BasicType.BOOL;
        } else {
            throw new ParseException("expected type (int or bool) but got " + this.tokenSource.peek());
        }
        Identifier ident = this.tokenSource.expectIdentifier();
        ExpressionTree expr = null;
        if (this.tokenSource.peek().isOperator(OperatorType.ASSIGN)) {
            this.tokenSource.expectOperator(OperatorType.ASSIGN);
            expr = parseExpression();
        }
        return new DeclarationTree(new TypeTree(type, this.tokenSource.peek().span()), name(ident), expr);
    }

    private StatementTree parseSimple() {
        LValueTree lValue = parseLValue();
        Operator assignmentOperator = parseAssignmentOperator();
        ExpressionTree expression = parseExpression();
        return new AssignmentTree(lValue, assignmentOperator, expression);
    }

    private Operator parseAssignmentOperator() {
        if (this.tokenSource.peek() instanceof Operator op) {
            return switch (op.type()) {
                case ASSIGN, ASSIGN_DIV, ASSIGN_MINUS, ASSIGN_MOD, ASSIGN_MUL, ASSIGN_PLUS,
                     ASSIGN_AND, ASSIGN_OR, ASSIGN_XOR, ASSIGN_SHL, ASSIGN_SHR -> {
                    this.tokenSource.consume();
                    yield op;
                }
                default -> throw new ParseException("expected assignment but got " + op.type());
            };
        }
        throw new ParseException("expected assignment but got " + this.tokenSource.peek());
    }

    private LValueTree parseLValue() {
        if (this.tokenSource.peek().isSeparator(SeparatorType.PAREN_OPEN)) {
            this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
            LValueTree inner = parseLValue();
            this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
            return inner;
        }
        Identifier identifier = this.tokenSource.expectIdentifier();
        return new LValueIdentTree(name(identifier));
    }

    private StatementTree parseReturn() {
        Keyword ret = this.tokenSource.expectKeyword(KeywordType.RETURN);
        ExpressionTree expression = parseExpression();
        return new ReturnTree(expression, ret.span().start());
    }

    private ExpressionTree parseExpression() {
        return parseTernary();
    }

    private ExpressionTree parseTernary() {
        ExpressionTree condition = parseLogicalOr();
        
        // Check for ternary operator
        if (this.tokenSource.peek().isOperator(OperatorType.QUESTION)) {
            this.tokenSource.consume(); // consume '?'
            ExpressionTree trueExpr = parseExpression();
            this.tokenSource.expectOperator(OperatorType.COLON);
            ExpressionTree falseExpr = parseExpression();
            return new TernaryTree(condition, trueExpr, falseExpr, condition.span().merge(falseExpr.span()));
        }
        
        return condition;
    }

    private ExpressionTree parseLogicalOr() {
        ExpressionTree lhs = parseLogicalAnd();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                && (type == OperatorType.LOGICAL_OR)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseLogicalAnd(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseLogicalAnd() {
        ExpressionTree lhs = parseBitwiseOr();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                && (type == OperatorType.LOGICAL_AND)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseBitwiseOr(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseBitwiseOr() {
        ExpressionTree lhs = parseBitwiseXOr();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                && (type == OperatorType.BITWISE_OR)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseBitwiseXOr(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseBitwiseXOr() {
        ExpressionTree lhs = parseBitwiseAnd();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                && (type == OperatorType.BITWISE_XOR)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseBitwiseAnd(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseBitwiseAnd() {
        ExpressionTree lhs = parseEquality();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                && (type == OperatorType.BITWISE_AND)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseEquality(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseEquality() {
        ExpressionTree lhs = parseComparison();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                && (type == OperatorType.EQ || type == OperatorType.NE)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseComparison(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseComparison() {
        ExpressionTree lhs = parseShift();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                && (type == OperatorType.LT || type == OperatorType.LE || type == OperatorType.GT || type == OperatorType.GE)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseShift(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseShift() {
        ExpressionTree expression = parseAdditive();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                && (type == OperatorType.SHL || type == OperatorType.SHR)) {
                this.tokenSource.consume();
                expression = new BinaryOperationTree(expression, parseAdditive(), type);
            } else {
                return expression;
            }
        }
    }

    private ExpressionTree parseAdditive() {
        ExpressionTree lhs = parseTerm();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                && (type == OperatorType.PLUS || type == OperatorType.MINUS)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseTerm(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseTerm() {
        ExpressionTree lhs = parseFactor();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                && (type == OperatorType.MUL || type == OperatorType.DIV || type == OperatorType.MOD)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseFactor(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseFactor() {
        return switch (this.tokenSource.peek()) {
            case Separator(var type, _) when type == SeparatorType.PAREN_OPEN -> {
                this.tokenSource.consume();
                ExpressionTree expression = parseExpression();
                this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
                yield expression;
            }
            case Operator(var type, _) when type == OperatorType.MINUS -> {
                Span span = this.tokenSource.consume().span();
                yield new UnaryOperationTree(parseFactor(), OperatorType.MINUS, span);
            }
            case Operator(var type, _) when type == OperatorType.BITWISE_NOT -> {
                Span span = this.tokenSource.consume().span();
                yield new UnaryOperationTree(parseFactor(), OperatorType.BITWISE_NOT, span);
            }
            case Operator(var type, _) when type == OperatorType.LOGICAL_NOT -> {
                Span span = this.tokenSource.consume().span();
                yield new UnaryOperationTree(parseFactor(), OperatorType.LOGICAL_NOT, span);
            }
            case Identifier ident -> {
                this.tokenSource.consume();
                yield new IdentExpressionTree(name(ident));
            }
            case NumberLiteral(String value, int base, Span span) -> {
                this.tokenSource.consume();
                yield new LiteralTree(value, base, span);
            }
            case Keyword(var type, Span span) when type == KeywordType.TRUE -> {
                this.tokenSource.consume();
                yield new BooleanLiteralTree(true, span);
            }
            case Keyword(var type, Span span) when type == KeywordType.FALSE -> {
                this.tokenSource.consume();
                yield new BooleanLiteralTree(false, span);
            }
            case Token t -> throw new ParseException("invalid factor " + t);
        };
    }

    private StatementTree parseIf() {
        Keyword ifKeyword = this.tokenSource.expectKeyword(KeywordType.IF);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        ExpressionTree condition = parseExpression();
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
        
        // Parse then branch - can be either a block or a single statement
        StatementTree thenBranch;
        if (this.tokenSource.peek().isSeparator(SeparatorType.BRACE_OPEN)) {
            thenBranch = parseBlock();
        } else {
            thenBranch = parseStatement();
        }
        
        // Parse else branch if it exists - can be either a block or a single statement
        StatementTree elseBranch = null;
        if (this.tokenSource.peek().isKeyword(KeywordType.ELSE)) {
            this.tokenSource.expectKeyword(KeywordType.ELSE);
            if (this.tokenSource.peek().isSeparator(SeparatorType.BRACE_OPEN)) {
                elseBranch = parseBlock();
            } else {
                elseBranch = parseStatement();
            }
        }
        
        return new IfTree(condition, thenBranch, elseBranch, ifKeyword.span());
    }

    private StatementTree parseWhile() {
        Keyword whileKeyword = this.tokenSource.expectKeyword(KeywordType.WHILE);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        ExpressionTree condition = parseExpression();
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
        
        // Parse body - can be either a block or a single statement
        StatementTree body;
        if (this.tokenSource.peek().isSeparator(SeparatorType.BRACE_OPEN)) {
            body = parseBlock();
        } else {
            body = parseStatement();
        }
        
        return new WhileTree(condition, body, whileKeyword.span());
    }

    private StatementTree parseFor() {
        Keyword forKeyword = this.tokenSource.expectKeyword(KeywordType.FOR);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        
        // Parse initializer - can be empty, a declaration, or a simple statement
        StatementTree initializer;
        if (this.tokenSource.peek().isSeparator(SeparatorType.SEMICOLON)) {
            initializer = new NoOpTree(forKeyword.span());
        } else if (this.tokenSource.peek().isKeyword(KeywordType.INT) || this.tokenSource.peek().isKeyword(KeywordType.BOOL)) {
            initializer = parseDeclaration();
        } else {
            initializer = parseSimple();
        }
        this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        
        // Parse condition
        ExpressionTree condition = parseExpression();
        this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        
        // Parse increment
        StatementTree increment;
        if (this.tokenSource.peek().isSeparator(SeparatorType.SEMICOLON)) {
            increment = new NoOpTree(forKeyword.span());
        } else if (this.tokenSource.peek().isKeyword(KeywordType.INT) || this.tokenSource.peek().isKeyword(KeywordType.BOOL)) {
            increment = parseDeclaration();
        } else {
            increment = parseSimple();
        }
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
        
        // Parse body - can be either a block or a single statement
        StatementTree body;
        if (this.tokenSource.peek().isSeparator(SeparatorType.BRACE_OPEN)) {
            body = parseBlock();
        } else {
            body = parseStatement();
        }
        
        return new ForTree(initializer, condition, increment, body, forKeyword.span());
    }

    private StatementTree parseBreak() {
        Keyword breakKeyword = this.tokenSource.expectKeyword(KeywordType.BREAK);
        return new BreakTree(breakKeyword.span());
    }

    private StatementTree parseContinue() {
        Keyword continueKeyword = this.tokenSource.expectKeyword(KeywordType.CONTINUE);
        return new ContinueTree(continueKeyword.span());
    }

    private static NameTree name(Identifier ident) {
        return new NameTree(Name.forIdentifier(ident), ident.span());
    }
}
