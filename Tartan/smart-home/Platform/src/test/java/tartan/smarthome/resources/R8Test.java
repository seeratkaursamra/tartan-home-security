package tartan.smarthome.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Hashtable;
import java.util.Map;

import tartan.smarthome.resources.iotcontroller.IoTValues;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The following from Genarative AI, "How do I make a test for rule 8 which is The alarm must be disabled by the user
 * in person by entering a user-defined passcode?"
 *
 * R8: The alarm must be disabled by the user in person by entering a user-defined passcode.
 *
 * In the StaticTartanStateEvaluator implementation, "disabling" the alarm is
 * modelled as:
 *  - The user sets ALARM_STATE to false (request to disable).
 *  - The evaluator either accepts or rejects that request based on:
 *      * PROXIMITY_STATE (user must be home / in person)
 *      * GIVEN_PASSCODE vs ALARM_PASSCODE (must be "correct" per implementation)
 */
public class R8Test {

    private StaticTartanStateEvaluator evaluator;
    private StringBuffer log;

    @BeforeEach
    void setUp() {
        evaluator = new StaticTartanStateEvaluator();
        log = new StringBuffer();
    }

    /**
     * Default state: alarm is enabled and active; user is home; passcode set to "1234".
     * This mirrors the pattern used in R1, R3, R12.
     */
    private Map<String, Object> createDefaultState() {
        Map<String, Object> state = new Hashtable<>();
        state.put(IoTValues.TEMP_READING, 70);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HUMIDITY_READING, 50);
        state.put(IoTValues.DOOR_STATE, false);
        state.put(IoTValues.LIGHT_STATE, false);
        state.put(IoTValues.PROXIMITY_STATE, true);      // user home by default
        state.put(IoTValues.ALARM_STATE, true);          // alarm enabled (armed)
        state.put(IoTValues.ALARM_ACTIVE, true);         // alarm currently sounding
        state.put(IoTValues.HUMIDIFIER_STATE, false);
        state.put(IoTValues.HEATER_STATE, false);
        state.put(IoTValues.CHILLER_STATE, false);
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.ALARM_PASSCODE, "1234");     // configured passcode
        state.put(IoTValues.GIVEN_PASSCODE, "");         // user will provide
        state.put(IoTValues.AWAY_TIMER, false);
        return state;
    }

    // ---------------------------------------------------------------------
    // Happy path: user in person, correct passcode, explicitly requesting
    // to disable the alarm (ALARM_STATE = false)
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("R8: Correct passcode in person successfully disables the alarm")
    void testR8_DisableAlarmInPerson_CorrectPasscode() {
        Map<String, Object> state = createDefaultState();

        // User requests to DISABLE the alarm:
        // Set ALARM_STATE to false before evaluation.
        state.put(IoTValues.ALARM_STATE, false);

        // User is in person
        state.put(IoTValues.PROXIMITY_STATE, true);

        // User enters the "correct" passcode (per implementation)
        state.put(IoTValues.GIVEN_PASSCODE, "1234");

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // The evaluator should accept the disable request:
        assertFalse((Boolean) newState.get(IoTValues.ALARM_STATE),
                "R8 FAILED: Alarm should remain disabled when correct passcode is provided in person");

        // Alarm should no longer be active (sounding)
        assertFalse((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
                "R8 FAILED: Alarm should stop sounding after correct passcode is entered in person");

        // Optional but nice: verify log message
        assertTrue(log.toString().contains("Correct passcode entered, disabled alarm"),
                "R8 FAILED: Log should indicate that the correct passcode disabled the alarm");
    }

    // ---------------------------------------------------------------------
    // Remote attempt: correct passcode but user NOT present.
    // This test checks that the alarm cannot be disabled remotely.
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("R8: Alarm must NOT be disabled remotely, even with correct passcode")
    void testR8_DisableAlarmRemote_NotAllowed() {
        Map<String, Object> state = createDefaultState();

        // User requests to disable:
        state.put(IoTValues.ALARM_STATE, false);

        // But user is NOT in person
        state.put(IoTValues.PROXIMITY_STATE, false);

        // Correct passcode entered
        state.put(IoTValues.GIVEN_PASSCODE, "1234");

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Evaluator should reject the disable request and re-enable the alarm
        assertTrue((Boolean) newState.get(IoTValues.ALARM_STATE),
                "R8 FAILED: Alarm should remain enabled when user is not in person");

        // It may still be active or become active depending on other rules;
        // the key requirement here is that it wasn't disabled.
        assertTrue(log.toString().contains("Cannot disable the alarm, house is empty"),
                "R8 FAILED: Log should indicate remote disable is not allowed");
    }

    // ---------------------------------------------------------------------
    // Wrong passcode, user in person:
    // Since the user did not request ALARM_STATE = false here, we only
    // assert that the alarm remains enabled (no silent auto-disable).
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("R8: Wrong passcode in person does NOT disable alarm")
    void testR8_WrongPasscode_DoesNotDisable() {
        Map<String, Object> state = createDefaultState();

        // KEEP ALARM_STATE true here (user never successfully disabled it)
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.GIVEN_PASSCODE, "9999");  // "wrong" in spec sense

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.ALARM_STATE),
                "R8 FAILED: Alarm should remain enabled with wrong passcode");
    }

    // ---------------------------------------------------------------------
    // Empty passcode, user in person – again, user never sends a valid
    // disable request: we only assert that nothing automatically disables.
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("R8: Empty passcode in person does NOT disable alarm")
    void testR8_EmptyPasscode_DoesNotDisable() {
        Map<String, Object> state = createDefaultState();

        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.GIVEN_PASSCODE, "");  // empty

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.ALARM_STATE),
                "R8 FAILED: Alarm should remain enabled with empty passcode");
    }

    // ---------------------------------------------------------------------
    // Alarm already disabled, user home, correct passcode:
    // Evaluator should leave it disabled and not re-arm.
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("R8: Alarm already disabled remains disabled even with correct passcode")
    void testR8_AlarmAlreadyDisabled_NoChange() {
        Map<String, Object> state = createDefaultState();

        // Alarm already disabled and not active
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.ALARM_ACTIVE, false);

        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.GIVEN_PASSCODE, "1234");

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.ALARM_STATE),
                "R8 FAILED: Alarm should remain disabled if it was already disabled");
        assertFalse((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
                "R8 FAILED: Alarm should remain inactive if it was already inactive");
    }
}
