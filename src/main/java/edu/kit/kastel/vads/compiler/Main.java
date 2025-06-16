package edu.kit.kastel.vads.compiler;

import edu.kit.kastel.vads.compiler.backend.aasm.CodeGenerator;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.SsaTranslation;
import edu.kit.kastel.vads.compiler.ir.optimize.LocalValueNumbering;
import edu.kit.kastel.vads.compiler.ir.util.YCompPrinter;
import edu.kit.kastel.vads.compiler.lexer.Lexer;
import edu.kit.kastel.vads.compiler.parser.ParseException;
import edu.kit.kastel.vads.compiler.parser.Parser;
import edu.kit.kastel.vads.compiler.parser.TokenSource;
import edu.kit.kastel.vads.compiler.parser.ast.FunctionTree;
import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.semantic.SemanticAnalysis;
import edu.kit.kastel.vads.compiler.semantic.SemanticException;
import edu.kit.kastel.vads.compiler.ir.util.GraphVizPrinter;
import edu.kit.kastel.vads.compiler.parser.Printer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Invalid arguments: Expected one input file and one output file");
            System.exit(3);
        }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        ProgramTree program = lexAndParse(input);
        System.out.println(Printer.print(program));

        try {
            new SemanticAnalysis(program).analyze();
        } catch (SemanticException e) {
            e.printStackTrace();
            System.exit(7);
            return;
        }
        List<IrGraph> graphs = new ArrayList<>();
        for (FunctionTree function : program.topLevelTrees()) {
            SsaTranslation translation = new SsaTranslation(function, new LocalValueNumbering());
            graphs.add(translation.translate());
        }

        if ("vcg".equals(System.getenv("DUMP_GRAPHS")) || "vcg".equals(System.getProperty("dumpGraphs"))) {
            Path tmp = output.toAbsolutePath().resolveSibling("graphs");
            //Files.createDirectory(tmp);
            for (IrGraph graph : graphs) {
                dumpGraph(graph, tmp, "before-codegen");
            }
        }

        for (IrGraph graph : graphs) {
            System.out.println(GraphVizPrinter.print(graph));
        }

        // Generate assembly code
        String assembly = new CodeGenerator().generateCode(graphs);
        Path asmFile = output.resolveSibling(output.getFileName() + ".s");
        Files.writeString(asmFile, assembly);

        // Compile and link
        try {
            compileAssembly(asmFile, output);
            // Clean up assembly file
            //Files.deleteIfExists(asmFile);
        } catch (IOException e) {
            System.err.println("Compilation failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static ProgramTree lexAndParse(Path input) throws IOException {
        try {
            Lexer lexer = Lexer.forString(Files.readString(input));
            TokenSource tokenSource = new TokenSource(lexer);
            Parser parser = new Parser(tokenSource);
            return parser.parseProgram();
        } catch (ParseException e) {
            e.printStackTrace();
            System.exit(42);
            throw new AssertionError("unreachable");
        }
    }

    private static void dumpGraph(IrGraph graph, Path path, String key) throws IOException {
        Files.writeString(
            path.resolve(graph.name() + "-" + key + ".vcg"),
            YCompPrinter.print(graph)
        );
    }

    public static void compileAssembly(Path asmFile, Path outputFile) throws IOException {
        // Compile assembly file
        ProcessBuilder pb = new ProcessBuilder("gcc", "-o", outputFile.toString(), asmFile.toString());
        Process p = pb.start();
        try {
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                throw new IOException("GCC failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("GCC interrupted", e);
        }
    }
}
