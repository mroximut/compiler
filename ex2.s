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
  subq $88, %rsp

.Lstart:
  movl $0, %r10d
  movl $1, %r11d

  movl %r11d, %eax
  testl %eax, %eax
  jz .Lblock4
  jmp .Lblock3


.Lblock3:
  movl %r10d, %r14d

  jmp .Lblock5


.Lblock4:
  movl %r10d, %r14d

  jmp .Lblock5


.Lblock5:

  movl %r14d, %eax
  testl %eax, %eax
  jz .Lblock1
  jmp .Lblock0


.Lblock1:

  movl %r11d, %eax
  cmpl $0, %eax
  sete %al
  movzbl %al, %eax
  movl %eax, -4(%rbp)

  movl -4(%rbp), %eax
  testl %eax, %eax
  jz .Lblock10
  jmp .Lblock9


.Lblock9:

  movl %r10d, %eax
  cmpl $0, %eax
  sete %al
  movzbl %al, %eax
  movl %eax, -12(%rbp)
  movl -12(%rbp), %r9d
  movl %r9d, -20(%rbp)

  jmp .Lblock11


.Lblock10:
  movl %r10d, -20(%rbp)

  jmp .Lblock11


.Lblock11:

  movl -20(%rbp), %eax
  testl %eax, %eax
  jz .Lblock7
  jmp .Lblock6


.Lblock6:

  movl %r11d, %eax
  testl %eax, %eax
  jz .Lblock19
  jmp .Lblock18


.Lblock18:

  movl %r10d, %eax
  cmpl $0, %eax
  sete %al
  movzbl %al, %eax
  movl %eax, -32(%rbp)
  movl -32(%rbp), %r9d
  movl %r9d, -40(%rbp)

  jmp .Lblock20


.Lblock19:
  movl %r10d, -40(%rbp)

  jmp .Lblock20


.Lblock20:

  movl -40(%rbp), %eax
  testl %eax, %eax
  jz .Lblock16
  jmp .Lblock15


.Lblock15:

  movl %r11d, %eax
  cmpl $0, %eax
  sete %al
  movzbl %al, %eax
  movl %eax, -48(%rbp)
  movl -48(%rbp), %r9d
  movl %r9d, -56(%rbp)

  jmp .Lblock17


.Lblock16:
  movl %r10d, -56(%rbp)

  jmp .Lblock17


.Lblock17:

  movl -56(%rbp), %eax
  testl %eax, %eax
  jz .Lblock13
  jmp .Lblock12


.Lblock12:
  movl %r10d, -68(%rbp)

  jmp .Lblock14


.Lblock13:
  movl %r10d, -68(%rbp)

  jmp .Lblock14


.Lblock14:
  movl -68(%rbp), %r9d
  movl %r9d, -76(%rbp)

  jmp .Lblock8


.Lblock7:
  movl %r10d, -76(%rbp)

  jmp .Lblock8


.Lblock8:
  movl -76(%rbp), %r9d
  movl %r9d, -84(%rbp)

  jmp .Lblock2


.Lblock0:
  movl %r11d, -84(%rbp)

  jmp .Lblock2


.Lblock2:

  movl -84(%rbp), %eax
  cmpl $0, %eax
  sete %al
  movzbl %al, %eax
  movl %eax, -88(%rbp)

  movl -88(%rbp), %eax
  testl %eax, %eax
  jz .Lblock22
  jmp .Lblock21


.Lblock21:
  movl %r10d, %eax
  leave
  ret

.Lblock22:
  movl %r11d, %eax
  leave
  ret

.Lend:


.section .note.GNU-stack,"",@progbits
