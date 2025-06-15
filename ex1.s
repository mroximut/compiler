.global main
.global _main
.text
main:
call _main
movq %rax, %rdi
movq $0x3C, %rax
syscall
_main:
  pushq %rbp
  movq %rsp, %rbp
  subq $8, %rsp

.Lstart:
  movl $5, %r10d
  movl $1, %r11d
  movl $8, %r12d
  movl %r10d, -8(%rbp)

  jmp .Lblock0


.Lblock0:

  movl -8(%rbp), %eax
  cmpl %r12d, %eax
  setl %al
  movzbl %al, %eax
  movl %eax, %r14d

  movl %r14d, %eax
  testl %eax, %eax
  jz .Lblock2
  jmp .Lblock1


.Lblock1:

  movl -8(%rbp), %r9d
  addl %r11d, %r9d
  movl %r9d, -4(%rbp)
  movl -4(%rbp), %r9d
  movl %r9d, -8(%rbp)

  jmp .Lblock0


.Lblock2:
  movl -8(%rbp), %eax
  leave
  ret

.Lend:


.section .note.GNU-stack,"",@progbits
