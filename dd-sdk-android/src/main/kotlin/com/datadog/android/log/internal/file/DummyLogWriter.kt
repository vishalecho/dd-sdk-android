package com.datadog.android.log.internal.file

import com.datadog.android.log.internal.DataStorageCallback
import com.datadog.android.log.internal.Log
import com.datadog.android.log.internal.LogWriter

/**
 * Dummy Log Writer.
 */
internal class DummyLogWriter : LogWriter {
    override fun writeLog(log: Log) {
        // NO-OP
    }

    override fun setCallback(callback: DataStorageCallback) {
        // NO-OP
    }

    override fun removeCallback() {
        // NO-OP
    }
}
