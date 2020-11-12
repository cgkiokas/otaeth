//import Chisel.iotesters
//import chisel3.iotesters.PeekPokeTester
//import org.scalatest._
//
//class HelloSpec(c: scala.Crossbar) extends PeekPokeTester(c) {
//
//  var ledStatus = -1
//  println("Start the blinking LED")
//  for (i <- 0 until 100) {
//    step(10000)
//    val ledNow = peek(c.io.led).toInt
//    val s = if (ledNow == 0) "o" else "*"
//    if (ledStatus != ledNow) {
//      System.out.println(s)
//      ledStatus = ledNow
//    }
//  }
//  println("\nEnd the blinking LED")
//}
//
//object FifoTester extends App {
//  chisel3.iotesters.Driver.execute(Array("--target-dir", "generated", "--generate-vcd-output", "on"), () => new scala.Crossbar(2,8)) {
//    // iotesters.Driver.execute(Array("--target-dir", "generated", "--fint-write-vcd", "--wave-form-file-name", "generated/BubbleFifo.vcd"), () => new BubbleFifo(8, 4)) {
//    c => new HelloSpec(c)
//  }
//}