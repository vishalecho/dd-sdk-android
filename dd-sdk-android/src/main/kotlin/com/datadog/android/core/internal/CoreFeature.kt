/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.datadog.android.DatadogConfig
import com.datadog.android.DatadogEndpoint
import com.datadog.android.core.internal.net.GzipRequestInterceptor
import com.datadog.android.core.internal.net.info.BroadcastReceiverNetworkInfoProvider
import com.datadog.android.core.internal.net.info.CallbackNetworkInfoProvider
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.net.info.NoOpNetworkInfoProvider
import com.datadog.android.core.internal.system.BroadcastReceiverSystemInfoProvider
import com.datadog.android.core.internal.system.NoOpSystemInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.time.KronosTimeProvider
import com.datadog.android.core.internal.time.LoggingSyncListener
import com.datadog.android.core.internal.time.NoOpTimeProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.DatadogUserInfoProvider
import com.datadog.android.log.internal.user.MutableUserInfoProvider
import com.datadog.android.log.internal.user.NoOpMutableUserInfoProvider
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol

internal object CoreFeature {

    // region Constants

    internal val NETWORK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(45)
    private val THREAD_POOL_MAX_KEEP_ALIVE_MS = TimeUnit.SECONDS.toMillis(5)
    private const val CORE_DEFAULT_POOL_SIZE = 1 // Only one thread will be kept alive

    // endregion

    internal val initialized = AtomicBoolean(false)
    internal var contextRef: WeakReference<Context?> = WeakReference(null)
    internal var networkInfoProvider: NetworkInfoProvider = NoOpNetworkInfoProvider()
    internal var systemInfoProvider: SystemInfoProvider = NoOpSystemInfoProvider()
    internal var timeProvider: TimeProvider = NoOpTimeProvider()

    internal var userInfoProvider: MutableUserInfoProvider = NoOpMutableUserInfoProvider()

    internal var okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
    internal lateinit var kronosClock: KronosClock

    internal var packageName: String = ""
    internal var packageVersion: String = ""
    internal var serviceName: String = ""
    internal var isMainProcess: Boolean = true

    internal lateinit var dataUploadScheduledExecutor: ScheduledThreadPoolExecutor
    internal lateinit var dataPersistenceExecutorService: ExecutorService

    fun initialize(
        appContext: Context,
        config: DatadogConfig.CoreConfig
    ) {
        if (initialized.get()) {
            return
        }

        kronosClock = AndroidClockFactory.createKronosClock(
            appContext,
            ntpHosts = listOf(
                DatadogEndpoint.NTP_0,
                DatadogEndpoint.NTP_1,
                DatadogEndpoint.NTP_2,
                DatadogEndpoint.NTP_3
            ),
            cacheExpirationMs = TimeUnit.MINUTES.toMillis(30),
            minWaitTimeBetweenSyncMs = TimeUnit.MINUTES.toMillis(5),
            syncListener = LoggingSyncListener()
        ).apply { syncInBackground() }

        serviceName = config.serviceName ?: appContext.packageName
        contextRef = WeakReference(appContext)
        isMainProcess = resolveIsMainProcess(appContext)

        readApplicationInformation(appContext)

        setupInfoProviders(appContext)

        setupOkHttpClient(config.needsClearTextHttp)
        dataUploadScheduledExecutor = ScheduledThreadPoolExecutor(CORE_DEFAULT_POOL_SIZE)
        dataPersistenceExecutorService =
            ThreadPoolExecutor(
                CORE_DEFAULT_POOL_SIZE,
                Runtime.getRuntime().availableProcessors(),
                THREAD_POOL_MAX_KEEP_ALIVE_MS,
                TimeUnit.MILLISECONDS,
                LinkedBlockingDeque()
            )
        initialized.set(true)
    }

    fun stop() {
        if (initialized.get()) {
            contextRef.get()?.let {
                networkInfoProvider.unregister(it)
                systemInfoProvider.unregister(it)
            }
            contextRef.clear()

            timeProvider = NoOpTimeProvider()
            systemInfoProvider = NoOpSystemInfoProvider()
            networkInfoProvider = NoOpNetworkInfoProvider()
            userInfoProvider = NoOpMutableUserInfoProvider()
            serviceName = ""
            packageName = ""
            packageVersion = ""
            shutDownExecutors()
            initialized.set(false)
        }
    }

    // region Internal

    private fun shutDownExecutors() {
        dataUploadScheduledExecutor.shutdownNow()
        dataPersistenceExecutorService.shutdownNow()
    }

    @Suppress("DEPRECATION")
    private fun readApplicationInformation(
        appContext: Context
    ) {
        packageName = appContext.packageName
        packageVersion = appContext.packageManager.getPackageInfo(packageName, 0).let {
            it.versionName ?: it.versionCode.toString()
        }
    }

    private fun setupInfoProviders(appContext: Context) {
        // Time Provider
        timeProvider = KronosTimeProvider(kronosClock)

        // System Info Provider
        systemInfoProvider = BroadcastReceiverSystemInfoProvider()
        systemInfoProvider.register(appContext)

        // Network Info Provider
        networkInfoProvider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            CallbackNetworkInfoProvider()
        } else {
            BroadcastReceiverNetworkInfoProvider()
        }
        networkInfoProvider.register(appContext)

        // User Info Provider
        userInfoProvider = DatadogUserInfoProvider()
    }

    private fun setupOkHttpClient(needsClearTextHttp: Boolean) {
        val connectionSpec = when {
            needsClearTextHttp -> ConnectionSpec.CLEARTEXT
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> ConnectionSpec.RESTRICTED_TLS
            else -> ConnectionSpec.MODERN_TLS
        }

        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(GzipRequestInterceptor())
            .callTimeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionSpecs(listOf(connectionSpec))
            .build()
    }

    private fun resolveIsMainProcess(appContext: Context): Boolean {
        val currentProcessId = Process.myPid()
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val currentProcess = manager?.runningAppProcesses?.firstOrNull {
            it.pid == currentProcessId
        }
        return if (currentProcess == null) {
            true
        } else {
            appContext.packageName == currentProcess.processName
        }
    }

    // endregion
}
