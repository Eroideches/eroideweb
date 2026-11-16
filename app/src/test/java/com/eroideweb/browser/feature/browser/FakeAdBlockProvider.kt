package com.eroideweb.browser.feature.browser

import com.eroideweb.browser.core.adblock.AdBlockEngine
import com.eroideweb.browser.core.adblock.AdBlockProvider

/** Engine già pronto (o assente) per i test del ViewModel. */
class FakeAdBlockProvider(
    private val engine: AdBlockEngine? = null,
) : AdBlockProvider {
    override fun engineOrNull(): AdBlockEngine? = engine
}
