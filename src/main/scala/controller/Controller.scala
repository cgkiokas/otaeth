package main.scala.controller

import chisel3._
import chisel3.util._
import main.scala.axi.AXI4LiteMMBridge
import ocp._


object Constants {
  def DEFAULT_MAC_ADDR = "x00005E00FACE".U(32.W)

  def TX_FRAME_START =  "x0000".U(64.W)
  def TX_FRAME_LENGTH = "x07F4".U(64.W)

  def RX_FRAME_START = "x1000".U(64.W)
  def RX_FRAME_END = "x17FB".U(64.W)

  def STATUS_BIT = "x17FC".U(64.W)
  def TX_BIT = "x07FC".U(64.W)
}
//00-00-5E-00-FA-CE

//class AxiMasterControlSignals(addrWidth: Int, dataWidth: Int) extends Bundle {
//  val read = (Bool())
//  val write = (Bool())
//  val address = (UInt(addrWidth.W))
//  val data = (UInt(dataWidth.W))
//  val slaveData = (UInt(dataWidth.W))
//  val byteEn = (UInt((dataWidth/4).W))
//  val dataValid = (Bool())

//  override def cloneType() = {
//    val res = new AxiMasterControlSignals(addrWidth,dataWidth)
//    res.asInstanceOf[this.type]
//  }
//}



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
    val ocpMasters = Vec(4, new OcpCoreMasterPort(32,32))
    val patmosInterface = new OcpCoreSlavePort(16,32)
    val testData = Input(UInt(32.W))
    val timerInput = Input(UInt(64.W))

    //Schedule Registers
    val activations = Vec(4, Input(UInt(32.W)))
    val periods = Vec(4, Input(UInt(32.W)))
    val windows = Vec(4, Input(UInt(32.W)))
    val jitter =  Input(UInt(32.W))

    //Debug
    val sendFrame = Vec(4, Output(Bool()))

    val led1 = Output(Bool())
    val led2 = Output(Bool())
    val led3 = Output(Bool())

  })

  val leds = RegInit(VecInit(Seq.fill(3)(false.B)));

  io.led1 := leds(0)
  io.led2 := leds(1)
  io.led3 := leds(2)

  val sendPacket = RegInit(VecInit(Seq.fill(4)(false.B)))

  io.sendFrame(0) :=  sendPacket(0)
  io.sendFrame(1) :=  sendPacket(1)
  io.sendFrame(2) :=  sendPacket(2)
  io.sendFrame(3) :=  sendPacket(3)


  val port1 :: port2 :: port3 :: port4 :: Nil = Enum (4)
  val stateReg1 = RegInit(port2)
  val stateReg2 = RegInit(port3)
  val stateReg3 = RegInit(port4)
  val stateReg4 = RegInit(port1)

  val stateReg1next = RegInit(port2)
  val stateReg2next = RegInit(port3)
  val stateReg3next = RegInit(port4)
  val stateReg4next = RegInit(port1)

  val port1Act = RegInit(io.activations(0))
  val port2Act = RegInit(io.activations(1))
  val port3Act = RegInit(io.activations(2))
  val port4Act = RegInit(io.activations(3))
  val port1T = RegInit(io.periods(0))
  val port2T = RegInit(io.periods(1))
  val port3T = RegInit(io.periods(2))
  val port4T = RegInit(io.periods(3))
  val port1Window = RegInit(io.windows(0))
  val port2Window = RegInit(io.windows(1))
  val port3Window = RegInit(io.windows(2))
  val port4Window = RegInit(io.windows(3))
  val jitter = RegInit(io.jitter)

  val startFlag = RegInit(false.B)
  val resetFlag = RegInit(false.B)


  val memCountersRx = RegInit(VecInit(Seq.fill(4)(Constants.RX_FRAME_START)))
  val memCountersTx = RegInit(VecInit(Seq.fill(4)(Constants.TX_FRAME_START)))


  val port1WindowCounterReg = RegInit (0.U(32.W))
  val port1WindowDone = port1WindowCounterReg === (port1Window + jitter)

  val port2WindowCounterReg = RegInit (0.U(32.W))
  val port2WindowDone = port2WindowCounterReg === (port2Window + jitter)

  val port3WindowCounterReg = RegInit (0.U(32.W))
  val port3WindowDone = port3WindowCounterReg === (port3Window + jitter)

  val port4WindowCounterReg = RegInit (0.U(32.W))
  val port4WindowDone = port4WindowCounterReg === (port4Window + jitter)

  val secondsReg = RegInit(0.U(32.W))
  val nanoReg = RegInit(0.U(32.W))

  val idle::readPort1::readPort2::readPort3::readPort4::writePort1::Nil = Enum(6)
  val state = RegInit(idle)



  val slaveDataRegs =   RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val slaveStatusBits = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val slaveDataValids = RegInit(VecInit(Seq.fill(4)(false.B)))


  val PeriodReg1 = RegInit(port1Act)
  val PeriodReg2 = RegInit(port2Act)
  val PeriodReg3 = RegInit(port3Act)
  val PeriodReg4 = RegInit(port4Act)

  
  val startPort1 = RegInit(false.B)
  val startPort2 = RegInit(false.B)
  val startPort3 = RegInit(false.B)
  val startPort4 = RegInit(false.B)

  val delayReg = RegInit(0.U(32.W))

  val poll::readRX::writeTX::writeLength::writeStatus::writeStatus2::Nil = Enum(6)
  val transmssionStates = RegInit(VecInit(Seq.fill(4)(poll)))

  val txLengthCounter = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))

  val respReg = RegInit(OcpResp.NULL)
  val dataReg = RegInit(0.U(32.W))
  val mOcpReg = RegInit(io.patmosInterface.M)
  val mOcpAddr = RegInit(0.U(16.W))
  val mOcpData = RegInit(0.U(32.W))
  //register patmos inputs
  mOcpReg := io.patmosInterface.M

  nanoReg := io.timerInput(63,32)
  secondsReg := io.timerInput(31,0)

  respReg := OcpResp.NULL

  io.patmosInterface.S.Data := dataReg
  io.patmosInterface.S.Resp := respReg


  val stopped::cmd::dva::Nil = Enum(3)
  val controlState = RegInit(idle)

  when(mOcpReg.Cmd === OcpCmd.RD || mOcpReg.Cmd === OcpCmd.WR=== OcpCmd.WR) {
    mOcpAddr := mOcpReg.Addr(6,0)
    mOcpData := mOcpReg.Data
    controlState := cmd;
  }

  switch(controlState) {
    is(stopped) {

    }
    is(cmd){
      switch(mOcpAddr) {
        is("x0".U(16.W)) {
          startFlag := mOcpData === 1.U
          PeriodReg1 := port1Act + nanoReg
          PeriodReg2 := port2Act + nanoReg
          PeriodReg3 := port3Act + nanoReg
          PeriodReg4 := port4Act + nanoReg
        }
        is("x4".U(16.W)) {
          resetFlag := mOcpData === 1.U
        }
        is("x8".U(16.W)){
          port1Act := mOcpData
        }
        is("xc".U(16.W)){
          port2Act := mOcpData
        }
        is("x10".U(16.W)) {
          port3Act := mOcpData
        }
        is("x14".U(16.W)) {
          port4Act := mOcpData
        }
        is("x18".U(16.W)) {
          port1T := mOcpData
        }
        is("x1c".U(16.W)) {
          port2T := mOcpData
        }
        is("x20".U(16.W)) {
          port3T := mOcpData
        }
        is("x24".U(16.W)) {
          port4T := mOcpData
        }
        is("x28".U(16.W)) {
          port1Window := mOcpData
        }
        is("x2c".U(16.W)) {
          port2Window := mOcpData
        }
        is("x30".U(16.W)) {
          port3Window := mOcpData
        }
        is("x34".U(16.W)) {
          port4Window := mOcpData
        }
        is("x38".U(16.W)) {
          jitter := mOcpData
        }
      }
      controlState := dva;
    }
    is(dva){
      respReg := OcpResp.DVA
      controlState := stopped;
    }

  }




