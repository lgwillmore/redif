package codes.laurence.redif.compatibility

import codes.laurence.redif.RedifAPI
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.coroutines.runBlocking

/**
 * The base of every contract suite: one suite for each command group, each holding the cases that
 * group's commands must pass on any implementation. See [RedifHarness] for how an implementation
 * runs them.
 */
abstract class RedifContract(private val harness: RedifHarness) {

  protected val client: RedifAPI by lazy { harness.client() }

  @BeforeTest
  fun harnessBeforeEach() {
    runBlocking { harness.beforeEach(client) }
  }

  @AfterTest
  fun harnessAfterEach() {
    runBlocking { harness.afterEach(client) }
  }

  /** A string no other case will make, for keys, members and values. */
  protected fun randString() = "GENERATED${Random.nextLong().absoluteValue}"
}
