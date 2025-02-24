/*
 * SPDX-FileCopyrightText: 2014 The Android Open Source Project
 * SPDX-FileCopyrightText: 2022-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.voltage.flash.ui

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.IntRange
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.voltage.flash.R
import com.voltage.flash.camera.CameraViewModel
import com.voltage.flash.ext.*
import com.voltage.flash.utils.Rotation

/**
 * This class manages the looks of the countdown.
 */
class CountDownView(context: Context, attrs: AttributeSet?) : FrameLayout(
    context, attrs
) {
    // Views
    private val remainingSecondsView by lazy {
        findViewById<TextView>(R.id.remainingSeconds)
    }

    // System services
    private val layoutInflater = context.getSystemService(LayoutInflater::class.java)

    private var remainingSeconds = 0
    private lateinit var listener: () -> Unit
    private val previewArea = Rect()

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                SET_TIMER_TEXT -> remainingSecondsChanged(remainingSeconds - 1)
            }
        }
    }

    /**
     * Returns whether countdown is on-going.
     */
    private val isCountingDown: Boolean
        get() = remainingSeconds > 0

    internal var cameraViewModel: CameraViewModel? = null
        set(value) {
            val lifecycleOwner = findViewTreeLifecycleOwner() ?: return

            // Unregister
            field?.screenRotation?.removeObservers(lifecycleOwner)

            field = value

            value?.let { cameraViewModel ->
                cameraViewModel.screenRotation.observe(lifecycleOwner) {
                    val screenRotation = it ?: return@observe

                    updateViewsRotation(screenRotation)
                }
            }
        }

    init {
        layoutInflater.inflate(R.layout.count_down_view, this)
    }

    /**
     * Responds to preview area change by centering the countdown UI in the new
     * preview area.
     */
    fun onPreviewAreaChanged(previewArea: Rect) {
        this.previewArea.set(previewArea)
    }

    private fun remainingSecondsChanged(seconds: Int) {
        remainingSeconds = seconds
        if (seconds == 0) {
            // Countdown has finished.
            isInvisible = true
            listener()
        } else {
            remainingSecondsView.text = seconds.toString()
            // Fade-out animation.
            startFadeOutAnimation()
            // Schedule the next remainingSecondsChanged() call in 1 second
            handler.sendEmptyMessageDelayed(SET_TIMER_TEXT, 1000)
        }
    }

    private fun startFadeOutAnimation() {
        val textWidth = remainingSecondsView.measuredWidth
        val textHeight = remainingSecondsView.measuredHeight
        remainingSecondsView.scaleX = 1f
        remainingSecondsView.scaleY = 1f
        remainingSecondsView.translationX = previewArea.centerX() - textWidth / 2f
        remainingSecondsView.translationY = previewArea.centerY() - textHeight / 2f
        remainingSecondsView.pivotX = textWidth / 2f
        remainingSecondsView.pivotY = textHeight / 2f
        remainingSecondsView.alpha = 1f
        val endScale = 2.5f
        remainingSecondsView.animate().apply {
            scaleX(endScale)
            scaleY(endScale)
            alpha(0f)
            duration = ANIMATION_DURATION_MS
        }.start()
    }

    /**
     * Starts showing countdown in the UI.
     *
     * @param sec duration of the countdown, in seconds
     * @param listener callback for when the status of countdown has finished.
     */
    fun startCountDown(@IntRange(from = 0) sec: Int, listener: () -> Unit) {
        if (isCountingDown) {
            cancelCountDown()
        }
        isVisible = true
        this.listener = listener
        remainingSecondsChanged(sec)
    }

    /**
     * Cancels the on-going countdown in the UI, if any.
     */
    fun cancelCountDown(): Boolean {
        if (remainingSeconds > 0) {
            remainingSeconds = 0
            handler.removeMessages(SET_TIMER_TEXT)
            isInvisible = true
            return true
        }
        return false
    }

    private fun updateViewsRotation(screenRotation: Rotation) {
        val compensationValue = screenRotation.compensationValue.toFloat()

        remainingSecondsView.smoothRotate(compensationValue)
    }

    companion object {
        private const val SET_TIMER_TEXT = 1
        private const val ANIMATION_DURATION_MS = 800L
    }
}
