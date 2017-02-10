// !DIAGNOSTICS: -UNUSED_PARAMETER
// SKIP_TXT
// FILE: Outer.kt
package abc
class Outer {
    inner class Inner() {
        constructor(x: Int) : this() {}
    }

    companion object {
        fun Inner(x: String) {}

        fun baz() {
            <!INNER_CLASS_CONSTRUCTOR_NO_RECEIVER!>Inner<!>()
            <!INNER_CLASS_CONSTRUCTOR_NO_RECEIVER!>Inner<!>(1)
            Inner("")
        }
    }
}

fun foo() {
    Outer.<!INNER_CLASS_CONSTRUCTOR_NO_RECEIVER!>Inner<!>()
    Outer.<!INNER_CLASS_CONSTRUCTOR_NO_RECEIVER!>Inner<!>(1)
    Outer.Inner("")
}

// FILE: imported.kt
import abc.Outer
import abc.Outer.Inner
import abc.Outer.Companion.Inner

fun bar() {
    <!INNER_CLASS_CONSTRUCTOR_NO_RECEIVER!>Inner<!>()
    <!INNER_CLASS_CONSTRUCTOR_NO_RECEIVER!>Inner<!>(1)
    Inner("")

    with(Outer()) {
        Inner()
        Inner(1)
        Inner("")
    }
}
