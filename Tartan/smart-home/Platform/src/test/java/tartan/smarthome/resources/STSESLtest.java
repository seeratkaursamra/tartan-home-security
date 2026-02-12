package tartan.smarthome.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tartan.smarthome.resources.iotcontroller.IoTValues;

/**
 * Openai, chatgpt 5.2, 2026-02-11 - "I added some logic to this function, please generate a test suite to ensure the proper function of this logic"
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

    /**
     * STSE requires several unrelated fields to avoid NPEs (temp/target/hvac etc.),
     * so this baseState includes them.
     */
    private Map<String, Object> baseState() {
        Map<String, Object> s = new HashMap<>();

        // Required by your STSE to avoid NPEs
        s.put(IoTValues.TEMP_READING, 70);
        s.put(IoTValues.TARGET_TEMP, 70);
        s.put(IoTValues.HUMIDITY_READING, 50);
        s.put(IoTValues.HVAC_MODE, "Heater"); // must be non-null before hvacSetting.equals(...)

        // Existing required states
        s.put(IoTValues.DOOR_STATE, false);
        s.put(IoTValues.LIGHT_STATE, false);
        s.put(IoTValues.PROXIMITY_STATE, false);

        s.put(IoTValues.ALARM_STATE, false);
        s.put(IoTValues.ALARM_ACTIVE, false);
        s.put(IoTValues.ALARM_PASSCODE, "1234");
        s.put(IoTValues.GIVEN_PASSCODE, "");

        s.put(IoTValues.HEATER_STATE, false);
        s.put(IoTValues.CHILLER_STATE, false);
        s.put(IoTValues.HUMIDIFIER_STATE, false);

        // Away timer default
        s.put(IoTValues.AWAY_TIMER, false);

        // New Smart Lock defaults
        s.put(IoTValues.LOCK_STATE, true);
        s.put(IoTValues.KEYLESS_ENABLED, false);
        s.put(IoTValues.AUTHORIZED_APPROACH, false);
        s.put(IoTValues.INTRUDER_ACTIVE, false);
        s.put(IoTValues.NIGHT_ACTIVE, false);

        return s;
    }

    private void assertLogContains(String needle) {
        String txt = log.toString().toLowerCase();
        assertTrue(
                txt.contains(needle.toLowerCase()),
                "Expected log to contain: " + needle + "\nActual log:\n" + log
        );
    }

    // -------------------------------------------------------------------------
    // Backwards-compatible behavior: missing new inputs should not break anything
    // -------------------------------------------------------------------------

    @Test
    public void missingSmartLockInputs_defaultsToLocked_andOutputsLockState() {
        Map<String, Object> state = baseState();

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
        Map<String, Object> state = baseState();
        state.put(IoTValues.LOCK_STATE, true);
        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(false, newState.get(IoTValues.LOCK_STATE), "Expected Keyless Entry to unlock.");
        assertLogContains("keyless entry");
        assertLogContains("unlocking");
    }

    @Test
    public void keylessDisabled_doesNotUnlock_evenIfAuthorized() {
        Map<String, Object> state = baseState();
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
        Map<String, Object> state = baseState();
        state.put(IoTValues.LOCK_STATE, false); // unlocked
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.KEYLESS_ENABLED, false);
        state.put(IoTValues.AUTHORIZED_APPROACH, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE), "Night lock should relock when unlocked.");
        assertLogContains("night lock");
        assertLogContains("locking door");
    }

    @Test
    public void keylessOverridesNight_unlocksDuringNight_ifAuthorized() {
        Map<String, Object> state = baseState();
        state.put(IoTValues.LOCK_STATE, true); // locked
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(false, newState.get(IoTValues.LOCK_STATE), "Keyless should override Night and unlock.");
        assertLogContains("keyless entry");
        assertLogContains("unlocking");
    }

    // -------------------------------------------------------------------------
    // Intruder Defence (highest priority)
    // -------------------------------------------------------------------------

    @Test
    public void intruderActive_forcesLocked_evenIfKeylessAndNight() {
        Map<String, Object> state = baseState();
        state.put(IoTValues.LOCK_STATE, false); // currently unlocked
        state.put(IoTValues.INTRUDER_ACTIVE, true);
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE), "Intruder defence must force locked.");
        assertLogContains("possible intruder detected");
        assertLogContains("locking door");
    }
}
