package edu.kit.kastel.vads.compiler.semantic;

import java.util.ArrayList;

import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor;

public class SemanticAnalysis {

    private final ProgramTree program;

    public SemanticAnalysis(ProgramTree program) {
        this.program = program;
    }

    public void analyze() {
        this.program.accept(new RecursivePostorderVisitor<>(new IntegerLiteralRangeAnalysis()), new Namespace<>());
        this.program.accept(new RecursivePostorderVisitor<>(new LoopAnalysis()), new ArrayList<>());
        this.program.accept(new RecursivePostorderVisitor<>(new ReturnAnalysis()), new ArrayList<>());
        this.program.accept(new TypeCheckingAnalysis(), new Namespace<>());

        //this.program.accept(new RecursivePostorderVisitor<>(new VariableStatusAnalysis()), new Namespace<>());
        this.program.accept(new VariableStatusAnalysis(), new Namespace<>());
        
    }

}
