package com.example.network

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.coroutineContext

/**
 * Defines the progression of network connection retry phases:
 * 1. PHASE_1_TEN_SEC: Try 3 times, with 10 seconds between each attempt.
 * 2. PHASE_2_AFTER_20_SEC: Then wait 20 seconds, and try 3 times again (with 10 seconds between attempts).
 * 3. PHASE_3_MINUTE_CYCLE: If still no connection, try 3 times every minute.
 */
enum class RetryPhase(val label: String) {
    IDLE("Bereit"),
    INITIAL("Initialer Verbindungsversuch"),
    PHASE_1_TEN_SEC("Phase 1 (3x alle 10s)"),
    PHASE_2_AFTER_20_SEC("Phase 2 (Nach 20s Pause, 3x)"),
    PHASE_3_MINUTE_CYCLE("Phase 3 (3x jede Minute)"),
    CONNECTED("Verbunden"),
    FAILED("Fehlgeschlagen"),
    CANCELLED("Abgebrochen")
}

data class NetworkRetryStatus(
    val operationName: String = "Netzwerk",
    val phase: RetryPhase = RetryPhase.IDLE,
    val attemptInPhase: Int = 0,
    val maxAttemptsInPhase: Int = 3,
    val totalAttempts: Int = 0,
    val countdownSecondsRemaining: Int = 0,
    val isWaitingCountdown: Boolean = false,
    val lastErrorMessage: String? = null,
    val userFriendlyMessage: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object NetworkRetryPolicy {
    private const val TAG = "NetworkRetryPolicy"

    // Default Configuration based on user request
    const val PHASE_1_MAX_ATTEMPTS = 3
    const val PHASE_1_DELAY_SECONDS = 10

    const val PHASE_2_INITIAL_WAIT_SECONDS = 20
    const val PHASE_2_MAX_ATTEMPTS = 3
    const val PHASE_2_DELAY_SECONDS = 10

    const val PHASE_3_INTERVAL_SECONDS = 60
    const val PHASE_3_BURST_ATTEMPTS = 3
    const val PHASE_3_DELAY_SECONDS = 10

    // Shared global status for currently executing/retrying network operation
    private val _globalStatus = MutableStateFlow<NetworkRetryStatus?>(null)
    val globalStatus: StateFlow<NetworkRetryStatus?> = _globalStatus.asStateFlow()

    private val immediateRetrySignal = MutableStateFlow(0L)

    /**
     * Call this when internet connectivity returns or user clicks "Retry Now"
     * to skip the remaining countdown and execute the next attempt immediately.
     */
    fun triggerImmediateRetry() {
        immediateRetrySignal.value = System.currentTimeMillis()
    }

    /**
     * Checks whether an exception is considered a network/connection failure that warrants retry.
     */
    fun isRecoverableConnectionError(e: Throwable): Boolean {
        if (e is CancellationException) return false
        return e is SocketTimeoutException ||
                e is UnknownHostException ||
                e is ConnectException ||
                e is SSLException ||
                e is IOException ||
                (e.message?.contains("timeout", ignoreCase = true) == true) ||
                (e.message?.contains("Unable to resolve host", ignoreCase = true) == true) ||
                (e.message?.contains("Failed to connect", ignoreCase = true) == true) ||
                (e.message?.contains("Network is unreachable", ignoreCase = true) == true) ||
                (e.message?.contains("Connection reset", ignoreCase = true) == true) ||
                (e.message?.contains("Software caused connection abort", ignoreCase = true) == true)
    }

    /**
     * Executes a network block adhering to the strict retry specification:
     * - Attempt 0: Immediate initial attempt.
     * - If failed:
     *   - Phase 1: Try 3 times for 10 seconds each.
     *   - Phase 2: Wait 20 seconds, then try again 3 times (10s between attempts).
     *   - Phase 3: Try 3 times each minute (repeats every 60s).
     */
    suspend fun <T> executeWithConnectionRetry(
        operationName: String,
        maxMinuteCycles: Int = 10,
        onStatus: ((NetworkRetryStatus) -> Unit)? = null,
        block: suspend () -> T
    ): T {
        var totalAttempts = 0
        var lastError: Throwable? = null

        fun updateStatus(status: NetworkRetryStatus) {
            _globalStatus.value = status
            onStatus?.invoke(status)
        }

        // ==========================================
        // STEP 0: INITIAL IMMEDIATE ATTEMPT
        // ==========================================
        totalAttempts++
        updateStatus(
            NetworkRetryStatus(
                operationName = operationName,
                phase = RetryPhase.INITIAL,
                attemptInPhase = 1,
                maxAttemptsInPhase = 1,
                totalAttempts = totalAttempts,
                countdownSecondsRemaining = 0,
                isWaitingCountdown = false,
                userFriendlyMessage = "Verbindung wird aufgebaut: $operationName..."
            )
        )

        try {
            val result = block()
            updateStatus(
                NetworkRetryStatus(
                    operationName = operationName,
                    phase = RetryPhase.CONNECTED,
                    attemptInPhase = 1,
                    maxAttemptsInPhase = 1,
                    totalAttempts = totalAttempts,
                    userFriendlyMessage = "Verbindung erfolgreich hergestellt ($operationName)."
                )
            )
            return result
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            lastError = e
            Log.w(TAG, "Initial attempt for '$operationName' failed: ${e.message}")
        }

        // ==========================================
        // PHASE 1: TRY 3 TIMES FOR 10 SECONDS EACH
        // ==========================================
        Log.i(TAG, "Starting Phase 1 for '$operationName': 3 attempts, 10s interval.")
        for (attempt in 1..PHASE_1_MAX_ATTEMPTS) {
            // Wait 10 seconds countdown before attempting
            countdownWait(
                seconds = PHASE_1_DELAY_SECONDS,
                operationName = operationName,
                phase = RetryPhase.PHASE_1_TEN_SEC,
                attempt = attempt,
                maxAttempts = PHASE_1_MAX_ATTEMPTS,
                totalAttempts = totalAttempts,
                lastError = lastError?.message,
                messageBuilder = { sec ->
                    "Keine Verbindung. Neuer Versuch in ${sec}s (Versuch $attempt/$PHASE_1_MAX_ATTEMPTS)..."
                },
                onStatus = ::updateStatus
            )

            totalAttempts++
            updateStatus(
                NetworkRetryStatus(
                    operationName = operationName,
                    phase = RetryPhase.PHASE_1_TEN_SEC,
                    attemptInPhase = attempt,
                    maxAttemptsInPhase = PHASE_1_MAX_ATTEMPTS,
                    totalAttempts = totalAttempts,
                    countdownSecondsRemaining = 0,
                    isWaitingCountdown = false,
                    lastErrorMessage = lastError?.message,
                    userFriendlyMessage = "Phase 1: Verbinde... (Versuch $attempt von $PHASE_1_MAX_ATTEMPTS)"
                )
            )

            try {
                val result = block()
                updateStatus(
                    NetworkRetryStatus(
                        operationName = operationName,
                        phase = RetryPhase.CONNECTED,
                        attemptInPhase = attempt,
                        maxAttemptsInPhase = PHASE_1_MAX_ATTEMPTS,
                        totalAttempts = totalAttempts,
                        userFriendlyMessage = "Verbindung in Phase 1 (Versuch $attempt) erfolgreich hergestellt!"
                    )
                )
                return result
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                lastError = e
                Log.w(TAG, "Phase 1 attempt $attempt for '$operationName' failed: ${e.message}")
            }
        }

        // ==========================================
        // PHASE 2: WAIT 20 SECONDS, THEN TRY 3 TIMES AGAIN
        // ==========================================
        Log.i(TAG, "Phase 1 exhausted for '$operationName'. Waiting 20s before Phase 2 (3 attempts).")
        // Initial 20-second pause
        countdownWait(
            seconds = PHASE_2_INITIAL_WAIT_SECONDS,
            operationName = operationName,
            phase = RetryPhase.PHASE_2_AFTER_20_SEC,
            attempt = 0,
            maxAttempts = PHASE_2_MAX_ATTEMPTS,
            totalAttempts = totalAttempts,
            lastError = lastError?.message,
            messageBuilder = { sec ->
                "Phase 1 fehlgeschlagen. Warte 20s vor Phase 2 (noch ${sec}s)..."
            },
            onStatus = ::updateStatus
        )

        for (attempt in 1..PHASE_2_MAX_ATTEMPTS) {
            if (attempt > 1) {
                // 10 seconds between attempts in Phase 2
                countdownWait(
                    seconds = PHASE_2_DELAY_SECONDS,
                    operationName = operationName,
                    phase = RetryPhase.PHASE_2_AFTER_20_SEC,
                    attempt = attempt,
                    maxAttempts = PHASE_2_MAX_ATTEMPTS,
                    totalAttempts = totalAttempts,
                    lastError = lastError?.message,
                    messageBuilder = { sec ->
                        "Phase 2: Neuer Versuch in ${sec}s (Versuch $attempt/$PHASE_2_MAX_ATTEMPTS)..."
                    },
                    onStatus = ::updateStatus
                )
            }

            totalAttempts++
            updateStatus(
                NetworkRetryStatus(
                    operationName = operationName,
                    phase = RetryPhase.PHASE_2_AFTER_20_SEC,
                    attemptInPhase = attempt,
                    maxAttemptsInPhase = PHASE_2_MAX_ATTEMPTS,
                    totalAttempts = totalAttempts,
                    countdownSecondsRemaining = 0,
                    isWaitingCountdown = false,
                    lastErrorMessage = lastError?.message,
                    userFriendlyMessage = "Phase 2: Verbinde... (Versuch $attempt von $PHASE_2_MAX_ATTEMPTS)"
                )
            )

            try {
                val result = block()
                updateStatus(
                    NetworkRetryStatus(
                        operationName = operationName,
                        phase = RetryPhase.CONNECTED,
                        attemptInPhase = attempt,
                        maxAttemptsInPhase = PHASE_2_MAX_ATTEMPTS,
                        totalAttempts = totalAttempts,
                        userFriendlyMessage = "Verbindung in Phase 2 (Versuch $attempt) erfolgreich wiederhergestellt!"
                    )
                )
                return result
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                lastError = e
                Log.w(TAG, "Phase 2 attempt $attempt for '$operationName' failed: ${e.message}")
            }
        }

        // ==========================================
        // PHASE 3: STILL NO CONNECTION -> TRY 3 TIMES EACH MINUTE
        // ==========================================
        Log.i(TAG, "Phase 2 exhausted for '$operationName'. Entering Phase 3: 3 attempts each minute.")
        var minuteCycle = 0
        while (minuteCycle < maxMinuteCycles && coroutineContext.isActive) {
            minuteCycle++
            // Wait 1 minute (60s) before this minute's 3 attempts
            countdownWait(
                seconds = PHASE_3_INTERVAL_SECONDS,
                operationName = operationName,
                phase = RetryPhase.PHASE_3_MINUTE_CYCLE,
                attempt = 0,
                maxAttempts = PHASE_3_BURST_ATTEMPTS,
                totalAttempts = totalAttempts,
                lastError = lastError?.message,
                messageBuilder = { sec ->
                    "Periodischer Modus (3x/Min): Nächster Durchlauf in ${sec}s (Zyklus #$minuteCycle)..."
                },
                onStatus = ::updateStatus
            )

            for (attempt in 1..PHASE_3_BURST_ATTEMPTS) {
                if (attempt > 1) {
                    // 10 seconds between attempts in minute burst
                    countdownWait(
                        seconds = PHASE_3_DELAY_SECONDS,
                        operationName = operationName,
                        phase = RetryPhase.PHASE_3_MINUTE_CYCLE,
                        attempt = attempt,
                        maxAttempts = PHASE_3_BURST_ATTEMPTS,
                        totalAttempts = totalAttempts,
                        lastError = lastError?.message,
                        messageBuilder = { sec ->
                            "Zyklus #$minuteCycle: Versuch $attempt/$PHASE_3_BURST_ATTEMPTS in ${sec}s..."
                        },
                        onStatus = ::updateStatus
                    )
                }

                totalAttempts++
                updateStatus(
                    NetworkRetryStatus(
                        operationName = operationName,
                        phase = RetryPhase.PHASE_3_MINUTE_CYCLE,
                        attemptInPhase = attempt,
                        maxAttemptsInPhase = PHASE_3_BURST_ATTEMPTS,
                        totalAttempts = totalAttempts,
                        countdownSecondsRemaining = 0,
                        isWaitingCountdown = false,
                        lastErrorMessage = lastError?.message,
                        userFriendlyMessage = "Periodischer Modus: Verbinde (Zyklus #$minuteCycle, Versuch $attempt/3)..."
                    )
                )

                try {
                    val result = block()
                    updateStatus(
                        NetworkRetryStatus(
                            operationName = operationName,
                            phase = RetryPhase.CONNECTED,
                            attemptInPhase = attempt,
                            maxAttemptsInPhase = PHASE_3_BURST_ATTEMPTS,
                            totalAttempts = totalAttempts,
                            userFriendlyMessage = "Verbindung im periodischen Minuten-Modus erfolgreich hergestellt!"
                        )
                    )
                    return result
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    lastError = e
                    Log.w(TAG, "Phase 3 (cycle $minuteCycle, attempt $attempt) for '$operationName' failed: ${e.message}")
                }
            }
        }

        // Final failure state after all configured minute cycles
        updateStatus(
            NetworkRetryStatus(
                operationName = operationName,
                phase = RetryPhase.FAILED,
                attemptInPhase = 3,
                maxAttemptsInPhase = 3,
                totalAttempts = totalAttempts,
                lastErrorMessage = lastError?.message,
                userFriendlyMessage = "Keine Internetverbindung nach $totalAttempts Versuchen. Tippe auf 'Erneut versuchen'."
            )
        )

        throw (lastError ?: IOException("Network connection could not be established for '$operationName' after $totalAttempts attempts."))
    }

    /**
     * Helper to perform a second-by-second countdown delay that can be interrupted
     * immediately via [triggerImmediateRetry].
     */
    private suspend fun countdownWait(
        seconds: Int,
        operationName: String,
        phase: RetryPhase,
        attempt: Int,
        maxAttempts: Int,
        totalAttempts: Int,
        lastError: String?,
        messageBuilder: (Int) -> String,
        onStatus: (NetworkRetryStatus) -> Unit
    ) {
        val initialSignal = immediateRetrySignal.value
        for (sec in seconds downTo 1) {
            // Check if immediate retry was triggered
            if (immediateRetrySignal.value != initialSignal) {
                Log.i(TAG, "Countdown skipped due to immediate retry signal!")
                break
            }

            onStatus(
                NetworkRetryStatus(
                    operationName = operationName,
                    phase = phase,
                    attemptInPhase = attempt,
                    maxAttemptsInPhase = maxAttempts,
                    totalAttempts = totalAttempts,
                    countdownSecondsRemaining = sec,
                    isWaitingCountdown = true,
                    lastErrorMessage = lastError,
                    userFriendlyMessage = messageBuilder(sec)
                )
            )

            delay(1000L)
        }
    }
}
