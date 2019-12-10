/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.os.Handler
import com.datadog.android.log.internal.net.LogUploadStatus
import com.datadog.android.log.internal.net.LogUploader
import com.datadog.android.log.internal.utils.sdkLogger
import java.util.concurrent.atomic.AtomicBoolean

internal class LogUploadRunnable(
    private val handler: Handler,
    private val logReader: LogReader,
    private val logUploader: LogUploader
) : UploadRunnable {

    private val attemptsCount = mutableMapOf<String, Int>()
    private var currentDelayInterval = MAX_DELAY_MS
    private val isPaused: AtomicBoolean = AtomicBoolean(false)

    //  region Runnable

    override fun run() {
        val batch = logReader.readNextBatch()
        if (batch != null) {
            val batchId = batch.id
            sdkLogger.i("$TAG: Sending batch $batchId")
            val status = logUploader.uploadLogs(batch.logs)
            if (shouldDropBatch(batchId, status)) {
                logReader.dropBatch(batchId)
            }
            currentDelayInterval = resolveInterval()
            handler.postDelayed(this, currentDelayInterval)
        } else {
            sdkLogger.i("$TAG: There was no batch to be sent")
            currentDelayInterval = MAX_DELAY_MS
            handler.removeCallbacks(this)
            isPaused.set(true)
        }
    }

    override fun onDataAdded() {
        resume()
    }

    // endregion

    // region Internal
    private fun resume() {
        if (isPaused.compareAndSet(true, false)) {
            handler.removeCallbacks(this) // we want to make sure we removed everything
            handler.postDelayed(this, resolveInterval())
        }
    }

    private fun shouldDropBatch(batchId: String, status: LogUploadStatus): Boolean {
        val maxAttempts = maxAttemptsMap[status] ?: 1
        val attemptCount = (attemptsCount[batchId] ?: 0) + 1

        val shouldDropBatch = attemptCount >= maxAttempts
        if (shouldDropBatch) {
            attemptsCount.remove(batchId)
        } else {
            attemptsCount[batchId] = attemptCount
        }
        return shouldDropBatch
    }

    private fun resolveInterval(): Long {
        return Math.max(MIN_DELAY_MS, currentDelayInterval * DELAY_PERCENT / 100)
    }

    // endregion

    companion object {
        private val maxAttemptsMap = mapOf(
            LogUploadStatus.NETWORK_ERROR to 3,
            LogUploadStatus.HTTP_SERVER_ERROR to 3
        )

        const val MAX_DELAY_MS = 5000L // 5 seconds
        const val MIN_DELAY_MS = 1000L // 1 second
        const val DELAY_PERCENT = 90 // as 90 percent of
        val TAG = "LogUploadRunnable"
    }
}
