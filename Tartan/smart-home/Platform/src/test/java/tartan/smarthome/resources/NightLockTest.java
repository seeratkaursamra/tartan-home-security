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
 * The following from Claude's prompt: What type of tests would you write for the Night Lock feature?
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
        state.put(IoTValues.LOCK_STATE, false);         
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);  
        state.put(IoTValues.NIGHT_LOCK_START, 22);    
        state.put(IoTValues.NIGHT_LOCK_END, 6);        
        state.put(IoTValues.CURRENT_HOUR, 12);        
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
        state.put(IoTValues.LOCK_STATE, false);      
        state.put(IoTValues.CURRENT_HOUR, 2);       

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
        state.put(IoTValues.NIGHT_LOCK_ENABLED, false); 
        state.put(IoTValues.LOCK_STATE, false);         
        state.put(IoTValues.CURRENT_HOUR, 23);          

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Door should stay unlocked when feature is disabled");
        assertFalse(log.toString().contains("Night Lock"),
                "Night Lock FAILED: No Night Lock log expected when feature is disabled");
    }

    // ---- Cycle 5: Midnight crossing ----

    @Test
    @DisplayName("Night Lock: Midnight crossing — hour 23, start=22, end=6 → locked")
    void testNightLock_MidnightCrossing_Hour23_Locked() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.NIGHT_LOCK_START, 22);
        state.put(IoTValues.NIGHT_LOCK_END, 6);
        state.put(IoTValues.LOCK_STATE, false);
        state.put(IoTValues.CURRENT_HOUR, 23);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Hour 23 should be night when start=22, end=6");
    }

    @Test
    @DisplayName("Night Lock: Midnight crossing — hour 3, start=22, end=6 → locked")
    void testNightLock_MidnightCrossing_Hour3_Locked() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.NIGHT_LOCK_START, 22);
        state.put(IoTValues.NIGHT_LOCK_END, 6);
        state.put(IoTValues.LOCK_STATE, false);
        state.put(IoTValues.CURRENT_HOUR, 3);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Hour 3 should be night when start=22, end=6");
    }

    @Test
    @DisplayName("Night Lock: Midnight crossing — hour 7, start=22, end=6 → NOT locked")
    void testNightLock_MidnightCrossing_Hour7_NotLocked() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.NIGHT_LOCK_START, 22);
        state.put(IoTValues.NIGHT_LOCK_END, 6);
        state.put(IoTValues.LOCK_STATE, false);
        state.put(IoTValues.CURRENT_HOUR, 7);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Hour 7 should NOT be night when start=22, end=6");
    }

    // ---- Cycle 6: Edge cases & log messages ----

    @Test
    @DisplayName("Night Lock: Boundary — hour == start → locked")
    void testNightLock_Boundary_HourEqualsStart_Locked() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.NIGHT_LOCK_START, 22);
        state.put(IoTValues.NIGHT_LOCK_END, 6);
        state.put(IoTValues.LOCK_STATE, false);
        state.put(IoTValues.CURRENT_HOUR, 22);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: hour==start should be night (inclusive)");
    }

    @Test
    @DisplayName("Night Lock: Boundary — hour == end → NOT locked")
    void testNightLock_Boundary_HourEqualsEnd_NotLocked() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.NIGHT_LOCK_START, 22);
        state.put(IoTValues.NIGHT_LOCK_END, 6);
        state.put(IoTValues.LOCK_STATE, false);
        state.put(IoTValues.CURRENT_HOUR, 6);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: hour==end should NOT be night (exclusive)");
    }

    @Test
    @DisplayName("Night Lock: Already locked — no duplicate log entry")
    void testNightLock_AlreadyLocked_NoDoubleLog() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, true);     
        state.put(IoTValues.CURRENT_HOUR, 23);      

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Door should remain locked");
        assertFalse(log.toString().contains("Night Lock"),
                "Night Lock FAILED: No log entry when already locked");
    }

    @Test
    @DisplayName("Night Lock: Non-crossing range — start=8, end=18, hour=10 → locked")
    void testNightLock_NonCrossingRange_DuringRange_Locked() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.NIGHT_LOCK_START, 8);
        state.put(IoTValues.NIGHT_LOCK_END, 18);
        state.put(IoTValues.LOCK_STATE, false);
        state.put(IoTValues.CURRENT_HOUR, 10);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Hour 10 should be in range [8,18)");
    }

    @Test
    @DisplayName("Night Lock: Non-crossing range — start=8, end=18, hour=20 → NOT locked")
    void testNightLock_NonCrossingRange_OutsideRange_NotLocked() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.NIGHT_LOCK_START, 8);
        state.put(IoTValues.NIGHT_LOCK_END, 18);
        state.put(IoTValues.LOCK_STATE, false);
        state.put(IoTValues.CURRENT_HOUR, 20);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Hour 20 should NOT be in range [8,18)");
    }

    // ---- Cycle 7: Full stack integration ----

    @Test
    @DisplayName("Night Lock Integration: TartanHome fields round-trip through state conversion")
    void testNightLock_Integration_TartanHomeFields() {
        tartan.smarthome.core.TartanHome home = new tartan.smarthome.core.TartanHome();
        home.setLockState(tartan.smarthome.core.TartanHomeValues.LOCKED);
        home.setNightLockEnabled("true");
        home.setNightLockStart("22");
        home.setNightLockEnd("6");

        assertEquals(tartan.smarthome.core.TartanHomeValues.LOCKED, home.getLockState(),
                "Integration FAILED: lockState should be LOCKED");
        assertEquals("true", home.getNightLockEnabled(),
                "Integration FAILED: nightLockEnabled should be true");
        assertEquals("22", home.getNightLockStart(),
                "Integration FAILED: nightLockStart should be 22");
        assertEquals("6", home.getNightLockEnd(),
                "Integration FAILED: nightLockEnd should be 6");
    }

    @Test
    @DisplayName("Night Lock Integration: Evaluator output includes lock state for full pipeline")
    void testNightLock_Integration_EvaluatorPipeline() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, false);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.NIGHT_LOCK_START, 22);
        state.put(IoTValues.NIGHT_LOCK_END, 6);
        state.put(IoTValues.CURRENT_HOUR, 23);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue(newState.containsKey(IoTValues.LOCK_STATE),
                "Integration FAILED: Output state should contain LOCK_STATE");
        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Integration FAILED: LOCK_STATE should be true during night");
    }
}
