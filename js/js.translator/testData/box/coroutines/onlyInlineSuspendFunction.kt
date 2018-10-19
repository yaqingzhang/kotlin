// IGNORE_BACKEND: JS_IR
// EXPECTED_REACHABLE_NODES: 1280

// MODULE: lib
// FILE: lib.kt
import kotlin.coroutines.*

var resume: () -> Unit = {}

suspend fun suspendAndReturn(msg: String): String {
    return suspendCoroutine<String> { cont ->
        resume = {
            cont.resume(msg)
        }
    }
}

// MODULE: libInline(lib)
// FILE: libInline.kt

suspend inline fun foo(): String {
    return suspendAndReturn("O") + suspendAndReturn("K")
}

// MODULE: main(libInline, lib)
// FILE: main.kt

import kotlin.coroutines.*

fun box(): String {
    var testResult: String = "fail"

    val continuation = Continuation<String>(EmptyCoroutineContext) { result ->
        testResult = result.getOrThrow()
    }

    // Test the noinline version of public inline suspend functions
    js("libInline").foo(continuation)
    resume()
    resume()

    return testResult
}