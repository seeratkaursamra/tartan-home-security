package tartan.smarthome.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for Electronic Operation feature of the Smart Door Lock.
 *
 * Electronic Operation: If a person requests a lock or unlock operation from an
 * access panel, first check if that operation requires a passcode. If so, validate
 * the input before proceeding. If the passcode is rejected, send an appropriate
 * response. Otherwise, carry out the requested operation.
 *
 * Lock state (locked/unlocked) is separate from door state (open/closed).
 */
public class ElectronicOperationTest {

    private ElectronicOperation lock;
    private static final String LOCK_PASSCODE = "5678";

    @BeforeEach
    void setUp() {
        lock = new ElectronicOperation(LOCK_PASSCODE);
    }

    // ---- Lock starts locked by default ----

    @Test
    @DisplayName("Lock should start in locked state")
    void lockStartsLocked() {
        assertTrue(lock.isLocked(), "New lock should default to locked");
    }

    // ---- Unlock with passcode ----

    @Test
    @DisplayName("Unlock with correct passcode should succeed")
    void unlockWithCorrectPasscode() {
        String result = lock.requestUnlock(LOCK_PASSCODE);

        assertFalse(lock.isLocked(), "Lock should be unlocked after correct passcode");
        assertTrue(result.contains("unlocked"), "Access panel message should confirm unlock");
    }

    @Test
    @DisplayName("Unlock with wrong passcode should be rejected")
    void unlockWithWrongPasscode() {
        String result = lock.requestUnlock("0000");

        assertTrue(lock.isLocked(), "Lock should remain locked after wrong passcode");
        assertTrue(result.toLowerCase().contains("denied") || result.toLowerCase().contains("invalid"),
            "Access panel message should indicate rejection");
    }

    @Test
    @DisplayName("Unlock with null passcode should be rejected")
    void unlockWithNullPasscode() {
        String result = lock.requestUnlock(null);

        assertTrue(lock.isLocked(), "Lock should remain locked with null passcode");
        assertTrue(result.toLowerCase().contains("denied") || result.toLowerCase().contains("invalid"),
            "Access panel message should indicate rejection");
    }

    @Test
    @DisplayName("Unlock with empty passcode should be rejected")
    void unlockWithEmptyPasscode() {
        String result = lock.requestUnlock("");

        assertTrue(lock.isLocked(), "Lock should remain locked with empty passcode");
        assertTrue(result.toLowerCase().contains("denied") || result.toLowerCase().contains("invalid"),
            "Access panel message should indicate rejection");
    }

    // ---- Lock with passcode ----

    @Test
    @DisplayName("Lock with correct passcode should succeed")
    void lockWithCorrectPasscode() {
        lock.requestUnlock(LOCK_PASSCODE); // unlock first
        assertFalse(lock.isLocked());

        String result = lock.requestLock(LOCK_PASSCODE);

        assertTrue(lock.isLocked(), "Lock should be locked after correct passcode");
        assertTrue(result.contains("locked"), "Access panel message should confirm lock");
    }

    @Test
    @DisplayName("Lock with wrong passcode should be rejected")
    void lockWithWrongPasscode() {
        lock.requestUnlock(LOCK_PASSCODE); // unlock first
        assertFalse(lock.isLocked());

        String result = lock.requestLock("9999");

        assertFalse(lock.isLocked(), "Lock should remain unlocked after wrong passcode");
        assertTrue(result.toLowerCase().contains("denied") || result.toLowerCase().contains("invalid"),
            "Access panel message should indicate rejection");
    }

    // ---- Already in requested state ----

    @Test
    @DisplayName("Unlock when already unlocked should return appropriate message")
    void unlockWhenAlreadyUnlocked() {
        lock.requestUnlock(LOCK_PASSCODE);
        assertFalse(lock.isLocked());

        String result = lock.requestUnlock(LOCK_PASSCODE);

        assertFalse(lock.isLocked(), "Lock should remain unlocked");
        assertTrue(result.toLowerCase().contains("already"),
            "Access panel message should say already unlocked");
    }

    @Test
    @DisplayName("Lock when already locked should return appropriate message")
    void lockWhenAlreadyLocked() {
        assertTrue(lock.isLocked());

        String result = lock.requestLock(LOCK_PASSCODE);

        assertTrue(lock.isLocked(), "Lock should remain locked");
        assertTrue(result.toLowerCase().contains("already"),
            "Access panel message should say already locked");
    }

    // ---- Integration: multi-step scenarios ----

    @Test
    @DisplayName("Integration: full unlock-then-lock cycle")
    void unlockThenLock_fullCycle() {
        assertTrue(lock.isLocked(), "Should start locked");

        String unlockResult = lock.requestUnlock(LOCK_PASSCODE);
        assertFalse(lock.isLocked(), "Should be unlocked after correct passcode");
        assertTrue(unlockResult.toLowerCase().contains("unlocked"));

        String lockResult = lock.requestLock(LOCK_PASSCODE);
        assertTrue(lock.isLocked(), "Should be locked again");
        assertTrue(lockResult.toLowerCase().contains("locked"));
    }

    @Test
    @DisplayName("Integration: wrong passcode then correct passcode recovers")
    void wrongPasscodeThenCorrect_recovers() {
        String failResult = lock.requestUnlock("0000");
        assertTrue(lock.isLocked(), "Should stay locked after wrong passcode");
        assertTrue(failResult.toLowerCase().contains("denied") || failResult.toLowerCase().contains("invalid"));

        String successResult = lock.requestUnlock(LOCK_PASSCODE);
        assertFalse(lock.isLocked(), "Should unlock after correct passcode");
        assertTrue(successResult.toLowerCase().contains("unlocked"));
    }

    @Test
    @DisplayName("Integration: setLocked(false) then requestUnlock says already unlocked")
    void setLockedDirectly_thenRequestUnlock() {
        lock.setLocked(false); 
        assertFalse(lock.isLocked());

        String result = lock.requestUnlock(LOCK_PASSCODE);
        assertFalse(lock.isLocked(), "Should remain unlocked");
        assertTrue(result.toLowerCase().contains("already"),
            "Should say already unlocked when programmatically set");
    }

    @Test
    @DisplayName("Integration: setLocked(true) then requestLock says already locked")
    void setLockedTrue_thenRequestLock_alreadyLocked() {
        lock.setLocked(true);
        assertTrue(lock.isLocked());

        String result = lock.requestLock(LOCK_PASSCODE);
        assertTrue(lock.isLocked(), "Should remain locked");
        assertTrue(result.toLowerCase().contains("already"),
            "Should say already locked when programmatically set");
    }
}
