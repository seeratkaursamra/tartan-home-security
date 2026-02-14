package tartan.smarthome.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tartan.smarthome.resources.iotcontroller.IoTValues;

/**
 *  KeylessEntryTesting
 *  - provides unit testing for the keyless entry
 *  Openai, chatgpt 5.2, 2026-02-14 - "There was a bug related to the nightlock please ammend the tests so that it works with this new implementation"
 */
public class KeylessEntryTest {

    private StaticTartanStateEvaluator evaluator;
    private StringBuffer log;

    @BeforeEach
    void setup() {
        evaluator = new StaticTartanStateEvaluator();
        log = new StringBuffer();
    }

    @Test
    public void keylessOverridesNight_unlocksDuringNightWhenAuthorized() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);
        state.put(IoTValues.LOCK_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(false, newState.get(IoTValues.LOCK_STATE),
                "Expected Keyless Entry to UNLOCK even during night.");
        LogAssertions.assertLogContains(log, "keyless");
        LogAssertions.assertLogContains(log, "unlock");
    }

    @Test
    public void nightLocksIfNoKeylessTrigger() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, false);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE),
                "Expected Night Lock to lock when Keyless does not trigger.");
        LogAssertions.assertLogContains(log,"night");
        LogAssertions.assertLogContains(log,"lock");
    }

    @Test
    public void intruderOverridesKeyless_staysLocked() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.INTRUDER_ACTIVE, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE),
                "Expected Intruder Defence to force LOCKED state.");
        LogAssertions.assertLogContains(log,"intruder");

        assertFalse(log.toString().toLowerCase().contains("keyless") &&
                        log.toString().toLowerCase().contains("unlock"),
                "Keyless success must not occur while intruder is active.\nLog:\n" + log);
    }

    @Test
    public void keylessDisabled_doesNotUnlock() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.KEYLESS_ENABLED, false);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);
        state.put(IoTValues.LOCK_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE),
                "Expected no unlock when keyless is disabled.");
        LogAssertions.assertLogContains(log,"keyless");
        LogAssertions.assertLogContains(log,"disabled");
    }

    @Test
    public void idempotent_alreadyUnlocked_staysUnlocked() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(false, newState.get(IoTValues.LOCK_STATE),
                "Expected to remain unlocked (no accidental toggle).");
    }

    @Test
    public void intruderActive_alreadyLocked_logsAlreadyLocked() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.INTRUDER_ACTIVE, true);
        state.put(IoTValues.LOCK_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE),
                "Intruder defence should keep the door locked if already locked.");
        LogAssertions.assertLogContains(log,"intruder");
        LogAssertions.assertLogContains(log,"already locked");
    }

    @Test
    public void keylessEnabled_authorizedApproach_alreadyUnlocked_logsAlreadyUnlocked() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(false, newState.get(IoTValues.LOCK_STATE),
                "Keyless Entry should NOT relock or toggle; it should remain unlocked.");
        LogAssertions.assertLogContains(log,"keyless");
        LogAssertions.assertLogContains(log,"already unlocked");
    }

    @Test
    public void keylessUnlocksThenNightRelocks() {
        Map<String, Object> state1 = TestStateFactory.baseStateCopy();
        state1.put(IoTValues.NIGHT_ACTIVE, true);
        state1.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state1.put(IoTValues.KEYLESS_ENABLED, true);
        state1.put(IoTValues.AUTHORIZED_APPROACH, true);
        state1.put(IoTValues.LOCK_STATE, true);

        Map<String, Object> result1 = evaluator.evaluateState(state1, log);
        assertEquals(false, result1.get(IoTValues.LOCK_STATE),
                "Keyless should unlock even during night");

        StringBuffer log2 = new StringBuffer();
        Map<String, Object> state2 = TestStateFactory.baseStateCopy();
        state2.put(IoTValues.NIGHT_ACTIVE, true);
        state2.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state2.put(IoTValues.KEYLESS_ENABLED, true);
        state2.put(IoTValues.AUTHORIZED_APPROACH, false);
        state2.put(IoTValues.LOCK_STATE, (Boolean) result1.get(IoTValues.LOCK_STATE));

        Map<String, Object> result2 = evaluator.evaluateState(state2, log2);
        assertEquals(true, result2.get(IoTValues.LOCK_STATE),
                "Night lock should re-lock after keyless person entered");
        LogAssertions.assertLogContains(log2, "night");
    }

    @Test
    public void intruderClearedThenKeylessWorks() {
        Map<String, Object> state1 = TestStateFactory.baseStateCopy();
        state1.put(IoTValues.INTRUDER_ACTIVE, true);
        state1.put(IoTValues.KEYLESS_ENABLED, true);
        state1.put(IoTValues.AUTHORIZED_APPROACH, true);
        state1.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> result1 = evaluator.evaluateState(state1, log);
        assertEquals(true, result1.get(IoTValues.LOCK_STATE),
                "Intruder should force lock");

        StringBuffer log2 = new StringBuffer();
        Map<String, Object> state2 = TestStateFactory.baseStateCopy();
        state2.put(IoTValues.INTRUDER_ACTIVE, false);
        state2.put(IoTValues.KEYLESS_ENABLED, true);
        state2.put(IoTValues.AUTHORIZED_APPROACH, true);
        state2.put(IoTValues.LOCK_STATE, (Boolean) result1.get(IoTValues.LOCK_STATE));

        Map<String, Object> result2 = evaluator.evaluateState(state2, log2);
        assertEquals(false, result2.get(IoTValues.LOCK_STATE),
                "Keyless should unlock after intruder is cleared");
        LogAssertions.assertLogContains(log2, "keyless");
        LogAssertions.assertLogContains(log2, "unlock");
    }

    @Test
    public void keylessWithDoorOpen_noAlarmConflict() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);
        state.put(IoTValues.LOCK_STATE, true);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(false, newState.get(IoTValues.LOCK_STATE),
                "Keyless should unlock");
        assertFalse((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
                "Alarm should not fire (alarm disabled, occupied)");
    }

    @Test
    public void keylessDisabledAndNightActive_nightLockTakesOver() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.KEYLESS_ENABLED, false);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE),
                "Night lock should lock when keyless is disabled and doesn't trigger");
        LogAssertions.assertLogContains(log, "night lock");
    }
}