fun main() {
    fun square(value: Int): Int = value * value
    val values = (1..5).map(::square)
    println("Hello from real Kotlin!")
    println(values.joinToString())
    check(values.sum() == 55)
    println("KSTUDIO_CONSOLE_OK")
}
