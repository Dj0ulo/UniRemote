package com.dj0ulo.uniremote

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import java.util.*


open class MainActivity : AppCompatActivity() {
    lateinit var irb : IrBlaster
    val TAG = "UniRemote"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        irb = IrBlaster(this)

        Log.i(TAG, UDP.getLocalIpAddress())

        val threadWithRunnable = Thread(UDP { irb.transmit(it) })
        threadWithRunnable.start()

        setContentView(R.layout.activity_main)
    }
}
