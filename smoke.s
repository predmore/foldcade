.syntax unified
.cpu arm7tdmi
.arm
.section .text
.global _start
_start:
 b reset
.org 0xa0
.ascii "FOLDCADE QA "
.ascii "FCQ1"
.ascii "00"
.byte 0x96,0,0
.space 7
.byte 0,0
.space 2
.org 0xc0
reset:
 ldr r0, =0x04000000
 ldr r1, =0x0403
 strh r1, [r0]
 ldr r0, =0x06000000
 mov r1, #31
 mov r2, #0x9600
fill:
 strh r1, [r0], #2
 subs r2, r2, #1
 bne fill
 ldr r0, =0x0e000000
 ldrb r1, [r0]
 add r1, r1, #1
 strb r1, [r0]
 mov r1, #0x51
 strb r1, [r0, #1]
wait:
 b wait
.ltorg
.balign 4
.ascii "SRAM_V113"
.org 0x20000
