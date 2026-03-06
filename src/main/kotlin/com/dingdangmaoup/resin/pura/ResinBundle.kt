package com.dingdangmaoup.resin.pura

import com.intellij.DynamicBundle
import java.util.function.Supplier
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

object ResinBundle {
    @NonNls
    const val BUNDLE: String = "messages.ResinBundle"

    private val INSTANCE = DynamicBundle(ResinBundle::class.java, BUNDLE)

    @JvmStatic
    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        return INSTANCE.getMessage(key, *params)
    }

    @JvmStatic
    fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<@Nls String> {
        return INSTANCE.getLazyMessage(key, *params)
    }
}
