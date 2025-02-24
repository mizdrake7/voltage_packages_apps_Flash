/*
 * SPDX-FileCopyrightText: 2022-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.voltage.flash.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AttributeSet
import android.widget.ImageButton
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.voltage.flash.R
import com.voltage.flash.camera.CameraViewModel
import com.voltage.flash.ext.*
import com.voltage.flash.utils.ExifUtils
import com.voltage.flash.utils.MediaType
import com.voltage.flash.utils.Rotation
import java.io.InputStream

/**
 * Image/video preview fragment
 */
class CapturePreviewLayout(context: Context, attrs: AttributeSet?) : ConstraintLayout(
    context, attrs
) {
    private var uri: Uri? = null
    private var photoInputStream: InputStream? = null
    private lateinit var mediaType: MediaType

    private var exoPlayer: ExoPlayer? = null

    private val cancelButton by lazy { findViewById<ImageButton>(R.id.cancelButton) }
    private val confirmButton by lazy { findViewById<ImageButton>(R.id.confirmButton) }
    private val imageView by lazy { findViewById<ImageView>(R.id.imageView) }
    private val videoView by lazy { findViewById<PlayerView>(R.id.videoView) }

    /**
     * input is null == canceled
     * input is not null == confirmed
     */
    internal var onChoiceCallback: (input: Any?) -> Unit = {}

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
    private val screenRotation
        get() = cameraViewModel?.screenRotation?.value ?: Rotation.ROTATION_0

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        cancelButton.setOnClickListener {
            stopPreview()
            onChoiceCallback(null)
        }
        confirmButton.setOnClickListener {
            stopPreview()
            onChoiceCallback(uri ?: photoInputStream)
        }
    }

    internal fun updateSource(uri: Uri, mediaType: MediaType) {
        this.uri = uri
        this.photoInputStream = null
        this.mediaType = mediaType

        imageView.isVisible = mediaType == MediaType.PHOTO
        videoView.isVisible = mediaType == MediaType.VIDEO

        startPreview()
    }

    internal fun updateSource(photoInputStream: InputStream) {
        this.uri = null
        this.photoInputStream = photoInputStream
        this.mediaType = MediaType.PHOTO

        imageView.isVisible = true
        videoView.isVisible = false

        startPreview()
    }

    private fun startPreview() {
        assert((uri == null) != (photoInputStream == null)) {
            "Expected uri or photoInputStream, not both."
        }
        when (mediaType) {
            MediaType.PHOTO -> {
                if (uri != null) {
                    imageView.rotation = 0f
                    imageView.setImageURI(uri)
                } else {
                    val inputStream = photoInputStream!!
                    val transform = ExifUtils.getTransform(inputStream)
                    inputStream.mark(Int.MAX_VALUE)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.reset()
                    imageView.rotation =
                        transform.rotation.offset.toFloat() - screenRotation.offset
                    imageView.setImageBitmap(bitmap)
                }
            }

            MediaType.VIDEO -> {
                exoPlayer = ExoPlayer.Builder(context)
                    .build()
                    .also {
                        videoView.player = it

                        it.setMediaItem(MediaItem.fromUri(uri!!))

                        it.playWhenReady = true
                        it.seekTo(0)
                        it.prepare()
                    }
            }
        }
    }

    private fun stopPreview() {
        when (mediaType) {
            MediaType.PHOTO -> {}
            MediaType.VIDEO -> {
                exoPlayer?.release()
                exoPlayer = null
            }
        }
    }

    private fun updateViewsRotation(screenRotation: Rotation) {
        val compensationValue = screenRotation.compensationValue.toFloat()

        cancelButton.smoothRotate(compensationValue)
        confirmButton.smoothRotate(compensationValue)
    }
}
