package com.nuvio.tv.core.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * Subtitle data to pass to an external player.
 */
data class SubtitleInput(
    val url: String,
    val name: String,
    val lang: String
)

/**
 * Input data for launching an external video player.
 */
data class ExternalPlayerInput(
    val url: String,
    val title: String? = null,
    val headers: Map<String, String>? = null,
    val resumePositionMs: Long = 0L,
    val subtitles: List<SubtitleInput>? = null,
    // Pre-resolved intro/outro skip segments as a JSON array string
    // (`[{"type","start","end"}]`, times in seconds). Read by players that support skip_segments.
    val skipSegmentsJson: String? = null
)

/**
 * Result returned by an external video player after playback ends.
 * Not all players return this data — MX Player, VLC, and Just Player are known to support it.
 */
data class ExternalPlayerResult(
    val positionMs: Long,
    val durationMs: Long?,
    val endedByUser: Boolean = true
)

/**
 * ActivityResultContract that launches an external video player via ACTION_VIEW and parses the
 * playback result. Players disagree wildly on what they return, so [parseResult] is deliberately
 * lenient. Observed contracts (verified on-device):
 * - MX Player: position + duration (Int ms) + end_by.
 * - Just Player: on completion returns ONLY end_by=playback_completion (no position/duration).
 * - VLC: extra_position/extra_duration (Long ms), no end_by; duration is <= 0 for network streams
 *   (the tracker backfills it). Ignores our launch resume extras, so resume into VLC won't take.
 * - Vimu: position.
 * - NovaPlayer: returns nothing usable (RESULT_CANCELED + null intent) — cannot be supported here.
 */
class ExternalPlayerResultContract : ActivityResultContract<ExternalPlayerInput, ExternalPlayerResult?>() {

    override fun createIntent(context: Context, input: ExternalPlayerInput): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(input.url), "video/*")

            input.title?.let {
                putExtra("title", it)
                putExtra(Intent.EXTRA_TITLE, it)
                putExtra("forcename", it) // Vimu Player
            }

            input.headers?.let { hdrs ->
                if (hdrs.isNotEmpty()) {
                    val headerArray = hdrs.entries.map { "${it.key}: ${it.value}" }.toTypedArray()
                    putExtra("headers", headerArray)
                }
            }

            // Resume position — supported by MX Player, VLC, Just Player, Vimu
            if (input.resumePositionMs > 0L) {
                putExtra("position", input.resumePositionMs.toInt())  // MX Player / Just Player (Int ms)
                putExtra("extra_position", input.resumePositionMs)    // VLC (Long ms)
                putExtra("startfrom", input.resumePositionMs.toInt()) // Vimu Player (Int ms)
                putExtra("forceresume", true)                         // Vimu: enable resume for network streams
                putExtra("from_start", false)                         // VLC: don't force start from beginning
            }

            // Request that the player returns result with position/duration.
            // Required by MX Player; harmless for other players.
            putExtra("return_result", true)

            // Pre-resolved intro/outro skip segments; players that support skip_segments read it.
            input.skipSegmentsJson?.let { putExtra("skip_segments", it) }

            // Subtitle extras for external players
            val subs = input.subtitles
            if (!subs.isNullOrEmpty()) {
                val subtitleUris = subs.map { Uri.parse(it.url) }.toTypedArray()
                val subtitleNames = subs.map { it.name }.toTypedArray()
                val subtitleFilenames = subs.map { "${it.lang}_${it.name}.srt" }.toTypedArray()

                // Grant read permission for content:// URIs via ClipData.
                // FLAG_GRANT_READ_URI_PERMISSION only covers intent.data, not extras.
                // Adding all subtitle URIs to ClipData ensures the receiving player
                // gets read access to all of them.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val clipData = android.content.ClipData(
                    "subtitles",
                    arrayOf("application/x-subrip", "text/vtt"),
                    android.content.ClipData.Item(subtitleUris.first())
                )
                subtitleUris.drop(1).forEach { uri ->
                    clipData.addItem(android.content.ClipData.Item(uri))
                }
                setClipData(clipData)

                // MX Player / Nova-style players
                putExtra("subs", subtitleUris)
                putExtra("subs.name", subtitleNames)
                putExtra("subs.filename", subtitleFilenames)
                putExtra("subs.enable", arrayOf(subtitleUris.first()))

                // Just Player
                putExtra("subtitle_uri", subtitleUris)
                putExtra("subtitle_name", subtitleNames)

                // VLC (single subtitle — use first one)
                putExtra("subtitles_location", subtitleUris.first())

                // Vimu Player
                putExtra("forcedsrt", subs.first().url)
            }

            // Do NOT add FLAG_ACTIVITY_NEW_TASK — it prevents receiving the result.
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): ExternalPlayerResult? {
        // Some players return RESULT_OK, others return RESULT_CANCELED even on normal exit.
        // We try to parse position regardless of resultCode.
        android.util.Log.d("ExtPlayerContract", "parseResult: resultCode=$resultCode, intent=$intent, extras=${intent?.extras?.keySet()?.toList()}")
        val data = intent ?: return null

        val position = parsePosition(data)
        val duration = parseDuration(data)
        // MX Player and Just Player report the end reason here; VLC and Vimu do not.
        val endBy = data.getStringExtra("end_by")
        val completedByEndReason = endBy == "playback_completion"

        // Players that signal completion via end_by often reset the returned position to 0 at
        // EOF — don't discard those, or the episode never marks watched / auto-advances. Only
        // bail when there is genuinely nothing to act on: no position AND no completion signal.
        if (position == null && !completedByEndReason) {
            android.util.Log.d("ExtPlayerContract", "parseResult: no position and no completion signal; dropping")
            return null
        }

        // On a bare completion signal with no/zero position, report the end position so the
        // 90%-of-duration completion check passes and progress is saved as watched (not 0%).
        val effectivePosition = position ?: duration ?: 0L
        // Only an explicit "playback_completion" counts as a natural end.
        // Absent end_by (VLC/Vimu) stays endedByUser=true and relies on the 90% rule, so a
        // genuine mid-video user exit is NOT treated as a completion.
        val endedByUser = endBy != "playback_completion"

        android.util.Log.d(
            "ExtPlayerContract",
            "parseResult parsed position=${effectivePosition}ms, duration=${duration}ms, " +
                "end_by=$endBy; endedByUser=$endedByUser"
        )

        return ExternalPlayerResult(
            positionMs = effectivePosition,
            durationMs = duration,
            endedByUser = endedByUser
        )
    }

    // Robust against key + type variants across players:
    // - VLC: extra_position (Long)
    // - MX Player / Just Player / Nova: position (Int)
    private fun parsePosition(data: Intent): Long? =
        firstPositiveExtra(data, "extra_position", "position")

    private fun parseDuration(data: Intent): Long? =
        firstPositiveExtra(data, "extra_duration", "duration")

    /** Reads the first key that holds a positive Long or Int value, trying both types. */
    private fun firstPositiveExtra(data: Intent, vararg keys: String): Long? {
        for (key in keys) {
            val asLong = data.getLongExtra(key, -1L)
            if (asLong > 0) return asLong
            val asInt = data.getIntExtra(key, -1)
            if (asInt > 0) return asInt.toLong()
        }
        return null
    }
}
