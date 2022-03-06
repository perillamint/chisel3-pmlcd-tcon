package moe.silicon.fpga.openip.lcd_tcon

import chisel3._
import chisel3.util.{switch, is}
import chisel3.experimental.ChiselEnum
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

protected object TConState extends ChiselEnum {
  val state_newrow = Value
  val state_row = Value
  val state_nextrow = Value
}

class LCDTCon(width: Int, height: Int) extends Module {
  val lcd = IO(new Bundle {
    val data = Output(UInt(4.W))
    val flm  = Output(Bool())
    val lp   = Output(Bool())
    val xscl = Output(Bool()) // Data shift pulse
    val m    = Output(Bool())
  })

  // Internal register definitions
  val row = RegInit(0.U(BitUtil.getBitWidth(height).W))
  val col = RegInit(0.U(BitUtil.getBitWidth(width / 4).W))
  val isUpper4Bit = RegInit(0.B)
  
  val fb = IO(new Bundle {
    val data = Input(UInt(8.W))
    val addr = Output(UInt(32.W)) // 32-bit address
    val clk  = Output(Bool())
  })

  // Register definitions for framebuffers
  //val fb_addr = RegInit(0.U(32.W))
  val fb_addr = Wire(UInt(32.W))
  fb_addr := row * (width / 8).U + col(col.getWidth - 1, 1)
  val fb_clk = RegInit(0.B)

  // Wire up register to the fb port
  fb.addr := fb_addr
  fb.clk := fb_clk

  // Register definition for LCDs
  val lcd_data = RegInit(0.U(4.W))
  val lcd_flm = RegInit(0.B)
  val lcd_lp = RegInit(0.B)
  val lcd_xscl = RegInit(0.B)
  val lcd_m = RegInit(0.B)

  // Wire up register to the LCD
  //lcd.data := lcd_data
  lcd.data := Mux(isUpper4Bit, fb.data(7,4), fb.data(3,0))
  lcd.flm := lcd_flm
  lcd.lp := lcd_lp
  lcd.xscl := lcd_xscl
  lcd.m := lcd_m

  val state = RegInit(TConState.state_newrow) // State machine

  switch (state) {
    is (TConState.state_newrow) {
      // When first row, send the FLM signal
      when(row === 0.U) {
        lcd_flm := 1.B
        lcd_m := !lcd_m
      }.otherwise {
        lcd_flm := 0.B
      }

      state := TConState.state_row
      lcd_lp := 1.B
      isUpper4Bit := 1.B
    }

    is (TConState.state_row) {
      lcd_lp := 0.B
      // Send row to the space
      // TODO: Clean up the mess
      when(col < (width / 4 - 1).U) {
        when (lcd_xscl === 1.U) {
          col := col + 1.U
          when (isUpper4Bit) {
            lcd_data := fb.data(7,4)
          }.otherwise {
            lcd_data := fb.data(3,0)
          }
          isUpper4Bit := !isUpper4Bit
        }.otherwise {
        }
        lcd_xscl := !lcd_xscl
      }.otherwise {
        when (lcd_xscl === 1.U) {
          col := 0.U
          state := TConState.state_newrow
          lcd_flm := 0.B
          when (row < height.U - 1.U) {
            row := row + 1.U
          }.otherwise {
            row := 0.U
          }
          when (isUpper4Bit) {
            lcd_data := fb.data(7,4)
          }.otherwise {
            lcd_data := fb.data(3,0)
          }
          isUpper4Bit := !isUpper4Bit
        }
        lcd_xscl := !lcd_xscl
      }
    }

    is (TConState.state_nextrow) {
      state := TConState.state_newrow
      lcd_flm := 0.B

      when (row < height.U - 1.U) {
        row := row + 1.U
      }.otherwise {
        row := 0.U
      }
    }
  }
}

object LCDTCon extends App {
  // Emit the verilog
  (new ChiselStage).emitVerilog(new LCDTCon(320, 200), Array("-td", "vout"))

  // generate graph files for circuit visualization
  (new layered.stage.ElkStage).execute(
    Array("-td", "vout", "--lowFir"),
    Seq(ChiselGeneratorAnnotation(() => new LCDTCon(320, 200)))
  )
}
