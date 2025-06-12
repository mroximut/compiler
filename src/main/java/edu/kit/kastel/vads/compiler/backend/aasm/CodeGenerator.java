package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.AddNode;
import edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
import edu.kit.kastel.vads.compiler.ir.node.DivNode;
import edu.kit.kastel.vads.compiler.ir.node.ModNode;
import edu.kit.kastel.vads.compiler.ir.node.MulNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.BitwiseNotNode;
import edu.kit.kastel.vads.compiler.ir.node.Phi;
import edu.kit.kastel.vads.compiler.ir.node.ProjNode;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;
import edu.kit.kastel.vads.compiler.ir.node.StartNode;
import edu.kit.kastel.vads.compiler.ir.node.SubNode;
import edu.kit.kastel.vads.compiler.ir.node.LogicalNotNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;


public class CodeGenerator {

    private static final String[] PHYSICAL_REGS = {
        "%r10d", "%r11d", "%r12d", "%r13d", "%r14d", "%r15d" //, "%ebx", "%ecx", "%esi", "%edi"
    };

    private static int maxSpillSlot = 0;

    private static String getPhysicalRegister(Register reg) {
        int idx = reg.getID(); 
        if (idx < PHYSICAL_REGS.length) {
            return PHYSICAL_REGS[idx];
        } else {
            int slot = idx - PHYSICAL_REGS.length + 1;
            if (slot > maxSpillSlot) maxSpillSlot = slot; // Update max slot used
            return "-%d(%%rbp)".formatted(slot * 4);
        }
    }

    public String generateCode(List<IrGraph> program) {
        StringBuilder builder = new StringBuilder();
        // Add GNU assembly directives
        builder.append(".global main\n");
        builder.append(".global _main\n");
        builder.append(".text\n");
        builder.append("main:\n");
        builder.append("call _main\n");
        builder.append("movq %rax, %rdi\n");
        builder.append("movq $0x3C, %rax\n");
        builder.append("syscall\n");
        builder.append("_main:\n");
        builder.append("  pushq %rbp\n");
        builder.append("  movq %rsp, %rbp\n");
        int prologueStackAllocPos = builder.length();
        builder.append("  subq $XXXX, %rsp\n"); // Placeholder
        maxSpillSlot = 0; // Reset before codegen
        for (IrGraph graph : program) {
            AasmRegisterAllocator allocator = new AasmRegisterAllocator();
            Map<Node, Register> registers = allocator.allocateRegisters(graph);
            //builder.append("function ")
            //    .append(graph.name())
            //    .append(" {\n");
            generateForGraph(graph, builder, registers);
            //builder.append("}\n");
            builder.append("\n");
            builder.append(".section .note.GNU-stack,\"\",@progbits");
        }
        
        int totalSpillBytes = maxSpillSlot * 4;
        String stackAlloc = String.format("  subq $%d, %%rsp\n", totalSpillBytes);
        builder.replace(prologueStackAllocPos, prologueStackAllocPos + "  subq $XXXX, %rsp\n".length(), stackAlloc);
        return builder.toString();
    }

    private void generateForGraph(IrGraph graph, StringBuilder builder, Map<Node, Register> registers) {
        Set<Node> visited = new HashSet<>();
        scan(graph.endBlock(), visited, builder, registers);
    }

