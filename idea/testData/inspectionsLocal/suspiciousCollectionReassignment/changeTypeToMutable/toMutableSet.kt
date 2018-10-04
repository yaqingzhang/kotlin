// FIX: Change type to mutable
// WITH_RUNTIME
fun test() {
    var set = foo()
    set <caret>+= 1
}

fun foo() = setOf(1)