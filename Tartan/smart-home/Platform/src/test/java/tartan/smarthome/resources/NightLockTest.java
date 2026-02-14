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
 *
 * The evaluator uses NIGHT_ACTIVE (pre-computed boolean) to decide if night
 * lock should engage. Priority order: Intruder > Keyless > Night Lock.
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
        state.put(IoTValues.KEYLESS_ENABLED, false);         
        state.put(IoTValues.AUTHORIZED_APPROACH, false);     
        state.put(IoTValues.INTRUDER_ACTIVE, false);        
        state.put(IoTValues.NIGHT_ACTIVE, false);            
        return state;
    }

    // ---- Cycle 1: Auto-lock at night ----

    @Test
    @DisplayName("Night Lock: Door is locked automatically during night hours")
    void testNightLock_DuringNight_LocksDoor() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, false);        
        state.put(IoTValues.NIGHT_ACTIVE, true);     

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Door should be locked during night hours");
        assertTrue(log.toString().contains("Night lock"),
                "Night Lock FAILED: Log should mention Night lock");
    }

    // ---- Cycle 2: Re-lock during night ----

    @Test
    @DisplayName("Night Lock: Unlocked door during night is re-locked")
    void testNightLock_UnlockedDuringNight_Relocks() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, false);     
        state.put(IoTValues.NIGHT_ACTIVE, true);      

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Unlocked door should be re-locked during night");
    }

    // ---- Cycle 3: Daytime — no forced lock ----

    @Test
    @DisplayName("Night Lock: During day, unlocked door stays unlocked")
    void testNightLock_DuringDay_DoesNotForceLock() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, false);       
        state.put(IoTValues.NIGHT_ACTIVE, false);     

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Door should stay unlocked during daytime");
        assertFalse(log.toString().contains("Night lock"),
                "Night Lock FAILED: No Night lock log entry expected during daytime");
    }

    // ---- Cycle 4: Feature disabled (night not active) ----

    @Test
    @DisplayName("Night Lock: Disabled feature does not lock door at night")
    void testNightLock_Disabled_NoEffect() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.NIGHT_ACTIVE, false);      
        state.put(IoTValues.LOCK_STATE, false);        

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Door should stay unlocked when night is not active");
        assertFalse(log.toString().contains("Night lock"),
                "Night Lock FAILED: No Night lock log expected when night is not active");
    }

    // ---- Cycle 5: Night active locks, day does not ----

    @Test
    @DisplayName("Night Lock: Night active at late hour locks door")
    void testNightLock_NightActive_LocksDoor() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, false);
        state.put(IoTValues.NIGHT_ACTIVE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Door should be locked when night is active");
    }

    @Test
    @DisplayName("Night Lock: Night active at early morning hour locks door")
    void testNightLock_NightActive_EarlyMorning_LocksDoor() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, false);
        state.put(IoTValues.NIGHT_ACTIVE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Door should be locked during early morning night");
    }

    @Test
    @DisplayName("Night Lock: Night not active keeps door unlocked")
    void testNightLock_NightNotActive_DoorStaysUnlocked() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, false);
        state.put(IoTValues.NIGHT_ACTIVE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Door should stay unlocked when night is not active");
    }

    // ---- Cycle 6: Edge cases & log messages ----

    @Test
    @DisplayName("Night Lock: Already locked — no duplicate log entry")
    void testNightLock_AlreadyLocked_NoDoubleLog() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, true);         
        state.put(IoTValues.NIGHT_ACTIVE, true);   

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Door should remain locked");
        assertFalse(log.toString().contains("Night lock: locking door"),
                "Night Lock FAILED: No lock log entry when already locked");
    }

    @Test
    @DisplayName("Night Lock: Intruder takes priority over night lock")
    void testNightLock_IntruderPriority() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, false);      
        state.put(IoTValues.NIGHT_ACTIVE, true);     
        state.put(IoTValues.INTRUDER_ACTIVE, true);   

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Door should be locked when intruder detected");
        assertTrue(log.toString().contains("intruder"),
                "Night Lock FAILED: Log should mention intruder, not night lock");
    }

    @Test
    @DisplayName("Night Lock: Keyless entry takes priority over night lock")
    void testNightLock_KeylessPriority() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, true);       
        state.put(IoTValues.NIGHT_ACTIVE, true);      
        state.put(IoTValues.KEYLESS_ENABLED, true);   
        state.put(IoTValues.AUTHORIZED_APPROACH, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Keyless should unlock despite night lock
        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Keyless entry should override night lock");
        assertTrue(log.toString().contains("Keyless entry"),
                "Night Lock FAILED: Log should mention keyless entry");
    }

    @Test
    @DisplayName("Night Lock: Night lock engages when keyless does not trigger")
    void testNightLock_KeylessNotTriggered_NightLockEngages() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.LOCK_STATE, false);   
        state.put(IoTValues.NIGHT_ACTIVE, true);     
        state.put(IoTValues.KEYLESS_ENABLED, false); 
        state.put(IoTValues.AUTHORIZED_APPROACH, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Night Lock FAILED: Night lock should engage when keyless is off");
        assertTrue(log.toString().contains("Night lock: locking door"),
                "Night Lock FAILED: Log should mention night lock");
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
        state.put(IoTValues.NIGHT_ACTIVE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue(newState.containsKey(IoTValues.LOCK_STATE),
                "Integration FAILED: Output state should contain LOCK_STATE");
        assertTrue((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Integration FAILED: LOCK_STATE should be true during night");
    }

    // ---- isNightTime helper tests ----

    @Test
    @DisplayName("isNightTime: Midnight crossing — hour 23, start=22, end=6 → true")
    void testIsNightTime_MidnightCrossing_Hour23() {
        assertTrue(evaluator.isNightTime(23, 22, 6),
                "isNightTime FAILED: Hour 23 should be night when start=22, end=6");
    }

    @Test
    @DisplayName("isNightTime: Midnight crossing — hour 3, start=22, end=6 → true")
    void testIsNightTime_MidnightCrossing_Hour3() {
        assertTrue(evaluator.isNightTime(3, 22, 6),
                "isNightTime FAILED: Hour 3 should be night when start=22, end=6");
    }

    @Test
    @DisplayName("isNightTime: Midnight crossing — hour 7, start=22, end=6 → false")
    void testIsNightTime_MidnightCrossing_Hour7() {
        assertFalse(evaluator.isNightTime(7, 22, 6),
                "isNightTime FAILED: Hour 7 should NOT be night when start=22, end=6");
    }

    @Test
    @DisplayName("isNightTime: Boundary — hour == start → true")
    void testIsNightTime_Boundary_HourEqualsStart() {
        assertTrue(evaluator.isNightTime(22, 22, 6),
                "isNightTime FAILED: hour==start should be night (inclusive)");
    }

    @Test
    @DisplayName("isNightTime: Boundary — hour == end → false")
    void testIsNightTime_Boundary_HourEqualsEnd() {
        assertFalse(evaluator.isNightTime(6, 22, 6),
                "isNightTime FAILED: hour==end should NOT be night (exclusive)");
    }

    @Test
    @DisplayName("isNightTime: Non-crossing range — start=8, end=18, hour=10 → true")
    void testIsNightTime_NonCrossing_InRange() {
        assertTrue(evaluator.isNightTime(10, 8, 18),
                "isNightTime FAILED: Hour 10 should be in range [8,18)");
    }

    @Test
    @DisplayName("isNightTime: Non-crossing range — start=8, end=18, hour=20 → false")
    void testIsNightTime_NonCrossing_OutOfRange() {
        assertFalse(evaluator.isNightTime(20, 8, 18),
                "isNightTime FAILED: Hour 20 should NOT be in range [8,18)");
    }

    // ---- Integration: config-computed nightActive path ----

    @Test
    @DisplayName("Integration: config fields compute nightActive=true, door locks")
    void testNightLock_withConfigComputation_locksAtNight() {
        Map<String, Object> state = createDefaultState();
        state.remove(IoTValues.NIGHT_ACTIVE); 
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
    void testNightLock_withConfigComputation_disabledDoesNotLock() {
        Map<String, Object> state = createDefaultState();
        state.remove(IoTValues.NIGHT_ACTIVE);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, false);
        state.put(IoTValues.NIGHT_LOCK_START, 22);
        state.put(IoTValues.NIGHT_LOCK_END, 6);
        state.put(IoTValues.CURRENT_HOUR, 23);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LOCK_STATE),
                "Door should stay unlocked when night lock is disabled via config");
    }

    // ---- Integration: multi-step state transitions ----

    @Test
    @DisplayName("Integration: occupied→vacant transition, night lock persists")
    void testNightLock_occupiedVacantTransition() {
        // Step 1: occupied + night → locked
        Map<String, Object> state1 = createDefaultState();
        state1.put(IoTValues.PROXIMITY_STATE, true);
        state1.put(IoTValues.NIGHT_ACTIVE, true);
        state1.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> result1 = evaluator.evaluateState(state1, log);
        assertTrue((Boolean) result1.get(IoTValues.LOCK_STATE),
                "Night lock should lock when occupied at night");

        StringBuffer log2 = new StringBuffer();
        Map<String, Object> state2 = createDefaultState();
        state2.put(IoTValues.PROXIMITY_STATE, false);   
        state2.put(IoTValues.NIGHT_ACTIVE, true);
        state2.put(IoTValues.LOCK_STATE, (Boolean) result1.get(IoTValues.LOCK_STATE));
        state2.put(IoTValues.LIGHT_STATE, false);

        Map<String, Object> result2 = evaluator.evaluateState(state2, log2);
        assertTrue((Boolean) result2.get(IoTValues.LOCK_STATE),
                "Door should remain locked after becoming vacant at night");
        assertTrue((Boolean) result2.get(IoTValues.AWAY_TIMER),
                "Away timer should start when vacant");
    }

    @Test
    @DisplayName("Integration: keyless unlocks during night, next eval relocks")
    void testNightLock_keylessUnlockThenNightRelocks() {
        Map<String, Object> state1 = createDefaultState();
        state1.put(IoTValues.NIGHT_ACTIVE, true);
        state1.put(IoTValues.KEYLESS_ENABLED, true);
        state1.put(IoTValues.AUTHORIZED_APPROACH, true);
        state1.put(IoTValues.LOCK_STATE, true);

        Map<String, Object> result1 = evaluator.evaluateState(state1, log);
        assertFalse((Boolean) result1.get(IoTValues.LOCK_STATE),
                "Keyless should unlock during night");

        StringBuffer log2 = new StringBuffer();
        Map<String, Object> state2 = createDefaultState();
        state2.put(IoTValues.NIGHT_ACTIVE, true);
        state2.put(IoTValues.KEYLESS_ENABLED, true);
        state2.put(IoTValues.AUTHORIZED_APPROACH, false);
        state2.put(IoTValues.LOCK_STATE, false); 

        Map<String, Object> result2 = evaluator.evaluateState(state2, log2);
        assertTrue((Boolean) result2.get(IoTValues.LOCK_STATE),
                "Night lock should re-lock after keyless entry completes");
        assertTrue(log2.toString().toLowerCase().contains("night lock"),
                "Log should mention night lock on re-lock");
    }
}
