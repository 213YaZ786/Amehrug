package net.zetetic.database
// Compile only stub.
interface LogTarget
class NoopTarget : LogTarget
object Logger { @JvmStatic fun setTarget(target: LogTarget) {} }
