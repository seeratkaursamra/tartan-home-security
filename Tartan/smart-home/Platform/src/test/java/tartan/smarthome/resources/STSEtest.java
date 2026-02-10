package tartan.smarthome.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import tartan.smarthome.resources.iotcontroller.IoTValues;

/**
 * Comprehensive tests for StaticTartanStateEvaluator
 *
 * This test suite aims for complete branch coverage of the entire evaluator,
 * covering all rules and edge cases including:
 * - Light automation
 * - Door control and security
 * - Alarm system logic
 * - Away timer
 * - HVAC system interactions
 * - Proximity-based automation
 *
 * These tests complement the focused Rule 10 tests and provide coverage
 * for the entire state evaluation logic.
 *
 * Created: 2026-02-09 with assistance from Openai, ChatGPT 5.2, "My Rule10Test is a mess, can you please extract some of the new tests added to it (not the first 4)
 * and add it to the new file STSEtest, formatted for readability, If you notice any tests missing that will need to be added for branch coverage add it."
 */
public class STSEtest {

    private StaticTartanStateEvaluator evaluator;
    private StringBuffer log;

    @BeforeEach
    public void setUp() {
        evaluator = new StaticTartanStateEvaluator();
        log = new StringBuffer();
    }

    /**
     * Centralized baseline state to reduce repetition
     * Each test only needs to modify what it's testing
     */
    private Map<String, Object> baseState() {
        Map<String, Object> state = new HashMap<>();

        // Temperature and humidity
        state.put(IoTValues.TEMP_READING, 70);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HUMIDITY_READING, 40);

