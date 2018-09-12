// PROBLEM: none
// WITH_RUNTIME
// ERROR: Unsupported [suspend operator "getValue"]

import kotlin.reflect.KProperty

<caret>suspend fun bar(): String {
    val x: String by Delegate()
    return x
}

class Delegate {
    suspend operator fun getValue(thisRef: Any?, property: KProperty<*>): String = ""
}
