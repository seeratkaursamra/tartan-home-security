package tartan.smarthome.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

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
     * Helper for mutation testing:
     * We need a robust way to distinguish between different alarm logs.
     * PIT has survivors on the composite alarm condition that gates whether "Activating alarm" is appended.
     * Counting occurrences avoids false positives due to the earlier "Break in detected: Activating alarm" message.
     */
    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }


    /**
     * Baseline state acts as a default representative of the “normal” equivalence class.
     * Each test perturbs only the variables relevant to its partition / boundary / interaction.
     */

    // ========== LIGHT AUTOMATION TESTS ==========

    /**
     * ECP: occupied + light requested ON.
     * WB: covers lightState == true branch and proximityState == true sub-branch ("Light on").
     */
    @Test
    public void testLightOnWhenSomeoneHome() {
        System.out.println("STSE: Testing light on when someone is home (normal operation)");

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.AWAY_TIMER, true);
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.PROXIMITY_STATE, true);


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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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
        Map<String, Object> state = TestStateFactory.baseStateCopy();

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
        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

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

        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.TEMP_READING, 70);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.HEATER_STATE, true);
        state.put(IoTValues.CHILLER_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.HEATER_STATE),"Heater should be OFF when temp equals target");
        assertFalse((Boolean) newState.get(IoTValues.CHILLER_STATE), "Chiller should be OFF when temp equals target");
    }

    // =========Mutation testing ========
    // openai, chatgpt 5.2, 2026-02-13, "Please provide assistance with mutation testing."
    // openai, chatgpt 5.2, 2026-02-13, "Here are my Mutation tests, can you please check them for any obvious errors and add comments to the tests that are already there?"

    //This test was added but it technically only tests a system.out.println, technically this is a mutation but not the kind
    // of thing we would normally mutation test, we can (and I will, because it is a mutation), but technically we should
    // treat it differently.
    /**
     * - PIT survivor: VoidMethodCallMutator removed call to System.out.println at STSE line ~69.
     *
     * White-box: asserts a side-effect of the specific println line being executed.
     * Note: this is not "good design" for production behavior, but it kills the surviving mutant.
     */
    @Test
    public void testEvaluateStatePrintsStaticMessage() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        PrintStream originalOut = System.out;
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        try {
            evaluator.evaluateState(state, log);
        } finally {
            System.setOut(originalOut);
        }

        assertTrue(outContent.toString().contains("Evaluating new state statically"),
                "Expected STSE to print its static evaluation banner (kills println removal mutant).");
    }

    /**
     * MUTATION TARGET:
     * - PIT survivors in isNightTime(): ConditionalsBoundaryMutator at lines ~17 and ~20.
     *
     * Key kill case:
     * - nightStart == nightEnd must NOT be treated as "crossing midnight".
     *   If mutated from '>' to '>=' it flips behavior to "always night" (because X>=start OR X<end with start=end).
     */
    @Test
    public void testIsNightTime_startEqualsEnd_isNeverNight() {
        // start == end should produce an empty night window (always false)
        assertFalse(evaluator.isNightTime(0, 22, 22));
        assertFalse(evaluator.isNightTime(21, 22, 22));
        assertFalse(evaluator.isNightTime(22, 22, 22));
        assertFalse(evaluator.isNightTime(23, 22, 22));
    }

    /**
     * MUTATION TARGET:
     * - PIT survivors in isNightTime(): boundary changes in the non-crossing case.
     *
     * We assert:
     * - Inclusive start (hour == nightStart is night)
     * - Exclusive end (hour == nightEnd is NOT night)
     */
    @Test
    public void testIsNightTime_nonCrossingWindow_boundaries() {
        int start = 20;
        int end = 23;

        assertTrue(evaluator.isNightTime(20, start, end), "Start boundary should be included.");
        assertTrue(evaluator.isNightTime(22, start, end), "Interior hour should be night.");
        assertFalse(evaluator.isNightTime(23, start, end), "End boundary should be excluded.");
        assertFalse(evaluator.isNightTime(19, start, end), "Hour before start should be false.");
    }

    /**
     * MUTATION TARGET:
     * - Ensures the midnight-crossing branch is correct on both sides of midnight.
     *
     * Window: 22 -> 6
     * - 22,23,0,5 should be night
     * - 6,21 should not be night
     */
    @Test
    public void testIsNightTime_crossMidnightWindow_boundaries() {
        int start = 22;
        int end = 6;

        assertTrue(evaluator.isNightTime(22, start, end));
        assertTrue(evaluator.isNightTime(23, start, end));
        assertTrue(evaluator.isNightTime(0, start, end));
        assertTrue(evaluator.isNightTime(5, start, end));

        assertFalse(evaluator.isNightTime(6, start, end));
        assertFalse(evaluator.isNightTime(21, start, end));
    }

    /**
     * MUTATION TARGET:
     * - PIT survivors: NegateConditionalsMutator at lines ~202 and ~206 (if (!alarmState)).
     *
     * Black-box: alarm disabled should log "Alarm disabled".
     * White-box: alarmActiveState MUST be forced false when alarmState is false.
     *
     * We keep proximityState=true so STSE doesn't re-enable the alarm ("Cannot disable the alarm, house is empty").
     */
    @Test
    public void testAlarmDisabled_logsAndClearsAlarmActive() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.PROXIMITY_STATE, true);     // user is home
        state.put(IoTValues.ALARM_STATE, false);        // alarm disabled request
        state.put(IoTValues.ALARM_ACTIVE, true);        // ensure the clear-path is observable
        state.put(IoTValues.ALARM_PASSCODE, "1234");
        state.put(IoTValues.GIVEN_PASSCODE, "");        // avoids invalid-pass path due to length==0 behavior

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(false, newState.get(IoTValues.ALARM_STATE), "Alarm should remain disabled.");
        assertEquals(false, newState.get(IoTValues.ALARM_ACTIVE), "Alarm active must be cleared when alarm is disabled.");
        assertTrue(log.toString().contains("Alarm disabled"), "Log should contain 'Alarm disabled' (kills negated condition mutant).");
    }

    /**
     * MUTATION TARGET:
     * - PIT survivors: multiple NegateConditionalsMutator instances on the composite condition at line ~215.
     *
     * Scenario: door OPEN, alarm ENABLED, house VACANT
     * - Earlier logic logs "Break in detected: Activating alarm"
     * - Composite condition should ALSO log "Activating alarm"
     *
     * We count occurrences to avoid substring ambiguity and to ensure line ~215 actually ran.
     */
    @Test
    public void testAlarmActivationCondition_doorOpenVacant_alarmEnabled_logsTwice() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.ALARM_STATE, true);
        state.put(IoTValues.DOOR_STATE, true);          // open
        state.put(IoTValues.PROXIMITY_STATE, false);    // vacant
        state.put(IoTValues.ALARM_ACTIVE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.ALARM_ACTIVE), "Alarm should activate for door-open + vacant when armed.");

        String s = log.toString();
        int n = countOccurrences(s, "Activating alarm");
        assertTrue(n >= 2,
                "Expected at least 2 'Activating alarm' occurrences (break-in + composite condition). " +
                        "Kills mutants that prevent the line~215 composite alarm activation branch.");
    }

    /**
     * MUTATION TARGET:
     * - Same PIT survivors at line ~215, but now for the "suddenly occupied" case:
     *   alarm ENABLED, door CLOSED, house OCCUPIED.
     */
    @Test
    public void testAlarmActivationCondition_doorClosedOccupied_alarmEnabled_logsTwice() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.ALARM_STATE, true);
        state.put(IoTValues.DOOR_STATE, false);         // closed
        state.put(IoTValues.PROXIMITY_STATE, true);     // occupied
        state.put(IoTValues.ALARM_ACTIVE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(true, newState.get(IoTValues.ALARM_ACTIVE), "Alarm should activate for occupied + armed (break-in rule).");

        String s = log.toString();
        int n = countOccurrences(s, "Activating alarm");
        assertTrue(n >= 2,
                "Expected at least 2 'Activating alarm' occurrences (break-in + composite condition). " +
                        "Kills mutants negating proximity/door checks in the composite condition.");
    }

    /**
     * MUTATION TARGET:
     * - Ensures the composite condition does NOT fire in a near-miss case.
     * - This kills mutants that incorrectly negate parts of the condition and cause activation when it shouldn't.
     *
     * Scenario: alarm ENABLED, door CLOSED, house VACANT.
     * Composite condition should be false; alarmActive should stay false (assuming no earlier rule triggers).
     */
    @Test
    public void testAlarmActivationCondition_nearMiss_doorClosedVacant_noActivation() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.ALARM_STATE, true);
        state.put(IoTValues.DOOR_STATE, false);         // closed
        state.put(IoTValues.PROXIMITY_STATE, false);    // vacant
        state.put(IoTValues.ALARM_ACTIVE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(false, newState.get(IoTValues.ALARM_ACTIVE),
                "Alarm should NOT activate for door-closed + vacant (no break-in case).");

        assertFalse(log.toString().contains("Activating alarm"),
                "Log should not contain composite 'Activating alarm' in near-miss scenario (kills negation mutants).");
    }

    /**
     * MUTATION TARGET:
     * - PIT survivor: ConditionalsBoundaryMutator at line ~236 (tempReading > targetTempSetting).
     *
     * Boundary test: temp == target must NOT turn on AC.
     * If mutated to >=, STSE would turn on AC and log "Turning on air conditioner".
     */
    @Test
    public void testTemperatureEqualsTarget_doesNotTurnOnAC() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.TEMP_READING, 70);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.CHILLER_STATE, false);
        state.put(IoTValues.HVAC_MODE, "Chiller"); // keep later hvacSetting.equals(...) from NPE

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals(false, newState.get(IoTValues.CHILLER_STATE), "AC should remain off when temp == target.");
        assertFalse(log.toString().contains("Turning on air conditioner"),
                "Log should NOT mention turning on AC at equality boundary (kills >= boundary mutant).");
    }

    /**
     * MUTATION TARGET:
     * - PIT NO_COVERAGE at lines ~253/255 where hvacSetting is auto-selected.
     *
     * Case: heater required (temp < target) and hvacSetting empty -> must set hvacSetting="Heater".
     * This both adds coverage and kills the "negated conditional" mutants on those lines.
     */
    @Test
    public void testAutoHvacSetting_whenEmpty_andHeaterNeeded_setsHeater() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.TEMP_READING, 65);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HVAC_MODE, "");             // forces auto-select block
        state.put(IoTValues.CHILLER_STATE, false);      // avoid null and avoid turning on AC

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals("Heater", newState.get(IoTValues.HVAC_MODE),
                "Expected hvacSetting to auto-select Heater when temp < target and hvacSetting empty.");
    }

    /**
     * MUTATION TARGET:
     * - PIT NO_COVERAGE at lines ~253/255 (auto hvacSetting selection).
     *
     * Case: chiller required (temp > target) and hvacSetting empty -> must set hvacSetting="Chiller".
     */
    @Test
    public void testAutoHvacSetting_whenEmpty_andChillerNeeded_setsChiller() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();

        state.put(IoTValues.TEMP_READING, 75);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.HVAC_MODE, "");             // forces auto-select block
        state.put(IoTValues.CHILLER_STATE, false);      // allow STSE to flip it on

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertEquals("Chiller", newState.get(IoTValues.HVAC_MODE),
                "Expected hvacSetting to auto-select Chiller when temp > target and hvacSetting empty.");
        assertEquals(true, newState.get(IoTValues.CHILLER_STATE),
                "Chiller should be turned on when temp > target (supports the auto-select branch).");
    }




}
