package com.dj0ulo.uniremote

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Math.abs
import java.util.*
import kotlin.collections.HashMap


class IrBlaster(ctx: Context) {
    class Command(val name: String, val freq: Int, val signal: IntArray){
        override fun toString(): String {
            return "Command[$name] {f: $freq, n_pulses: "+signal.size+"}"
        }
    }

    private var commands: HashMap<String, Command> = HashMap()
    private var cir : ConsumerIrManager
    init {
        cir = ctx.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
        val ins: InputStream = ctx.resources.openRawResource(
            ctx.resources.getIdentifier(
                "commands",
                "raw", ctx.packageName
            )
        )
        val reader = BufferedReader(InputStreamReader(ins))
        var line = ""
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
        if(command == null)
            throw Exception("Unknown IR code")
        cir.transmit(command.freq, command.signal)
    }

    fun transmit(instruction: String) {
        Log.i(MainActivity().TAG, "Received instruction : $instruction")
        if(instruction.toLowerCase().startsWith("volume")){
            try {
                val amount = instruction.substring("volume".length).toInt()
                val code = if(amount<0) "volume_down" else "volume_up"
                for (i in 0 until kotlin.math.abs(amount)+1){
                    val suffix = if(i % 2==0) "2" else ""
                    val c = commands[code+suffix]
                    transmit(c)
                }
                return
            }catch (e: NumberFormatException) { }
        }
        val c = commands[instruction.toLowerCase(Locale.ROOT)]
        transmit(c)
    }
}