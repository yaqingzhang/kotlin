// PROBLEM: none
// WITH_RUNTIME
// ERROR: Unsupported [suspend operator "setValue"]

import kotlin.reflect.KProperty

<caret>suspend fun bar(): String {
    var x: String by Delegate()
    x = "Hello"
    return x
}

class Delegate {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String = ""

    suspend operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {}
}
