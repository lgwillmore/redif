package codes.laurence.redif

/**
 * Redis refused a command. [code] is the first word of Redis's error reply, which names the kind of
 * error and is stable across Redis versions: `WRONGTYPE` for a command against a key holding
 * another type, and `ERR` for most of the rest. The message is Redis's own wording and is not part
 * of the contract: branch on the code.
 */
class RedifCommandException(val code: String, message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {

  companion object {
    const val ERR = "ERR"
    const val WRONGTYPE = "WRONGTYPE"
  }
}
