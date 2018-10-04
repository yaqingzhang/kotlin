// FIX: Assign to local variable
// WITH_RUNTIME
fun test() {
    var map = mapOf(1 to 2)
    map -<caret>= 1 to 2
}