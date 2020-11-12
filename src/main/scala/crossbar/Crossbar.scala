package main.scala.crossbar

/*
 * This code is a minimal hardware described in Chisel.
 * 
 * Blinking LED: the FPGA version of Hello World
 */

import chisel3._
import chisel3.util._


/**
 * The crossbar
 */


class Crossbar2x2(width : Int) extends Module {


  val io = IO(new Bundle {
    val ports = Vec(2, new CrossbarPort(width))
    val control = Input(UInt(2.W))
  })

  for (i <- 0 until 2 by 1) {
    io.ports(i).out := io.ports((io.control(i))).in
  }

}

class Crossbar4x4(width : Int) extends Module
{
  val io = IO(new Bundle {
    val ports = Vec(4, new CrossbarPort(width))
    val control = Vec(6, Input(UInt(2.W)))
  })

  val crossbar0 =  Module(new Crossbar2x2(width))
  val crossbar1 =  Module(new Crossbar2x2(width))
  val crossbar2 =  Module(new Crossbar2x2(width))
  val crossbar3 =  Module(new Crossbar2x2(width))
  val crossbar4 =  Module(new Crossbar2x2(width))
  val crossbar5 =  Module(new Crossbar2x2(width))

  crossbar0.io.control := io.control(0)
  crossbar1.io.control := io.control(1)
  crossbar2.io.control := io.control(2)
  crossbar3.io.control := io.control(3)
  crossbar4.io.control := io.control(4)
  crossbar5.io.control := io.control(5)

  //Crossbar 0
  crossbar0.io.ports(0).in := io.ports(0).in
  crossbar0.io.ports(1).in := io.ports(1).in

  crossbar2.io.ports(0).in := crossbar0.io.ports(0).out
  crossbar3.io.ports(0).in := crossbar0.io.ports(1).out

  //Crossbar 1
  crossbar1.io.ports(0).in := io.ports(2).in
  crossbar1.io.ports(1).in := io.ports(3).in

  crossbar2.io.ports(1).in := crossbar1.io.ports(0).out
  crossbar3.io.ports(1).in := crossbar1.io.ports(1).out

  //Crossbar 2

  crossbar4.io.ports(0).in := crossbar2.io.ports(0).out
  crossbar5.io.ports(0).in := crossbar2.io.ports(1).out

  //Crossbar 3

  crossbar4.io.ports(1).in := crossbar3.io.ports(0).out
  crossbar5.io.ports(1).in := crossbar3.io.ports(1).out

  //Crossbar 4

  io.ports(0).out := crossbar4.io.ports(0).out
  io.ports(1).out := crossbar4.io.ports(1).out


  //Crossbar 5

   io.ports(2).out := crossbar5.io.ports(0).out
   io.ports(3).out := crossbar5.io.ports(1).out

}

class Crossbar(width : Int) extends Module {
  val io = IO(new Bundle {
    val ports = Vec(16, new CrossbarPort(width))
    val control = Vec(12, Vec(6,(Input(UInt(2.W)))))
  })

  val crossbar1_in =  Module(new Crossbar4x4(width))
  val crossbar2_in =  Module(new Crossbar4x4(width))
  val crossbar3_in =  Module(new Crossbar4x4(width))
  val crossbar4_in =  Module(new Crossbar4x4(width))

  val crossbarA =  Module(new Crossbar4x4(width))
  val crossbarB =  Module(new Crossbar4x4(width))
  val crossbarC =  Module(new Crossbar4x4(width))
  val crossbarD =  Module(new Crossbar4x4(width))

  val crossbar1_out =  Module(new Crossbar4x4(width))
  val crossbar2_out =  Module(new Crossbar4x4(width))
  val crossbar3_out =  Module(new Crossbar4x4(width))
  val crossbar4_out =  Module(new Crossbar4x4(width))


  crossbar1_in.io.control := io.control(0)
  crossbar2_in.io.control := io.control(1)
  crossbar3_in.io.control := io.control(2)
  crossbar4_in.io.control := io.control(3)

  crossbarA.io.control := io.control(4)
  crossbarB.io.control := io.control(5)
  crossbarC.io.control := io.control(6)
  crossbarD.io.control := io.control(7)

  crossbar1_out.io.control := io.control(8)
  crossbar2_out.io.control := io.control(9)
  crossbar3_out.io.control := io.control(10)
  crossbar4_out.io.control := io.control(11)

  //1st Stage
  crossbar1_in.io.ports(0).in := io.ports(0).in
  crossbar1_in.io.ports(1).in := io.ports(1).in
  crossbar1_in.io.ports(2).in := io.ports(2).in
  crossbar1_in.io.ports(3).in := io.ports(3).in

