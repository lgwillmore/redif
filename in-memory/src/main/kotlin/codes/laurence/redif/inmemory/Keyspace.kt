package codes.laurence.redif.inmemory

import codes.laurence.redif.RedifCommandException
import java.util.TreeSet
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The keys of a [RedifInMemory]: each holds one value of one type, with the key's expiry beside it,
 * so expiry and deletion are the same for every type. Everything but [locked] is to be called
 * inside it.
 */
internal class Keyspace(private val clock: Clock) {

  val mutex = Mutex()
  private val entries = LinkedHashMap<String, Entry>()

  class Entry(val value: Value, var expireAt: Instant?)

  /** Runs one command, atomically. */
  suspend inline fun <T> locked(block: Keyspace.() -> T): T = mutex.withLock { block() }

  fun now(): Instant = clock.now()

  fun after(millis: Long): Instant = now() + millis.milliseconds

  /** The key's entry, or null when it has none or has expired. */
  fun find(key: String): Entry? {
    val entry = entries[key] ?: return null
    val expireAt = entry.expireAt
    if (expireAt != null && expireAt <= now()) {
      entries.remove(key)
      return null
    }
    return entry
  }

  /** The key's value as a [T]: null for no key, and `WRONGTYPE` for a key of another type. */
  inline fun <reified T : Value> typed(key: String): T? {
    val value = find(key)?.value ?: return null
    return value as? T ?: throw wrongType()
  }

  /** As [typed], creating the value, with no expiry, when the key has none. */
  inline fun <reified T : Value> typedOrPut(key: String, create: () -> T): T =
      typed<T>(key) ?: create().also { put(key, it) }

  fun put(key: String, value: Value, expireAt: Instant? = null) {
    entries[key] = Entry(value, expireAt)
  }

  fun remove(key: String): Boolean = find(key) != null && entries.remove(key) != null

  /** Redis keeps no empty hash, set or sorted set. */
  fun removeIfEmpty(key: String, value: Value) {
    if (value.isEmpty()) entries.remove(key)
  }

  /** Every key that has not expired. */
  fun keys(): List<String> = entries.keys.toList().filter { find(it) != null }
}

internal fun wrongType() =
    RedifCommandException(
        RedifCommandException.WRONGTYPE,
        "WRONGTYPE Operation against a key holding the wrong kind of value",
    )

internal fun err(message: String) = RedifCommandException(RedifCommandException.ERR, "ERR $message")

/** What a key holds. [type] is what Redis's `TYPE` calls it. */
internal sealed interface Value {
  val type: String

  /** Whether this is a collection Redis would remove the key of. */
  fun isEmpty(): Boolean = false
}

internal class StringValue(var text: String) : Value {
  override val type = "string"
}

internal class HashValue : Value {
  val fields = LinkedHashMap<String, String>()
  override val type = "hash"

  override fun isEmpty() = fields.isEmpty()
}

internal class SetValue : Value {
  val members = LinkedHashSet<String>()
  override val type = "set"

  override fun isEmpty() = members.isEmpty()
}

internal class SortedSetValue : Value {
  val scores = HashMap<String, Double>()

  /** The members in Redis's order: by score, then by member byte-wise. */
  val ordered = TreeSet<Scored>()
  override val type = "zset"

  class Scored(val member: String, val score: Double) : Comparable<Scored> {
    private val bytes = member.toByteArray()

    override fun compareTo(other: Scored): Int {
      val byScore = score.compareTo(other.score)
      return if (byScore != 0) byScore else java.util.Arrays.compareUnsigned(bytes, other.bytes)
    }
  }

  fun put(member: String, score: Double) {
    // Redis has one zero; `compareTo` would order -0.0 before 0.0.
    val normal = score + 0.0
    drop(member)
    scores[member] = normal
    ordered.add(Scored(member, normal))
  }

  fun drop(member: String): Boolean {
    val score = scores.remove(member) ?: return false
    ordered.remove(Scored(member, score))
    return true
  }

  override fun isEmpty() = scores.isEmpty()
}

/** Redis keeps a HyperLogLog in a string, so that is its type; this one counts exactly. */
internal class HyperLogLogValue : Value {
  val elements = HashSet<String>()
  override val type = "string"
}

/** Redis's glob, as `KEYS` and `SCAN` match: `*`, `?`, `[abc]`, `[a-z]`, `[^a]`. */
internal fun globToRegex(pattern: String): Regex {
  val regexStr = buildString {
    var i = 0
    while (i < pattern.length) {
      val char = pattern[i]
      when (char) {
        '*' -> append(".*")
        '?' -> append(".")
        '[' -> {
          append("[")
          i++
          if (i < pattern.length && pattern[i] == '^') {
            append("^")
            i++
          }
          while (i < pattern.length && pattern[i] != ']') {
            if (pattern[i] == '\\' && i + 1 < pattern.length) {
              append("\\")
              i++
            }
            append(pattern[i])
            i++
          }
          append("]")
        }
        '\\' -> {
          // An escaped character matches itself.
          if (i + 1 < pattern.length) i++
          append(Regex.escape(pattern[i].toString()))
        }
        '.',
        '(',
        ')',
        '+',
        '{',
        '}',
        '|',
        '^',
        '$' -> {
          append("\\")
          append(char)
        }
        else -> append(char)
      }
      i++
    }
  }
  return Regex("^$regexStr$", RegexOption.DOT_MATCHES_ALL)
}
