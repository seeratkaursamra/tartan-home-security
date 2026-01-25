package tartan.smarthome.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Hashtable;
import java.util.Map;

import tartan.smarthome.resources.iotcontroller.IoTValues;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R1: If the house is vacant, then the light cannot be turned on.
 */
public class R1Test {

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

    @Test
    @DisplayName("R1: If the house is vacant, then the light cannot be turned on")
    void testR1_HouseVacant_LightCannotTurnOn() {
        Map<String, Object> state = createDefaultState();
        // house is vacant (no one home)
        state.put(IoTValues.PROXIMITY_STATE, false);
        // attempt to turn on the light
        state.put(IoTValues.LIGHT_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        Boolean lightState = (Boolean) newState.get(IoTValues.LIGHT_STATE);
        assertFalse(lightState, 
            "R1 FAILED: when the house is vacant, the light should not be allowed to turn on");
        
        // Verify the log contains the expected message
        assertTrue(log.toString().contains("Cannot turn on light because user not home"),
            "R1 FAILED: Log should indicate light was rejected due to vacant house");
    }
}