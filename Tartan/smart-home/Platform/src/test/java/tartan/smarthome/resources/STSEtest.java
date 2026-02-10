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
 * and add it to the new file STSEtest, Sorted by what it tests, If you notice any tests missing that will need to be added for branch coverage add it."
 *
 * 2026-02-09 with assistance from openai, chatpgt 5.2, "do not add code, add ceomments how each test adheres to whitebox and blackbox testing"
 * BLACK-BOX DESIGN:
 * - Equivalence Class Partitioning (ECP) for core state variables:
 *   - proximityState ∈ {occupied(true), vacant(false)}
 *   - doorState ∈ {open(true), closed(false)}
 *   - alarmState ∈ {enabled(true), disabled(false)}
 *   - lightState ∈ {on(true), off(false)}
 *   - HVAC: hvacSetting ∈ {"Heater", "Chiller"} and tempReading relative to target ∈ {<, =, >}
 * - Boundary Value (BVA) for temperature comparisons:
 *   - temp < target (heater needed), temp == target (neither needed), temp > target (chiller needed)
 * - Interaction tests:
 *   - door/proximity/alarm combinations for break-in logic
 *   - proximity/alarm/light for auto-light behavior
 *   - awayTimer behavior forcing a secure state
 *
 * WHITE-BOX STRATEGY:
 * - Coverage-driven: tests target uncovered if/else branches, short-circuit conditions,
 *   and “already-on/already-off” cases (idempotence) inside evaluateState().
 * - Many tests assert on both output state and log strings to confirm a specific branch executed.

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
     * Baseline state acts as a default representative of the “normal” equivalence class.
     * Each test perturbs only the variables relevant to its partition / boundary / interaction.
     */
    private Map<String, Object> baseState() {
        Map<String, Object> state = new HashMap<>();

        state.put(IoTValues.TEMP_READING, 70);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HUMIDITY_READING, 40);

        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.HEATER_STATE, false);
        state.put(IoTValues.CHILLER_STATE, false);
        state.put(IoTValues.HUMIDIFIER_STATE, false);

        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.DOOR_STATE, false);
        state.put(IoTValues.LIGHT_STATE, false);

        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.ALARM_ACTIVE, false);
        state.put(IoTValues.ALARM_PASSCODE, "1234");
        state.put(IoTValues.GIVEN_PASSCODE, "");

        state.put(IoTValues.AWAY_TIMER, false);

        return state;
    }

    // ========== LIGHT AUTOMATION TESTS ==========

    /**
     * ECP: occupied + light requested ON.
     * WB: covers lightState == true branch and proximityState == true sub-branch ("Light on").
     */
    @Test
    public void testLightOnWhenSomeoneHome() {
        System.out.println("STSE: Testing light on when someone is home (normal operation)");

        Map<String, Object> state = baseState();
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LIGHT_STATE),"Light should remain ON when someone is home");
        assertTrue(log.toString().contains("Light on"), "Log should confirm light is on");
    }

    /**
     * ECP: vacant + light requested ON (invalid by rule implied in evaluator).
     * WB: covers lightState == true branch and !proximityState branch ("Cannot turn on light...").
     */
    @Test
    public void testLightOnWhenVacantForcesLightOff() {
        System.out.println("STSE: Testing light forced off when vacant");

        Map<String, Object> state = baseState();
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.PROXIMITY_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LIGHT_STATE), "Light should be forced OFF when house is vacant");
        assertTrue(log.toString().contains("Cannot turn on light because user not home"), "Log should explain why light was turned off");
    }

    /**
     * Interaction: occupied arrival + alarm disabled + light off => auto-light on.
     * WB: covers proximityState == true and inner condition (!lightState && !alarmState).
     */
    @Test
    public void testAutoLightWhenArrivingHome() {
        System.out.println("STSE: Testing auto-light when arriving home");

        Map<String, Object> state = baseState();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.LIGHT_STATE, false);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LIGHT_STATE), "Light should auto-turn on when someone arrives home with alarm disabled");
        assertTrue(log.toString().contains("Turning on light"), "Log should mention turning on light");
    }

    // ========== DOOR CONTROL TESTS ==========

    /**
     * ECP: door open + occupied (allowed) with alarm disabled.
     * WB: covers doorState == true and “else { log Door open }”.
     */
    @Test
    public void testDoorOpenWithSomeoneHome() {
        System.out.println("STSE: Testing door open with someone home (allowed)");

        Map<String, Object> state = baseState();
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.DOOR_STATE), "Door should remain open when someone is home");
        assertTrue(log.toString().contains("Door open"), "Log should confirm door is open");
    }

    /**
     * Interaction: door open + vacant + alarm enabled => break-in path activates alarmActiveState.
     * WB: covers doorState == true and (!proximityState && alarmState) branch.
     */
    @Test
    public void testDoorOpenVacantAlarmEnabledActivatesAlarm() {
        System.out.println("STSE: Testing door open + vacant + alarm enabled = break-in");

        Map<String, Object> state = baseState();
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.ALARM_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.ALARM_ACTIVE), "Alarm should activate when door opens with house vacant and alarm enabled");
        assertTrue(log.toString().contains("Break in detected"), "Log should mention break-in detection");
    }

    /**
     * Interaction: door open + vacant + alarm disabled => evaluator closes door.
     * WB: covers doorState == true and else-if (!proximityState) branch ("Closed door because house vacant").
     */
    @Test
    public void testDoorOpenVacantAlarmDisabledClosesDoor() {
        System.out.println("STSE: Testing door open + vacant + alarm disabled = close door");

        Map<String, Object> state = baseState();
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE), "Door should be closed when house is vacant and alarm is disabled");
        assertTrue(log.toString().contains("Closed door because house vacant"), "Log should explain door was closed");
    }

    /**
     * Interaction: door closed + occupied + alarm enabled => break-in path.
     * WB: covers else-if (!doorState) and (alarmState && proximityState) branch.
     */
    @Test
    public void testBreakInViaClosedDoor() {
        System.out.println("STSE: Testing break-in detection via closed door");

        Map<String, Object> state = baseState();
        state.put(IoTValues.DOOR_STATE, false);
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.ALARM_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.ALARM_ACTIVE), "Alarm should activate when someone is detected inside with closed door and alarm enabled");
        assertTrue(log.toString().contains("Break in detected"), "Log should mention break-in detection");
    }

    // ========== AWAY TIMER TESTS ==========

    /**
     * ECP: awayTimerState = true forces secure configuration.
     * WB: covers awayTimerState == true block which sets:
     *     light=false, door=false, alarm=true, awayTimer=false.
     */
    @Test
    public void testAwayTimerAutolockForcesSecureState() {
        System.out.println("STSE: Testing away timer autolock");

        Map<String, Object> state = baseState();
        state.put(IoTValues.AWAY_TIMER, true);
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LIGHT_STATE), "Light should be turned off by away timer");
        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE), "Door should be closed by away timer");
        assertTrue((Boolean) newState.get(IoTValues.ALARM_STATE), "Alarm should be enabled by away timer");
        assertFalse((Boolean) newState.get(IoTValues.AWAY_TIMER), "Away timer should be reset to false after triggering");
    }

    // ========== ALARM SYSTEM TESTS ==========

    /**
     * ECP: attempt to disable alarm while house vacant.
     * WB: covers !alarmState branch, then !proximityState sub-branch forcing alarmState=true.
     */
    @Test
    public void testCannotDisableAlarmWhenVacant() {
        System.out.println("STSE: Testing cannot disable alarm when house is vacant");

        Map<String, Object> state = baseState();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.ALARM_ACTIVE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.ALARM_STATE), "Alarm should be forced ON when trying to disable while house is vacant");
        assertTrue(log.toString().contains("Cannot disable the alarm, house is empty"), "Log should explain why alarm cannot be disabled");
    }

    /**
     * ECP: alarmActive=true + invalid passcode (givenPassCode length > 0 and compareTo < 0).
     * WB: covers the “invalid passcode” branch that forces alarmState=true.
     */
    @Test
    public void testAlarmActiveInvalidPasscodeKeepsAlarmEnabled() {
        System.out.println("STSE: Testing invalid passcode keeps alarm active");

        Map<String, Object> state = baseState();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.ALARM_ACTIVE, true);
        state.put(IoTValues.ALARM_PASSCODE, "1234");
        state.put(IoTValues.GIVEN_PASSCODE, "0000");

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.ALARM_STATE), "Alarm should remain enabled with invalid passcode");
        assertTrue((Boolean) newState.get(IoTValues.ALARM_ACTIVE), "Alarm should still be active with invalid passcode");
        assertTrue(log.toString().contains("Cannot disable alarm, invalid passcode given"), "Log should mention invalid passcode");
    }

    /**
     * ECP: alarmActive=true + “valid” passcode per current implementation (compareTo >= 0).
     * WB: covers the else-branch that disables alarmActiveState.
     */
    @Test
    public void testAlarmActiveValidPasscodeDisablesAlarmActive() {
        System.out.println("STSE: Testing valid passcode disables alarm");

        Map<String, Object> state = baseState();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.ALARM_ACTIVE, true);
        state.put(IoTValues.ALARM_PASSCODE, "1234");
        state.put(IoTValues.GIVEN_PASSCODE, "9999");

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.ALARM_ACTIVE), "Alarm should be deactivated with valid passcode");
        assertTrue(log.toString().contains("Correct passcode entered, disabled alarm"), "Log should mention correct passcode");
    }

    // ========== HVAC SYSTEM TESTS (BVA on temp comparisons) ==========

    /**
     * BVA: temp > target => cooling needed.
     * WB: covers tempReading > targetTempSetting branch and inner (!chillerOnState) activation.
     */
    @Test
    public void testACTurnsOnWhenTempAboveTarget() {
        System.out.println("STSE: Testing AC turns on when temperature above target");

        Map<String, Object> state = baseState();
        state.put(IoTValues.TEMP_READING, 75);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HVAC_MODE, "Chiller");
        state.put(IoTValues.HEATER_STATE, false);
        state.put(IoTValues.CHILLER_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.CHILLER_STATE), "Chiller should be ON when temp above target");
        assertFalse((Boolean) newState.get(IoTValues.HEATER_STATE), "Heater should be OFF");
        assertEquals("Chiller", newState.get(IoTValues.HVAC_MODE), "HVAC mode should be Chiller");
    }

    /**
     * Interaction/regression: “switching” from heater to chiller mode under temp > target.
     * WB: confirms heater forced off when hvacSetting="Chiller".
     */
    @Test
    public void testSwitchingFromHeaterToChiller() {
        System.out.println("STSE: Testing switch from Heater to Chiller mode");

        Map<String, Object> state = baseState();
        state.put(IoTValues.TEMP_READING, 75);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HVAC_MODE, "Chiller");
        state.put(IoTValues.HEATER_STATE, true);
        state.put(IoTValues.CHILLER_STATE, false);
        state.put(IoTValues.HUMIDIFIER_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.CHILLER_STATE), "Chiller should be ON because temp (75) > target (70)");
        assertFalse((Boolean) newState.get(IoTValues.HEATER_STATE), "Heater should be OFF when cooling is needed");
        assertEquals("Chiller", newState.get(IoTValues.HVAC_MODE), "HVAC mode should be Chiller");
    }

    /**
     * BVA: temp < target => heating needed.
     * WB: covers tempReading < targetTempSetting branch.
     */
    @Test
    public void testHeaterTurnsOnWhenTempBelowTarget() {
        System.out.println("STSE: Testing heater turns on when temperature below target");

        Map<String, Object> state = baseState();
        state.put(IoTValues.TEMP_READING, 65);
        state.put(IoTValues.TARGET_TEMP, 72);
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.HEATER_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.HEATER_STATE), "Heater should be ON when temp below target");
        assertFalse((Boolean) newState.get(IoTValues.CHILLER_STATE), "Chiller should be OFF");
        assertEquals("Heater", newState.get(IoTValues.HVAC_MODE), "HVAC mode should be Heater");
    }

    // ========== EDGE / COVERAGE-DRIVEN TESTS ==========

    /**
     * WB (defensive branch): CHILLER_STATE missing (null) + temp == target.
     * This documents the behavior that the evaluator’s “AC not needed” else-branch sets chillerOnState=false,
     * avoiding NPE later in hvacSetting inference.
     */
    @Test
    public void testChillerStateNullHandledByElseBranch() {
        System.out.println("STSE: Testing chillerState null is handled");

        Map<String, Object> state = baseState();
        state.put(IoTValues.TEMP_READING, 70);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.HEATER_STATE, false);
        // CHILLER_STATE intentionally not set

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertNotNull(newState);
        assertFalse((Boolean) newState.get(IoTValues.CHILLER_STATE), "Chiller should be false after null is handled");
    }

    /**
     * ECP: vacant => away timer starts.
     * WB: covers proximityState == false branch setting awayTimerState=true and logging start.
     */
    @Test
    public void testProximityStateStartsAwayTimer() {
        System.out.println("STSE: Testing away timer starts when house becomes vacant");

        Map<String, Object> state = baseState();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.AWAY_TIMER, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.AWAY_TIMER), "Away timer should start when house becomes vacant");
        assertTrue(log.toString().contains("House is vacant, starting away timer"), "Log should mention starting away timer");
    }

    /**
     * WB: hvacSetting="Heater" branch turns off chiller if it was running.
     */
    @Test
    public void testHeaterModeDisablesRunningChiller() {
        Map<String, Object> state = baseState();
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.HEATER_STATE, true);
        state.put(IoTValues.CHILLER_STATE, true);
        state.put(IoTValues.HUMIDIFIER_STATE, true);
        state.put(IoTValues.TEMP_READING, 75);
        state.put(IoTValues.TARGET_TEMP, 70);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.CHILLER_STATE), "Chiller should be OFF when HVAC mode is Heater");
        assertTrue(log.toString().contains("Turning off air conditioner"), "Log should mention turning off air conditioner");
    }

    /**
     * WB: hvacSetting="Chiller" branch turns off heater if it was running.
     */
    @Test
    public void testChillerModeDisablesRunningHeater() {
        Map<String, Object> state = baseState();
        state.put(IoTValues.HVAC_MODE, "Chiller");
        state.put(IoTValues.CHILLER_STATE, true);
        state.put(IoTValues.HEATER_STATE, true);
        state.put(IoTValues.HUMIDIFIER_STATE, false);
        state.put(IoTValues.TEMP_READING, 65);
        state.put(IoTValues.TARGET_TEMP, 70);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.HEATER_STATE), "Heater should be OFF when HVAC mode is Chiller");
        assertTrue(log.toString().contains("Turning off heater"), "Log should mention turning off heater");
    }

    /**
     * WB: door closed + NOT(alarm && proximity) branch logs "Closed door".
     */
    @Test
    public void testDoorClosedNormalConditionLogsClosed() {
        System.out.println("STSE: Testing door closed normal condition");

        Map<String, Object> state = baseState();
        state.put(IoTValues.DOOR_STATE, false);
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE), "Door should remain closed");
        assertTrue(log.toString().contains("Closed door"), "Log should contain 'Closed door' message");
    }

    /**
     * WB: proximity true but light already ON => skip auto-light inner if.
     */
    @Test
    public void testProximityHomeLightAlreadyOnNoAutoLight() {
        System.out.println("STSE: Testing no auto-light when light already on");

        Map<String, Object> state = baseState();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LIGHT_STATE), "Light should stay on");
        assertTrue(log.toString().contains("House is occupied"), "Log should say house is occupied");
        assertFalse(log.toString().contains("Turning on light"), "Log should NOT mention turning on light (it was already on)");
    }

    /**
     * WB: proximity true but alarm enabled => skip auto-light inner if.
     */
    @Test
    public void testProximityHomeAlarmEnabledNoAutoLight() {
        System.out.println("STSE: Testing no auto-light when alarm is enabled");

        Map<String, Object> state = baseState();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.LIGHT_STATE, false);
        state.put(IoTValues.ALARM_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue(log.toString().contains("House is occupied"), "Log should say house is occupied");
        assertFalse(log.toString().contains("Turning on light"), "Log should NOT mention turning on light (alarm is enabled)");
    }

    /**
     * WB/documentation: empty passcode triggers the else-branch due to (length > 0) being false.
     * This documents current behavior (arguably a defect), and also increases branch coverage.
     */
    @Test
    public void testAlarmActiveEmptyPasscodeFallsToElseBranch() {
        System.out.println("STSE: Testing alarm active with empty passcode (hits else branch)");

        Map<String, Object> state = baseState();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.ALARM_ACTIVE, true);
        state.put(IoTValues.ALARM_PASSCODE, "1234");
        state.put(IoTValues.GIVEN_PASSCODE, "");

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.ALARM_ACTIVE), "Alarm active should be false (empty passcode falls to else branch - possible bug)");
        assertTrue(log.toString().contains("Correct passcode entered, disabled alarm"), "Log should say correct passcode (even though it was empty - possible bug)");
    }

    /**
     * WB: temp > target with chiller already ON => inner (!chillerOnState) is false (idempotence branch).
     */
    @Test
    public void testChillerAlreadyOnWhenTempAboveTarget() {
        System.out.println("STSE: Testing chiller already on, no redundant activation");

        Map<String, Object> state = baseState();
        state.put(IoTValues.TEMP_READING, 80);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HVAC_MODE, "Chiller");
        state.put(IoTValues.CHILLER_STATE, true);
        state.put(IoTValues.HEATER_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.CHILLER_STATE), "Chiller should remain ON");
        assertFalse((Boolean) newState.get(IoTValues.HEATER_STATE), "Heater should be OFF");
        assertFalse(log.toString().contains("Turning on air conditioner"), "Log should NOT mention turning on AC (it was already on)");
    }

    /**
     * WB: door open + occupied + alarm enabled => falls to "Door open" branch (no forced close / no break-in).
     */
    @Test
    public void testDoorOpenSomeoneHomeAlarmEnabled() {
        System.out.println("STSE: Testing door open with someone home and alarm enabled");

        Map<String, Object> state = baseState();
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.ALARM_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.DOOR_STATE), "Door should remain open");
        assertTrue(log.toString().contains("Door open"), "Log should say door open");
        assertTrue(log.toString().contains("Alarm enabled"), "Log should confirm alarm is enabled");
    }

    /**
     * BVA: temp == target (equality boundary).
     * WB: covers heater “not needed” else and chiller “not needed” else.
     */
    @Test
    public void testTempEqualsTargetNoHVACNeeded() {
        System.out.println("STSE: Testing temp equals target, no HVAC needed");

        Map<String, Object> state = baseState();
        state.put(IoTValues.TEMP_READING, 70);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.HEATER_STATE, true);
        state.put(IoTValues.CHILLER_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.HEATER_STATE),"Heater should be OFF when temp equals target");
        assertFalse((Boolean) newState.get(IoTValues.CHILLER_STATE), "Chiller should be OFF when temp equals target");
    }
}
