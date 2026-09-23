package com.dj0ulo.uniremote

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*


class IrBlaster(private val mAct: MainActivity) {
    class Command(val code: String, val freq: Int, val signal: IntArray) {
        /** The stored signal holds one frame per RC-6 toggle state, split on the
         *  inter-frame gaps.  Holding a key repeats a single frame, so that the
         *  toggle bit stays put and the system reads a repeat instead of a new
         *  press. */
        val frames: List<IntArray> = splitFrames(signal)

        /** Which frame the next distinct press uses, so presses alternate the
         *  toggle bit the way a real remote does. */
        var nextFrame: Int = 0

        override fun toString(): String {
            return "Command[$code] {f: $freq, n_pulses: " + signal.size + "}"
        }

        companion object {
            private const val GAP = 5000      // shortest duration counting as a gap
            private const val DEFAULT_GAP = 84888

            private fun splitFrames(signal: IntArray): List<IntArray> {
                val frames = ArrayList<IntArray>()
                var frame = ArrayList<Int>()
                for (duration in signal) {
                    frame.add(duration)
                    if (duration > GAP) {
                        frames.add(frame.toIntArray())
                        frame = ArrayList()
                    }
                }
                // A frame has to end on a gap to pace the repeats correctly.
                if (frame.isNotEmpty()) {
                    frame.add(DEFAULT_GAP)
                    frames.add(frame.toIntArray())
                }
                return frames
            }
        }
    }

    private var commands: HashMap<String, Command> = HashMap()
    private var cir: ConsumerIrManager = mAct.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
    private var lastCommand: String = ""
    private var timerVolume: Long = System.currentTimeMillis()

    /** Serialises the blaster: a released key may still be finishing its last
     *  frame when the next press starts. */
    private val irLock = Any()

    @Volatile private var holding = false

    /** Only the newest hold thread is allowed to keep going. */
    @Volatile private var holdGeneration = 0

    init {
        val ins: InputStream = mAct.resources.openRawResource(
            mAct.resources.getIdentifier(
                "commands",
                "raw", mAct.packageName
            )
        )
        val reader = BufferedReader(InputStreamReader(ins))
        var line : String
        try {
            while (reader.readLine().also { line = it ?: "" } != null) {
                val ss = line.split(";").toTypedArray()
                val code = ss[2]
                val freq = ss[5].toInt()
                val signal = (ss.last().split(",").map { it.trim().toInt() }).toIntArray()
                commands[code.toLowerCase(Locale.ROOT)] = Command(code, freq, signal)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        Log.i(MainActivity.TAG, commands.keys.joinToString(","))
    }

    private fun transmit(freq: Int, signal: IntArray) {
        if (!cir.hasIrEmitter())
            throw Exception("This device does not have an IR blaster")
        synchronized(irLock) { cir.transmit(freq, signal) }
    }

    private fun transmit(command: Command?) {
        if (command == null)
            throw Exception("Unknown IR code")
        Log.i(MainActivity.TAG, "Blast ${command.code}")
        lastCommand = command.code.toLowerCase(Locale.ROOT)
        transmit(command.freq, command.signal)
    }

    fun transmit(msg: String) {
        if (msg.isEmpty()) {
            Log.e(MainActivity.TAG, "Empty instruction")
            return
        }
        val ss = msg.split(";")
        val freq = ss[0].toIntOrNull()
        if (freq != null) {
            val data = ss.getOrNull(1)
            if (data == null) {
                Log.e(MainActivity.TAG, "Raw instruction without a signal : $msg")
                return
            }
            Log.i(MainActivity.TAG, "Transmitting at frequency : $freq")
            transmit(freq, data.split("[,\\s]".toRegex()).map { it.toInt(16) }.toIntArray())
        } else {
            transmit(ss[0], ss.getOrNull(1))
        }
    }
    fun transmit(instruction: String, displayText: String?) {
        Log.i(MainActivity.TAG, "Received instruction : $instruction")
        val lowInst = instruction.toLowerCase(Locale.ROOT)
        if(displayText != null)
            mAct.runOnUiThread { mAct.bounceCommand(displayText) }

        if (lowInst.startsWith("volume")) {
            try {
                val amount = instruction.substring("volume".length).toInt()
                val code = if (amount < 0) "volume_down" else "volume_up"

                var evenVolume = false
                if(lastCommand.startsWith("volume")){
                    if(System.currentTimeMillis() - timerVolume < 2500) {//inside volume mode
                        Log.i(MainActivity.TAG, "Still in volume mode")
                        if (code.substring(0, "volume_up".length) == lastCommand.substring(0, "volume_up".length))//same direction as previous
                            evenVolume = lastCommand.last() != '2'
                        else
                            evenVolume = true
                    }else {
                        if (code.substring(0, "volume_up".length) == lastCommand.substring(0, "volume_up".length) && lastCommand.last() == '2'){
                            transmit(commands[code])
                            evenVolume = true
                        }
                    }
                }

                for (i in 0 until kotlin.math.abs(amount)) {
                    val plus1 = if (evenVolume) 1 else 0
                    val suffix = if ((i + plus1) % 2 == 0) "" else "2"
                    transmit(commands[code + suffix])
                }
            } catch (e: NumberFormatException) {
                transmit(commands[lowInst])
            }
            timerVolume = System.currentTimeMillis()
            return
        }
        transmit(commands[lowInst])
    }

    private fun resolve(instruction: String): Command? {
        val lowInst = instruction.toLowerCase(Locale.ROOT)
        commands[lowInst]?.let { return it }
        if (lowInst.startsWith("volume")) {
            val amount = instruction.substring("volume".length).toIntOrNull() ?: 0
            return commands[if (amount < 0) "volume_down" else "volume_up"]
        }
        return null
    }

    /**
     * Starts blasting [instruction] and keeps repeating it until [stopHold].
     * A tap still sends [MIN_FRAMES] frames, so a short press carries as far as
     * it did before; holding is what the system needs for volume ramping, cursor
     * repeat, fast search, and the Ambisound settings mode behind SOUND.
     */
    fun startHold(instruction: String, displayText: String?) {
        stopHold()
        val command = resolve(instruction)
        if (command == null) {
            Log.e(MainActivity.TAG, "Unknown IR code : $instruction")
            return
        }
        if (displayText != null)
            mAct.runOnUiThread { mAct.bounceCommand(displayText) }

        val frame = command.frames[command.nextFrame % command.frames.size]
        command.nextFrame++
        lastCommand = command.code.toLowerCase(Locale.ROOT)
        timerVolume = System.currentTimeMillis()

        holding = true
        holdGeneration++
        val generation = holdGeneration
        Log.i(MainActivity.TAG, "Hold ${command.code}")
        Thread {
            var sent = 0
            try {
                while (generation == holdGeneration && (holding || sent < MIN_FRAMES)) {
                    transmit(command.freq, frame)
                    sent++
                }
            } catch (e: Exception) {
                Log.e(MainActivity.TAG, "[hold] Exception : $e")
            }
            Log.i(MainActivity.TAG, "Sent $sent frame(s) of ${command.code}")
        }.start()
    }

    /** Releases the key. The hold thread stops after its frame in flight. */
    fun stopHold() {
        holding = false
    }

    companion object {
        /** A tap sends both toggle frames on a real remote; match that. */
        private const val MIN_FRAMES = 2
    }
}
