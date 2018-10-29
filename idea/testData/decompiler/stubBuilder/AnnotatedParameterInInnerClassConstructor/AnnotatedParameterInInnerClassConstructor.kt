package test

annotation class Anno(val x: String)

class AnnotatedParameterInInnerClassConstructor {

    inner class Inner(@Anno("a") a: String, @Anno("b") b: String) {

    }
}