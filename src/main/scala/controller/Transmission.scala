package main.scala.controller

import chisel3._
import chisel3.util._
import main.scala.axi.AXI4LiteMMBridge
import ocp._

class Transmission() extends Module{
  val io = IO(new Bundle {
    val ocpMasterPortIn = new OcpCoreMasterPort(32,32)
    val ocpMasterPortOut = new OcpCoreMasterPort(32,32)
    val connect = Input(Bool())
  })

  val memCountersRx = RegInit(Constants.RX_FRAME_START)
  val memCountersTx = RegInit(Constants.TX_FRAME_START)
  val delayReg = RegInit(0.U(32.W))
  val txLengthCounter = RegInit(0.U(32.W))

  val idle::poll::readRX::writeTX::writeLength::writeStatus::writeStatus2::Nil = Enum(7)
  val transmssionState = RegInit(idle)



  val slaveDataReg =   RegInit(0.U(32.W))
  val slaveStatusBit = RegInit(0.U(32.W))
  val slaveDataValid = RegInit(false.B)



  io.ocpMasterPortIn.M.Data := 0.U
  io.ocpMasterPortIn.M.Cmd  := OcpCmd.IDLE
  io.ocpMasterPortIn.M.Addr := 0.U
  io.ocpMasterPortIn.M.ByteEn := 0.U

  io.ocpMasterPortOut.M.Data := 0.U
  io.ocpMasterPortOut.M.Cmd  := OcpCmd.IDLE
  io.ocpMasterPortOut.M.Addr := 0.U
  io.ocpMasterPortOut.M.ByteEn := 0.U


  val statusBitReg = RegInit(Constants.STATUS_BIT)
  val rxFrameEndReg = RegInit(Constants.RX_FRAME_END)
  val rxFrameStartReg = RegInit(Constants.RX_FRAME_START)
  val txFrameStartReg = RegInit(Constants.TX_FRAME_START)
  val txFrameLength = RegInit(Constants.TX_FRAME_LENGTH)

  switch(transmssionState)
  {
    is(idle){
      when(io.connect === true.B)
      {
        transmssionState := poll
      }
    }
    is(poll){

      delayReg := 0.U
      io.ocpMasterPortIn.M.Cmd := OcpCmd.RD
      io.ocpMasterPortIn.M.Addr := statusBitReg

      when(io.ocpMasterPortIn.M.Cmd === OcpResp.DVA){
        slaveStatusBit :=  io.ocpMasterPortIn.M.Data
      }

      when(slaveStatusBit === 1.U) {
        //leds(1) := true.B;
        transmssionState := readRX
      }

    }
    is(readRX){

      io.ocpMasterPortIn.M.Cmd := OcpCmd.RD
      io.ocpMasterPortIn.M.Addr := memCountersRx

      when(io.ocpMasterPortIn.M.Cmd === OcpResp.DVA){
        slaveDataReg := io.ocpMasterPortIn.M.Data
        transmssionState := writeTX
        //leds(1) := false.B;
      }

    }
    is(writeTX){
      //leds(2) := true.B;
      io.ocpMasterPortIn.M.Cmd := OcpCmd.WR
      io.ocpMasterPortIn.M.Addr:= memCountersTx
      io.ocpMasterPortIn.M.Data := slaveDataReg
      io.ocpMasterPortIn.M.ByteEn := "xFFFF".U


      delayReg := delayReg + 1.U

      when(memCountersRx === rxFrameEndReg){
        transmssionState := writeLength
        delayReg := 0.U

      }.otherwise{
        when(delayReg === 5.U){
          transmssionState := readRX
          delayReg := 0.U
          memCountersRx := memCountersRx + 1.U
          memCountersTx := memCountersTx + 1.U
          txLengthCounter := txLengthCounter + 4.U //32 bit word
          //leds(2) := false.B;
        }

      }

    }
    is(writeLength){

      memCountersRx := rxFrameStartReg
      memCountersTx := txFrameStartReg

      io.ocpMasterPortIn.M.Cmd := OcpCmd.WR
      io.ocpMasterPortIn.M.ByteEn := "xFFFF".U
      io.ocpMasterPortIn.M.Addr := statusBitReg
      io.ocpMasterPortIn.M.Data := 0.U


      io.ocpMasterPortOut.M.Cmd := OcpCmd.WR
      io.ocpMasterPortOut.M.ByteEn := "xFFFF".U
      io.ocpMasterPortOut.M.Addr := txFrameLength
      io.ocpMasterPortOut.M.Data := txLengthCounter //Write Length

      //sendPacket(destPort) := true.B

      delayReg := delayReg + 1.U

      when(delayReg === 5.U){
        transmssionState := writeStatus
        delayReg := 0.U
      }


    }
    is(writeStatus){

      io.ocpMasterPortOut.M.Cmd := OcpCmd.WR
      io.ocpMasterPortOut.M.ByteEn := "xFFFF".U
      io.ocpMasterPortOut.M.Addr := Constants.TX_BIT
      io.ocpMasterPortOut.M.Data := 1.U //Send

      txLengthCounter := 0.U

      delayReg := delayReg + 1.U

      //sendPacket(destPort) := true.B

      when(delayReg === 5.U){
        transmssionState := writeStatus2
        delayReg := 0.U
      }

    }

    is(writeStatus2)
    {
      io.ocpMasterPortOut.M.ByteEn := "xFFFF".U
      io.ocpMasterPortOut.M.Addr := Constants.TX_BIT
      io.ocpMasterPortOut.M.Data := 1.U //Send
      //sendPacket(destPort) := false.B
      transmssionState := idle
    }
  }


}

object Transmission extends App {
  chisel3.Driver.execute(Array("--target-dir", "generated"), () => new Transmission())
}