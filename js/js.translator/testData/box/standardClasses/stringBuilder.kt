package foo


fun box(): String {
    val arr = arrayOf("aa", 1, null, charArrayOf('d'))
    assertEquals("[aa, 1, null, [d]]", arr.contentDeepToString())

    val s = StringBuilder()
    s.append("a")
    s.append("b").append("c")
    s.append('d').append("e")

    if (s.toString() != "abcde") return s.toString()
    return "OK"
}