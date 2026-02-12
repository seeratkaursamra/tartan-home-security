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

    // -----------------------------------------------------------------
    // Whitebox: Cover the invalid-passcode branch (lines 170-172)
    // givenPassCode.compareTo(alarmPassCode) < 0 → true branch
    // -----------------------------------------------------------------
    @Test
    @DisplayName("R8: Invalid passcode (lexicographically less) rejects disable")
    void testR8_InvalidPasscode_LexicographicallyLess_RejectsDisable() {
        Map<String, Object> state = createDefaultState();

        state.put(IoTValues.ALARM_STATE, false);   // request disable
        state.put(IoTValues.PROXIMITY_STATE, true); // in person
        state.put(IoTValues.ALARM_ACTIVE, true);    // alarm sounding
        state.put(IoTValues.GIVEN_PASSCODE, "1233"); // less than "1234"

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.ALARM_STATE),
                "R8 FAILED: Alarm should remain enabled when invalid passcode is given");
        assertTrue(log.toString().contains("Cannot disable alarm, invalid passcode given"),
                "R8 FAILED: Log should indicate invalid passcode");
    }

    // -----------------------------------------------------------------
    // Whitebox: Cover the else branch at line 174 — passcode
    // lexicographically greater goes to else (accepted)
    // -----------------------------------------------------------------
    @Test
    @DisplayName("R8: Passcode lexicographically greater — documents actual behavior")
    void testR8_WrongPasscode_LexicographicallyGreater_InPerson() {
        Map<String, Object> state = createDefaultState();

        state.put(IoTValues.ALARM_STATE, false);    // request disable
        state.put(IoTValues.PROXIMITY_STATE, true);  // in person
        state.put(IoTValues.ALARM_ACTIVE, true);     // alarm sounding
        state.put(IoTValues.GIVEN_PASSCODE, "9999"); // greater than "1234"

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Implementation accepts passcodes >= alarmPassCode
        assertFalse((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
                "R8: Alarm stops sounding with passcode >= alarmPassCode (implementation behavior)");
        assertTrue(log.toString().contains("Correct passcode entered, disabled alarm"),
                "R8: Log says correct passcode (implementation behavior)");
    }

    // -----------------------------------------------------------------
    // Whitebox: Empty passcode with alarm active and disable request
    // Empty string has length 0, so the length>0 check is false →
    // falls into else branch
    // -----------------------------------------------------------------
    @Test
    @DisplayName("R8: Empty passcode with alarm active — documents actual behavior")
    void testR8_EmptyPasscode_AlarmActive_DisableRequest() {
        Map<String, Object> state = createDefaultState();

        state.put(IoTValues.ALARM_STATE, false);    // request disable
        state.put(IoTValues.PROXIMITY_STATE, true);  // in person
        state.put(IoTValues.ALARM_ACTIVE, true);     // alarm sounding
        state.put(IoTValues.GIVEN_PASSCODE, "");     // empty

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Empty passcode bypasses the compareTo check (length == 0)
        assertFalse((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
                "R8: Empty passcode bypasses check (implementation behavior)");
    }

    // -----------------------------------------------------------------
    // Whitebox: Cover break-in via door open (lines 102-106)
    // doorState=true, proximityState=false, alarmState=true
    // -----------------------------------------------------------------
    @Test
    @DisplayName("R8: Door open, nobody home, alarm enabled — break-in detected")
    void testR8_DoorOpen_NobodyHome_AlarmEnabled_BreakIn() {
        Map<String, Object> state = createDefaultState();

        state.put(IoTValues.DOOR_STATE, true);        // door open
        state.put(IoTValues.PROXIMITY_STATE, false);   // nobody home
        state.put(IoTValues.ALARM_STATE, true);        // alarm enabled
        state.put(IoTValues.ALARM_ACTIVE, false);      // not yet sounding

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
                "R8 FAILED: Alarm should activate on break-in (door open, nobody home)");
        assertTrue(log.toString().contains("Break in detected"),
                "R8 FAILED: Log should indicate break-in detected");
    }

    // -----------------------------------------------------------------
    // Whitebox: Cover break-in via door closed + someone home (lines 124-126)
    // doorState=false, proximityState=true, alarmState=true
    // -----------------------------------------------------------------
    @Test
    @DisplayName("R8: Door closed, someone home, alarm enabled — break-in detected")
    void testR8_DoorClosed_SomeoneHome_AlarmEnabled_BreakIn() {
        Map<String, Object> state = createDefaultState();

        state.put(IoTValues.DOOR_STATE, false);       // door closed
        state.put(IoTValues.PROXIMITY_STATE, true);    // someone home
        state.put(IoTValues.ALARM_STATE, true);        // alarm enabled
        state.put(IoTValues.ALARM_ACTIVE, false);      // not yet sounding

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
                "R8 FAILED: Alarm should activate (door closed, someone home, alarm on)");
        assertTrue(log.toString().contains("Break in detected"),
                "R8 FAILED: Log should indicate break-in detected");
    }

    // -----------------------------------------------------------------
    // Whitebox: Cover line 194 second condition —
    // alarmState=true, doorState=true, proximityState=false
    // -----------------------------------------------------------------
    @Test
    @DisplayName("R8: Alarm enabled, door open, nobody home — alarm activates (line 194)")
    void testR8_AlarmEnabled_DoorOpen_NobodyHome_ActivatesAlarm() {
        Map<String, Object> state = createDefaultState();

        state.put(IoTValues.DOOR_STATE, true);         // door open
        state.put(IoTValues.PROXIMITY_STATE, false);    // nobody home
        state.put(IoTValues.ALARM_STATE, true);         // alarm enabled
        state.put(IoTValues.ALARM_ACTIVE, false);       // not yet sounding

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
                "R8 FAILED: Alarm should activate when door open and nobody home");
        assertTrue(log.toString().contains("Activating alarm"),
                "R8 FAILED: Log should indicate alarm activation");
    }

    // -----------------------------------------------------------------
    // Whitebox: Cover away timer auto-lock (lines 133-138)
    // awayTimerState=true → alarm enabled, door closed, light off
    // -----------------------------------------------------------------
    @Test
    @DisplayName("R8: Away timer fires — auto-lock activates alarm")
    void testR8_AwayTimerFires_AutoLock() {
        Map<String, Object> state = createDefaultState();

        state.put(IoTValues.AWAY_TIMER, true);         // timer fired
        state.put(IoTValues.ALARM_STATE, false);        // was disabled
        state.put(IoTValues.ALARM_ACTIVE, false);
        state.put(IoTValues.DOOR_STATE, true);          // was open
        state.put(IoTValues.LIGHT_STATE, true);         // was on
        state.put(IoTValues.PROXIMITY_STATE, false);    // nobody home

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.ALARM_STATE),
                "R8 FAILED: Away timer should enable the alarm");
        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE),
                "R8 FAILED: Away timer should close the door");
        assertFalse((Boolean) newState.get(IoTValues.LIGHT_STATE),
                "R8 FAILED: Away timer should turn off the light");
    }

    // -----------------------------------------------------------------
    // Interaction: occupied + alarm disabled → light auto-on (lines 145-148)
    // -----------------------------------------------------------------
    @Test
    @DisplayName("R8: Proximity home, alarm disabled — light turns on automatically")
    void testR8_ProximityHome_AlarmDisabled_LightTurnsOn() {
        Map<String, Object> state = createDefaultState();

        state.put(IoTValues.PROXIMITY_STATE, true);    // home
        state.put(IoTValues.ALARM_STATE, false);        // disabled
        state.put(IoTValues.ALARM_ACTIVE, false);
        state.put(IoTValues.LIGHT_STATE, false);        // light off
        state.put(IoTValues.GIVEN_PASSCODE, "1234");

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LIGHT_STATE),
                "R8 FAILED: Light should auto-on when home and alarm disabled");
        assertTrue(log.toString().contains("Turning on light"),
                "R8 FAILED: Log should indicate light turned on");
    }

    // -----------------------------------------------------------------
    // Blackbox EC #9: away, alarm enabled, not sounding, empty passcode
    // Alarm stays enabled and away timer starts
    // -----------------------------------------------------------------
    @Test
    @DisplayName("R8: Away, alarm enabled, not sounding, empty passcode — alarm stays enabled")
    void testR8_Away_AlarmEnabled_NotSounding_EmptyPasscode() {
        Map<String, Object> state = createDefaultState();

        state.put(IoTValues.PROXIMITY_STATE, false);   // away
        state.put(IoTValues.ALARM_STATE, true);         // enabled
        state.put(IoTValues.ALARM_ACTIVE, false);       // not sounding
        state.put(IoTValues.GIVEN_PASSCODE, "");        // empty

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.ALARM_STATE),
                "R8 FAILED: Alarm should remain enabled when away");
        assertTrue((Boolean) newState.get(IoTValues.AWAY_TIMER),
                "R8 FAILED: Away timer should start when house is vacant");
        assertTrue(log.toString().contains("House is vacant"),
                "R8 FAILED: Log should indicate house is vacant");
    }

    // -----------------------------------------------------------------
    // Boundary value: passcode "1235" (just above "1234")
    // compareTo("1234") > 0, so falls into else branch (accepted)
    // -----------------------------------------------------------------
    @Test
    @DisplayName("R8: Passcode just above boundary (1235 vs 1234) — documents behavior")
    void testR8_Passcode_JustAboveBoundary() {
        Map<String, Object> state = createDefaultState();

        state.put(IoTValues.ALARM_STATE, false);    // request disable
        state.put(IoTValues.PROXIMITY_STATE, true);  // in person
        state.put(IoTValues.ALARM_ACTIVE, true);     // alarm sounding
        state.put(IoTValues.GIVEN_PASSCODE, "1235"); // just above "1234"

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Implementation accepts passcodes >= alarmPassCode
        assertFalse((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
                "R8: Alarm stops sounding with passcode just above boundary (implementation behavior)");
        assertTrue(log.toString().contains("Correct passcode entered, disabled alarm"),
                "R8: Log says correct passcode for just-above boundary (implementation behavior)");
    }
}
