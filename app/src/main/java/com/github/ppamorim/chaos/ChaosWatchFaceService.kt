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

    internal val animationHandler = Handler()
    internal val updateTimeHandler: Handler = EngineHandler(this)
    internal var registeredTimeZoneReceiver = false
    internal var registeredColorAccentReceiver = false
    internal var backgroundPaint: Paint? = null
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
    internal var bounds: Rect? = null
    internal val centerBallRadius = 10F

    internal var ambient = false
    internal var time: Time? = null

    internal var xOffset = 0F
    internal var yOffset = 0F

    internal var bias = 0
    internal var diff = 0F
    internal var including = false

    internal var centerX = 0F
    internal var centerY = 0F
    internal var secRot = 0F
    internal var minutes = 0
    internal var minRot = 0F
    internal var hrRot = 0F
    internal var secLength = 0F
    internal var minLength = 0F
    internal var hrLength = 0F
    internal var minX = 0F
    internal var minY = 0F
    internal var hrX = 0F
    internal var hrY = 0F
    internal var secX = 0F
    internal var secY = 0F

    internal var invalidator: Runnable? = null

    /**
     * Whether the display supports fewer bits for each color in ambient mode. When true, we
     * disable anti-aliasing in ambient mode.
     */
    internal var lowBitAmbient = false

    internal val timeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra(TIME_ZONE)) {
          time?.clear(intent.getStringExtra(TIME_ZONE))
          time?.setToNow()
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
          calculatePoints(bounds)
          invalidate()
        }
      }
    }

    override fun onCreate(holder: SurfaceHolder?) {
      super.onCreate(holder)

      setWatchFaceStyle(WatchFaceStyle.Builder(this@ChaosWatchFaceService).setCardPeekMode(
          WatchFaceStyle.PEEK_MODE_VARIABLE).setBackgroundVisibility(
          WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE).setShowSystemUiTime(
          false).setAcceptsTapEvents(true).build())
      val resources = this@ChaosWatchFaceService.resources
      yOffset = resources.getDimension(R.dimen.digital_y_offset)

      val shared = this@ChaosWatchFaceService.applicationContext.getSharedPreferences(
          "com.github.ppamorim.chaos", Context.MODE_PRIVATE)

      if (shared.contains(ACCENT_COLOR)) {
        accentColor = shared.getInt(ACCENT_COLOR, Color.GREEN)
      }

      backgroundPaint = Paint()
      backgroundPaint?.color = resources.getColor(R.color.background_1)

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

      time = Time()

      registerConfigChangeReceiver()

    }

    override fun onDestroy() {
      updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
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
        time?.clear(TimeZone.getDefault().id)
        time?.setToNow()
      } else {
        unregisterTimerReceiver()
      }

      // Whether the timer should be running depends on whether we're visible (as well as
      // whether we're in ambient mode), so we may need to start or stop the timer.
      updateTimer()
    }

    private fun registerTimerReceiver() {
      if (registeredTimeZoneReceiver) {
        return
      }
      registeredTimeZoneReceiver = true
      this@ChaosWatchFaceService.registerReceiver(timeZoneReceiver,
          IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))
    }

    private fun unregisterTimerReceiver() {
      if (!registeredTimeZoneReceiver) {
        return
      }
      registeredTimeZoneReceiver = false
      this@ChaosWatchFaceService.unregisterReceiver(timeZoneReceiver)
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
      xOffset = resources.getDimension(
          if (isRound) R.dimen.digital_x_offset_round else R.dimen.digital_x_offset)
    }

    override fun onPropertiesChanged(properties: Bundle?) {
      super.onPropertiesChanged(properties)
      lowBitAmbient = properties?.getBoolean(WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
          ?: lowBitAmbient
    }

    override fun onTimeTick() {
      super.onTimeTick()
      calculatePoints(bounds)
      invalidate()
    }

    override fun onAmbientModeChanged(inAmbientMode: Boolean) {
      super.onAmbientModeChanged(inAmbientMode)

      if (ambient != inAmbientMode) {

        ambient = inAmbientMode

        if (lowBitAmbient) {
          pointerPaint?.isAntiAlias = !inAmbientMode
          textTimePaint?.isAntiAlias = !inAmbientMode
        }

        if (invalidator == null) {
          invalidator = Runnable {
            if (!ambient) {

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

              println("Diff $diff")

              calculatePoints(bounds)
              invalidate()
              animationHandler.postDelayed(invalidator, 30)
            } else {
              invalidator = null
            }
          }
          animationHandler.postDelayed(invalidator, 30)
        }

        calculatePoints(bounds)
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
      calculatePoints(bounds)
      invalidate()
    }

    fun calculatePoints(bounds: Rect? = null) {

      if (bounds == null) {
        return
      }

      time?.let { time ->

        time.setToNow()

        // Find the center. Ignore the window insets so that, on round watches with a
        // "chin", the watch face is centered on the entire screen, not just the usable
        // portion.
        if (centerX == 0F) {
          centerX = bounds.width() / 2F
        }
        if (centerY == 0F) {
          centerY = bounds.height() / 2F
        }

        secRot = time.second / 30F * Math.PI.toFloat()
        minutes = time.minute
        minRot = minutes / 30F * Math.PI.toFloat()
        hrRot = (time.hour + minutes / 60F) / 6F * Math.PI.toFloat()

        secLength = centerX - 60
        minLength = centerX - 40
        hrLength = centerX - 20

        minX = Math.sin(minRot.toDouble()).toFloat() * minLength
        minY = (-Math.cos(minRot.toDouble())).toFloat() * minLength

        hrX = Math.sin(hrRot.toDouble()).toFloat() * hrLength
        hrY = (-Math.cos(hrRot.toDouble())).toFloat() * hrLength

        secX = Math.sin(secRot.toDouble()).toFloat() * secLength
        secY = (-Math.cos(secRot.toDouble())).toFloat() * secLength

      }
    }

    override fun onDraw(canvas: Canvas?, bounds: Rect?) {
      super.onDraw(canvas, bounds)

      this.bounds = bounds

      canvas?.run {

        // Draw the background.
        if (isInAmbientMode) {
          drawColor(Color.BLACK)
        } else {
          drawRect(0f, 0f, centerX * 2, centerY * 2, backgroundPaint)
        }

        if (!ambient) {

          //Hour circle
          drawCircle(centerX, centerY, hrLength + radius, hoursBallPaintOut)
          drawCircle(centerX, centerY, hrLength + diff, hoursBallPaint)
          drawLine(centerX, centerY, centerX + hrX - diff, centerY + hrY - diff, pointerPaint)

          //Minute circle
          drawCircle(centerX, centerY, minLength + radius, minutesBallPaintOut)
          drawCircle(centerX, centerY, minLength - diff, minutesBallPaint)
          drawLine(centerX, centerY, centerX + minX + diff, centerY + minY + diff, pointerPaint)

          //Seconds circle
          drawCircle(centerX, centerY, secLength + radius, secondsBallPaintOut)
          drawCircle(centerX, centerY, secLength + diff, secondsBallPaint)

          //Seconds line
          drawLine(centerX, centerY, centerX + secX + diff, centerY + secY + diff, pointerPaint)

          //Center ball and inner black ball
          drawCircle(centerX, centerY, secLength - (20 - radius) , secondsBallPaintIn)
          drawCircle(centerX, centerY, centerBallRadius, centerBallPaint)

        } else {
          drawLine(centerX, centerY, centerX + minX, centerY + minY, pointerPaint)
          drawLine(centerX, centerY, centerX + hrX, centerY + hrY, pointerPaint)
          drawCircle(centerX, centerY, centerBallRadius, pointerPaint)
        }

      }

          //Sorry!

//          val text = if (ambient) String.format("%d:%02d", time.hour, time.minute)
//          else String.format("%d:%02d:%02d", time.hour, time.minute, time.second)
//
//          val textTimeBounds = Rect()
//          textTimePaint?.getTextBounds(text, 0, text.length, bounds)
//          val height = textTimeBounds.height()
//          val width = textTimeBounds.width()
//
//          canvas?.drawText(text, centerX - width/2, centerY + height, textTimePaint)

    }

    /**
     * Starts the [.updateTimeHandler] timer if it should be running and isn't currently
     * or stops it if it shouldn't be running but currently is.
     */
    private fun updateTimer() {
      updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
      if (shouldTimerBeRunning()) {
        updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
      }
    }

    /**
     * Returns whether the [.updateTimeHandler] timer should be running. The timer should
     * only run when we're visible and in interactive mode.
     */
    private fun shouldTimerBeRunning() = isVisible && !isInAmbientMode

    /**
     * Handle updating the time periodically in interactive mode.
     */
    fun handleUpdateTimeMessage() {
      calculatePoints(bounds)
      invalidate()
      if (shouldTimerBeRunning()) {
        val timeMs = System.currentTimeMillis()
        val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
        updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
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
