package tartan.smarthome.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import tartan.smarthome.resources.iotcontroller.IoTValues;

/**
 * This will unit test Rule 10
 * R10: The heater and the dehumidifier cannot be run simultaneously.
 *  - when the heater is running verify that automatically turn off the dehumidifier
 *
 * The code - Tartan/smart-home/Platform/src/main/java/tartan/smarthome/resources/StaticTartanStateEvaluator.java
 * ~ lines 274-300 will hold all of the needed logic for this
 *
 * tests 4 cases
 * tc1 - heater enabled disables dehumid
 * tc2 - heater disabled, humid enabled -enable he-> he YES, hu NO
 * tc3 - chiller enabled, doesn't disable the dehumid
 * tc4 - switching from chiller to heater disables the dehumidifier
 *
 * This was written with completion assistance from claude Sonnet 4.5, 2026-01-24
 *  - claudes input was mainly used as bug testing - why doesn't this work type questions
 *  - and to clean the documentation
 *
 *  Openai chatgpt 5.2, 2026-02-10 "Please add comments on how this test adheres to black and white box testing
 *
 * BLACK-BOX DESIGN (rule-level):
 * - Equivalence Classes (ECP) over the relevant inputs:
 *   - hvacSetting ∈ {"Heater", "Chiller"} (and implicitly: other modes are irrelevant to R10 here)
 *   - humidifierState ∈ {true, false}
 *   - heaterOnState/chillerOnState ∈ {true, false}
 * - Rule interaction scenarios:
 *   - HVAC mode selection interacts with the dehumidifier constraint:
 *       * In "Heater" mode: dehumidifier must be forced OFF.
 *       * In "Chiller" mode: dehumidifier may remain ON.
 *
 * WHITE-BOX INTENT (code-coverage guided):
 * - Target branches in StaticTartanStateEvaluator.evaluateState():
 *   - hvacSetting.equals("Heater") path forces humidifierState = false.
 *   - humidifierState && hvacSetting.equals("Chiller") path logs "Enabled Dehumidifier".
 *   - else branch logs "Automatically disabled dehumidifier when running heater".

 */
public class Rule10Test {
    private StaticTartanStateEvaluator evaluator;
    private StringBuffer log;

    @BeforeEach
    public void setUp() {
        evaluator = new StaticTartanStateEvaluator();
        log = new StringBuffer();
    }

