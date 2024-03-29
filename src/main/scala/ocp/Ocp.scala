
/*
 * Generic definitions for OCP
 *
 * Authors: Wolfgang Puffitsch (wpuffitsch@gmail.com)
 *
 */

package ocp

import Chisel._

// Constants for MCmd
object OcpCmd {
  val IDLE = UInt("b000")
  val WR   = UInt("b001")
  val RD   = UInt("b010")
//  val RDEX = UInt("b011")
//  val RDL  = UInt("b100")
//  val WRNP = UInt("b101")
//  val WRC  = UInt("b110")
//  val BCST = UInt("b111")
}

// Constants for SResp
object OcpResp {
  val NULL = UInt("b00")
  val DVA  = UInt("b01")
  val FAIL = UInt("b10")
  val ERR  = UInt("b11")
}

// MS: would like fields (e.g. data) to start with lower case.
// Just classes start with upper case.

// Signals generated by master
class OcpMasterSignals(addrWidth : Int, dataWidth : Int) extends Bundle() {
  val Cmd = Output(UInt(width = 3))
  val Addr = Output(UInt(width = addrWidth))
  val Data = Output(UInt(width = dataWidth))

  // This does not really clone, but Data.clone doesn't either
  override def cloneType() = {
    val res = new OcpMasterSignals(addrWidth, dataWidth)
    res.asInstanceOf[this.type]
  }
}

// Signals generated by slave
class OcpSlaveSignals(dataWidth : Int) extends Bundle() {
  val Resp = Input(UInt(width = 2))
  val Data = Input(UInt(width = dataWidth))

  // This does not really clone, but Data.clone doesn't either
  override def cloneType() = {
    val res = new OcpSlaveSignals(dataWidth)
    res.asInstanceOf[this.type]
  }
}
