package com.amehrug.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * When the app must ask again, and the setting row that lets the answer
 * survive the process being killed.
 */
class LockPolicyTest {

    private val on = AppSettings(lockEnabled = true, lockTimeoutSeconds = 900)

    @Test
    fun `a lock that is off never locks`() {
        assertFalse(LockPolicy.shouldLock(AppSettings(lockEnabled = false), null, 1_000))
    }

    /** A process that never went away has nothing to go on, so it locks. */
    @Test
    fun `no departure time locks`() {
        assertTrue(LockPolicy.shouldLock(on, null, 1_000))
    }

    @Test
    fun `inside the window the app stays open`() {
        assertFalse(LockPolicy.shouldLock(on, 1_000, 1_000 + 899_000))
    }

    @Test
    fun `at the window and past it the app locks`() {
        assertTrue(LockPolicy.shouldLock(on, 1_000, 1_000 + 900_000))
        assertTrue(LockPolicy.shouldLock(on, 1_000, 1_000 + 900_001))
    }

    /**
     * Rebooting resets the clock this counts on, so a time written before
     * the reboot reads as being in the future. That has to lock.
     */
    @Test
    fun `a clock that went backwards locks`() {
        assertTrue(LockPolicy.shouldLock(on, 500_000, 1_000))
    }

    @Test
    fun `immediately means at once`() {
        val immediate = AppSettings(lockEnabled = true, lockTimeoutSeconds = 0)
        assertTrue(LockPolicy.shouldLock(immediate, 1_000, 1_000))
    }

    @Test
    fun `the departure time survives a round trip`() {
        val written = SettingsCodec.encodeLockLeftAt(4_242L).second
        assertEquals(4_242L, SettingsCodec.decodeLockLeftAt(written))
    }

    @Test
    fun `clearing the departure time reads as never`() {
        assertNull(SettingsCodec.decodeLockLeftAt(SettingsCodec.encodeLockLeftAt(null).second))
        assertNull(SettingsCodec.decodeLockLeftAt(null))
        assertNull(SettingsCodec.decodeLockLeftAt("nope"))
        assertNull(SettingsCodec.decodeLockLeftAt("-5"))
    }

    /** It is a row in the settings table, not a setting anything can show. */
    @Test
    fun `the departure time is not part of the settings`() {
        assertEquals(
            AppSettings(),
            SettingsCodec.decode(mapOf(SettingsCodec.LOCK_LEFT_AT to "7")),
        )
    }

    @Test
    fun `a damaged dock order is repaired rather than trusted`() {
        val order = SettingsCodec.decodeDockOrder("NOTE,NOPE,NOTE,SEARCH")
        assertEquals(DockItem.entries.size, order.size)
        assertEquals(DockItem.NOTE, order.first())
        assertEquals(DockItem.entries.toSet(), order.toSet())
    }

    @Test
    fun `an unknown stored value falls back to the default`() {
        assertEquals(AppSettings().lockMethod, SettingsCodec.decodeLockMethod("SOMETHING"))
        assertEquals(AppSettings().noteTimestamp, SettingsCodec.decodeNoteTimestamp(null))
        assertEquals(
            AppSettings().lockTimeoutSeconds,
            SettingsCodec.decode(mapOf(SettingsCodec.LOCK_TIMEOUT to "77")).lockTimeoutSeconds,
        )
    }
}