    private void scan(Node node, Set<Node> visited, StringBuilder builder, Map<Node, Register> registers) {
        for (Node predecessor : node.predecessors()) {
            if (visited.add(predecessor)) {
                scan(predecessor, visited, builder, registers);
            }
        }

        switch (node) {
            case AddNode add -> binary(builder, registers, add, "addl");
            case SubNode sub -> binary(builder, registers, sub, "subl");
            case MulNode mul -> binary(builder, registers, mul, "imull");
            case DivNode div -> div(builder, registers, div, "%eax");
            case ModNode mod -> div(builder, registers, mod, "%edx");
            case BitwiseNotNode not -> {
                String operand = getPhysicalRegister(registers.get(predecessorSkipProj(not, 0)));
                String dest = getPhysicalRegister(registers.get(not));
                builder.append("\n");
                if (operand.startsWith("-")) {
                    builder.append("  movl ").append(operand).append(", %r9d\n");
                    operand = "%r9d";
                }
                if (dest.startsWith("-")) {
                    builder.append("  movl ").append(operand).append(", %r9d\n");
                    builder.append("  notl %r9d\n");
                    builder.append("  movl %r9d, ").append(dest).append("\n");
                } else {
                    builder.append("  movl ").append(operand).append(", ").append(dest).append("\n");
                    builder.append("  notl ").append(dest).append("\n");
                }
            }
            case LogicalNotNode not -> {
                String operand = getPhysicalRegister(registers.get(predecessorSkipProj(not, 0)));
                String dest = getPhysicalRegister(registers.get(not));
                builder.append("\n");
                builder.append("  movl ").append(operand).append(", %eax\n");
                builder.append("  cmpl $0, %eax\n");
                builder.append("  sete %al\n");
                builder.append("  movzbl %al, %eax\n");
                builder.append("  movl %eax, ").append(dest).append("\n");
            }
            case ReturnNode r -> builder.repeat(" ", 2).append("movl ")
                .append(getPhysicalRegister(registers.get(predecessorSkipProj(r, ReturnNode.RESULT))))
                .append(", %eax")
                .append("\n").repeat(" ", 2)
                .append("leave")
                .append("\n").repeat(" ", 2)
                .append("ret");
            case ConstIntNode c -> builder.repeat(" ", 2)
                .append("movl $")
                .append(c.value())
                .append(", ")
                .append(getPhysicalRegister(registers.get(c)));                
            case Phi _ -> throw new UnsupportedOperationException("phi");
            case Block _, ProjNode _, StartNode _ -> {
                // do nothing, skip line break
                return;
            }
        }
        builder.append("\n");
    }

    private static void binary(StringBuilder builder, Map<Node, Register> registers, BinaryOperationNode node, String opcode) {
        String left = getPhysicalRegister(registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT)));
        String right = getPhysicalRegister(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)));
        String dest = getPhysicalRegister(registers.get(node));
        builder.append("\n");

        // Use %r8d and %r9d as temporary registers for stack slot loads
        // These are caller-saved and not used by the ABI for return values or arguments
        // so they are safe for temporaries in code generation
        if (left.startsWith("-")) {
            builder.append("  movl ").append(left).append(", %r9d\n");
            left = "%r9d";
        }
        if (right.startsWith("-")) {
            builder.append("  movl ").append(right).append(", %r8d\n");
            right = "%r8d";
        }
        if (dest.startsWith("-")) {
            builder.append("  movl ").append(left).append(", %r9d\n");
            builder.append("  ").append(opcode).append(" ").append(right).append(", %r9d\n");
            builder.append("  movl %r9d, ").append(dest).append("\n");
        } else {
            builder.append("  movl ").append(left).append(", ").append(dest).append("\n");
            builder.append("  ").append(opcode).append(" ").append(right).append(", ").append(dest).append("\n");
        }
    }

    private static void div(StringBuilder builder, Map<Node, Register> registers, BinaryOperationNode node, String resultReg) {
        String left = getPhysicalRegister(registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT)));
        String right = getPhysicalRegister(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)));
        String dest = getPhysicalRegister(registers.get(node));
        builder.append("\n");

        // Always load divisor into a register if it's a stack slot
        if (right.startsWith("-")) {
            builder.append("  movl ").append(right).append(", %r8d\n");
            right = "%r8d";
        }
        // Load dividend into %eax
        builder.append("  movl ").append(left).append(", %eax\n");

        builder.append("  cltd\n");
        builder.append("  idivl ").append(right).append("\n");
        builder.append("  movl ").append(resultReg).append(", ").append(dest).append("\n");
    }
}