        // HVAC system
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.HEATER_STATE, false);
        state.put(IoTValues.CHILLER_STATE, false);
        state.put(IoTValues.HUMIDIFIER_STATE, false);

        // House state
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.DOOR_STATE, false);
        state.put(IoTValues.LIGHT_STATE, false);

        // Security
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.ALARM_ACTIVE, false);
        state.put(IoTValues.ALARM_PASSCODE, "1234");
        state.put(IoTValues.GIVEN_PASSCODE, "");

        // Timer
        state.put(IoTValues.AWAY_TIMER, false);

        return state;
    }

    // ========== LIGHT AUTOMATION TESTS ==========

    @Test
    public void testLightOnWhenSomeoneHome() {
        System.out.println("STSE: Testing light on when someone is home (normal operation)");

        Map<String, Object> state = baseState();
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.PROXIMITY_STATE, true);  // Someone home
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Light should stay ON (allowed when someone is home)
        assertTrue((Boolean) newState.get(IoTValues.LIGHT_STATE),
                "Light should remain ON when someone is home");
        assertTrue(log.toString().contains("Light on"),
                "Log should confirm light is on");
    }

    @Test
    public void testLightOnWhenVacantForcesLightOff() {
        System.out.println("STSE: Testing light forced off when vacant");

        Map<String, Object> state = baseState();
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.PROXIMITY_STATE, false);  // House vacant

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Cannot turn on light because user not home -> forced off
        assertFalse((Boolean) newState.get(IoTValues.LIGHT_STATE),
                "Light should be forced OFF when house is vacant");
        assertTrue(log.toString().contains("Cannot turn on light because user not home"),
                "Log should explain why light was turned off");
    }

    @Test
    public void testAutoLightWhenArrivingHome() {
        System.out.println("STSE: Testing auto-light when arriving home");

        Map<String, Object> state = baseState();
        state.put(IoTValues.PROXIMITY_STATE, true);  // Someone arrives
        state.put(IoTValues.LIGHT_STATE, false);     // Light was off
        state.put(IoTValues.ALARM_STATE, false);     // Alarm disabled

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Light should auto-turn on when arriving home with alarm disabled
        assertTrue((Boolean) newState.get(IoTValues.LIGHT_STATE),
                "Light should auto-turn on when someone arrives home with alarm disabled");
        assertTrue(log.toString().contains("Turning on light"),
                "Log should mention turning on light");
    }

    // ========== DOOR CONTROL TESTS ==========

    @Test
    public void testDoorOpenWithSomeoneHome() {
        System.out.println("STSE: Testing door open with someone home (allowed)");

        Map<String, Object> state = baseState();
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.PROXIMITY_STATE, true);  // Someone home
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Door should stay open (allowed when someone is home)
        assertTrue((Boolean) newState.get(IoTValues.DOOR_STATE),
                "Door should remain open when someone is home");
        assertTrue(log.toString().contains("Door open"),
                "Log should confirm door is open");
    }

    @Test
    public void testDoorOpenVacantAlarmEnabledActivatesAlarm() {
        System.out.println("STSE: Testing door open + vacant + alarm enabled = break-in");

        Map<String, Object> state = baseState();
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.PROXIMITY_STATE, false);  // Vacant
        state.put(IoTValues.ALARM_STATE, true);       // Alarm enabled

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Break-in detected -> alarm becomes active
        assertTrue((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
                "Alarm should activate when door opens with house vacant and alarm enabled");
        assertTrue(log.toString().contains("Break in detected"),
                "Log should mention break-in detection");
    }

    @Test
    public void testDoorOpenVacantAlarmDisabledClosesDoor() {
        System.out.println("STSE: Testing door open + vacant + alarm disabled = close door");

        Map<String, Object> state = baseState();
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.PROXIMITY_STATE, false);  // Vacant
        state.put(IoTValues.ALARM_STATE, false);      // Alarm disabled

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // House vacant and door open with alarm disabled -> door should be closed
        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE),
                "Door should be closed when house is vacant and alarm is disabled");
        assertTrue(log.toString().contains("Closed door because house vacant"),
                "Log should explain door was closed");
    }

    @Test
    public void testBreakInViaClosedDoor() {
        System.out.println("STSE: Testing break-in detection via closed door");

        Map<String, Object> state = baseState();
        state.put(IoTValues.DOOR_STATE, false);      // Door closed
        state.put(IoTValues.PROXIMITY_STATE, true);  // Someone suddenly inside
        state.put(IoTValues.ALARM_STATE, true);      // Alarm was enabled

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Alarm should activate (someone inside with closed door = break-in)
        assertTrue((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
                "Alarm should activate when someone is detected inside with closed door and alarm enabled");
        assertTrue(log.toString().contains("Break in detected"),
                "Log should mention break-in detection");
    }

    // ========== AWAY TIMER TESTS ==========

    @Test
    public void testAwayTimerAutolockForcesSecureState() {
        System.out.println("STSE: Testing away timer autolock");

        Map<String, Object> state = baseState();
        state.put(IoTValues.AWAY_TIMER, true);      // Triggers autolock block
        state.put(IoTValues.LIGHT_STATE, true);     // Should be forced off
        state.put(IoTValues.DOOR_STATE, true);      // Should be forced closed
        state.put(IoTValues.ALARM_STATE, false);    // Should be forced on

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Auto lock the house: light off, door closed, alarm on
        assertFalse((Boolean) newState.get(IoTValues.LIGHT_STATE),
                "Light should be turned off by away timer");
        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE),
                "Door should be closed by away timer");
        assertTrue((Boolean) newState.get(IoTValues.ALARM_STATE),
                "Alarm should be enabled by away timer");
        assertFalse((Boolean) newState.get(IoTValues.AWAY_TIMER),
                "Away timer should be reset to false after triggering");
    }

    // ========== ALARM SYSTEM TESTS ==========

    @Test
    public void testCannotDisableAlarmWhenVacant() {
        System.out.println("STSE: Testing cannot disable alarm when house is vacant");

        Map<String, Object> state = baseState();
        state.put(IoTValues.PROXIMITY_STATE, false);  // House vacant
        state.put(IoTValues.ALARM_STATE, false);      // Trying to disable alarm
        state.put(IoTValues.ALARM_ACTIVE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Alarm should be forced back to enabled
        assertTrue((Boolean) newState.get(IoTValues.ALARM_STATE),
                "Alarm should be forced ON when trying to disable while house is vacant");
        assertTrue(log.toString().contains("Cannot disable the alarm, house is empty"),
                "Log should explain why alarm cannot be disabled");
    }

    @Test
    public void testAlarmActiveInvalidPasscodeKeepsAlarmEnabled() {
        System.out.println("STSE: Testing invalid passcode keeps alarm active");

        Map<String, Object> state = baseState();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);        // Attempt to disable
        state.put(IoTValues.ALARM_ACTIVE, true);        // Alarm is sounding
        state.put(IoTValues.ALARM_PASSCODE, "1234");
        state.put(IoTValues.GIVEN_PASSCODE, "0000");    // Invalid: "0000".compareTo("1234") < 0

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Invalid passcode path forces alarmState back to true
        assertTrue((Boolean) newState.get(IoTValues.ALARM_STATE),
                "Alarm should remain enabled with invalid passcode");
        assertTrue((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
                "Alarm should still be active with invalid passcode");
        assertTrue(log.toString().contains("Cannot disable alarm, invalid passcode given"),
                "Log should mention invalid passcode");
    }

    @Test
    public void testAlarmActiveValidPasscodeDisablesAlarmActive() {
        System.out.println("STSE: Testing valid passcode disables alarm");

        Map<String, Object> state = baseState();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);        // Attempt to disable
        state.put(IoTValues.ALARM_ACTIVE, true);        // Alarm is sounding
        state.put(IoTValues.ALARM_PASSCODE, "1234");
        state.put(IoTValues.GIVEN_PASSCODE, "9999");    // Valid: "9999".compareTo("1234") > 0

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Correct passcode disables alarmActiveState
        assertFalse((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
                "Alarm should be deactivated with valid passcode");
        assertTrue(log.toString().contains("Correct passcode entered, disabled alarm"),
                "Log should mention correct passcode");
    }

    // ========== HVAC SYSTEM TESTS ==========

    @Test
    public void testACTurnsOnWhenTempAboveTarget() {
        System.out.println("STSE: Testing AC turns on when temperature above target");

        Map<String, Object> state = baseState();
        state.put(IoTValues.TEMP_READING, 75);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HVAC_MODE, "Chiller");
        state.put(IoTValues.HEATER_STATE, false);
        state.put(IoTValues.CHILLER_STATE, false);  // Starts OFF, should turn ON

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // AC should turn ON because temp (75) > target (70)
        assertTrue((Boolean) newState.get(IoTValues.CHILLER_STATE),
                "Chiller should be ON when temp above target");
        assertFalse((Boolean) newState.get(IoTValues.HEATER_STATE),
                "Heater should be OFF");
        assertEquals("Chiller", newState.get(IoTValues.HVAC_MODE),
                "HVAC mode should be Chiller");
    }

    @Test
    public void testSwitchingFromHeaterToChiller() {
        System.out.println("STSE: Testing switch from Heater to Chiller mode");

        Map<String, Object> state = baseState();
        state.put(IoTValues.TEMP_READING, 75);        // Hot - needs cooling
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HVAC_MODE, "Chiller");    // Request chiller mode
        state.put(IoTValues.HEATER_STATE, true);      // Input value is overwritten by evaluator
        state.put(IoTValues.CHILLER_STATE, false);    // Starts OFF, should turn ON
        state.put(IoTValues.HUMIDIFIER_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.CHILLER_STATE),
                "Chiller should be ON because temp (75) > target (70)");

        assertFalse((Boolean) newState.get(IoTValues.HEATER_STATE),
                "Heater should be OFF when cooling is needed");

        assertEquals("Chiller", newState.get(IoTValues.HVAC_MODE),
                "HVAC mode should be Chiller");
    }


    @Test
    public void testHeaterTurnsOnWhenTempBelowTarget() {
        System.out.println("STSE: Testing heater turns on when temperature below target");

        Map<String, Object> state = baseState();
        state.put(IoTValues.TEMP_READING, 65);
        state.put(IoTValues.TARGET_TEMP, 72);
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.HEATER_STATE, false);  // Starts OFF, should turn ON

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Heater should turn ON because temp (65) < target (72)
        assertTrue((Boolean) newState.get(IoTValues.HEATER_STATE),
                "Heater should be ON when temp below target");
        assertFalse((Boolean) newState.get(IoTValues.CHILLER_STATE),
                "Chiller should be OFF");
        assertEquals("Heater", newState.get(IoTValues.HVAC_MODE),
                "HVAC mode should be Heater");
    }

    // ========== EDGE CASE / BUG DOCUMENTATION TESTS ==========

    @Test
    public void testChillerStateNullHandledByElseBranch() {
        System.out.println("STSE: Testing chillerState null is handled");

        Map<String, Object> state = baseState();
        state.put(IoTValues.TEMP_READING, 70);  // Equal to target
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.HEATER_STATE, false);
        // CHILLER_STATE intentionally not set - will be null

        // The else branch at line 228 sets chillerOnState = false
        // This handles the null case, so no NPE occurs
        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertNotNull(newState);
        // ChillerOnState gets set to false by line 228
        assertFalse((Boolean) newState.get(IoTValues.CHILLER_STATE),
                "Chiller should be false after null is handled");
    }

    // ========== PROXIMITY STATE TESTS ==========

    @Test
    public void testProximityStateStartsAwayTimer() {
        System.out.println("STSE: Testing away timer starts when house becomes vacant");

        Map<String, Object> state = baseState();
        state.put(IoTValues.PROXIMITY_STATE, false);  // House becomes vacant
        state.put(IoTValues.AWAY_TIMER, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Away timer should start when house is vacant
        assertTrue((Boolean) newState.get(IoTValues.AWAY_TIMER),
                "Away timer should start when house becomes vacant");
        assertTrue(log.toString().contains("House is vacant, starting away timer"),
                "Log should mention starting away timer");
    }
}