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
     * Test Case 1: When heater is turned ON, dehumidifier should be turned OFF
     *
     * Setup:
     * - Set HVAC mode to "Heater"
     * - Set heater state to ON
     * - Set humidifier state to ON (attempting to run both)
     *
     * Expected Result:
     * - Humidifier state should be OFF in the returned state
     */
    @Test
    public void testHeaterOnForcesDehumidifierOff() {
        // Arrange: Create initial state with heater ON and humidifier ON
        Map<String, Object> state = new HashMap<>();
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.HEATER_STATE, true);
        state.put(IoTValues.HUMIDIFIER_STATE, true);  // Try to turn on humidifier
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
     * Test Case 2: When humidifier is ON and heater is requested, humidifier turns OFF
     *
     * Setup:
     * - Temperature reading below target (heater will turn on)
     * - Humidifier is initially ON
     *
     * Expected Result:
     * - Heater should turn ON
     * - Humidifier should be turned OFF
     */
    @Test
    public void testHeaterActivationDisablesHumidifier() {
        // Arrange: Create state where heater will activate
        Map<String, Object> state = new HashMap<>();
        state.put(IoTValues.TEMP_READING, 65);
        state.put(IoTValues.TARGET_TEMP, 72);  // Target higher than current
        state.put(IoTValues.HUMIDIFIER_STATE, true);  // Humidifier initially ON
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
     * Test Case 3: Dehumidifier CAN run with chiller (AC), but NOT with heater
     *
     * Setup:
     * - HVAC mode set to "Chiller"
     * - Chiller (AC) is ON
     * - Humidifier is ON
     *
     * Expected Result:
     * - Humidifier should remain ON (allowed with chiller)
     */
    @Test
    public void testDehumidifierCanRunWithChiller() {
        // Arrange: Create state with chiller ON and humidifier ON
        Map<String, Object> state = new HashMap<>();
        state.put(IoTValues.HVAC_MODE, "Chiller");
        state.put(IoTValues.CHILLER_STATE, true);
        state.put(IoTValues.HUMIDIFIER_STATE, true);  // Should be allowed with AC
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
     * Test Case 4: Switching from Chiller to Heater disables humidifier
     *
     * Setup:
     * - Initially in Chiller mode with humidifier ON
     * - Switch to Heater mode
     *
     * Expected Result:
     * - Humidifier should be turned OFF when switching to Heater
     */
    @Test
    public void testSwitchingToHeaterDisablesHumidifier() {
        // Arrange: Start with chiller and humidifier ON
        Map<String, Object> state = new HashMap<>();
        state.put(IoTValues.HVAC_MODE, "Heater");  // Switching to heater
        state.put(IoTValues.HEATER_STATE, true);
        state.put(IoTValues.CHILLER_STATE, false);
        state.put(IoTValues.HUMIDIFIER_STATE, true);  // Was ON with chiller
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