//  when(mOcpReg.Cmd === OcpCmd.WR)
//  {

//  }

//  when(mOcpReg.Cmd === OcpCmd.RD){
//    switch(mOcpAddr) {
//      is(0.U) {
//        dataReg := port1Act
//
//      }
//      is(1.U) {
//        dataReg := port2Act
//
//      }
//      is(2.U) {
//        dataReg := port3Act
//
//      }
//      is(3.U) {
//        dataReg := port4Act
//
//      }
//      is(4.U) {
//        dataReg := port1T
//
//      }
//      is(5.U) {
//        dataReg := port2T
//
//      }
//      is(6.U) {
//        dataReg := port3T
//
//      }
//      is(7.U) {
//        dataReg := port4T
//
//      }
//      is(8.U) {
//        dataReg := port1Window
//
//      }
//      is(9.U) {
//        dataReg := port2Window
//
//      }
//      is(10.U) {
//        dataReg := port3Window
//
//      }
//      is(11.U) {
//        dataReg := port4Window
//
//      }
//      is(12.U) {
//        dataReg := jitter
//
//      }
//    }
//  }



  for (i <- 0 until 4) {
    io.ocpMasters(i).M.Data := 0.U
    io.ocpMasters(i).M.Cmd  := OcpCmd.IDLE
    io.ocpMasters(i).M.Addr := 0.U
    io.ocpMasters(i).M.ByteEn := 0.U

  }

  leds(0) := startFlag

  when(startFlag)
  {
    when((PeriodReg1 - jitter) === nanoReg){
      startPort1 := true.B
      PeriodReg1 := nanoReg + port1T
    }
    when((PeriodReg2 - jitter) === nanoReg){
      startPort2 := true.B
      PeriodReg2 := nanoReg + port2T
    }
    when((PeriodReg3 - jitter) === nanoReg){
      startPort3 := true.B
      PeriodReg3 := nanoReg + port3T
    }
    when((PeriodReg4 - jitter) === nanoReg){
      startPort4 := true.B
      PeriodReg4 := nanoReg + port4T
    }
  }.otherwise{
    startPort1 := false.B
    startPort2 := false.B
    startPort3 := false.B
    startPort4 := false.B
  }

  when(resetFlag){
    PeriodReg1 := 0.U
    PeriodReg2 := 0.U
    PeriodReg3 := 0.U
    PeriodReg4 := 0.U
  }





  when(startPort1)
  {
    port1WindowCounterReg := port1WindowCounterReg +1.U
    when(port1WindowDone){
      port1WindowCounterReg := 0.U
      startPort1 := false.B
      stateReg1 := stateReg1next
    }
    //send any messages that need to be send
    //read poll { if packet arrive send to coresponding port }
    //continue reading poll?
    portStateMachine(0)

  }
  when(startPort2)
  {
    port2WindowCounterReg := port2WindowCounterReg +1.U
    when(port2WindowDone){
      port2WindowCounterReg := 0.U
      startPort2 := false.B
      stateReg2 := stateReg2next
    }
    portStateMachine(1)

  }
  when(startPort3)
  {
    port3WindowCounterReg := port3WindowCounterReg + 1.U
    when(port3WindowDone){
      port3WindowCounterReg := 0.U
      startPort3 := false.B
      stateReg3 := stateReg3next
    }
    portStateMachine(2)
  }
  when(startPort4)
  {
    port4WindowCounterReg := port4WindowCounterReg +1.U
    when(port4WindowDone){
      port4WindowCounterReg := 0.U
      startPort4 := false.B
      stateReg4 := stateReg4next
    }
   portStateMachine(3)
  }



  def transmission(srcPort: Int, destPort: Int): Unit ={

    io.ocpMasters(srcPort).M.Cmd := OcpCmd.IDLE

    switch(transmssionStates(srcPort))
    {
      is(poll){

        delayReg := 0.U
        io.ocpMasters(srcPort).M.Cmd := OcpCmd.RD
        io.ocpMasters(srcPort).M.Addr := Constants.STATUS_BIT

        when(io.ocpMasters(srcPort).S.Resp === OcpResp.DVA){
          slaveStatusBits(srcPort) :=  io.ocpMasters(srcPort).S.Data
        }

        when(slaveStatusBits(srcPort) === 1.U) {
          leds(1) := true.B;
          transmssionStates(srcPort) := readRX
        }

      }
      is(readRX){

        io.ocpMasters(srcPort).M.Cmd := OcpCmd.RD
        io.ocpMasters(srcPort).M.Addr := memCountersRx(srcPort)

        when(io.ocpMasters(srcPort).S.Resp === OcpResp.DVA){
          slaveDataRegs(srcPort) := io.ocpMasters(srcPort).S.Data
          transmssionStates(srcPort) := writeTX
          leds(1) := false.B;
        }

      }
      is(writeTX){
        leds(2) := true.B;
        io.ocpMasters(destPort).M.Cmd := OcpCmd.WR
        io.ocpMasters(destPort).M.Addr:= memCountersTx(destPort)
        io.ocpMasters(destPort).M.Data := slaveDataRegs(srcPort)
        io.ocpMasters(destPort).M.ByteEn := "xFFFF".U


        delayReg := delayReg + 1.U

        when(memCountersRx(srcPort) === Constants.RX_FRAME_END){
            transmssionStates(srcPort) := writeLength
            delayReg := 0.U

        }.otherwise{
          when(delayReg === 5.U){
            transmssionStates(srcPort) := readRX
            delayReg := 0.U
            memCountersRx(srcPort) := memCountersRx(srcPort) + 1.U
            memCountersTx(destPort) := memCountersTx(destPort) + 1.U
            txLengthCounter(destPort) := txLengthCounter(destPort) + 4.U //32 bit word
            leds(2) := false.B;
          }

        }

      }
      is(writeLength){

        memCountersRx(srcPort) := Constants.RX_FRAME_START
        memCountersTx(destPort) := Constants.TX_FRAME_START

        io.ocpMasters(srcPort).M.Cmd := OcpCmd.WR
        io.ocpMasters(srcPort).M.ByteEn := "xFFFF".U
        io.ocpMasters(srcPort).M.Addr := Constants.STATUS_BIT
        io.ocpMasters(srcPort).M.Data := 0.U


        io.ocpMasters(destPort).M.Cmd := OcpCmd.WR
        io.ocpMasters(destPort).M.ByteEn := "xFFFF".U
        io.ocpMasters(destPort).M.Addr := Constants.TX_FRAME_LENGTH
        io.ocpMasters(destPort).M.Data := txLengthCounter(destPort) //Write Length

        sendPacket(destPort) := true.B

        delayReg := delayReg + 1.U

        when(delayReg === 5.U){
          transmssionStates(srcPort) := writeStatus
          delayReg := 0.U
        }


      }
      is(writeStatus){

        io.ocpMasters(destPort).M.Cmd := OcpCmd.WR
        io.ocpMasters(destPort).M.ByteEn := "xFFFF".U
        io.ocpMasters(destPort).M.Addr := Constants.TX_BIT
        io.ocpMasters(destPort).M.Data := 1.U //Send

        txLengthCounter(destPort) := 0.U

        delayReg := delayReg + 1.U

        sendPacket(destPort) := true.B

        when(delayReg === 5.U){
          transmssionStates(srcPort) := writeStatus2
          delayReg := 0.U
        }

      }

      is(writeStatus2)
      {
        io.ocpMasters(destPort).M.ByteEn := "xFFFF".U
        io.ocpMasters(destPort).M.Addr := Constants.TX_BIT
        io.ocpMasters(destPort).M.Data := 1.U //Send
        sendPacket(destPort) := false.B
        transmssionStates(srcPort) := poll
      }
    }


//    when(slaveStatusBits(srcPort) === 1.U){
//
//      when(memCountersRx(srcPort) <= Constants.RX_FRAME_END){
//          io.ocpMasters(srcPort).M.Cmd := OcpCmd.RD
//          io.ocpMasters(srcPort).M.Addr := memCountersRx(srcPort)
//          slaveDataRegs(srcPort) := io.ocpMasters(srcPort).S.Data
//
//          when (io.ocpMasters(srcPort).S.Resp === OcpResp.DVA){
//            io.ocpMasters(destPort).M.Cmd := OcpCmd.WR
//            io.ocpMasters(destPort).M.Addr:= memCountersTx(destPort)
//            io.ocpMasters(destPort).M.Data := slaveDataRegs(srcPort)
//            memCountersRx(srcPort) := memCountersRx(srcPort) + 1.U
//            memCountersTx(destPort) := memCountersTx(destPort) + 1.U
//            txLengthCounter(destPort) := txLengthCounter(destPort) + 4.U //32 bit word
//          }
//        }
//      when(memCountersRx(srcPort) === Constants.RX_FRAME_END){
//
//            memCountersRx(srcPort) := Constants.RX_FRAME_START
//            memCountersTx(destPort) := Constants.TX_FRAME_START
//
//            io.ocpMasters(srcPort).M.Cmd := OcpCmd.WR
//            io.ocpMasters(srcPort).M.Addr := Constants.STATUS_BIT
//            io.ocpMasters(srcPort).M.Data := 0.U
//
//            io.ocpMasters(destPort).M.Cmd := OcpCmd.WR
//            io.ocpMasters(destPort).M.Addr := Constants.TX_FRAME_LENGTH
//            io.ocpMasters(destPort).M.Data := txLengthCounter(destPort) //Write Length
//            sendPacket(destPort) := true.B
//      }
//
//
//      }.otherwise{
//
//    }
//


  }

  def portStateMachine(portNum : Int): Unit ={
    if (portNum == 0) {
      switch(stateReg1) {
        is(port1) {
          //should not happen
          transmission(portNum,0)
          stateReg1next := port2
        }
        is(port2) {
          transmission(portNum,1)
          stateReg1next := port3
        }
        is(port3) {
          transmission(portNum,2)
          stateReg1next := port4
        }
        is(port4) {
          transmission(portNum,3)
          stateReg1next := port2
        }
      }
    }
    else if (portNum == 1) {
      switch(stateReg2) {
        is(port1) {
          transmission(portNum,0)
          stateReg1next := port3
        }
        is(port2) {
          //Should not happen
          transmission(portNum,1)
          stateReg1next := port3
        }
        is(port3) {
          transmission(portNum,2)
          stateReg1next := port4
        }
        is(port4) {
          transmission(portNum,3)
          stateReg1next := port1
        }
      }
    }
    else if (portNum == 2) {
      switch(stateReg3) {
        is(port1) {
          transmission(portNum,0)
          stateReg1next := port2
        }
        is(port2) {
          transmission(portNum,1)
          stateReg1next := port4
        }
        is(port3) {
          //Should not happen
          transmission(portNum,2)
          stateReg1next := port4
        }
        is(port4) {
          transmission(portNum,3)
          stateReg1next := port1
        }
      }
    }
    else if (portNum == 3){
      switch (stateReg4) {
        is (port1) {
          transmission(portNum,0)
          stateReg1next := port2
        }
        is (port2) {
          transmission(portNum,1)
          stateReg1next := port3
        }
        is (port3) {
          transmission(portNum,2)
          stateReg1next := port1
        }
        is (port4) {
          //Should not happen
          transmission(portNum,3)
          stateReg1next := port1
        }
      }
    }
  }


}

object Controller extends App {
  chisel3.Driver.execute(Array("--target-dir", "generated"), () => new Controller())
}