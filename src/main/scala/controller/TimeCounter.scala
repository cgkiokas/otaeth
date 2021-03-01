package main.scala.controller

import chisel3._
import chisel3.util._


class TimeCounter extends Module {
  val io = IO(new Bundle {
    val time = Output(UInt(64.W))
  })

  val secondsCounterReg = RegInit (0.U(32.W))
  val secondsTick = secondsCounterReg === (100000000 -1).U
  val secondsReg = RegInit(0.U(32.W))
  secondsCounterReg := secondsCounterReg + 1.U
  when (secondsTick) {
    secondsReg := secondsReg + 1.U
  }

  val nanoCountReg = RegInit(0.U(32.W))
  nanoCountReg := nanoCountReg + 1.U

  io.time := Cat(nanoCountReg,secondsReg)
}

object TimeCounter extends App {
  chisel3.Driver.execute(Array("--target-dir", "generated"), () => new TimeCounter())
}