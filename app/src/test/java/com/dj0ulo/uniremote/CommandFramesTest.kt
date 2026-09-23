package com.dj0ulo.uniremote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The hold repeat depends on the stored signal being cut back into the single
 * RC-6 frames it was captured as, one per toggle state.
 */
class CommandFramesTest {
    /** Captured Volume_Up: two frames, each closed by an inter-frame gap. */
    private val volumeUp = intArrayOf(
        2619, 864, 486, 864, 459, 432, 459, 459, 459, 864, 864, 432, 459, 459, 459, 459,
        864, 891, 459, 432, 459, 459, 459, 432, 486, 405, 459, 432, 459, 459, 891, 891,
        459, 432, 459, 459, 432, 459, 459, 84429,
        2619, 864, 486, 864, 459, 486, 405, 459, 1323, 1296, 459, 459, 459, 432, 891, 891,
        459, 432, 459, 459, 459, 432, 486, 405, 513, 378, 459, 459, 891, 891, 459, 486,
        405, 432, 459, 459, 459, 84429)

    /** Captured Power: a single frame. */
    private val power = intArrayOf(
        2646, 891, 486, 837, 459, 459, 432, 459, 459, 891, 837, 459, 432, 459, 459, 432,
        459, 459, 459, 432, 864, 918, 432, 486, 864, 486, 432, 891, 432, 459, 432, 486,
        864, 486, 405, 486, 405, 84888)

    @Test
    fun splitsTheTwoToggleFrames() {
        val frames = IrBlaster.Command("Volume_Up", 37037, volumeUp).frames
        assertEquals(2, frames.size)
        assertEquals(40, frames[0].size)
        assertEquals(38, frames[1].size)
        // Each frame has to end on its gap, which is what paces the repeats.
        assertEquals(84429, frames[0].last())
        assertEquals(84429, frames[1].last())
        // The two frames differ only by the double width toggle bit.
        assertEquals(1323, frames[1][8])
        assertEquals(459, frames[0][8])
    }

    @Test
    fun keepsASingleFrameSignalWhole() {
        val frames = IrBlaster.Command("Power", 37037, power).frames
        assertEquals(1, frames.size)
        assertEquals(power.size, frames[0].size)
    }

    @Test
    fun alternatesFramesAcrossPressesAndRepeatsOneWhileHeld() {
        val command = IrBlaster.Command("Volume_Up", 37037, volumeUp)
        // A press takes the next toggle state, a hold keeps reusing that frame.
        val first = command.frames[command.nextFrame++ % command.frames.size]
        val second = command.frames[command.nextFrame++ % command.frames.size]
        val third = command.frames[command.nextFrame++ % command.frames.size]
        assertEquals(command.frames[0], first)
        assertEquals(command.frames[1], second)
        assertEquals(command.frames[0], third)
    }

    @Test
    fun closesAFrameThatWasCapturedWithoutItsTrailingGap() {
        val truncated = power.copyOf(power.size - 1)
        val frames = IrBlaster.Command("Power", 37037, truncated).frames
        assertEquals(1, frames.size)
        assertEquals(84888, frames[0].last())
    }
}
