
package kotlinx.coroutines.experimental

import org.junit.*
import org.junit.Test
import kotlin.coroutines.experimental.*
import kotlin.test.*

@Suppress("RedundantAsync")
class ThreadLocalTest : TestBase() {
    private val stringThreadLocal = ThreadLocal<String?>()
    private val intThreadLocal = ThreadLocal<Int?>()
    private val executor = newFixedThreadPoolContext(1, "threadLocalTest")

    @After
    fun tearDown() {
        executor.close()
    }

    @Test
    fun testThreadLocal() = runTest {
        assertNull(stringThreadLocal.get())
        val deferred = async(CommonPool + stringThreadLocal.asContextElement("value")) {
            assertEquals("value", stringThreadLocal.get())
            withContext(executor) {
                assertEquals("value", stringThreadLocal.get())
            }
            assertEquals("value", stringThreadLocal.get())
        }

        assertNull(stringThreadLocal.get())
        deferred.await()
        assertNull(stringThreadLocal.get())
    }

    @Test
    fun testThreadLocalInitialValue() = runTest {
        intThreadLocal.set(42)
        val deferred = async(CommonPool + intThreadLocal.asContextElement(239)) {
            assertEquals(239, intThreadLocal.get())
            withContext(executor) {
                assertEquals(239, intThreadLocal.get())
            }
            assertEquals(239, intThreadLocal.get())
        }

        deferred.await()
        assertEquals(42, intThreadLocal.get())
    }

    @Test
    fun testMultipleThreadLocals() = runTest {
        stringThreadLocal.set("test")
        intThreadLocal.set(314)

        val deferred = async(CommonPool
                + intThreadLocal.asContextElement(value = 239) + stringThreadLocal.asContextElement(value = "pew")) {
            assertEquals(239, intThreadLocal.get())
            assertEquals("pew", stringThreadLocal.get())

            withContext(executor) {
                assertEquals(239, intThreadLocal.get())
                assertEquals("pew", stringThreadLocal.get())
            }

            assertEquals(239, intThreadLocal.get())
            assertEquals("pew", stringThreadLocal.get())
        }

        deferred.await()
        assertEquals(314, intThreadLocal.get())
        assertEquals("test", stringThreadLocal.get())
    }

    @Test
    fun testConflictingThreadLocals() = runTest {
        intThreadLocal.set(42)

        val deferred = async(CommonPool
                + intThreadLocal.asContextElement(1)) {
            assertEquals(1, intThreadLocal.get())

            withContext(executor + intThreadLocal.asContextElement(42)) {
                assertEquals(42, intThreadLocal.get())
            }

            assertEquals(1, intThreadLocal.get())

            val deferred = async(coroutineContext + intThreadLocal.asContextElement(53)) {
                assertEquals(53, intThreadLocal.get())
            }

            deferred.await()
            assertEquals(1, intThreadLocal.get())

            val deferred2 = async(executor) {
                assertNull(intThreadLocal.get())
            }

            deferred2.await()
            assertEquals(1, intThreadLocal.get())
        }

        deferred.await()
        assertEquals(42, intThreadLocal.get())
    }

    @Test
    fun testThreadLocalModification() = runTest {
        stringThreadLocal.set("main")

        val deferred = async(CommonPool
                + stringThreadLocal.asContextElement("initial")) {
            assertEquals("initial", stringThreadLocal.get())

            stringThreadLocal.set("overridden") // <- this value is not reflected in the context, so it's not restored

            withContext(executor + stringThreadLocal.asContextElement("ctx")) {
                assertEquals("ctx", stringThreadLocal.get())
            }

            val deferred = async(coroutineContext + stringThreadLocal.asContextElement("async")) {
                assertEquals("async", stringThreadLocal.get())
            }

            deferred.await()
            assertEquals("initial", stringThreadLocal.get()) // <- not restored
        }

        deferred.await()
        assertEquals("main", stringThreadLocal.get())
    }



    private data class Counter(var cnt: Int)
    private val myCounterLocal = ThreadLocal<Counter>()

    @Test
    fun testThreadLocalModificationMutableBox() = runTest {
        myCounterLocal.set(Counter(42))

        val deferred = async(CommonPool
                + myCounterLocal.asContextElement(Counter(0))) {
            assertEquals(0, myCounterLocal.get().cnt)

            // Mutate
            myCounterLocal.get().cnt = 71

            withContext(executor + myCounterLocal.asContextElement(Counter(-1))) {
                assertEquals(-1, myCounterLocal.get().cnt)
                ++myCounterLocal.get().cnt
            }

            val deferred = async(coroutineContext + myCounterLocal.asContextElement(Counter(31))) {
                assertEquals(31, myCounterLocal.get().cnt)
                ++myCounterLocal.get().cnt
            }

            deferred.await()
            assertEquals(71, myCounterLocal.get().cnt)
        }

        deferred.await()
        assertEquals(42, myCounterLocal.get().cnt)
    }

    @Test
    fun testWithContext() = runTest {
        expect(1)
        newSingleThreadContext("withContext").use {
            val data = 42
            async(CommonPool + intThreadLocal.asContextElement(42)) {

                assertSame(data, intThreadLocal.get())
                expect(2)

                async(it + intThreadLocal.asContextElement(31)) {
                    assertEquals(31, intThreadLocal.get())
                    expect(3)
                }.await()

                withContext(it + intThreadLocal.asContextElement(2)) {
                    assertSame(2, intThreadLocal.get())
                    expect(4)
                }

                async(it) {
                    assertNull(intThreadLocal.get())
                    expect(5)
                }.await()

                expect(6)
            }.await()
        }

        finish(7)
    }
}
