package tartan.smarthome.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tartan.smarthome.resources.iotcontroller.IoTValues;

/**
 * Openai, chatgpt 5.2, 2026-02-11 - "I added some logic to this function, please generate a test suite to ensure the proper function of this logic"
 * Openai, chatgpt 5.2, 2026-02-14 - "There was a bug related to the nightlock please ammend the tests so that it works with this new implementation"
 *
 * Unit tests for the Smart Lock logic added to StaticTartanStateEvaluator:
 * Priority: Intruder > Keyless > Night
 *
 * These tests assume the STSE uses getOrDefault() for the new inputs and always
 * writes IoTValues.LOCK_STATE into the returned newState map.
 */
public class STSESLtest {

    private StaticTartanStateEvaluator evaluator;
    private StringBuffer log;

    @BeforeEach
    void setup() {
        evaluator = new StaticTartanStateEvaluator();
        log = new StringBuffer();
    }

    // -------------------------------------------------------------------------
    // Backwards-compatible behavior: missing new inputs should not break anything
    // -------------------------------------------------------------------------

    @Test
    public void missingSmartLockInputs_defaultsToLocked_andOutputsLockState() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();


        // Simulate legacy callers: remove all new inputs
        state.remove(IoTValues.LOCK_STATE);
        state.remove(IoTValues.KEYLESS_ENABLED);
        state.remove(IoTValues.AUTHORIZED_APPROACH);
        state.remove(IoTValues.INTRUDER_ACTIVE);
        state.remove(IoTValues.NIGHT_ACTIVE);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue(newState.containsKey(IoTValues.LOCK_STATE), "Expected LOCK_STATE in newState.");
        assertEquals(true, newState.get(IoTValues.LOCK_STATE), "Default LOCK_STATE should be locked (true).");
    }

    // -------------------------------------------------------------------------
    // Keyless Entry
    // -------------------------------------------------------------------------

    @Test
    public void keylessEnabledAndAuthorized_unlocksDoor() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.LOCK_STATE, true);
        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(false, newState.get(IoTValues.LOCK_STATE), "Expected Keyless Entry to unlock.");
        LogAssertions.assertLogContains(log,"keyless entry");
        LogAssertions.assertLogContains(log,"unlocking");
    }

    @Test
    public void keylessDisabled_doesNotUnlock_evenIfAuthorized() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.LOCK_STATE, true);
        state.put(IoTValues.KEYLESS_ENABLED, false);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE), "Keyless disabled: lock must remain locked.");
    }

    // -------------------------------------------------------------------------
    // Night Lock (only applies if Keyless did not trigger)
    // -------------------------------------------------------------------------

    @Test
    public void nightActive_relocksIfUnlocked_whenNoKeylessTrigger() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.LOCK_STATE, false); // unlocked
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true); // ADD THIS LINE
        state.put(IoTValues.KEYLESS_ENABLED, false);
        state.put(IoTValues.AUTHORIZED_APPROACH, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE), "Night lock should relock when unlocked.");
        LogAssertions.assertLogContains(log,"night lock");
        LogAssertions.assertLogContains(log,"locking door");
    }

    @Test
    public void keylessOverridesNight_unlocksDuringNight_ifAuthorized() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.LOCK_STATE, true); // locked
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(false, newState.get(IoTValues.LOCK_STATE), "Keyless should override Night and unlock.");
        LogAssertions.assertLogContains(log,"keyless entry");
        LogAssertions.assertLogContains(log,"unlocking");
    }

    // -------------------------------------------------------------------------
    // Intruder Defence (highest priority)
    // -------------------------------------------------------------------------

    @Test
    public void intruderActive_forcesLocked_evenIfKeylessAndNight() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.LOCK_STATE, false); // currently unlocked
        state.put(IoTValues.INTRUDER_ACTIVE, true);
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE), "Intruder defence must force locked.");
        LogAssertions.assertLogContains(log,"possible intruder detected");
        LogAssertions.assertLogContains(log,"locking door");
    }

    //Chatgpt openai 5.2, 2026-02-11 : "please check these files for branch, statement and mutation coverage add tests to ensure this coverage"
    @Test
    public void smartLock_missingKeys_defaultsToLocked_andDoesNotCrash() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();


        // Intentionally omit:
        // IoTValues.LOCK_STATE, IoTValues.KEYLESS_ENABLED, IoTValues.AUTHORIZED_APPROACH,
        // IoTValues.INTRUDER_ACTIVE, IoTValues.NIGHT_ACTIVE
        // This kills mutants that change defaults (e.g., default unlocked) or cause NPEs.

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue(newState.containsKey(IoTValues.LOCK_STATE),
                "STSE should always output a lock state once smart-lock is integrated.");
        assertEquals(true, newState.get(IoTValues.LOCK_STATE),
                "Expected default lock state to be LOCKED when no smart-lock inputs are provided.");
    }

    @Test
    public void intruderActive_alreadyLocked_doesNotUnlock_andLogsAlreadyLocked() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.INTRUDER_ACTIVE, true);
        state.put(IoTValues.LOCK_STATE, true); // already locked
        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true); // try to tempt keyless
        state.put(IoTValues.NIGHT_ACTIVE, true);         // try to tempt night

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE),
                "Intruder defence must keep the door locked (highest priority).");

        String lower = log.toString().toLowerCase();
        assertTrue(lower.contains("intruder"),
                "Expected intruder messaging in log.\nLog:\n" + log);
        assertTrue(lower.contains("already locked") || lower.contains("locking door"),
                "Expected intruder locking/already-locked message.\nLog:\n" + log);

        // Mutation-killer: ensure keyless didn't 'win'
        assertFalse(lower.contains("keyless") && lower.contains("unlock"),
                "Keyless must not unlock while intruder is active.\nLog:\n" + log);
    }

}
