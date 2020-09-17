/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.realm

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import io.realm.Realm
import java.io.Closeable
internal const val REALM_ERROR_MESSAGE = "Error while executing Realm transaction"

/**
 * Executes the given [block] function on this [Realm] instance
 * and then closes it down correctly whether an exception
 * is thrown or not.
 * In case the [block] will throw any exception this will be intercepted and propagated as
 * a Rum error event.
 * @param block a function to process this [Closeable] resource.
 * @return the result of [block] function invoked on this resource.
 */
@Suppress("TooGenericExceptionCaught")
fun <R> Realm.useWithRum(block: (Realm) -> R): R {
    try {
        return block(this)
    } catch (e: Throwable) {
        handleError(this, e)
        throw e
    } finally {
        try {
            close()
        } catch (closeException: Throwable) {
            handleError(this, closeException)
        }
    }
}

private fun handleError(realm: Realm, throwable: Throwable) {
    GlobalRum.get().addError(
        REALM_ERROR_MESSAGE, RumErrorSource.SOURCE, throwable, mapOf(
            RumAttributes.ERROR_DATABASE_PATH to realm.path,
            RumAttributes.ERROR_DATABASE_VERSION to realm.version
        )
    )
}
