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
        override fun toString(): String {
            return "Command[$code] {f: $freq, n_pulses: " + signal.size + "}"
        }
    }

    private var commands: HashMap<String, Command> = HashMap()
    private var cir: ConsumerIrManager = mAct.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
    private var lastCommand: String = ""
    private var timerVolume: Long = System.currentTimeMillis()

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
                val freq = ss[5].toInt();
                val signal = (ss.last().split(",").map { it.trim().toInt() }).toIntArray()
                commands[code.toLowerCase(Locale.ROOT)] = Command(code, freq, signal)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        Log.i(MainActivity().TAG, commands.keys.joinToString(","))
    }

    private fun transmit(freq: Int, signal: IntArray) {
        if (!cir.hasIrEmitter())
            throw Exception("This device does not have an IR blaster")
        cir.transmit(freq, signal)
    }

    private fun transmit(command: Command?) {
        if (command == null)
            throw Exception("Unknown IR code")
        Log.i(MainActivity().TAG, "Blast ${command.code}")
        lastCommand = command.code.toLowerCase(Locale.ROOT)
        transmit(command.freq, command.signal)
    }
    fun transmit(msg: String) {
        val ss = msg.split(";")
        val instruction = ss[0]
        Log.i(MainActivity().TAG, "Received instruction : $instruction")
        val lowInst = instruction.toLowerCase(Locale.ROOT)
        mAct.runOnUiThread(Runnable { mAct.bounceCommand(ss[1]) })

        if (lowInst.startsWith("volume")) {
            try {
                val amount = instruction.substring("volume".length).toInt()
                val code = if (amount < 0) "volume_down" else "volume_up"

                var evenVolume = false
                if(lastCommand.startsWith("volume")){
                    if(System.currentTimeMillis() - timerVolume < 2500) {//inside volume mode
                        Log.i(MainActivity().TAG, "Still in volume mode")
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
}