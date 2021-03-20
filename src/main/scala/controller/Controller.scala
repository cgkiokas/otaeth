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
  def RX_FRAME_SIZE = "x100c".U(64.W)
  def RX_IP_LENGTH = "x1010".U(64.W)

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
    val activations = Vec(4, Input(UInt(64.W)))
    val periods = Vec(4, Input(UInt(32.W)))
    val windows = Vec(4, Input(UInt(32.W)))
    val jitter =  Input(UInt(32.W))

    //Debug
    val sendFrame = Vec(4, Output(Bool()))

    val led1 = Output(Bool())
    val led2 = Output(Bool())
    val led3 = Output(Bool())
    val led4 = Output(Bool())

    val start1 = Output(Bool())
    val start2 = Output(Bool())
    val start3 = Output(Bool())
    val start4 = Output(Bool())

    val debugPeriodReg1 = Output(UInt(32.W))
    val debugPeriodReg2 = Output(UInt(32.W))
    val debugPeriodReg3 = Output(UInt(32.W))
    val debugPeriodReg4 = Output(UInt(32.W))

  })

  val leds = RegInit(VecInit(Seq.fill(4)(false.B)));




  io.led1 := leds(0)
  io.led2 := leds(1)
  io.led3 := leds(2)
  io.led4 := leds(3)

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
  val startFlagOld = RegInit(startFlag)
  val resetFlag = RegInit(false.B)
  startFlagOld := startFlag
  val startRising = startFlag && !startFlagOld

  val memCountersRx = RegInit(VecInit(Seq.fill(4)(Constants.RX_FRAME_START)))
  val memCountersTx = RegInit(VecInit(Seq.fill(4)(Constants.TX_FRAME_START)))


  val port1WindowCounterReg = RegInit (0.U(32.W))
  val port1WindowDone = port1WindowCounterReg > (port1Window )


  val port2WindowCounterReg = RegInit (0.U(32.W))
  val port2WindowDone = port2WindowCounterReg > (port2Window )

  val port3WindowCounterReg = RegInit (0.U(32.W))
  val port3WindowDone = port3WindowCounterReg > (port3Window )

  val port4WindowCounterReg = RegInit (0.U(32.W))
  val port4WindowDone = port4WindowCounterReg > (port4Window )

  val secondsReg = RegInit(0.U(32.W))
  val nanoReg = RegInit(0.U(32.W))

  val idle::readPort1::readPort2::readPort3::readPort4::writePort1::Nil = Enum(6)
  val state = RegInit(idle)



  val slaveDataRegs =   RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val slaveStatusBits = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val slaveDataValids = RegInit(VecInit(Seq.fill(4)(false.B)))
  val rxFrameLength = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val isIP = RegInit(VecInit(Seq.fill(4)(false.B)))

  val PeriodReg1 = RegInit(port1Act)
  val PeriodReg2 = RegInit(port2Act)
  val PeriodReg3 = RegInit(port3Act)
  val PeriodReg4 = RegInit(port4Act)

  val PeriodReg1Config = RegInit(port1Act)
  val PeriodReg2Config = RegInit(port2Act)
  val PeriodReg3Config = RegInit(port3Act)
  val PeriodReg4Config = RegInit(port4Act)

  val startPort1 = RegInit(false.B)
  val startPort2 = RegInit(false.B)
  val startPort3 = RegInit(false.B)
  val startPort4 = RegInit(false.B)



  val delayReg = RegInit(0.U(32.W))

  val poll::rxDelay::readRX::writeTX::writeLength::writeStatus::writeStatus2::Nil = Enum(7)
  val transmssionStates = RegInit(VecInit(Seq.fill(4)(poll)))

  val txLengthCounter = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))

  val respReg = RegInit(OcpResp.NULL)
  val dataReg = RegInit(0.U(32.W))
  val mOcpReg = RegInit(io.patmosInterface.M)
  val mOcpAddr = RegInit(0.U(16.W))
  val mOcpData = RegInit(0.U(32.W))
  //register patmos inputs
  mOcpReg := io.patmosInterface.M