    /**
     * TC1 (ECP + WB branch): Heater mode + humidifier ON request.
     *
     * BLACK-BOX:
     * - Class: hvacSetting="Heater" with humidifierState=true (invalid combination by spec).
     * - Oracle: humidifier must be false in evaluated state.
     *
     * WHITE-BOX:
     * - Exercises hvacSetting.equals("Heater") branch (forces humidifierState=false).
     * - Also hits the dehumidifier enforcement else-branch that logs
     *   "Automatically disabled dehumidifier when running heater".
     */
    @Test
    public void testHeaterOnForcesDehumidifierOff() {
        Map<String, Object> state = new HashMap<>();
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.HEATER_STATE, true);
        state.put(IoTValues.HUMIDIFIER_STATE, true);
        state.put(IoTValues.CHILLER_STATE, false);
        state.put(IoTValues.TEMP_READING, 60);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.DOOR_STATE, false);
        state.put(IoTValues.LIGHT_STATE, false);
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.ALARM_ACTIVE, false);
        state.put(IoTValues.ALARM_PASSCODE, "1234");

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.HUMIDIFIER_STATE),
                "Humidifier should be OFF when heater is ON");
        assertTrue((Boolean) newState.get(IoTValues.HEATER_STATE),
                "Heater should remain ON");

        assertTrue(log.toString().contains("Automatically disabled dehumidifier when running heater"),
                "Log should mention automatically disabling dehumidifier");
    }

    /**
     * TC2 (ECP + interaction): Heater becomes needed (temp < target) while humidifier is ON.
     *
     * BLACK-BOX:
     * - Class: temp < target implies heating behavior; with humidifier initially ON.
     * - Oracle: heater ON, humidifier forced OFF (R10).
     *
     * WHITE-BOX:
     * - Exercises tempReading < targetTempSetting path that sets heaterOnState=true.
     * - Then exercises heater-mode enforcement that disables humidifierState.
     */
    @Test
    public void testHeaterActivationDisablesHumidifier() {
        Map<String, Object> state = new HashMap<>();
        state.put(IoTValues.TEMP_READING, 65);
        state.put(IoTValues.TARGET_TEMP, 72);
        state.put(IoTValues.HUMIDIFIER_STATE, true);
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.HEATER_STATE, false);
        state.put(IoTValues.CHILLER_STATE, false);
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.DOOR_STATE, false);
        state.put(IoTValues.LIGHT_STATE, false);
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.ALARM_ACTIVE, false);
        state.put(IoTValues.ALARM_PASSCODE, "1234");

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.HEATER_STATE),
                "Heater should be ON because temp is below target");
        assertFalse((Boolean) newState.get(IoTValues.HUMIDIFIER_STATE),
                "Humidifier should be OFF when heater turns ON");
    }

    /**
     * TC3 (ECP + WB branch): Chiller mode allows dehumidifier.
     *
     * BLACK-BOX:
     * - Class: hvacSetting="Chiller" with humidifierState=true (allowed combination).
     * - Oracle: humidifier remains true.
     *
     * WHITE-BOX:
     * - Exercises humidifierState && hvacSetting.equals("Chiller") branch,
     *   which logs "Enabled Dehumidifier".
     */
    @Test
    public void testDehumidifierCanRunWithChiller() {
        Map<String, Object> state = new HashMap<>();
        state.put(IoTValues.HVAC_MODE, "Chiller");
        state.put(IoTValues.CHILLER_STATE, true);
        state.put(IoTValues.HUMIDIFIER_STATE, true);
        state.put(IoTValues.HEATER_STATE, false);
        state.put(IoTValues.TEMP_READING, 75);
        state.put(IoTValues.TARGET_TEMP, 70);
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.DOOR_STATE, false);
        state.put(IoTValues.LIGHT_STATE, false);
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.ALARM_ACTIVE, false);
        state.put(IoTValues.ALARM_PASSCODE, "1234");

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.HUMIDIFIER_STATE),
                "Humidifier should be ON when chiller is ON");
        assertTrue((Boolean) newState.get(IoTValues.CHILLER_STATE),
                "Chiller should remain ON");

        assertTrue(log.toString().contains("Enabled Dehumidifier"),
                "Log should mention enabled dehumidifier");
    }

    /**
     * TC4 (interaction regression): "switching" scenario (represented by requesting Heater mode
     * while humidifier was ON previously).
     *
     * BLACK-BOX:
     * - Class: heater-mode requested with humidifierState=true.
     * - Oracle: humidifier forced OFF.
     *
     * WHITE-BOX:
     * - Exercises the same heater-mode enforcement branch, but framed as a mode-change scenario.
     */
    @Test
    public void testSwitchingToHeaterDisablesHumidifier() {
        Map<String, Object> state = new HashMap<>();
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.HEATER_STATE, true);
        state.put(IoTValues.CHILLER_STATE, false);
        state.put(IoTValues.HUMIDIFIER_STATE, true);
        state.put(IoTValues.TEMP_READING, 65);
        state.put(IoTValues.TARGET_TEMP, 72);
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.DOOR_STATE, false);
        state.put(IoTValues.LIGHT_STATE, false);
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.ALARM_ACTIVE, false);
        state.put(IoTValues.ALARM_PASSCODE, "1234");

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.HUMIDIFIER_STATE),
                "Humidifier should be OFF after switching to heater mode");
        assertEquals("Heater", newState.get(IoTValues.HVAC_MODE),
                "HVAC mode should be Heater");
    }
}