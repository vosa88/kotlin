package soSuspendableCall

import forTests.builder
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

private fun foo(a: Any) {}

fun main(args: Array<String>) {
    builder {
        //Breakpoint!
        run()
        foo("End")
    }
}

suspend fun run() {
    suspendCoroutine { cont: Continuation<Unit> ->
        Thread {
            cont.resume(Unit)
            Thread.sleep(10)
        }.start()
    }
}