//  nanoReg := io.timerInput(31,0)
//  secondsReg := io.timerInput(63,32)

  respReg := OcpResp.NULL

  io.patmosInterface.S.Data := dataReg
  io.patmosInterface.S.Resp := respReg

  io.start1 := startPort1
  io.start2 := startPort2
  io.start3 := startPort3
  io.start4 := startPort4

  io.debugPeriodReg1 := PeriodReg1
  io.debugPeriodReg2 := PeriodReg2
  io.debugPeriodReg3 := PeriodReg3
  io.debugPeriodReg4 := PeriodReg4

  val stopped::cmd::dva::Nil = Enum(3)
  val controlState = RegInit(idle)
  val isWrite = RegInit(false.B)

  when(mOcpReg.Cmd === OcpCmd.RD || mOcpReg.Cmd === OcpCmd.WR) {
    mOcpAddr := mOcpReg.Addr(6,0)
    mOcpData := mOcpReg.Data
    when(mOcpReg.Cmd === OcpCmd.WR){
      isWrite := true.B
    }.otherwise{
      isWrite := false.B
    }
    controlState := cmd;
  }

  switch(controlState) {
    is(stopped) {

    }
    is(cmd){
      switch(mOcpAddr) {
        is("x0".U(16.W)) {
          when(isWrite){
            startFlag := mOcpData === 1.U

          }.otherwise{
            when(startFlag){
              dataReg := 1.U
            }.otherwise{
              dataReg := 0.U
            }
          }

        }
        is("x4".U(16.W)) {
          when(isWrite){
            resetFlag := mOcpData === 1.U

          }.otherwise{
            when(resetFlag){
              dataReg := 1.U
            }.otherwise{
              dataReg := 0.U
            }
          }

        }
        is("x8".U(16.W)){
          when(isWrite){
            port1Act := mOcpData
          }.otherwise{
            dataReg := port1Act
          }
        }
        is("xc".U(16.W)){
          when(isWrite){
            port2Act := mOcpData
          }.otherwise{
            dataReg := port2Act
          }
        }
        is("x10".U(16.W)) {
          when(isWrite){
            port3Act := mOcpData
          }.otherwise{
            dataReg := port3Act
          }
        }
        is("x14".U(16.W)) {
          when(isWrite){
            port4Act := mOcpData
          }.otherwise{
            dataReg := port4Act
          }
        }
        is("x18".U(16.W)) {
          when(isWrite){
            port1T := mOcpData
          }.otherwise{
            dataReg := port1T
          }
        }
        is("x1c".U(16.W)) {
          when(isWrite){
            port2T := mOcpData
          }.otherwise{
            dataReg := port2T
          }
        }
        is("x20".U(16.W)) {
          when(isWrite){
            port3T := mOcpData
          }.otherwise{
            dataReg := port3T
          }
        }
        is("x24".U(16.W)) {
          when(isWrite){
            port4T := mOcpData
          }.otherwise{
            dataReg := port4T
          }
        }
        is("x28".U(16.W)) {
          when(isWrite){
            port1Window := mOcpData
          }.otherwise{
            dataReg := port1Window
          }
        }
        is("x2c".U(16.W)) {
          when(isWrite){
            port2Window := mOcpData
          }.otherwise{
            dataReg := port2Window
          }
        }
        is("x30".U(16.W)) {
          when(isWrite){
            port3Window := mOcpData
          }.otherwise{
            dataReg := port3Window
          }
        }
        is("x34".U(16.W)) {
          when(isWrite){
            port4Window := mOcpData
          }.otherwise{
            dataReg := port4Window
          }
        }
        is("x38".U(16.W)) {
          jitter := mOcpData
        }
        is("x3c".U(16.W)) {
          when(!isWrite) {
            when(startPort1) {
              when(stateReg1 === port1) {
                dataReg := 1.U
              }
              when(stateReg1 === port2) {
                dataReg := 2.U
              }
              when(stateReg1 === port3) {
                dataReg := 3.U
              }
              when(stateReg1 === port4) {
                dataReg := 4.U
              }
            }
            when(startPort2) {
              when(stateReg2 === port1) {
                dataReg := 5.U
              }
              when(stateReg2 === port2) {
                dataReg := 6.U
              }
              when(stateReg2 === port3) {
                dataReg := 7.U
              }
              when(stateReg2 === port4) {
                dataReg := 8.U
              }
            }
            when(startPort3) {
              when(stateReg3 === port1) {
                dataReg := 9.U
              }
              when(stateReg3 === port2) {
                dataReg := 10.U
              }
              when(stateReg3 === port3) {
                dataReg := 11.U
              }
              when(stateReg3 === port4) {
                dataReg := 12.U
              }
            }
            when(startPort4) {
              when(stateReg4 === port1) {
                dataReg := 13.U
              }
              when(stateReg4 === port2) {
                dataReg := 14.U
              }
              when(stateReg4 === port3) {
                dataReg := 15.U
              }
              when(stateReg4 === port4) {
                dataReg := 16.U
              }
            }
          }.otherwise{
            dataReg := 2147483647.U
          }
        }

        is("x40".U(16.W)){
          when(!isWrite){
            dataReg := io.timerInput
          }.otherwise{
            dataReg := 1.U
          }
        }
      }
      controlState := dva;
    }
    is(dva){
      respReg := OcpResp.DVA
      controlState := stopped;
    }

  }


  for (i <- 0 until 4) {
    io.ocpMasters(i).M.Data := 0.U
    io.ocpMasters(i).M.Cmd  := OcpCmd.IDLE
    io.ocpMasters(i).M.Addr := 0.U
    io.ocpMasters(i).M.ByteEn := 0.U

  }

  leds(0) := startPort1
  leds(1) := startPort2
  leds(2) := startPort3
  leds(3) := startPort4

  when(startRising){
    PeriodReg1 := port1Act + io.timerInput
    PeriodReg2 := port2Act + io.timerInput
    PeriodReg3 := port3Act + io.timerInput
    PeriodReg4 := port4Act + io.timerInput
  }.otherwise{
    PeriodReg1 := Mux((io.timerInput > PeriodReg1) && startFlag, PeriodReg1 + port1T, PeriodReg1)
    PeriodReg2 := Mux((io.timerInput > PeriodReg2) && startFlag, PeriodReg2 + port2T, PeriodReg2)
    PeriodReg3 := Mux((io.timerInput > PeriodReg3) && startFlag, PeriodReg3 + port3T, PeriodReg3)
    PeriodReg4 := Mux((io.timerInput > PeriodReg4) && startFlag, PeriodReg4 + port4T, PeriodReg4)
  }

