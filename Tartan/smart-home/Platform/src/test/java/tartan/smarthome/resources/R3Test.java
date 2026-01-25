package tartan.smarthome.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Hashtable;
import java.util.Map;

import tartan.smarthome.resources.iotcontroller.IoTValues;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3: If the house is vacant, then close the door.
 */
public class R3Test {

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
    @DisplayName("R3: If the house is vacant, then close the door")
    void testR3_HouseVacant_DoorShouldClose() {
        Map<String, Object> state = createDefaultState();
        // house is vacant ie. no one home
        state.put(IoTValues.PROXIMITY_STATE, false);
        // the door is open
        state.put(IoTValues.DOOR_STATE, true);
        // the alarm is disabled 
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        Boolean doorState = (Boolean) newState.get(IoTValues.DOOR_STATE);
        assertFalse(doorState, 
            "R3 FAILED: When the house is vacant, the door should be automatically closed");
        
        // Verify the log contains the expected message
        assertTrue(log.toString().contains("Closed door because house vacant"),
            "R3 FAILED: Log should indicate door was closed due to vacant house");
    }
}