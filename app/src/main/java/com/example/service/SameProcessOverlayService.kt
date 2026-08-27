package com.example.service

import android.content.Context
import android.content.Intent
import android.util.Log

class SameProcessOverlayService : BaseOverlayService() {
    override val variantLabel = "same-process"
    override val tag = "SameProcessOverlay"

    companion object {
        private const val TAG = "SameProcessOverlay"

        fun show(context: Context, source: String) {
            val intent = Intent(context, SameProcessOverlayService::class.java).apply {
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
