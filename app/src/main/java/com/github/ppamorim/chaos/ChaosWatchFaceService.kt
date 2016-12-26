/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.ppamorim.chaos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.text.format.Time
import android.view.SurfaceHolder
import android.view.WindowInsets

import java.lang.ref.WeakReference
import java.util.TimeZone
import java.util.concurrent.TimeUnit

const val CHANGE_COLOR = "com.github.ppamorim.chaos.update.accent.color"
const val ACCENT_COLOR = "accent.color"

const val TIME_ZONE = "time-zone"

class ChaosWatchFaceService: CanvasWatchFaceService() {

  override fun onCreateEngine(): Engine {
    return Engine()
  }

  private class EngineHandler(reference: ChaosWatchFaceService.Engine) : Handler() {
    private val mWeakReference: WeakReference<ChaosWatchFaceService.Engine>

    init {
      mWeakReference = WeakReference(reference)
    }

    override fun handleMessage(msg: Message) {
      val engine = mWeakReference.get()
      engine?.let {
        when (msg.what) {
          MSG_UPDATE_TIME -> it.handleUpdateTimeMessage()
        }
      }

    }
  }

  inner class Engine : CanvasWatchFaceService.Engine() {

    internal val mUpdateTimeHandler: Handler = EngineHandler(this)
    internal var mRegisteredTimeZoneReceiver = false
    internal var registeredColorAccentReceiver = false
    internal var mBackgroundPaint: Paint? = null
    internal var pointerPaint: Paint? = null

    internal var centerBallPaint: Paint? = null
    internal var hoursBallPaint: Paint? = null
    internal var hoursBallPaintOut: Paint? = null
    internal var minutesBallPaint: Paint? = null
    internal var minutesBallPaintOut: Paint? = null
    internal var secondsBallPaint: Paint? = null
    internal var secondsBallPaintOut: Paint? = null
    internal var secondsBallPaintIn: Paint? = null

    internal var textTimePaint: Paint? = null

    internal var accentColor = Color.RED

    internal var radius = 5

    internal val centerBallRadius = 10F

    internal var mAmbient = false
    internal var mTime: Time? = null

    internal val mTimeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra(TIME_ZONE)) {
          mTime?.clear(intent.getStringExtra(TIME_ZONE))
          mTime?.setToNow()
        }
      }
    }

    internal val colorAccentReceiver = object: BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        println("accentColor")
        if (intent.hasExtra(ACCENT_COLOR)) {
          accentColor = intent.getIntExtra(ACCENT_COLOR, Color.GREEN)
          secondsBallPaintOut = createBallPaint(accentColor)
          minutesBallPaintOut = createBallPaint(accentColor)
          hoursBallPaintOut = createBallPaint(accentColor)
          centerBallPaint = createBallPaint(accentColor)
          textTimePaint = createBallPaint(accentColor)
          invalidate()
        }
      }
    }

    internal var mXOffset = 0F
    internal var mYOffset = 0F

    internal val handler = Handler()
    internal var invalidator: Runnable? = null

    /**
     * Whether the display supports fewer bits for each color in ambient mode. When true, we
     * disable anti-aliasing in ambient mode.
     */
    internal var mLowBitAmbient: Boolean = false

    override fun onCreate(holder: SurfaceHolder?) {
      super.onCreate(holder)

      setWatchFaceStyle(WatchFaceStyle.Builder(this@ChaosWatchFaceService).setCardPeekMode(
          WatchFaceStyle.PEEK_MODE_VARIABLE).setBackgroundVisibility(
          WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE).setShowSystemUiTime(
          false).setAcceptsTapEvents(true).build())
      val resources = this@ChaosWatchFaceService.resources
      mYOffset = resources.getDimension(R.dimen.digital_y_offset)

      val shared = this@ChaosWatchFaceService.applicationContext.getSharedPreferences(
          "com.github.ppamorim.chaos", Context.MODE_PRIVATE)

      if (shared.contains(ACCENT_COLOR)) {
        accentColor = shared.getInt(ACCENT_COLOR, Color.GREEN)
      }

      mBackgroundPaint = Paint()
      mBackgroundPaint?.color = resources.getColor(R.color.background_1)

      pointerPaint = createPointerPaint(resources.getColor(R.color.digital_text))

      centerBallPaint = createBallPaint(accentColor)
      secondsBallPaint = createBallPaint(resources.getColor(R.color.background_1))
      secondsBallPaintOut = createBallPaint(accentColor)
      minutesBallPaint = createBallPaint(resources.getColor(R.color.background_1))
      minutesBallPaintOut = createBallPaint(accentColor)
      hoursBallPaint = createBallPaint(resources.getColor(R.color.background_1))
      hoursBallPaintOut = createBallPaint(accentColor)
      secondsBallPaintIn = createBallPaint(resources.getColor(R.color.background_1))
      textTimePaint = createBallPaint(accentColor)

      mTime = Time()

      registerConfigChangeReceiver()

    }

    override fun onDestroy() {
      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
      unregisterConfigChangeReceiver()
      super.onDestroy()
    }

    private fun createPointerPaint(textColor: Int): Paint {
      val paint = Paint()
      paint.color = textColor
      paint.typeface = NORMAL_TYPEFACE
      paint.isAntiAlias = true
      paint.strokeWidth = 5.0F
      return paint
    }

    private fun createBallPaint(color: Int): Paint {
      val paint = Paint()
      paint.color = color
      paint.isAntiAlias = true
      return paint
    }

    override fun onVisibilityChanged(visible: Boolean) {
      super.onVisibilityChanged(visible)

      if (visible) {
        registerTimerReceiver()

        // Update time zone in case it changed while we weren't visible.
        mTime?.clear(TimeZone.getDefault().id)
        mTime?.setToNow()
      } else {
        unregisterTimerReceiver()
      }

      // Whether the timer should be running depends on whether we're visible (as well as
      // whether we're in ambient mode), so we may need to start or stop the timer.
      updateTimer()
    }

    private fun registerTimerReceiver() {
      if (mRegisteredTimeZoneReceiver) {
        return
      }
      mRegisteredTimeZoneReceiver = true
      this@ChaosWatchFaceService.registerReceiver(mTimeZoneReceiver,
          IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))
    }

    private fun unregisterTimerReceiver() {
      if (!mRegisteredTimeZoneReceiver) {
        return
      }
      mRegisteredTimeZoneReceiver = false
      this@ChaosWatchFaceService.unregisterReceiver(mTimeZoneReceiver)
    }

    private fun registerConfigChangeReceiver() {
      if (!registeredColorAccentReceiver) {
        registeredColorAccentReceiver = true
        val filter = IntentFilter()
        filter.addAction(CHANGE_COLOR)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        this@ChaosWatchFaceService.registerReceiver(colorAccentReceiver,
            filter)
      }
    }

    private fun unregisterConfigChangeReceiver() {
      if (registeredColorAccentReceiver) {
        registeredColorAccentReceiver = false
        this@ChaosWatchFaceService.unregisterReceiver(colorAccentReceiver)
      }
    }

    override fun onApplyWindowInsets(insets: WindowInsets) {
      super.onApplyWindowInsets(insets)

      // Load resources that have alternate values for round watches.
      val resources = this@ChaosWatchFaceService.resources
      val isRound = insets.isRound
      mXOffset = resources.getDimension(
          if (isRound) R.dimen.digital_x_offset_round else R.dimen.digital_x_offset)
    }

    override fun onPropertiesChanged(properties: Bundle?) {
      super.onPropertiesChanged(properties)
      mLowBitAmbient = properties?.getBoolean(WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
          ?: mLowBitAmbient
    }

    override fun onTimeTick() {
      super.onTimeTick()
      invalidate()
    }

    override fun onAmbientModeChanged(inAmbientMode: Boolean) {
      super.onAmbientModeChanged(inAmbientMode)
      if (mAmbient != inAmbientMode) {
        mAmbient = inAmbientMode
        if (mLowBitAmbient) {
          pointerPaint?.isAntiAlias = !inAmbientMode
          textTimePaint?.isAntiAlias = !inAmbientMode
        }

        if (invalidator == null) {
          invalidator = Runnable {
//            if (!mAmbient) {
            if (true) {

              if (bias == 100) {
                including = false
              } else if (bias == 0) {
                including = true
              }

              if (including) {
                bias += 10
              } else {
                bias -= 10
              }

              diff = Math.cos(bias/50.0).toFloat()

              invalidate()
              handler.postDelayed(invalidator, 30)
            } else {
              invalidator = null
            }
          }
          handler.postDelayed(invalidator, 30)
        }

        invalidate()
      }

      // Whether the timer should be running depends on whether we're visible (as well as
      // whether we're in ambient mode), so we may need to start or stop the timer.
      updateTimer()
    }

    /**
     * Captures tap event (and tap type) and toggles the background color if the user finishes
     * a tap.
     */
    override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
      when (tapType) {
        WatchFaceService.TAP_TYPE_TOUCH -> {
        }
        WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
        }
        WatchFaceService.TAP_TYPE_TAP -> {
        }
      }// The user has started touching the screen.
      // The user has started a different gesture or otherwise cancelled the tap.
      invalidate()
    }

    var bias = 0
    var diff = 0F
    var including = false

    override fun onDraw(canvas: Canvas?, bounds: Rect?) {
      super.onDraw(canvas, bounds)

      mTime?.let { mTime ->

        mTime.setToNow()

        bounds?.let { bounds ->

          // Find the center. Ignore the window insets so that, on round watches with a
          // "chin", the watch face is centered on the entire screen, not just the usable
          // portion.
          val centerX = bounds.width() / 2f
          val centerY = bounds.height() / 2f


          // Draw the background.
          if (isInAmbientMode) {
            canvas?.drawColor(Color.BLACK)
          } else {
            canvas?.drawRect(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat(),
                mBackgroundPaint)
          }

          val secRot = mTime.second / 30f * Math.PI.toFloat()
          val minutes = mTime.minute
          val minRot = minutes / 30f * Math.PI.toFloat()
          val hrRot = (mTime.hour + minutes / 60f) / 6f * Math.PI.toFloat()

          val secLength = centerX - 60
          val minLength = centerX - 40
          val hrLength = centerX - 20

          val minX = Math.sin(minRot.toDouble()).toFloat() * minLength
          val minY = (-Math.cos(minRot.toDouble())).toFloat() * minLength

          val hrX = Math.sin(hrRot.toDouble()).toFloat() * hrLength
          val hrY = (-Math.cos(hrRot.toDouble())).toFloat() * hrLength

          if (!mAmbient) {

            canvas?.drawCircle(centerX, centerY, hrLength + radius, hoursBallPaintOut)
            canvas?.drawCircle(centerX, centerY, hrLength + diff, hoursBallPaint)
            canvas?.drawLine(centerX, centerY, centerX + hrX - diff, centerY + hrY - diff, pointerPaint)

            canvas?.drawCircle(centerX, centerY, minLength + radius, minutesBallPaintOut)
            canvas?.drawCircle(centerX, centerY, minLength - diff, minutesBallPaint)
            canvas?.drawLine(centerX, centerY, centerX + minX + diff, centerY + minY + diff, pointerPaint)

            canvas?.drawCircle(centerX, centerY, secLength + radius, secondsBallPaintOut)
            canvas?.drawCircle(centerX, centerY, secLength + diff, secondsBallPaint)

            val secX = Math.sin(secRot.toDouble()).toFloat() * secLength
            val secY = (-Math.cos(secRot.toDouble())).toFloat() * secLength
            canvas?.drawLine(centerX, centerY, centerX + secX + diff, centerY + secY + diff, pointerPaint)

            canvas?.drawCircle(centerX, centerY, secLength - (20 - radius) , secondsBallPaintIn)

            canvas?.drawCircle(centerX, centerY, centerBallRadius, centerBallPaint)

          } else {
            canvas?.drawLine(centerX, centerY, centerX + minX, centerY + minY, pointerPaint)
            canvas?.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, pointerPaint)
            canvas?.drawCircle(centerX, centerY, centerBallRadius, pointerPaint)
          }

          //Sorry!

//          val text = if (mAmbient) String.format("%d:%02d", mTime.hour, mTime.minute)
//          else String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second)
//
//          val textTimeBounds = Rect()
//          textTimePaint?.getTextBounds(text, 0, text.length, bounds)
//          val height = textTimeBounds.height()
//          val width = textTimeBounds.width()
//
//          canvas?.drawText(text, centerX - width/2, centerY + height, textTimePaint)

        }

      }

    }

    /**
     * Starts the [.mUpdateTimeHandler] timer if it should be running and isn't currently
     * or stops it if it shouldn't be running but currently is.
     */
    private fun updateTimer() {
      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
      if (shouldTimerBeRunning()) {
        mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
      }
    }

    /**
     * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer should
     * only run when we're visible and in interactive mode.
     */
    private fun shouldTimerBeRunning() = isVisible && !isInAmbientMode

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

  companion object {
    private val NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private val INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1)

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private val MSG_UPDATE_TIME = 0
  }
}
