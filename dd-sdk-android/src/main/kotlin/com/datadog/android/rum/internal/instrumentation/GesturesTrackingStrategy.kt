/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation

import android.app.Activity
import com.datadog.android.rum.ActivityLifecycleTrackingStrategy
import com.datadog.android.rum.UserActionTrackingStrategy
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker

internal class GesturesTrackingStrategy(private val gesturesTracker: GesturesTracker) :
    ActivityLifecycleTrackingStrategy(), UserActionTrackingStrategy {
    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        gesturesTracker.startTracking(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        gesturesTracker.stopTracking(activity)
    }
}
