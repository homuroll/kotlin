// WITH_RUNTIME
// INTENTION_TEXT: Remove @ from annotation argument

annotation class X(val value: Array<Y>)
annotation class Y()

@X(arrayOf(Y(), @Y()<caret>))
fun foo() {
}