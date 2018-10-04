// FIX: Change type to mutable
// WITH_RUNTIME
fun toMutableMap() {
    var map = foo()
    map <caret>+= 3 to 4
}

fun foo() = mapOf(1 to 2)