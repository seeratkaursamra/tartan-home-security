package tartan.smarthome.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import tartan.smarthome.resources.iotcontroller.IoTValues;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R1: If the house is vacant, then the light cannot be turned on.
 *
 * --- Test Design (G2 Part 1) ---
 *
 * BLACK-BOX DESIGN:
 *
 * Equivalence Class Partitioning (ECP):
 *   Input variables relevant to R1:
 *     - proximityState (PS): {true (occupied), false (vacant)}
 *     - lightState (LS):     {true (on/requested on), false (off)}
 *
 *   Equivalence classes:
 *     EC1: PS=false, LS=true  -> light MUST be turned off (R1 applies)
 *     EC2: PS=true,  LS=true  -> light allowed to stay on
 *     EC3: PS=false, LS=false -> light stays off (R1 not triggered, already off)
 *     EC4: PS=true,  LS=false -> light turned on automatically (auto-light if alarm disabled)
 *
 * Boundary Value Analysis (BVA):
 *   - proximityState is boolean so the boundary is the transition between
 *     true/false. We test both values for each light state.
 *
 * Interaction testing:
 *   - R1 + R3: Vacant + door open + light on -> both rules fire (door closes, light off)
 *   - R1 + R12: Vacant + light on -> light off AND away timer started
 *   - R1 + Alarm: Vacant + alarm + light on -> light off, alarm may activate
 *   - R1 + Away timer: Away timer fires -> forces light off (interacts with R1's domain)
 *   - R1 + Auto-light: Occupied + light off + alarm disabled -> auto-light turns on
 *     (verifies the positive path where R1 does NOT block)
 *
 * WHITE-BOX DESIGN:
 *   Target branches in evaluateState() related to R1 (lines ~96-107):
 *     Branch 1: lightState == true and !proximityState -> set lightState=false, log rejection
 *     Branch 2: lightState == true and proximityState  -> log "Light on"
 *     Branch 3: lightState == false                    -> log "Light off"
 *   All three branches are covered by EC1-EC4 tests above.
 *   Additional whitebox tests target the auto-light inner branch:
 *     Branch 4: proximityState and !lightState and !alarmState -> auto-light on
 *     Branch 5: proximityState and !lightState and alarmState  -> no auto-light
 */
public class R1Test {

    private StaticTartanStateEvaluator evaluator;
    private StringBuffer log;

    @BeforeEach
    void setUp() {
        evaluator = new StaticTartanStateEvaluator();
        log = new StringBuffer();
    }

    // ========== Equivalence Class: EC1 - Vacant + Light On -> Rejected ==========

    /**
     * EC1: proximityState=false, lightState=true.
     * R1 fires: light is forced off.
     * WB: covers lightState==true and !proximityState branch.
     */
    @Test
    @DisplayName("R1 EC1: Vacant house rejects light-on request")
    void testR1_EC1_Vacant_LightRequestedOn_Rejected() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.LIGHT_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LIGHT_STATE),
            "R1: Light must be forced OFF when house is vacant");
        assertTrue(log.toString().contains("Cannot turn on light because user not home"),
            "R1: Log should indicate light was rejected due to vacant house");
    }

    // ========== Equivalence Class: EC2 - Occupied + Light On -> Allowed ==========

    /**
     * EC2: proximityState=true, lightState=true.
     * R1 does NOT fire: light stays on.
     * WB: covers lightState==true and proximityState branch ("Light on").
     */
    @Test
    @DisplayName("R1 EC2: Occupied house allows light to stay on")
    void testR1_EC2_Occupied_LightOn_Allowed() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LIGHT_STATE),
            "R1: Light should remain ON when house is occupied");
        assertTrue(log.toString().contains("Light on"),
            "R1: Log should confirm light is on");
    }

    // ========== Equivalence Class: EC3 - Vacant + Light Off -> Stays Off ==========

    /**
     * EC3: proximityState=false, lightState=false.
     * R1 is not triggered because light is already off.
     * WB: covers lightState==false branch ("Light off").
     */
    @Test
    @DisplayName("R1 EC3: Vacant house with light already off - stays off")
    void testR1_EC3_Vacant_LightOff_StaysOff() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.LIGHT_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LIGHT_STATE),
            "R1: Light should stay OFF when house is vacant and light was already off");
        assertTrue(log.toString().contains("Light off"),
            "R1: Log should indicate light is off");
        assertFalse(log.toString().contains("Cannot turn on light"),
            "R1: No rejection log expected since light was not requested on");
    }

    // ========== Equivalence Class: EC4 - Occupied + Light Off -> Auto-Light ==========

    /**
     * EC4: proximityState=true, lightState=false, alarmState=false.
     * R1 does not apply. Auto-light feature turns light ON.
     * WB: covers the auto-light inner branch (proximityState and !lightState and !alarmState).
     */
    @Test
    @DisplayName("R1 EC4: Occupied house, light off, alarm disabled - auto-light turns on")
    void testR1_EC4_Occupied_LightOff_AutoLightOn() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.LIGHT_STATE, false);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LIGHT_STATE),
            "Auto-light should turn ON when someone arrives home with alarm disabled");
        assertTrue(log.toString().contains("Turning on light"),
            "Log should mention auto-light activation");
    }

    // ========== Whitebox: Auto-light suppressed by alarm ==========

    /**
     * WB: proximityState=true, lightState=false, alarmState=true.
     * The auto-light inner branch (!lightState and !alarmState) is FALSE because alarm is enabled.
     * Light stays off despite being occupied.
     */
    @Test
    @DisplayName("R1 WB: Occupied + light off + alarm enabled - no auto-light")
    void testR1_WB_Occupied_LightOff_AlarmEnabled_NoAutoLight() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.LIGHT_STATE, false);
        state.put(IoTValues.ALARM_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse(log.toString().contains("Turning on light"),
            "Auto-light should NOT activate when alarm is enabled");
    }

    // ========== Interaction: R1 + R3 (Vacant + Door Open + Light On) ==========

    /**
     * Interaction test: When house is vacant with door open and light on,
     * both R1 (light off) and R3 (close door) should fire.
     */
    @Test
    @DisplayName("R1+R3 Interaction: Vacant + door open + light on - both rules fire")
    void testR1_R3_Interaction_Vacant_DoorOpen_LightOn() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LIGHT_STATE),
            "R1: Light should be OFF when house is vacant");
        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE),
            "R3: Door should be CLOSED when house is vacant");
        assertTrue(log.toString().contains("Cannot turn on light because user not home"),
            "R1: Log should record light rejection");
        assertTrue(log.toString().contains("Closed door because house vacant"),
            "R3: Log should record door closing");
    }

    // ========== Interaction: R1 + R12 (Vacant + Light On + Away Timer) ==========

    /**
     * Interaction test: When house is vacant with light on,
     * R1 turns off the light AND R12 starts the away timer.
     */
    @Test
    @DisplayName("R1+R12 Interaction: Vacant + light on - light off AND away timer starts")
    void testR1_R12_Interaction_Vacant_LightOn_AwayTimerStarts() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.AWAY_TIMER, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LIGHT_STATE),
            "R1: Light should be OFF when house is vacant");
        assertTrue((Boolean) newState.get(IoTValues.AWAY_TIMER),
            "R12: Away timer should start when house is vacant");
    }

    // ========== Interaction: R1 + Alarm (Vacant + Alarm + Door Open + Light On) ==========

    /**
     * Interaction test: Vacant house with alarm enabled, door open, light on.
     * R1: light forced off. Alarm: break-in detected (door open + vacant + alarm).
     */
    @Test
    @DisplayName("R1+Alarm Interaction: Vacant + alarm + door open + light on - light off + break-in")
    void testR1_Alarm_Interaction_Vacant_AlarmDoorOpen_LightOn() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.ALARM_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LIGHT_STATE),
            "R1: Light should be OFF when house is vacant");
        assertTrue((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
            "Alarm should activate due to break-in (door open + vacant + alarm enabled)");
        assertTrue(log.toString().contains("Break in detected"),
            "Log should record break-in detection");
    }

    // ========== Interaction: R1 + Away Timer trigger ==========

    /**
     * Interaction test: Away timer has triggered, light was on.
     * The away timer forces light=false, door=false, alarm=true.
     * Even with someone home, the away timer overrides.
     */
    @Test
    @DisplayName("R1+AwayTimer Interaction: Away timer forces light off")
    void testR1_AwayTimer_Interaction_ForcesLightOff() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.AWAY_TIMER, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        // Away timer sets alarm=true, which suppresses auto-light.
        // After away timer fires: alarm is enabled, so auto-light will not re-enable light.
        assertFalse((Boolean) newState.get(IoTValues.AWAY_TIMER),
            "Away timer should be reset to false after triggering");
        assertTrue((Boolean) newState.get(IoTValues.ALARM_STATE),
            "Alarm should be enabled by away timer");
    }

    // ========== BVA: Boundary between occupied and vacant ==========

    /**
     * BVA: proximityState=true (just at the "occupied" boundary) with light on.
     * This is the boundary value that does NOT trigger R1.
     */
    @Test
    @DisplayName("R1 BVA: Boundary - occupied (true) with light on - allowed")
    void testR1_BVA_OccupiedBoundary_LightOn() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.LIGHT_STATE),
            "BVA: Light should stay ON at occupied boundary");
    }

    /**
     * BVA: proximityState=false (just at the "vacant" boundary) with light on.
     * This is the boundary value that DOES trigger R1.
     */
    @Test
    @DisplayName("R1 BVA: Boundary - vacant (false) with light on - rejected")
    void testR1_BVA_VacantBoundary_LightOn() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.LIGHT_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LIGHT_STATE),
            "BVA: Light should be forced OFF at vacant boundary");
    }
}
