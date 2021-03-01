//package main.scala.axi
//
//import chisel3._
//import chisel3.util._
//
//
//
//
//class AXI4LiteMMBridgeMod(addrWidth: Int = 32, dataWidth: Int = 32) extends Module {
//  val io = IO(new Bundle() {
//      //Control Signals
//      val read = Input(Bool())
//      val write = Input(Bool())
//      val address = Input(UInt(addrWidth.W))
//      val data = Input(UInt(dataWidth.W))
//      val slaveData = Output(UInt(dataWidth.W))
//      val byteEn = Input(UInt((dataWidth/4).W))
//      val dataValid = Output(Bool())
//      //AXI Signals
//      val araddr = Output(UInt(addrWidth.W))
//      val arready = Input(Bool())
//      val arvalid = Output(Bool())
//      val awaddr = Output(UInt(addrWidth.W))
//      val awready = Input(Bool())
//      val awvalid = Output(Bool())
//      val bready = Output(Bool())
//      val bresp = Input(UInt(2.W))
//      val bvalid = Input(Bool())
//      val rdata = Input(UInt(dataWidth.W))
//      val rready = Output(Bool())
//      val rresp = Input(UInt(2.W))
//      val rvalid = Input(Bool())
//      val wdata = Output(UInt(dataWidth.W))
//      val wready = Input(Bool())
//      val wstrb = Output(UInt((dataWidth / 8).W))
//      val wvalid = Output(Bool())
//  })
//
//  val mAxiPort = Wire(new AxiLiteMasterPort(addrWidth, dataWidth))
//  mAxiPort.ar.bits.prot := 0.U
//  mAxiPort.aw.bits.prot := 0.U
//
//  val holdBusyReg = RegInit(false.B)
//
//  val readReg = RegInit(io.read)
//  val writeReg = RegInit(io.write)
//
//  // For simplicity we register new commands only when the AXI slave has answered fully
//  when(~holdBusyReg) {
//    readReg := io.read
//    writeReg := io.write
//  }
//
//  when(~holdBusyReg) {
//    when(io.read) {
//      holdBusyReg := ~mAxiPort.ar.ready
//    }.elsewhen(io.write) {
//      holdBusyReg := ~mAxiPort.aw.ready && ~mAxiPort.w.ready
//    }
//  }.otherwise {
//    when(readReg) {
//      holdBusyReg := ~mAxiPort.ar.ready
//    }.elsewhen(writeReg) {
//      holdBusyReg := ~mAxiPort.aw.ready && ~mAxiPort.w.ready
//    }
//  }
//
//  // Write channel
//  mAxiPort.aw.valid := io.write && holdBusyReg
//  mAxiPort.aw.bits.addr :=io.address
//  mAxiPort.w.valid := io.write && holdBusyReg
//  mAxiPort.w.bits.data := io.data
//  mAxiPort.w.bits.strb := io.byteEn
//
//  // Read channel
//  mAxiPort.ar.bits.addr := io.address
//  mAxiPort.ar.valid := io.read && holdBusyReg
//  mAxiPort.r.ready := true.B // the ocp bus is always ready to accept data
//  mAxiPort.b.ready := true.B
//
//  // Drive OCP slave
//  io.slaveData := mAxiPort.r.bits.data
//
//  io.dataValid:= Mux(mAxiPort.b.valid || mAxiPort.r.valid, true.B, false.B)
//
//  //  // Xilinx naming convention for nice block diagrams and inferring interfaces
//  //  // TODO: investigate erroneous generated Verilog
//
//  io.araddr.suggestName("m_axi_araddr")
//  io.arready.suggestName("m_axi_arready")
//  io.arvalid.suggestName("m_axi_arvalid")
//  io.awaddr.suggestName("m_axi_awaddr")
//  io.awready.suggestName("m_axi_awready")
//  io.awvalid.suggestName("m_axi_awvalid")
//  io.bready.suggestName("m_axi_bready")
//  io.bresp.suggestName("m_axi_bresp")
//  io.bvalid.suggestName("m_axi_bvalid")
//  io.rready.suggestName("m_axi_rready")
//  io.rdata.suggestName("m_axi_rdata")
//  io.rvalid.suggestName("m_axi_rvalid")
//  io.rresp.suggestName("m_axi_rresp")
//  io.wdata.suggestName("m_axi_wdata")
//  io.wready.suggestName("m_axi_wready")
//  io.wstrb.suggestName("m_axi_wstrb")
//  io.wvalid.suggestName("m_axi_wvalid")
//  //
//  // IO plumbing
//  io.araddr := mAxiPort.ar.bits.addr
//  mAxiPort.ar.ready := io.arready
//  io.arvalid := mAxiPort.ar.valid
//  io.awaddr := mAxiPort.aw.bits.addr
//  mAxiPort.aw.ready := io.awready
//  io.awvalid := mAxiPort.aw.valid
//  io.bready := mAxiPort.b.ready
//  mAxiPort.b.bits.resp := io.bresp
//  mAxiPort.b.valid := io.bvalid
//  mAxiPort.r.bits.data := io.rdata
//  io.rready := mAxiPort.r.ready
//  mAxiPort.r.bits.resp := io.rresp
//  mAxiPort.r.valid := io.rvalid
//  io.wdata := mAxiPort.w.bits.data
//  mAxiPort.w.ready := io.wready
//  io.wstrb := mAxiPort.w.bits.strb
//  io.wvalid := mAxiPort.w.valid
//
//}
//
//object AXI4LiteMMBridgeMod extends App {
//  chisel3.Driver.execute(Array("--target-dir", "generated"), () => new AXI4LiteMMBridgeMod())
//}