package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.AddNode;
import edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.BranchNode;
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
import edu.kit.kastel.vads.compiler.ir.node.UndefNode;
import edu.kit.kastel.vads.compiler.ir.node.LogicalNotNode;
import edu.kit.kastel.vads.compiler.ir.node.ShlNode;
import edu.kit.kastel.vads.compiler.ir.node.ShrNode;
import edu.kit.kastel.vads.compiler.ir.node.LtNode;
import edu.kit.kastel.vads.compiler.ir.node.LeNode;
import edu.kit.kastel.vads.compiler.ir.node.GtNode;
import edu.kit.kastel.vads.compiler.ir.node.JumpNode;
import edu.kit.kastel.vads.compiler.ir.node.GeNode;
import edu.kit.kastel.vads.compiler.ir.node.EqNode;
import edu.kit.kastel.vads.compiler.ir.node.NeNode;
import edu.kit.kastel.vads.compiler.ir.node.BitwiseAndNode;
import edu.kit.kastel.vads.compiler.ir.node.BitwiseXorNode;
import edu.kit.kastel.vads.compiler.ir.node.BitwiseOrNode;
//import edu.kit.kastel.vads.compiler.ir.node.LogicalAndNode;
//import edu.kit.kastel.vads.compiler.ir.node.LogicalOrNode;

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
    private static int labelCounter = 0;

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
            case BranchNode branch -> {
                String condition = getPhysicalRegister(registers.get(predecessorSkipProj(branch, 0)));
                String trueLabel = ".L" + labelCounter++;
                String falseLabel = ".L" + labelCounter++;
                
                builder.append("\n");
                builder.append("  movl ").append(condition).append(", %eax\n");
                builder.append("  testl %eax, %eax\n");
                builder.append("  jz ").append(falseLabel).append("\n");
                builder.append("  jmp ").append(trueLabel).append("\n");
                
                // Add labels for the blocks
                builder.append(trueLabel).append(":\n");
                builder.append(falseLabel).append(":\n");
            }
            case JumpNode jump -> {
                String targetLabel = ".L" + labelCounter++;
                builder.append("\n");
                builder.append("  jmp ").append(targetLabel).append("\n");
                builder.append(targetLabel).append(":\n");
            }
            case UndefNode undef -> {
                return;
            }
            case ShlNode shl -> {
                String left = getPhysicalRegister(registers.get(predecessorSkipProj(shl, BinaryOperationNode.LEFT)));
                String right = getPhysicalRegister(registers.get(predecessorSkipProj(shl, BinaryOperationNode.RIGHT)));
                String dest = getPhysicalRegister(registers.get(shl));
                builder.append("\n");
                builder.append("  movl ").append(left).append(", %eax\n");
                builder.append("  movl ").append(right).append(", %ecx\n");
                builder.append("  shll %cl, %eax\n");
                builder.append("  movl %eax, ").append(dest).append("\n");
            }
            case ShrNode shr -> {
                String left = getPhysicalRegister(registers.get(predecessorSkipProj(shr, BinaryOperationNode.LEFT)));
                String right = getPhysicalRegister(registers.get(predecessorSkipProj(shr, BinaryOperationNode.RIGHT)));
                String dest = getPhysicalRegister(registers.get(shr));
                builder.append("\n");
                builder.append("  movl ").append(left).append(", %eax\n");
                builder.append("  movl ").append(right).append(", %ecx\n");
                builder.append("  sarl %cl, %eax\n");
                builder.append("  movl %eax, ").append(dest).append("\n");
            }
            case LtNode lt -> {
                String left = getPhysicalRegister(registers.get(predecessorSkipProj(lt, BinaryOperationNode.LEFT)));
                String right = getPhysicalRegister(registers.get(predecessorSkipProj(lt, BinaryOperationNode.RIGHT)));
                String dest = getPhysicalRegister(registers.get(lt));
                builder.append("\n");
                builder.append("  movl ").append(left).append(", %eax\n");
                builder.append("  cmpl ").append(right).append(", %eax\n");
                builder.append("  setl %al\n");
                builder.append("  movzbl %al, %eax\n");
                builder.append("  movl %eax, ").append(dest).append("\n");
            }
            case LeNode le -> {
                String left = getPhysicalRegister(registers.get(predecessorSkipProj(le, BinaryOperationNode.LEFT)));
                String right = getPhysicalRegister(registers.get(predecessorSkipProj(le, BinaryOperationNode.RIGHT)));
                String dest = getPhysicalRegister(registers.get(le));
                builder.append("\n");
                builder.append("  movl ").append(left).append(", %eax\n");
                builder.append("  cmpl ").append(right).append(", %eax\n");
                builder.append("  setle %al\n");
                builder.append("  movzbl %al, %eax\n");
                builder.append("  movl %eax, ").append(dest).append("\n");
            }
            case GtNode gt -> {
                String left = getPhysicalRegister(registers.get(predecessorSkipProj(gt, BinaryOperationNode.LEFT)));
                String right = getPhysicalRegister(registers.get(predecessorSkipProj(gt, BinaryOperationNode.RIGHT)));
                String dest = getPhysicalRegister(registers.get(gt));
                builder.append("\n");
                builder.append("  movl ").append(left).append(", %eax\n");
                builder.append("  cmpl ").append(right).append(", %eax\n");
                builder.append("  setg %al\n");
                builder.append("  movzbl %al, %eax\n");
                builder.append("  movl %eax, ").append(dest).append("\n");
            }
            case GeNode ge -> {
                String left = getPhysicalRegister(registers.get(predecessorSkipProj(ge, BinaryOperationNode.LEFT)));
                String right = getPhysicalRegister(registers.get(predecessorSkipProj(ge, BinaryOperationNode.RIGHT)));
                String dest = getPhysicalRegister(registers.get(ge));
                builder.append("\n");
                builder.append("  movl ").append(left).append(", %eax\n");
                builder.append("  cmpl ").append(right).append(", %eax\n");
                builder.append("  setge %al\n");
                builder.append("  movzbl %al, %eax\n");
                builder.append("  movl %eax, ").append(dest).append("\n");
            }
            case EqNode eq -> {
                String left = getPhysicalRegister(registers.get(predecessorSkipProj(eq, BinaryOperationNode.LEFT)));
                String right = getPhysicalRegister(registers.get(predecessorSkipProj(eq, BinaryOperationNode.RIGHT)));
                String dest = getPhysicalRegister(registers.get(eq));
                builder.append("\n");
                builder.append("  movl ").append(left).append(", %eax\n");
                builder.append("  cmpl ").append(right).append(", %eax\n");
                builder.append("  sete %al\n");
                builder.append("  movzbl %al, %eax\n");
                builder.append("  movl %eax, ").append(dest).append("\n");
            }
            case NeNode ne -> {
                String left = getPhysicalRegister(registers.get(predecessorSkipProj(ne, BinaryOperationNode.LEFT)));
                String right = getPhysicalRegister(registers.get(predecessorSkipProj(ne, BinaryOperationNode.RIGHT)));
                String dest = getPhysicalRegister(registers.get(ne));
                builder.append("\n");
                builder.append("  movl ").append(left).append(", %eax\n");
                builder.append("  cmpl ").append(right).append(", %eax\n");
                builder.append("  setne %al\n");
                builder.append("  movzbl %al, %eax\n");
                builder.append("  movl %eax, ").append(dest).append("\n");
            }
            case BitwiseAndNode and -> {
                String left = getPhysicalRegister(registers.get(predecessorSkipProj(and, BinaryOperationNode.LEFT)));
                String right = getPhysicalRegister(registers.get(predecessorSkipProj(and, BinaryOperationNode.RIGHT)));
                String dest = getPhysicalRegister(registers.get(and));
                builder.append("\n");
                builder.append("  movl ").append(left).append(", %eax\n");
                builder.append("  andl ").append(right).append(", %eax\n");
                builder.append("  movl %eax, ").append(dest).append("\n");
            }
            case BitwiseXorNode xor -> {
                String left = getPhysicalRegister(registers.get(predecessorSkipProj(xor, BinaryOperationNode.LEFT)));
                String right = getPhysicalRegister(registers.get(predecessorSkipProj(xor, BinaryOperationNode.RIGHT)));
                String dest = getPhysicalRegister(registers.get(xor));
                builder.append("\n");
                builder.append("  movl ").append(left).append(", %eax\n");
                builder.append("  xorl ").append(right).append(", %eax\n");
                builder.append("  movl %eax, ").append(dest).append("\n");
            }
            case BitwiseOrNode or -> {
                String left = getPhysicalRegister(registers.get(predecessorSkipProj(or, BinaryOperationNode.LEFT)));
                String right = getPhysicalRegister(registers.get(predecessorSkipProj(or, BinaryOperationNode.RIGHT)));
                String dest = getPhysicalRegister(registers.get(or));
                builder.append("\n");
                builder.append("  movl ").append(left).append(", %eax\n");
                builder.append("  orl ").append(right).append(", %eax\n");
                builder.append("  movl %eax, ").append(dest).append("\n");
            }
            // case LogicalAndNode and -> {
            //     generateLogical(builder, getPhysicalRegister(registers.get(predecessorSkipProj(and, BinaryOperationNode.LEFT))), getPhysicalRegister(registers.get(predecessorSkipProj(and, BinaryOperationNode.RIGHT))), getPhysicalRegister(registers.get(and)), "jz", ".Lend");
            // }
            // case LogicalOrNode or -> {
            //     generateLogical(builder, getPhysicalRegister(registers.get(predecessorSkipProj(or, BinaryOperationNode.LEFT))), getPhysicalRegister(registers.get(predecessorSkipProj(or, BinaryOperationNode.RIGHT))), getPhysicalRegister(registers.get(or)), "jnz", ".Lend");
            // }
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

    private void generateLogical(StringBuilder builder, String left, String right, String dest, String jumpIf, String jumpTo) {
        String uniqueId = String.format("_%d", labelCounter++);
        String falseLabel = ".Lfalse" + uniqueId;
        String trueLabel = ".Ltrue" + uniqueId;
        String endLabel = ".Lend" + uniqueId;

        builder.append("\n");
        builder.append("  movl ").append(left).append(", %eax\n");
        builder.append("  testl %eax, %eax\n");
        if (jumpIf.equals("jz")) {
            // Logical AND: if first is false, result is false
            builder.append("  ").append(jumpIf).append(" ").append(falseLabel).append("\n");
            builder.append("  movl ").append(right).append(", %eax\n");
            builder.append("  testl %eax, %eax\n");
            builder.append("  ").append(jumpIf).append(" ").append(falseLabel).append("\n");
            builder.append("  movl $1, %eax\n");
            builder.append("  jmp ").append(endLabel).append("\n");
            builder.append(falseLabel).append(":\n");
            builder.append("  movl $0, %eax\n");
        } else if (jumpIf.equals("jnz")) {
            // Logical OR: if first is true, result is true
            builder.append("  ").append(jumpIf).append(" ").append(trueLabel).append("\n");
            // Only evaluate right operand if left is false
            builder.append("  movl ").append(right).append(", %eax\n");
            builder.append("  testl %eax, %eax\n");
            builder.append("  ").append(jumpIf).append(" ").append(trueLabel).append("\n");
            builder.append("  movl $0, %eax\n");
            builder.append("  jmp ").append(endLabel).append("\n");
            builder.append(trueLabel).append(":\n");
            builder.append("  movl $1, %eax\n");
        } else {
            throw new IllegalArgumentException("Invalid jumpIf: " + jumpIf);
        }
        builder.append(endLabel).append(":\n");
        builder.append("  movl %eax, ").append(dest).append("\n");
    }
}
