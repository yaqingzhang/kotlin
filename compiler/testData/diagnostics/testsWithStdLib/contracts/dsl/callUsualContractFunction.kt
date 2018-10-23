// !DIAGNOSTICS: -UNUSED_PARAMETER

object F {
    fun contract(block: () -> Unit) {}
    fun contract(i: Int) {}
    fun contract() {}

    fun test() {
        this.contract()
        contract()
        contract(42)
        contract {}
    }
}
