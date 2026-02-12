package tartan.smarthome.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Hashtable;
import java.util.Map;

import tartan.smarthome.resources.iotcontroller.IoTValues;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Night Lock feature.
 *
 * Night Lock: During configured night hours, the door is automatically locked
 * and re-locked if unlocked. The feature can be enabled/disabled and supports
 * midnight-crossing schedules (e.g., start=22, end=6).
 */
public class NightLockTest {

    private StaticTartanStateEvaluator evaluator;
    private StringBuffer log;

    @BeforeEach
    void setUp() {
        evaluator = new StaticTartanStateEvaluator();
        log = new StringBuffer();
    }

    private Map<String, Object> createDefaultState() {
        Map<String, Object> state = new Hashtable<>();
        state.put(IoTValues.TEMP_READING, 70);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HUMIDITY_READING, 50);
        state.put(IoTValues.DOOR_STATE, false);
        state.put(IoTValues.LIGHT_STATE, false);
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.ALARM_ACTIVE, false);
        state.put(IoTValues.HUMIDIFIER_STATE, false);
        state.put(IoTValues.HEATER_STATE, false);
        state.put(IoTValues.CHILLER_STATE, false);
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.ALARM_PASSCODE, "1234");
        state.put(IoTValues.GIVEN_PASSCODE, "");
        state.put(IoTValues.AWAY_TIMER, false);
        // Night Lock defaults
        state.put(IoTValues.LOCK_STATE, false);          // unlocked
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);   // feature on
        state.put(IoTValues.NIGHT_LOCK_START, 22);       // 10 PM
        state.put(IoTValues.NIGHT_LOCK_END, 6);          // 6 AM
        state.put(IoTValues.CURRENT_HOUR, 12);           // noon by default
        return state;
    }

    // ---- Cycle 1: Auto-lock at night ----

    @Test
    @DisplayName("Night Lock: Door is locked automatically during night hours")
    void testNightLock_DuringNight_LocksDoor() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, false);      // unlocked
        state.put(IoTValues.CURRENT_HOUR, 23);       // 11 PM — night time

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Door should be locked during night hours");
        assertTrue(log.toString().contains("Night Lock"),
                "Night Lock FAILED: Log should mention Night Lock");
    }

    // ---- Cycle 2: Re-lock during night ----

    @Test
    @DisplayName("Night Lock: Unlocked door during night is re-locked")
    void testNightLock_UnlockedDuringNight_Relocks() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, false);      // someone unlocked it
        state.put(IoTValues.CURRENT_HOUR, 2);        // 2 AM — night time

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Unlocked door should be re-locked during night");
    }

    // ---- Cycle 3: Daytime — no forced lock ----

    @Test
    @DisplayName("Night Lock: During day, unlocked door stays unlocked")
    void testNightLock_DuringDay_DoesNotForceLock() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, false);      // unlocked
        state.put(IoTValues.CURRENT_HOUR, 12);       // noon — daytime

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Door should stay unlocked during daytime");
        assertFalse(log.toString().contains("Night Lock"),
                "Night Lock FAILED: No Night Lock log entry expected during daytime");
    }

    // ---- Cycle 4: Feature disabled ----

    @Test
    @DisplayName("Night Lock: Disabled feature does not lock door at night")
    void testNightLock_Disabled_NoEffect() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.NIGHT_LOCK_ENABLED, false); // feature off
        state.put(IoTValues.LOCK_STATE, false);          // unlocked
        state.put(IoTValues.CURRENT_HOUR, 23);           // night time

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Door should stay unlocked when feature is disabled");
        assertFalse(log.toString().contains("Night Lock"),
                "Night Lock FAILED: No Night Lock log expected when feature is disabled");
    }
}
