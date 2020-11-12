package main.scala.crossbar

import Chisel._



class CrossbarPort(width : Int) extends Bundle()
{
  val in = UInt(INPUT ,width)
  val out = UInt(OUTPUT ,width)

  override def cloneType() = {
    val res = new CrossbarPort(width)
    res.asInstanceOf[this.type]
  }

}


