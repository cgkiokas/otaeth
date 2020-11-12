package main.scala.controller

import chisel3._
import chisel3.util._
import main.scala.axi.AXI4LiteMMBridge

class AxiMasterControlSignals(addrWidth: Int, dataWidth: Int) extends Bundle {
  val read = Input(Bool())
  val write = Input(Bool())
  val address = Input(UInt(addrWidth.W))
  val data = Input(UInt(dataWidth.W))
  val slaveData = Output(UInt(dataWidth.W))
  val byteEn = Input(UInt((dataWidth/4).W))
  val dataValid = Output(Bool())

  override def cloneType() = {
    val res = new AxiMasterControlSignals(addrWidth,dataWidth)
    res.asInstanceOf[this.type]
  }
}

class AxiMasterOutSignals(addrWidth: Int, dataWidth: Int) extends Bundle {
  val araddr = Output(UInt(addrWidth.W))
  val arready = Input(Bool())
  val arvalid = Output(Bool())
  val awaddr = Output(UInt(addrWidth.W))
  val awready = Input(Bool())
  val awvalid = Output(Bool())
  val bready = Output(Bool())
  val bresp = Input(UInt(2.W))
  val bvalid = Input(Bool())
  val rdata = Input(UInt(dataWidth.W))
  val rready = Output(Bool())
  val rresp = Input(UInt(2.W))
  val rvalid = Input(Bool())
  val wdata = Output(UInt(dataWidth.W))
  val wready = Input(Bool())
  val wstrb = Output(UInt((dataWidth / 8).W))
  val wvalid = Output(Bool())

  override def cloneType() = {
    val res = new AxiMasterOutSignals(addrWidth,dataWidth)
    res.asInstanceOf[this.type]
  }
}

class Controller extends Module {
  val io = IO(new Bundle {
    val axiMasterPorts = Vec(4, new AxiMasterOutSignals(32,32))

  })

  val slaveDataRegs = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val slaveDataValids = RegInit(VecInit(Seq.fill(4)(false.B)))

  val axiMasterControl = Wire(Vec(4, new AxiMasterControlSignals(32,32)))

  for (i <- 0 until 4)
  {
    axiMasterControl(i).read := false.B
    axiMasterControl(i).write := false.B
    axiMasterControl(i).address := 32.U
    axiMasterControl(i).data := 0.U
    slaveDataRegs(i) := axiMasterControl(i).slaveData
    slaveDataValids(i) := axiMasterControl(i).dataValid
    axiMasterControl(i).byteEn := 15.U

  }

  val axiMasters = for (i <- 0 until 4) yield
    {
      val axiMaster = Module(new AXI4LiteMMBridge())
      //Control signals
      axiMaster.io.read := axiMasterControl(i).read
      axiMaster.io.write :=  axiMasterControl(i).write
      axiMaster.io.address :=  axiMasterControl(i).address
      axiMaster.io.data :=  axiMasterControl(i).data
      axiMasterControl(i).slaveData := axiMaster.io.slaveData
      axiMasterControl(i).dataValid := axiMaster.io.dataValid
      axiMaster.io.byteEn := axiMasterControl(i).byteEn
      //AXI Signals

      axiMaster.io.arready := io.axiMasterPorts(i).arready
      axiMaster.io.awready := io.axiMasterPorts(i).awready
      axiMaster.io.bresp := io.axiMasterPorts(i).bresp
      axiMaster.io.bvalid := io.axiMasterPorts(i).bvalid
      axiMaster.io.rdata := io.axiMasterPorts(i).rdata
      axiMaster.io.rresp := io.axiMasterPorts(i).rresp
      axiMaster.io.rvalid := io.axiMasterPorts(i).rvalid
      axiMaster.io.wready := io.axiMasterPorts(i).wready
      io.axiMasterPorts(i).arvalid := axiMaster.io.arvalid
      io.axiMasterPorts(i).awaddr := axiMaster.io.awaddr
      io.axiMasterPorts(i).awvalid := axiMaster.io.awvalid
      io.axiMasterPorts(i).bready := axiMaster.io.bready
      io.axiMasterPorts(i).rready := axiMaster.io.rready
      io.axiMasterPorts(i).wdata := axiMaster.io.wdata
      io.axiMasterPorts(i).wstrb := axiMaster.io.wstrb
      io.axiMasterPorts(i).araddr := axiMaster.io.araddr
      io.axiMasterPorts(i).wvalid := axiMaster.io.wvalid
    }

}

object Controller extends App {
  chisel3.Driver.execute(Array("--target-dir", "generated"), () => new Controller())
}