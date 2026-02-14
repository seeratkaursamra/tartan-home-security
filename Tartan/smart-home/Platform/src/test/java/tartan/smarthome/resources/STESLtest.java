package tartan.smarthome.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tartan.smarthome.resources.iotcontroller.IoTValues;

/**
 * Cross-feature integration tests for StaticTartanStateEvaluator.
 */
public class STESLtest {

    private StaticTartanStateEvaluator evaluator;
    private StringBuffer log;

    @BeforeEach
    void setUp() {
        evaluator = new StaticTartanStateEvaluator();
        log = new StringBuffer();
    }

    private Map<String, Object> baseStateWithoutNightActive() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.remove(IoTValues.NIGHT_ACTIVE);
        return state;
    }

    @Test
    @DisplayName("Config path: night hour + enabled → nightActive=true, door locks")
    void nightActiveComputedFromConfig_nightHour_locks() {
        Map<String, Object> state = baseStateWithoutNightActive();
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.NIGHT_LOCK_START, 22);
        state.put(IoTValues.NIGHT_LOCK_END, 6);
        state.put(IoTValues.CURRENT_HOUR, 23);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> result = evaluator.evaluateState(state, log);

        assertTrue((Boolean) result.get(IoTValues.LOCK_STATE),
                "Door should be locked when config computes nightActive=true");
        assertTrue(log.toString().toLowerCase().contains("night lock"),
                "Log should mention night lock");
    }

    @Test
    @DisplayName("Config path: day hour + enabled → nightActive=false, door stays unlocked")
    void nightActiveComputedFromConfig_dayHour_noLock() {
        Map<String, Object> state = baseStateWithoutNightActive();
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.NIGHT_LOCK_START, 22);
        state.put(IoTValues.NIGHT_LOCK_END, 6);
        state.put(IoTValues.CURRENT_HOUR, 12);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> result = evaluator.evaluateState(state, log);

        assertFalse((Boolean) result.get(IoTValues.LOCK_STATE),
                "Door should stay unlocked during daytime");
        assertFalse(log.toString().toLowerCase().contains("night lock"),
                "No night lock log expected during day");
    }

    @Test
    @DisplayName("Config path: night hour + disabled → nightActive=false, door stays unlocked")
    void nightActiveComputedFromConfig_disabled_noLock() {
        Map<String, Object> state = baseStateWithoutNightActive();
        state.put(IoTValues.NIGHT_LOCK_ENABLED, false);
        state.put(IoTValues.NIGHT_LOCK_START, 22);
        state.put(IoTValues.NIGHT_LOCK_END, 6);
        state.put(IoTValues.CURRENT_HOUR, 23);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> result = evaluator.evaluateState(state, log);

        assertFalse((Boolean) result.get(IoTValues.LOCK_STATE),
                "Door should stay unlocked when night lock is disabled");
    }

    @Test
    @DisplayName("Config path: midnight crossing (start=22, end=6, hour=3) → locks")
    void nightActiveComputedFromConfig_midnightCross() {
        Map<String, Object> state = baseStateWithoutNightActive();
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.NIGHT_LOCK_START, 22);
        state.put(IoTValues.NIGHT_LOCK_END, 6);
        state.put(IoTValues.CURRENT_HOUR, 3);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> result = evaluator.evaluateState(state, log);

        assertTrue((Boolean) result.get(IoTValues.LOCK_STATE),
                "Door should lock at hour 3 with midnight-crossing schedule");
    }

    @Test
    @DisplayName("Config path: no config keys at all → defaults, door stays unlocked")
    void configPath_defaultValues_noNightLockKey() {
        Map<String, Object> state = baseStateWithoutNightActive();
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> result = evaluator.evaluateState(state, log);

        assertFalse((Boolean) result.get(IoTValues.LOCK_STATE),
                "Door should stay unlocked when no config keys are present (defaults: disabled)");
    }

    @Test
    @DisplayName("Cascade: vacant + night + door open + light on → R1+R3+R12+night lock all fire")
    void vacantHouse_nightLock_awayTimer_allFire() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.LOCK_STATE, false);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> result = evaluator.evaluateState(state, log);

        assertFalse((Boolean) result.get(IoTValues.LIGHT_STATE),
                "R1: light should be off when vacant");
        assertFalse((Boolean) result.get(IoTValues.DOOR_STATE),
                "R3: door should be closed when vacant");
        assertTrue((Boolean) result.get(IoTValues.AWAY_TIMER),
                "R12: away timer should start when vacant");
        assertTrue((Boolean) result.get(IoTValues.LOCK_STATE),
                "Night lock should lock during night");
    }

    @Test
    @DisplayName("Cascade: away timer fired + night active → alarm enabled + door locked")
    void awayTimerFired_nightActive_lockAndAlarm() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.AWAY_TIMER, true);
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.LOCK_STATE, false);
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.LIGHT_STATE, false);

        Map<String, Object> result = evaluator.evaluateState(state, log);

        assertTrue((Boolean) result.get(IoTValues.ALARM_STATE),
                "Away timer should enable alarm");
        assertTrue((Boolean) result.get(IoTValues.LOCK_STATE),
                "Night lock should lock door");
    }

    @Test
    @DisplayName("Cascade: intruder + alarm + door open + night → alarm fires, intruder locks (not night lock)")
    void intruder_alarm_nightLock_combined() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.INTRUDER_ACTIVE, true);
        state.put(IoTValues.ALARM_STATE, true);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> result = evaluator.evaluateState(state, log);

        assertTrue((Boolean) result.get(IoTValues.ALARM_ACTIVE),
                "Alarm should be active (break-in: door open + vacant + alarm)");
        assertTrue((Boolean) result.get(IoTValues.LOCK_STATE),
                "Intruder should force door locked");
        assertTrue(log.toString().toLowerCase().contains("intruder"),
                "Log should mention intruder");
        assertFalse(log.toString().toLowerCase().contains("night lock: locking"),
                "Night lock log should NOT appear when intruder takes priority");
    }

    @Test
    @DisplayName("Cascade: occupied + alarm off + light off + night active → auto-light on + night lock locks")
    void occupiedHouse_autoLight_nightLock() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.LIGHT_STATE, false);
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> result = evaluator.evaluateState(state, log);

        assertTrue((Boolean) result.get(IoTValues.LIGHT_STATE),
                "Auto-light should turn on (occupied + alarm off + light off)");
        assertTrue((Boolean) result.get(IoTValues.LOCK_STATE),
                "Night lock should lock the door");
    }

    @Test
    @DisplayName("Cascade: heater mode + humidifier on + night lock → humidifier off (R10) + door locked")
    void hvac_heater_dehumidifier_nightLock() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.HVAC_MODE, "Heater");
        state.put(IoTValues.HUMIDIFIER_STATE, true);
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> result = evaluator.evaluateState(state, log);

        assertFalse((Boolean) result.get(IoTValues.HUMIDIFIER_STATE),
                "Humidifier should be off in heater mode (R10)");
        assertTrue((Boolean) result.get(IoTValues.LOCK_STATE),
                "Night lock should still lock the door");
    }

    @Test
    @DisplayName("nightActive_relocksIfUnlocked_whenNoKeylessTrigger")
    void nightActive_relocksIfUnlocked_whenNoKeylessTrigger() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.NIGHT_ACTIVE, true);
        state.put(IoTValues.NIGHT_LOCK_ENABLED, true);
        state.put(IoTValues.LOCK_STATE, false);

        Map<String, Object> result = evaluator.evaluateState(state, log);

        assertTrue((Boolean) result.get(IoTValues.LOCK_STATE),
                "Night lock should lock when nightActive=true and enabled");
    }
}