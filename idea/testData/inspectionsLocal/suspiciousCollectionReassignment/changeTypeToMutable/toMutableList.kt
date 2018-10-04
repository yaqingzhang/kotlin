// FIX: Change type to mutable
// WITH_RUNTIME
fun test() {
    var list = foo()
    list <caret>+= 2
}

fun foo() = listOf(1)