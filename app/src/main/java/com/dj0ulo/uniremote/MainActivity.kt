package com.dj0ulo.uniremote

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.*
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


open class MainActivity : AppCompatActivity() {
    lateinit var irb : IrBlaster
    lateinit var thread : Thread
    lateinit var commandView : TextView
    lateinit var animCommand : AnimationSet
    private var boundButtons = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        irb = IrBlaster(this)

        Log.i(TAG, CommandReceiver.getLocalIpAddress())

        thread = Thread(CommandReceiver { irb.transmit(it) })
        thread.start()

        val scaleAnim: Animation = AnimationUtils.loadAnimation(this, R.anim.scale)
        scaleAnim.interpolator = OvershootInterpolator(10.0f)

        val fadeOut = AlphaAnimation(1f, 0f)
        fadeOut.interpolator = LinearInterpolator()
        fadeOut.startOffset = scaleAnim.duration
        fadeOut.duration = 1000
        fadeOut.isFillEnabled = true
        fadeOut.fillAfter = true

        animCommand = AnimationSet(false) //change to false
        animCommand.addAnimation(scaleAnim)
        animCommand.addAnimation(fadeOut)
        animCommand.fillAfter = true
        animCommand.isFillEnabled = true

        animCommand.reset()

        setContentView(R.layout.activity_main)
        (findViewById<TextView>(R.id.link)).text = CommandReceiver.url()
        commandView = findViewById(R.id.command)
        bindControllerButtons(findViewById(android.R.id.content))
        Log.i(TAG, "Bound $boundButtons remote buttons")
    }

    /**
     * Wires every tagged key of the on screen remote. A tap blasts the command
     * once; holding the key past the long press timeout makes the blaster
     * repeat frames until release, which is what the system needs for the
     * volume ramp, cursor repeat, fast search, and the Ambisound settings mode
     * behind SOUND.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun bindControllerButtons(view: View) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount)
                bindControllerButtons(view.getChildAt(i))
            return
        }
        val tag = (view.tag as? String)?.split(";") ?: return
        val instruction = tag[0]
        val displayText = tag.getOrNull(1)

        var held = false
        val beginHold = Runnable {
            held = true
            irb.startHold(instruction, displayText)
        }
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    held = false
                    v.postDelayed(beginHold, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(beginHold)
                    // Leave held set: it tells the click below to stand down.
                    if (held) irb.stopHold()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(beginHold)
                    if (held) {
                        irb.stopHold()
                        held = false
                    }
                }
            }
            false   // let the button keep its own pressed state and click handling
        }
        view.setOnClickListener {
            if (held) held = false else irb.transmit(instruction, displayText)
        }
        boundButtons++
    }
    fun bounceCommand(txt: String){
        commandView.text = txt
        commandView.visibility = View.VISIBLE
        runAnimation(commandView);
    }
    companion object {
        const val TAG = "UniRemote"
    }

    private fun runAnimation(view: View) {
        view.clearAnimation()
        view.startAnimation(animCommand)
    }
}
