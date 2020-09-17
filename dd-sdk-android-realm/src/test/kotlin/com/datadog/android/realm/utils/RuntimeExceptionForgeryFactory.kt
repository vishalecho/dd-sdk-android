package com.datadog.android.fresco.utils

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.lang.RuntimeException

class RuntimeExceptionForgeryFactory :
    ForgeryFactory<RuntimeException> {
    override fun getForgery(forge: Forge): RuntimeException {
        return RuntimeException(forge.anAlphabeticalString())
    }
}
