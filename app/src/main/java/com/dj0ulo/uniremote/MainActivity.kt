package com.dj0ulo.uniremote

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


open class MainActivity : AppCompatActivity() {
    lateinit var irb : IrBlaster
    val TAG = "UniRemote"
    lateinit var thread : Thread
    lateinit var commandView : TextView
    lateinit var animCommand : AnimationSet

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
    }
    fun bounceCommand(txt: String){
        commandView.text = txt
        commandView.visibility = View.VISIBLE
        runAnimation(commandView);
    }
    private fun runAnimation(view: View) {
        view.clearAnimation()
        view.startAnimation(animCommand)
    }
}
