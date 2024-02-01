package lang.taxi.logging

/**
 * A simple interface for providing updates about download progress
 * back to tooling.
 *
 * Not intended to be a full log implementation.
 */
interface MessageLogger {
   fun info(message: String)
   fun error(message: String)
   fun warn(message: String)
}
