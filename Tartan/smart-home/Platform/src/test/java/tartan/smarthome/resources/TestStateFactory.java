package tartan.smarthome.resources;

import java.util.HashMap;
import java.util.Map;

import tartan.smarthome.resources.iotcontroller.IoTValues;

public final class TestStateFactory {

    private TestStateFactory() {}

    /** Base state that avoids STSE NPEs and provides sensible defaults. */
    public static Map<String, Object> baseState() {
        Map<String, Object> s = new HashMap<>();

        // Required by STSE to avoid NPEs
        s.put(IoTValues.TEMP_READING, 70);
        s.put(IoTValues.TARGET_TEMP, 70);
        s.put(IoTValues.HUMIDITY_READING, 50);
        s.put(IoTValues.HVAC_MODE, "Heater");

        // Existing core variables
        s.put(IoTValues.DOOR_STATE, false);
        s.put(IoTValues.LIGHT_STATE, false);
        s.put(IoTValues.PROXIMITY_STATE, false);

        s.put(IoTValues.ALARM_STATE, false);
        s.put(IoTValues.ALARM_ACTIVE, false);
        s.put(IoTValues.ALARM_PASSCODE, "1234");
        s.put(IoTValues.GIVEN_PASSCODE, "");

        s.put(IoTValues.HEATER_STATE, false);
        s.put(IoTValues.CHILLER_STATE, false);
        s.put(IoTValues.HUMIDIFIER_STATE, false);

        s.put(IoTValues.AWAY_TIMER, false);

        // Smart lock defaults (safe/backwards compatible)
        s.put(IoTValues.LOCK_STATE, true);
        s.put(IoTValues.KEYLESS_ENABLED, false);
        s.put(IoTValues.AUTHORIZED_APPROACH, false);
        s.put(IoTValues.INTRUDER_ACTIVE, false);
        s.put(IoTValues.NIGHT_ACTIVE, false);

        return s;
    }

    /** Convenience helper: clone so each test can freely mutate. */
    public static Map<String, Object> baseStateCopy() {
        return new HashMap<>(baseState());
    }
}
