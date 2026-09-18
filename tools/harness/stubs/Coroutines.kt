package kotlinx.coroutines
import kotlin.coroutines.CoroutineContext
interface CoroutineScope
interface Job : CoroutineContext.Element
fun CoroutineScope(context: CoroutineContext): CoroutineScope = TODO()
fun SupervisorJob(): Job = TODO()
object Dispatchers { val Default: CoroutineContext = TODO()
    val IO: CoroutineContext = TODO() }
suspend fun <T> withContext(context: CoroutineContext, block: suspend CoroutineScope.() -> T): T = TODO()
fun CoroutineScope.launch(block: suspend CoroutineScope.() -> Unit): Job = TODO()
