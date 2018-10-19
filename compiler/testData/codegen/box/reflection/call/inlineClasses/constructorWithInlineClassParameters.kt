// IGNORE_BACKEND: JS_IR, JS, NATIVE, JVM_IR
// WITH_REFLECT
import kotlin.test.assertEquals

inline class Z(val x: Int)

class Outer(val z1: Z) {
    inner class Inner(val z2: Z)
}

fun box(): String {
    assertEquals(Z(1), ::Outer.call(Z(1)).z1)
    assertEquals(Z(2), Outer::Inner.call(Outer(Z(1)), Z(2)).z2)
    assertEquals(Z(3), Outer(Z(1))::Inner.call(Z(3)).z2)

    return "OK"
}