/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.realm

import com.datadog.android.fresco.utils.Configurator
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.tools.unit.getStaticValue
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.realm.Realm
import java.lang.RuntimeException
import java.util.concurrent.atomic.AtomicBoolean
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatadogRealmExtTest {

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Forgery
    lateinit var fakeRuntimeException: RuntimeException

    @Mock
    lateinit var testMockedRealm: Realm

    @RegexForgery("[a-z]{1,20}/[a-z]{1,20}")
    lateinit var fakeDatabasePath: String

    @LongForgery
    var fakeDatabaseVersion: Long = 0

    @BeforeEach
    fun `set up`() {
        whenever(testMockedRealm.path).thenReturn(fakeDatabasePath)
        whenever(testMockedRealm.version).thenReturn(fakeDatabaseVersion)
        GlobalRum.registerIfAbsent(mockRumMonitor)
    }

    @AfterEach
    fun `tear down`() {
        val isRegistered: AtomicBoolean = GlobalRum::class.java.getStaticValue("isRegistered")
        isRegistered.set(false)
    }

    @Test
    @Throws
    fun `M send an error event W exception in Realm transaction`(forge: Forge) {
        // GIVEN
        whenever(testMockedRealm.commitTransaction()).thenThrow(fakeRuntimeException)
        var caughtException: Exception? = null
        // WHEN
        try {
            testMockedRealm.useWithRum {
                it.beginTransaction()
                it.commitTransaction()
            }
        } catch (e: Exception) {
            caughtException = e
        }

        // THEN
        assertThat(fakeRuntimeException).isEqualTo(caughtException)
        verify(mockRumMonitor).addError(
            REALM_ERROR_MESSAGE,
            RumErrorSource.SOURCE,
            fakeRuntimeException,
            mapOf(
                RumAttributes.ERROR_DATABASE_PATH to fakeDatabasePath,
                RumAttributes.ERROR_DATABASE_VERSION to fakeDatabaseVersion
            )
        )
    }

    @Test
    fun `M close the Realm instance W exception in Realm transaction`(forge: Forge) {
        // GIVEN
        whenever(testMockedRealm.commitTransaction()).thenThrow(fakeRuntimeException)
        var caughtException: Exception? = null
        // WHEN
        try {
            testMockedRealm.useWithRum {
                it.beginTransaction()
                it.commitTransaction()
            }
        } catch (e: Exception) {
            caughtException = e
        }

        // THEN
        assertThat(fakeRuntimeException).isEqualTo(caughtException)
        inOrder(testMockedRealm).apply {
            verify(testMockedRealm).beginTransaction()
            verify(testMockedRealm).commitTransaction()
            verify(testMockedRealm).close()
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M close the Realm instance W no exception in Realm transaction`(forge: Forge) {
        // WHEN
        testMockedRealm.useWithRum {
            it.beginTransaction()
            it.commitTransaction()
        }

        // THEN
        inOrder(testMockedRealm).apply {
            verify(testMockedRealm).beginTransaction()
            verify(testMockedRealm).commitTransaction()
            verify(testMockedRealm).close()
            verifyNoMoreInteractions()
        }
    }
}