//  when(resetFlag){
//    PeriodReg1 := PeriodReg1Config
//    PeriodReg2 := PeriodReg2Config
//    PeriodReg3 := PeriodReg3Config
//    PeriodReg4 := PeriodReg4Config
//  }.otherwise{
//
//  }

  when(io.timerInput >= PeriodReg1 && startFlag)
  {
    startPort1 := true.B
  }.elsewhen(port1WindowDone){
    startPort1 := false.B
  }.otherwise{
    startPort1 := startPort1
  }

  when(io.timerInput >= PeriodReg2 && startFlag)
  {
    startPort2 := true.B
  }.elsewhen(port2WindowDone){
    startPort2 := false.B
  }.otherwise{
    startPort2 := startPort2
  }

  when(io.timerInput >= PeriodReg3 && startFlag)
  {
    startPort3 := true.B
  }.elsewhen(port3WindowDone){
    startPort3 := false.B
  }.otherwise{
    startPort3 := startPort3
  }

  when(io.timerInput >= PeriodReg4 && startFlag)
  {
    startPort4 := true.B
  }.elsewhen(port4WindowDone){
    startPort4 := false.B
  }.otherwise{
    startPort4 := startPort4
  }

//  when(startFlag)
//  {
//    when(io.timerInput(31,0) >= PeriodReg1 ){
//      startPort1 := true.B
//      PeriodReg1 := PeriodReg1 + port1T
//    }.elsewhen(io.timerInput(31,0) >= PeriodReg2 ){
//      startPort2 := true.B
//      PeriodReg2 := PeriodReg2 + port2T
//    }.elsewhen(io.timerInput(31,0) >= PeriodReg3){
//      startPort3 := true.B
//      PeriodReg3 := PeriodReg3 + port3T
//    }.elsewhen(io.timerInput(31,0) >= PeriodReg4){
//      startPort4 := true.B
//      PeriodReg4 := PeriodReg4 + port4T
//    }.otherwise{
//      startPort1 := false.B
//      startPort2 := false.B
//      startPort3 := false.B
//      startPort4 := false.B
//    }
//  }.otherwise{
//    startPort1 := false.B
//    startPort2 := false.B
//    startPort3 := false.B
//    startPort4 := false.B
//  }





  when(startPort1)
  {
    port1WindowCounterReg := port1WindowCounterReg +1.U
    when(port1WindowDone){
      port1WindowCounterReg := 0.U
//      startPort1 := false.B
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
//      startPort2 := false.B
      stateReg2 := stateReg2next
    }
    portStateMachine(1)

  }
  when(startPort3)
  {
    port3WindowCounterReg := port3WindowCounterReg + 1.U
    when(port3WindowDone){
      port3WindowCounterReg := 0.U
//      startPort3 := false.B
      stateReg3 := stateReg3next
    }
    portStateMachine(2)
  }
  when(startPort4)
  {
    port4WindowCounterReg := port4WindowCounterReg +1.U
    when(port4WindowDone){
      port4WindowCounterReg := 0.U
//      startPort4 := false.B
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
        rxFrameLength(srcPort) := Constants.RX_FRAME_END
        when(io.ocpMasters(srcPort).S.Resp === OcpResp.DVA){
          slaveStatusBits(srcPort) :=  io.ocpMasters(srcPort).S.Data
        }

        when(slaveStatusBits(srcPort) === 1.U) {

          transmssionStates(srcPort) := rxDelay
          io.ocpMasters(srcPort).M.Cmd := OcpCmd.IDLE
          io.ocpMasters(srcPort).M.Addr := memCountersRx(srcPort)
        }

      }
      is(rxDelay){
        delayReg := delayReg + 1.U
        when(delayReg === 5.U){
          delayReg := 0.U
          transmssionStates(srcPort) := readRX
        }
      }
      is(readRX){

        io.ocpMasters(srcPort).M.Cmd := OcpCmd.RD
        io.ocpMasters(srcPort).M.Addr := memCountersRx(srcPort)

        when(io.ocpMasters(srcPort).S.Resp === OcpResp.DVA){
          slaveDataRegs(srcPort) := io.ocpMasters(srcPort).S.Data
          transmssionStates(srcPort) := writeTX

        }

      }
      is(writeTX){

        io.ocpMasters(destPort).M.Cmd := OcpCmd.WR
        io.ocpMasters(destPort).M.Addr:= memCountersTx(destPort)
        io.ocpMasters(destPort).M.Data := slaveDataRegs(srcPort)
        io.ocpMasters(destPort).M.ByteEn := "xFFFF".U


        delayReg := delayReg + 1.U

        when(memCountersRx(srcPort) === Constants.RX_FRAME_SIZE) {
          when(Cat(slaveDataRegs(srcPort)(7, 0), slaveDataRegs(srcPort)(15, 8)) === "x800".U) {
            isIP(srcPort) := true.B
          }.otherwise {
            isIP(srcPort) := false.B
          }
        }

        when(isIP(srcPort))
        {
          when(memCountersRx(srcPort) === Constants.RX_IP_LENGTH) {
            rxFrameLength(srcPort) := 12.U + Cat(slaveDataRegs(srcPort)(7, 0), slaveDataRegs(srcPort)(15, 8))
          }
        }.otherwise{
          when(memCountersRx(srcPort) === Constants.RX_FRAME_SIZE) {
            rxFrameLength(srcPort)  := 12.U + Cat(slaveDataRegs(srcPort)(7, 0), slaveDataRegs(srcPort)(15, 8))
          }
        }


        when(memCountersRx(srcPort) >=  (Constants.RX_FRAME_START + (12.U + (rxFrameLength(srcPort)/4.U)))   ){
            transmssionStates(srcPort) := writeLength
            delayReg := 0.U

        }.otherwise{
          when(delayReg === 5.U){

            transmssionStates(srcPort) := readRX
            delayReg := 0.U
            memCountersRx(srcPort) := memCountersRx(srcPort) + 4.U
            memCountersTx(destPort) := memCountersTx(destPort) + 4.U
            txLengthCounter(destPort) := txLengthCounter(destPort) + 4.U //32 bit word

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
        io.ocpMasters(destPort).M.Data := rxFrameLength(srcPort) //Write Length

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
        slaveStatusBits(srcPort) := 0.U
        isIP(srcPort) := false.B
      }
    }
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