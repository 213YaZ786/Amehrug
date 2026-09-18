import com.amehrug.app.diagnostics.*
import java.time.ZoneId

fun check(ok: Boolean, what: String) {
    if (!ok) throw AssertionError(what)
    println("ok  $what")
}

fun main() {
    var now = 1_700_000_000_000L
    var nanos = 0L
    val log = DiagnosticLog(capacity = 3, clock = { now }, nanoClock = { nanos })
    log.info("start", "one")
    log.info("start", "two\nlines")
    log.warn("x", "three")
    log.error("x", "four")
    val snap = log.snapshot()
    check(snap.size == 3, "capacity keeps 3")
    check(snap.first().message == "two lines", "oldest dropped and newline flattened")
    val r = log.time("t", "work") {
        nanos += 42_000_000
        "done"
    }
    check(r == "done", "time returns block value")
    check(log.snapshot().last().message == "work 42 ms", "time logs elapsed ms")
    val secret = IllegalStateException("SECRET note text", RuntimeException("SECRET cause"))
    log.error("crash", "while saving", secret)
    val last = log.snapshot().last().message
    check(!last.contains("SECRET"), "exception messages never logged")
    check(last.contains("java.lang.IllegalStateException") && last.contains("Caused by: java.lang.RuntimeException"), "classes and cause kept")
    check(last.contains("  at "), "frames kept")
    val a = RuntimeException("a")
    val b = RuntimeException("b", a)
    a.initCause(b)
    check(DiagnosticLog.describe(a).lines().count { it.startsWith("Caused by") } == 1, "cause cycle stops")
    val text = log.export(listOf("Amehrug 0.1.3"), ZoneId.of("UTC"))
    check(text.startsWith("Amehrug 0.1.3\n\n2023-11-14 22:13:20.000 ERROR x: four\n2023-11-14 22:13:20.000 INFO  t: work 42 ms"), "export format")
    check(text.endsWith("\n"), "export ends with newline")
    check(DiagnosticLog.oneLine("y".repeat(1000)).length == 300, "line truncated")
    log.clear()
    check(log.snapshot().isEmpty(), "clear")
    println("ALL PASSED")
}
