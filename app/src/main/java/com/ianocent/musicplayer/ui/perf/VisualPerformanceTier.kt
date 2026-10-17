package com.ianocent.musicplayer.ui.perf

import android.app.ActivityManager
import android.content.Context

/**
 * Coarse runtime tier used to gate GPU/CPU-hungry effects (glass, particles).
 * Based on the dalvik heap size the app is allowed, which is a fair proxy for
 * device class without resorting to fragile hardware fingerprinting.
 */
enum class VisualPerformanceTier { LOW, BALANCED, HIGH }

object VisualTier {

    fun detect(context: Context): VisualPerformanceTier {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return VisualPerformanceTier.HIGH
            val memoryClassMb = am.memoryClass
            when {
                memoryClassMb >= 192 -> VisualPerformanceTier.HIGH
                memoryClassMb >= 128 -> VisualPerformanceTier.BALANCED
                else -> VisualPerformanceTier.LOW
            }
        } catch (e: Exception) {
            // Never crash over a heuristic; assume the best case.
            VisualPerformanceTier.HIGH
        }
    }
}