  crossbar2_in.io.ports(0).in := io.ports(4).in
  crossbar2_in.io.ports(1).in := io.ports(5).in
  crossbar2_in.io.ports(2).in := io.ports(6).in
  crossbar2_in.io.ports(3).in := io.ports(7).in

  crossbar3_in.io.ports(0).in := io.ports(8).in
  crossbar3_in.io.ports(1).in := io.ports(9).in
  crossbar3_in.io.ports(2).in := io.ports(10).in
  crossbar3_in.io.ports(3).in := io.ports(11).in

  crossbar4_in.io.ports(0).in := io.ports(12).in
  crossbar4_in.io.ports(1).in := io.ports(13).in
  crossbar4_in.io.ports(2).in := io.ports(14).in
  crossbar4_in.io.ports(3).in := io.ports(15).in

  //2nd Stage
  crossbarA.io.ports(0).in := crossbar1_in.io.ports(0).out
  crossbarA.io.ports(1).in := crossbar2_in.io.ports(0).out
  crossbarA.io.ports(2).in := crossbar3_in.io.ports(0).out
  crossbarA.io.ports(3).in := crossbar4_in.io.ports(0).out

  crossbarB.io.ports(0).in := crossbar1_in.io.ports(1).out
  crossbarB.io.ports(1).in := crossbar2_in.io.ports(1).out
  crossbarB.io.ports(2).in := crossbar3_in.io.ports(1).out
  crossbarB.io.ports(3).in := crossbar4_in.io.ports(1).out

  crossbarC.io.ports(0).in := crossbar1_in.io.ports(2).out
  crossbarC.io.ports(1).in := crossbar2_in.io.ports(2).out
  crossbarC.io.ports(2).in := crossbar3_in.io.ports(2).out
  crossbarC.io.ports(3).in := crossbar4_in.io.ports(2).out

  crossbarD.io.ports(0).in := crossbar1_in.io.ports(3).out
  crossbarD.io.ports(1).in := crossbar2_in.io.ports(3).out
  crossbarD.io.ports(2).in := crossbar3_in.io.ports(3).out
  crossbarD.io.ports(3).in := crossbar4_in.io.ports(3).out

  crossbar1_out.io.ports(0).in := crossbarA.io.ports(0).out
  crossbar2_out.io.ports(0).in := crossbarA.io.ports(1).out
  crossbar3_out.io.ports(0).in := crossbarA.io.ports(2).out
  crossbar4_out.io.ports(0).in := crossbarA.io.ports(3).out

  crossbar1_out.io.ports(1).in := crossbarB.io.ports(0).out
  crossbar2_out.io.ports(1).in := crossbarB.io.ports(1).out
  crossbar3_out.io.ports(1).in := crossbarB.io.ports(2).out
  crossbar4_out.io.ports(1).in := crossbarB.io.ports(3).out

  crossbar1_out.io.ports(2).in := crossbarC.io.ports(0).out
  crossbar2_out.io.ports(2).in := crossbarC.io.ports(1).out
  crossbar3_out.io.ports(2).in := crossbarC.io.ports(2).out
  crossbar4_out.io.ports(2).in := crossbarC.io.ports(3).out

  crossbar1_out.io.ports(3).in := crossbarD.io.ports(0).out
  crossbar2_out.io.ports(3).in := crossbarD.io.ports(1).out
  crossbar3_out.io.ports(3).in := crossbarD.io.ports(2).out
  crossbar4_out.io.ports(3).in := crossbarD.io.ports(3).out

  //3d Stage
  io.ports(0).out := crossbar1_out.io.ports(0).out
  io.ports(1).out := crossbar1_out.io.ports(1).out
  io.ports(2).out := crossbar1_out.io.ports(2).out
  io.ports(3).out := crossbar1_out.io.ports(3).out

  io.ports(4).out := crossbar2_out.io.ports(0).out
  io.ports(5).out := crossbar2_out.io.ports(1).out
  io.ports(6).out := crossbar2_out.io.ports(2).out
  io.ports(7).out := crossbar2_out.io.ports(3).out

  io.ports(8).out := crossbar3_out.io.ports(0).out
  io.ports(9).out := crossbar3_out.io.ports(1).out
  io.ports(10).out := crossbar3_out.io.ports(2).out
  io.ports(11).out := crossbar3_out.io.ports(3).out

  io.ports(12).out := crossbar4_out.io.ports(0).out
  io.ports(13).out := crossbar4_out.io.ports(1).out
  io.ports(14).out := crossbar4_out.io.ports(2).out
  io.ports(15).out := crossbar4_out.io.ports(3).out
}
/**
 *
 */
object Crossbar extends App {
  chisel3.Driver.execute(Array("--target-dir", "generated"), () => new Crossbar4x4(width = 8))
}