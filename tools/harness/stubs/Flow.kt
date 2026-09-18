package kotlinx.coroutines.flow
// Compile only stub.
interface Flow<out T>
interface StateFlow<out T> : Flow<T> { val value: T }
class MutableStateFlow<T>(initial: T) : StateFlow<T> { override var value: T = initial }
fun <T, R> Flow<T>.map(transform: suspend (T) -> R): Flow<R> = TODO()
fun <T> flowOf(vararg values: T): Flow<T> = TODO()
