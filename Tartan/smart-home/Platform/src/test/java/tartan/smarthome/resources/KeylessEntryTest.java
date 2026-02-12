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
public class KeylessEntryTests {

    private StaticTartanStateEvaluator evaluator;
    private StringBuffer log;

    @BeforeEach
    void setup() {
        evaluator = new StaticTartanStateEvaluator();
        log = new StringBuffer();
    }

    /**
     * IMPORTANT: This baseState is tailored to *your* STSE implementation.
     * It sets the keys that otherwise cause NPEs (temp/target/hvacSetting/etc).
     */
    private Map<String, Object> baseState() {
        Map<String, Object> s = new HashMap<>();

        // Existing STSE-required keys (avoid NPEs)
        s.put(IoTValues.TEMP_READING, 70);
        s.put(IoTValues.TARGET_TEMP, 70);
        s.put(IoTValues.HUMIDITY_READING, 50);

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

        //call hvacSetting.equals("Heater"/"Chiller") later, so make it non-null.
        s.put(IoTValues.HVAC_MODE, "Heater");

        // Away timer present in your output map; include input default
        s.put(IoTValues.AWAY_TIMER, false);

        // -------- NEW LOCK/KEYLESS KEYS (you must add these to IoTValues + STSE) --------
        s.put(IoTValues.LOCK_STATE, true);            // true=locked
        s.put(IoTValues.KEYLESS_ENABLED, true);
        s.put(IoTValues.AUTHORIZED_APPROACH, false);
        s.put(IoTValues.INTRUDER_ACTIVE, false);
        s.put(IoTValues.NIGHT_ACTIVE, false);

        return s;
    }

    //Secondary function to save space
    private void assertLogContains(String needle) {
        String txt = log.toString().toLowerCase();
        assertTrue(txt.contains(needle.toLowerCase()),
                "Expected log to contain: " + needle + "\nActual log:\n" + log);
    }

    // Priority: Intruder > Keyless > Night
    // Keyless overrides Night
    @Test
    public void keylessOverridesNight_unlocksDuringNightWhenAuthorized() {
        Map<String, Object> state = baseState();
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);
        state.put(IoTValues.LOCK_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(false, newState.get(IoTValues.LOCK_STATE),
                "Expected Keyless Entry to UNLOCK even during night.");
        assertLogContains("keyless");
        assertLogContains("unlock");
    }

    @Test
    public void nightLocksIfNoKeylessTrigger() {
        Map<String, Object> state = baseState();
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, false); // no trigger
        state.put(IoTValues.LOCK_STATE, false);          // currently unlocked

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE),
                "Expected Night Lock to lock when Keyless does not trigger.");
        assertLogContains("night");
        assertLogContains("lock");
    }

    @Test
    public void intruderOverridesKeyless_staysLocked() {
        Map<String, Object> state = baseState();
        state.put(IoTValues.INTRUDER_ACTIVE, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.LOCK_STATE, false); // even if unlocked, intruder should force lock

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE),
                "Expected Intruder Defence to force LOCKED state.");
        assertLogContains("intruder");

        // Ensure no "keyless unlocked" success message slipped through
        assertFalse(log.toString().toLowerCase().contains("keyless") &&
                        log.toString().toLowerCase().contains("unlock"),
                "Keyless success must not occur while intruder is active.\nLog:\n" + log);
    }


    // these two tests to help with branching and behaviour
    @Test
    public void keylessDisabled_doesNotUnlock() {
        Map<String, Object> state = baseState();
        state.put(IoTValues.KEYLESS_ENABLED, false);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);
        state.put(IoTValues.LOCK_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.LOCK_STATE),
                "Expected no unlock when keyless is disabled.");
        assertLogContains("keyless");
        assertLogContains("disabled"); // or "ignored" depending on your chosen message
    }

    @Test
    public void idempotent_alreadyUnlocked_staysUnlocked() {
        Map<String, Object> state = baseState();
        state.put(IoTValues.KEYLESS_ENABLED, true);
        state.put(IoTValues.AUTHORIZED_APPROACH, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(false, newState.get(IoTValues.LOCK_STATE),
                "Expected to remain unlocked (no accidental toggle).");
    }
}
