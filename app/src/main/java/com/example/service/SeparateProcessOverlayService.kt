package com.example.service

import android.content.Context
import android.content.Intent
import android.util.Log

class SeparateProcessOverlayService : BaseOverlayService() {
    override val variantLabel = "separate-process"
    override val tag = "SeparateProcessOverlay"

    companion object {
        private const val TAG = "SeparateProcessOverlay"

        fun show(context: Context, source: String) {
            val intent = Intent(context, SeparateProcessOverlayService::class.java).apply {
                putExtra(EXTRA_SOURCE, source)
            }
            try {
                val result = context.startService(intent)
                Log.i(TAG, "show(): startService returned $result")
            } catch (e: Exception) {
                Log.e(TAG, "show(): startService threw", e)
            }
        }
    }
}
