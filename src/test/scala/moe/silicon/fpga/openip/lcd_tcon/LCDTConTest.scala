package moe.silicon.fpga.openip.lcd_tcon

import chisel3.Module
import chisel3.tester.testableClock
import chiseltest.{ChiselScalatestTester, testableUInt}
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.freespec.AnyFreeSpec

class LCDTConTest extends AnyFreeSpec with ChiselScalatestTester {
  "Just simulate" in {
    test(new LCDTCon(16,10)).withAnnotations(Seq(WriteVcdAnnotation)) {
      tcon => {
        tcon.clock.setTimeout(65536)
        tcon.fb.data.poke(0xA5)
        for(i <- 1 to 4096) {
          tcon.clock.step()
        }
      }
    }
  }
}
