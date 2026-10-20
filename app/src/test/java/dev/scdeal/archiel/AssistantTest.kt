package dev.scdeal.archiel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTest {
    @Test
    fun `the watched run announces`() {
        assertTrue(isWatchedRun("session.execution.succeeded", "s1", "s1"))
    }

    @Test
    fun `another session stays quiet`() {
        assertFalse(isWatchedRun("session.execution.succeeded", "s2", "s1"))
    }

    @Test
    fun `another event stays quiet`() {
        assertFalse(isWatchedRun("session.execution.started", "s1", "s1"))
    }

    @Test
    fun `no watched session stays quiet`() {
        assertFalse(isWatchedRun("session.execution.succeeded", "s1", null))
    }
}
