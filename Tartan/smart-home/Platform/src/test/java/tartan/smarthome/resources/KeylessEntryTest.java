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
 */
public class KeylessEntryTest {

    private StaticTartanStateEvaluator evaluator;
    private StringBuffer log;

    @BeforeEach
    void setup() {
        evaluator = new StaticTartanStateEvaluator();
        log = new StringBuffer();
    }


    // Priority: Intruder > Keyless > Night
    // Keyless overrides Night
    @Test
    public void keylessOverridesNight_unlocksDuringNightWhenAuthorized() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.NIGHT_ACTIVE, true);
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
        state.put(IoTValues.AUTHORIZED_APPROACH, false); // no trigger
        state.put(IoTValues.LOCK_STATE, false);          // currently unlocked

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
        state.put(IoTValues.LOCK_STATE, false); // even if unlocked, intruder should force lock

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE),
                "Expected Intruder Defence to force LOCKED state.");
        LogAssertions.assertLogContains(log,"intruder");

        // Ensure no "keyless unlocked" success message slipped through
        assertFalse(log.toString().toLowerCase().contains("keyless") &&
                        log.toString().toLowerCase().contains("unlock"),
                "Keyless success must not occur while intruder is active.\nLog:\n" + log);
    }


    // these two tests to help with branching and behaviour
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
        LogAssertions.assertLogContains(log,"disabled"); // or "ignored" depending on your chosen message
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

    //Chatgpt openai 5.2, 2026-02-11 : "please check these files for branch, statement and mutation coverage add tests to ensure this coverage"
    @Test
    public void intruderActive_alreadyLocked_logsAlreadyLocked() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.INTRUDER_ACTIVE, true);
        state.put(IoTValues.LOCK_STATE, true); // already locked

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE),
                "Intruder defence should keep the door locked if already locked.");
        LogAssertions.assertLogContains(log,"intruder");
        LogAssertions.assertLogContains(log,"already locked"); // kills mutants that remove/skip the else branch
    }

    @Test
    public void keylessEnabled_authorizedApproach_alreadyUnlocked_logsAlreadyUnlocked() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);
        state.put(IoTValues.LOCK_STATE, false); // already unlocked

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(false, newState.get(IoTValues.LOCK_STATE),
                "Keyless Entry should NOT relock or toggle; it should remain unlocked.");
        LogAssertions.assertLogContains(log,"keyless");
        LogAssertions.assertLogContains(log,"already unlocked"); // kills mutants that remove the else branch in keyless
    }

}
