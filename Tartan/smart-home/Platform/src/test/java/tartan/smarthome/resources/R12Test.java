package tartan.smarthome.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Hashtable;
import java.util.Map;

import tartan.smarthome.resources.iotcontroller.IoTValues;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R12: If the house is empty, then start the away timer.
 *
 * --- Test design (Part 1) ---
 *
 * Blackbox design:
 * - Equivalence classes: (1) house empty (proximity = false), (2) house occupied (proximity = true).
 *   R12 applies only to class (1); for (2) the away timer must not be set by R12.
 * - Boundary: one test at each side of the partition (empty vs occupied).
 * - Interactions: house empty with door open (another rule closes door first); house empty with
 *   away timer already true in input (earlier block resets it, then R12 sets it true again).
 * Oracle: concrete inputs and expected AWAY_TIMER and log content.
 *
 * Whitebox design:
 * - Branch at "if (proximityState)" (evaluator): need both branches so the R12 block (else)
 *   is executed and the "house occupied" branch is executed. This yields full branch coverage
 *   for the R12-related decision.
 */
public class R12Test {

    private StaticTartanStateEvaluator evaluator;
    private StringBuffer log;

    private static final String R12_LOG_MESSAGE = "House is vacant, starting away timer";

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
        state.put(IoTValues.HUMIDIFIER_STATE, false);
        state.put(IoTValues.HEATER_STATE, false);
        state.put(IoTValues.CHILLER_STATE, false);
        state.put(IoTValues.ALARM_ACTIVE, false);
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.ALARM_PASSCODE, "1234");
        state.put(IoTValues.GIVEN_PASSCODE, "");
        state.put(IoTValues.AWAY_TIMER, false);
        return state;
    }

    // ---- Blackbox: equivalence class "house empty" ----

    @Test
    @DisplayName("R12: House empty (proximity false) -> away timer started")
    void testR12_HouseEmpty_ShouldStartAwayTimer() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.AWAY_TIMER, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.AWAY_TIMER),
            "R12: When the house is empty, the away timer should be started");
        assertTrue(log.toString().contains(R12_LOG_MESSAGE),
            "R12: Log should record that away timer was started when house vacant");
    }

    @Test
    @DisplayName("R12: House empty with door open -> away timer still started (door closed by other rule first)")
    void testR12_HouseEmpty_DoorOpen_AwayTimerStillStarted() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.AWAY_TIMER, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.AWAY_TIMER),
            "R12: When house is empty, away timer should start even if door was open (other rule closes door)");
        assertTrue(log.toString().contains(R12_LOG_MESSAGE),
            "R12: Log should contain away timer message when house vacant");
    }

    @Test
    @DisplayName("R12: House empty with away timer already true -> still true after R12 (reset then set again)")
    void testR12_HouseEmpty_AwayTimerAlreadyTrue_RemainsTrue() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.AWAY_TIMER, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.AWAY_TIMER),
            "R12: When house is empty, away timer should be true (R12 sets it after earlier reset)");
        assertTrue(log.toString().contains(R12_LOG_MESSAGE),
            "R12: Log should contain away timer message when house vacant");
    }

    // ---- Blackbox: equivalence class "house occupied" ----

    @Test
    @DisplayName("R12: House occupied (proximity true) -> away timer not started by R12")
    void testR12_HouseOccupied_AwayTimerNotStarted() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.AWAY_TIMER, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.AWAY_TIMER),
            "R12: When the house is occupied, the away timer should not be started");
        assertFalse(log.toString().contains(R12_LOG_MESSAGE),
            "R12: Log must not contain 'House is vacant, starting away timer' when house is occupied");
    }

    // ---- Whitebox: branch coverage (both branches of proximity check) ----

    @Test
    @DisplayName("R12 whitebox: else branch (house vacant) covered by house-empty tests above")
    void testR12_Whitebox_VacantBranch_LogAndAwayTimerSet() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.PROXIMITY_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.AWAY_TIMER));
        assertTrue(log.toString().contains(R12_LOG_MESSAGE));
    }

    @Test
    @DisplayName("R12 whitebox: if branch (house occupied) - no R12 effect")
    void testR12_Whitebox_OccupiedBranch_NoAwayTimerFromR12() {
        Map<String, Object> state = createDefaultState();
        state.put(IoTValues.PROXIMITY_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse(log.toString().contains(R12_LOG_MESSAGE));
        assertFalse((Boolean) newState.get(IoTValues.AWAY_TIMER));
    }
}
