package com.ltm.simpledigitalwatchface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import android.widget.Toast
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000
/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

class SimpleDigitalWatchFace: CanvasWatchFaceService() {
    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: SimpleDigitalWatchFace.Engine) : Handler(Looper.myLooper()!!) {
        private val mWeakReference: WeakReference<SimpleDigitalWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {
        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false
        private var mCenterX: Float = 0F
        private var mCenterY: Float = 0F

        private var timeOffsetX: Float = 0f
        private var timeOffsetY: Float = 0f
        private var dateOffsetX: Float = 0f
        private var dateOffsetY: Float = 0f

        private lateinit var mTimePaint: Paint
        private lateinit var mDatePaint: Paint

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateFormatter = SimpleDateFormat("E, d MMMM YY", Locale.getDefault())

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder?) {
            super.onCreate(holder)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@SimpleDigitalWatchFace)
                    .setAcceptsTapEvents(true)
                    .build()
            )

            mCalendar = Calendar.getInstance()

            initializeWatchFace()
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false
            )
            mBurnInProtection = properties.getBoolean(
                WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false
            )
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        private fun initializeWatchFace() {

            mTimePaint = Paint().apply {
                color = Color.CYAN
                textSize = 108f
                textAlign = Paint.Align.CENTER
                style = Paint.Style.FILL
                typeface = Typeface.DEFAULT_BOLD
                flags = Paint.ANTI_ALIAS_FLAG
            }

            mDatePaint = Paint().apply {
                color = Color.WHITE
                textSize = 24f
                textAlign = Paint.Align.CENTER
                style = Paint.Style.FILL
                flags = Paint.ANTI_ALIAS_FLAG
            }
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            updateTimer()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            mCenterX = width / 2f
            mCenterY = height / 2f
        }

        /*override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP ->
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    //Toast.makeText(applicationContext, R.string.message, Toast.LENGTH_SHORT).show()
            }
            invalidate()
        }*/

        override fun onDraw(canvas: Canvas, bounds: Rect?) {
            super.onDraw(canvas, bounds)
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            drawWatchFace(canvas)
        }

        private fun drawWatchFace(canvas: Canvas) {
            //set background
            canvas.drawColor(Color.BLACK)

            //format values
            val formattedTime = timeFormatter.format(mCalendar.time)
            val formattedDate = dateFormatter.format(mCalendar.time)

            //get bounds
            val timeBounds = Rect().apply {
                mTimePaint.getTextBounds(formattedTime, 0, formattedTime.length, this)
            }

            val dateBounds = Rect().apply {
                mDatePaint.getTextBounds(formattedDate, 0, formattedDate.length, this)
            }

            //get offset for
            timeOffsetX = mCenterX
            timeOffsetY = mCenterY + (timeBounds.height().toFloat() / 2)

            canvas.drawText(formattedTime, timeOffsetX, timeOffsetY, mTimePaint)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren"t visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@SimpleDigitalWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@SimpleDigitalWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}