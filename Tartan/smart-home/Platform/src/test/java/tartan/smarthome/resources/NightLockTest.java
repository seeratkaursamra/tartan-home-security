package tartan.smarthome.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tartan.smarthome.resources.iotcontroller.IoTValues;

/**
 * Unit tests for Night Lock feature in StaticTartanStateEvaluator.
 * Openai, chatgpt 5.2, 2026-02-14 - "There was a bug related to the nightlock please ammend the tests so that it works with this new implementation"
 */
public class NightLockTest {

    private StaticTartanStateEvaluator evaluator;
    private StringBuffer log;

    @BeforeEach
    void setUp() {
        evaluator = new StaticTartanStateEvaluator();
        log = new StringBuffer();
    }

    @Test
    @DisplayName("Night Lock: Door is locked automatically during night hours")
    void nightLock_locksWhenNightActive() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        // Set nightActive AND nightLockEnabled
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Door should be locked when night is active and feature is enabled");
        LogAssertions.assertLogContains(log, "night lock");
    }

    @Test
    @DisplayName("Night Lock: Unlocked door during night is re-locked")
    void nightLock_relocksUnlockedDoor() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Unlocked door should be re-locked during night");
        LogAssertions.assertLogContains(log, "lock");
    }

    @Test
    @DisplayName("Night Lock: During day, unlocked door stays unlocked")
    void nightLock_dayTime_noLock() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.NIGHT_ACTIVE, false);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Door should stay unlocked during day");
    }

    @Test
    @DisplayName("Night Lock: Night not active keeps door unlocked")
    void nightLock_notActive_noLock() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.NIGHT_ACTIVE, false);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Door should remain unlocked when night is not active");
    }

    @Test
    @DisplayName("Night Lock: Already locked — no duplicate log entry")
    void nightLock_alreadyLocked_noExtraLog() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.LOCK_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Door should remain locked");
        assertFalse(log.toString().contains("Night lock: locking door"),
                "Should not log locking when already locked");
    }

    @Test
    @DisplayName("Night Lock: Night active at late hour locks door")
    void nightLock_lateHour_locks() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Door should lock at late night hour");
    }

    @Test
    @DisplayName("Night Lock: Night active at early morning hour locks door")
    void nightLock_earlyMorning_locks() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Door should lock in early morning during night window");
    }

    @Test
    @DisplayName("Night Lock: Keyless entry takes priority over night lock")
    void nightLock_keylessOverrides() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);
        state.put(IoTValues.LOCK_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Keyless entry should unlock even during night");
        LogAssertions.assertLogContains(log, "keyless");
    }

    @Test
    @DisplayName("Night Lock: Intruder takes priority over night lock")
    void nightLock_intruderOverrides() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.INTRUDER_ACTIVE, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Intruder should lock door (takes priority over night lock)");
        LogAssertions.assertLogContains(log, "intruder");
    }

    @Test
    @DisplayName("Night Lock: Night lock engages when keyless does not trigger")
    void nightLock_keylessDoesNotTrigger_nightLocks() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, false); // No authorized approach
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night lock should engage when keyless doesn't trigger");
        LogAssertions.assertLogContains(log, "night lock");
    }

    @Test
    @DisplayName("Night Lock Integration: Evaluator output includes lock state for full pipeline")
    void nightLock_integration_outputIncludesLockState() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertNotNull(newState.get(IoTValues.LOCK_STATE),
                "Lock state should be in output");
        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Lock state should be true");
    }

    @Test
    @DisplayName("Integration: config fields compute nightActive=true, door locks")
    void integration_configFields_nightActive_locks() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.remove(IoTValues.NIGHT_ACTIVE); // Force config computation

        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.NIGHT_LOCK_START, 22);
        state.put(IoTValues.NIGHT_LOCK_END, 6);
        state.put(IoTValues.CURRENT_HOUR, 23);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Door should lock when config computes nightActive=true");
    }

    @Test
    @DisplayName("Integration: config fields with disabled → no lock")
    void integration_configFields_disabled_noLock() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.remove(IoTValues.NIGHT_ACTIVE);

        state.put(IoTValues.NIGHT_LOCK_ENABLED, false);
        state.put(IoTValues.NIGHT_LOCK_START, 22);
        state.put(IoTValues.NIGHT_LOCK_END, 6);
        state.put(IoTValues.CURRENT_HOUR, 23);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Door should stay unlocked when feature is disabled");
    }

    @Test
    @DisplayName("Integration: occupied→vacant transition, night lock persists")
    void integration_occupiedToVacant_nightLockPersists() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> result1 = evaluator.evaluateState(state, log);
        assertTrue((Boolean) result1.get(IoTValues.LOCK_STATE),
                "Night lock should lock when occupied");

        StringBuffer log2 = new StringBuffer();
        Map<String, Object> state2 = TestStateFactory.baseStateCopy();
        state2.put(IoTValues.NIGHT_ACTIVE, true);
        state2.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state2.put(IoTValues.PROXIMITY_STATE, false);
        state2.put(IoTValues.LOCK_STATE, (Boolean) result1.get(IoTValues.LOCK_STATE));

        Map<String, Object> result2 = evaluator.evaluateState(state2, log2);
        assertTrue((Boolean) result2.get(IoTValues.LOCK_STATE),
                "Night lock should keep door locked when transitioning to vacant");
    }

    @Test
    @DisplayName("Integration: keyless unlocks during night, next eval relocks")
    void integration_keylessUnlocks_thenNightRelocks() {
        // Step 1: Keyless unlocks during night
        Map<String, Object> state1 = TestStateFactory.baseStateCopy();
        state1.put(IoTValues.NIGHT_ACTIVE, true);
        state1.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state1.put(IoTValues.KEYLESS_ENABLED, true);
        state1.put(IoTValues.AUTHORIZED_APPROACH, true);
        state1.put(IoTValues.LOCK_STATE, true);

        Map<String, Object> result1 = evaluator.evaluateState(state1, log);
        assertFalse((Boolean) result1.get(IoTValues.LOCK_STATE),
                "Keyless should unlock");

        // Step 2: Authorized person leaves, night lock re-engages
        StringBuffer log2 = new StringBuffer();
        Map<String, Object> state2 = TestStateFactory.baseStateCopy();
        state2.put(IoTValues.NIGHT_ACTIVE, true);
        state2.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state2.put(IoTValues.KEYLESS_ENABLED, true);
        state2.put(IoTValues.AUTHORIZED_APPROACH, false);
        state2.put(IoTValues.LOCK_STATE, (Boolean) result1.get(IoTValues.LOCK_STATE));

        Map<String, Object> result2 = evaluator.evaluateState(state2, log2);
        assertTrue((Boolean) result2.get(IoTValues.LOCK_STATE),
                "Night lock should re-lock after keyless person leaves");
    }
}