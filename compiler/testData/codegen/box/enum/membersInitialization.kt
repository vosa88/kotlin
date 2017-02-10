// WITH_RUNTIME
enum class X {
    A {
        val K = "K"
        val O = "O".let { it + "" }
        val OK = O.letNoInline { it + K }
        override fun toString() = OK
    };
}

public fun <T, R> T.letNoInline(block: (T) -> R): R = block(this)

fun box() = "${X.A}"
