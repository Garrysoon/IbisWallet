package github.aeonbtc.ibiswallet.data.boltz

import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import lwk.LogLevel
import lwk.Logging

/**
 * Forwards LWK/boltz-rust internal logs into logcat under the `BoltzLwk` tag.
 *
 * Without this, boltz-rust's Boltz WebSocket lifecycle is a black box: reverse
 * swaps can be created over REST and then time out waiting for the first swap
 * status push, with nothing to show whether the socket connected, whether the
 * subscribe was acknowledged, or whether Boltz sent anything at all.
 *
 * Relevant boltz-rust messages this surfaces:
 * - "Error connecting to websocket"
 * - "Received text msg" / "Received pong"
 * - "Received stream error" / "Received close msg"
 * - "Failed to subscribe to swap ..., forcing reconnect"
 * - "[swap:<id>] subscribing to swap webhook"
 * - "Timeout while waiting state for swap id <id>"
 *
 * Debug builds only. Deliberately NOT gated on `Log.isLoggable`: LWK installs the
 * Rust logger via `log::set_boxed_logger`, which can only succeed once per
 * process, so skipping the bridge on an early session build would permanently
 * lose logging for the rest of the process lifetime.
 *
 * Only relays library messages; never logs wallet-derived values itself. LWK does
 * not log mnemonics or preimages, but swap ids and xpubs can appear, so this is
 * excluded from release builds entirely.
 */
class BoltzLwkLogBridge : Logging {
    override fun log(level: LogLevel, message: String) {
        // Must never throw back into Rust across the FFI boundary.
        runCatching {
            when (level) {
                LogLevel.ERROR -> Log.e(TAG, message)
                LogLevel.WARN -> Log.w(TAG, message)
                LogLevel.INFO -> Log.i(TAG, message)
                // Rust debug/trace is where the WebSocket frame logs live; keep them
                // at debug so `-s BoltzLwk:V` shows the full picture.
                LogLevel.DEBUG -> Log.d(TAG, message)
            }
        }
    }

    companion object {
        const val TAG = "BoltzLwk"

        /**
         * Returns the bridge on debug builds, otherwise null.
         *
         * Filter with `adb logcat -s BoltzLwk:V` rather than a system property.
         */
        fun createIfEnabled(): Logging? {
            if (!BuildConfig.DEBUG) return null
            return BoltzLwkLogBridge().also {
                runCatching { Log.i(TAG, "LWK Boltz log bridge attached") }
            }
        }
    }
}
