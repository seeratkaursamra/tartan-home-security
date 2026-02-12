package tartan.smarthome.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import tartan.smarthome.resources.iotcontroller.IoTValues;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3: If the house is vacant, then close the door.
 *
 * --- Test Design (G2 Part 1) ---
 *
 * BLACK-BOX DESIGN:
 *
 * Equivalence Class Partitioning (ECP):
 *   Input variables relevant to R3:
 *     - proximityState (PS): {true (occupied), false (vacant)}
 *     - doorState (DS):      {true (open), false (closed)}
 *     - alarmState (AS):     {true (enabled), false (disabled)}
 *
 *   Equivalence classes (strong ECP, all combinations):
 *     EC1: PS=false, DS=true,  AS=false -> R3 fires: door CLOSES
 *     EC2: PS=false, DS=true,  AS=true  -> break-in path (alarm activates, NOT R3)
 *     EC3: PS=false, DS=false, AS=false -> door already closed, R3 not needed
 *     EC4: PS=false, DS=false, AS=true  -> door closed, alarm enabled (no break-in)
 *     EC5: PS=true,  DS=true,  AS=false -> occupied, door open allowed
 *     EC6: PS=true,  DS=true,  AS=true  -> occupied, door open, alarm (no break-in via door path)
 *     EC7: PS=true,  DS=false, AS=false -> occupied, door closed, normal
 *     EC8: PS=true,  DS=false, AS=true  -> occupied, door closed, alarm -> break-in detected
 *
 * Boundary Value Analysis (BVA):
 *   - proximityState and doorState are boolean, so BVA tests both boundary values
 *     (true/false) for each, holding other variables constant.
 *
 * Interaction testing:
 *   - R3 + R1: Vacant + door open + light on -> both rules fire
 *   - R3 + R12: Vacant + door open -> door closes AND away timer starts
 *   - R3 + Alarm: Vacant + door open + alarm -> break-in overrides R3 (door may stay open)
 *   - R3 + Away timer: Away timer forces door=false
 *
 * WHITE-BOX DESIGN:
 *   Target branches in evaluateState() related to door control (lines ~110-139):
 *     Branch 1: doorState==true and !proximityState and alarmState  -> break-in, alarmActiveState=true
 *     Branch 2: doorState==true and !proximityState and !alarmState -> R3: doorState=false, log
 *     Branch 3: doorState==true and proximityState                  -> log "Door open"
 *     Branch 4: !doorState and alarmState and proximityState        -> break-in via closed door
 *     Branch 5: !doorState and else                                 -> log "Closed door"
 *   Tests cover all 5 branches above.
 */
public class R3Test {

    private StaticTartanStateEvaluator evaluator;
    private StringBuffer log;

    @BeforeEach
    void setUp() {
        evaluator = new StaticTartanStateEvaluator();
        log = new StringBuffer();
    }

    // ========== EC1: Vacant + Door Open + Alarm Off -> R3 Closes Door ==========

    /**
     * EC1: PS=false, DS=true, AS=false.
     * R3 fires: door is closed because house is vacant.
     * WB: covers doorState==true and !proximityState and !alarmState branch.
     */
    @Test
    @DisplayName("R3 EC1: Vacant + door open + alarm off -> door closes")
    void testR3_EC1_Vacant_DoorOpen_AlarmOff_DoorCloses() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE),
            "R3: Door should be CLOSED when house is vacant and alarm is disabled");
        assertTrue(log.toString().contains("Closed door because house vacant"),
            "R3: Log should indicate door was closed due to vacant house");
    }

    // ========== EC2: Vacant + Door Open + Alarm On -> Break-in (NOT R3) ==========

    /**
     * EC2: PS=false, DS=true, AS=true.
     * Break-in path takes priority over R3. Alarm activates.
     * WB: covers doorState==true and !proximityState and alarmState branch.
     */
    @Test
    @DisplayName("R3 EC2: Vacant + door open + alarm on -> break-in detected (not R3)")
    void testR3_EC2_Vacant_DoorOpen_AlarmOn_BreakIn() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.ALARM_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
            "Alarm should activate (break-in: door open + vacant + alarm enabled)");
        assertTrue(log.toString().contains("Break in detected"),
            "Log should record break-in detection");
        // Note: R3 does NOT fire in this path; the break-in branch handles it
        assertFalse(log.toString().contains("Closed door because house vacant"),
            "R3 log should NOT appear when break-in is detected instead");
    }

    // ========== EC3: Vacant + Door Closed + Alarm Off -> Already Closed ==========

    /**
     * EC3: PS=false, DS=false, AS=false.
     * Door is already closed. R3 is satisfied. No action needed.
     * WB: covers !doorState and else branch ("Closed door").
     */
    @Test
    @DisplayName("R3 EC3: Vacant + door already closed + alarm off -> stays closed")
    void testR3_EC3_Vacant_DoorClosed_AlarmOff_StaysClosed() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.DOOR_STATE, false);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE),
            "R3: Door should remain CLOSED");
        assertTrue(log.toString().contains("Closed door"),
            "Log should indicate door is closed");
    }

    // ========== EC4: Vacant + Door Closed + Alarm On -> No break-in via door ==========

    /**
     * EC4: PS=false, DS=false, AS=true.
     * Door is closed and alarm enabled, but no one is home. No break-in via door path.
     * WB: covers !doorState and !(alarmState and proximityState) -> "Closed door".
     */
    @Test
    @DisplayName("R3 EC4: Vacant + door closed + alarm on -> no break-in, door stays closed")
    void testR3_EC4_Vacant_DoorClosed_AlarmOn_NoBreakIn() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.DOOR_STATE, false);
        state.put(IoTValues.ALARM_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE),
            "Door should remain CLOSED");
        // No break-in via closed door path because proximityState is false
        // (closed door break-in requires alarmState and proximityState)
    }

    // ========== EC5: Occupied + Door Open + Alarm Off -> Door Stays Open ==========

    /**
     * EC5: PS=true, DS=true, AS=false.
     * R3 does NOT fire because house is occupied. Door stays open.
     * WB: covers doorState==true and proximityState branch ("Door open").
     */
    @Test
    @DisplayName("R3 EC5: Occupied + door open + alarm off -> door stays open")
    void testR3_EC5_Occupied_DoorOpen_AlarmOff_StaysOpen() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.DOOR_STATE),
            "R3: Door should remain OPEN when house is occupied");
        assertTrue(log.toString().contains("Door open"),
            "Log should confirm door is open");
    }

    // ========== EC6: Occupied + Door Open + Alarm On -> Door Stays Open ==========

    /**
     * EC6: PS=true, DS=true, AS=true.
     * House occupied, door open, alarm on. Falls to "Door open" path.
     * WB: covers doorState==true and else branch (proximityState is true).
     */
    @Test
    @DisplayName("R3 EC6: Occupied + door open + alarm on -> door stays open")
    void testR3_EC6_Occupied_DoorOpen_AlarmOn_StaysOpen() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.ALARM_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.DOOR_STATE),
            "Door should remain OPEN when someone is home");
        assertTrue(log.toString().contains("Door open"),
            "Log should confirm door is open");
    }

    // ========== EC7: Occupied + Door Closed + Alarm Off -> Normal ==========

    /**
     * EC7: PS=true, DS=false, AS=false.
     * Normal state: occupied, door closed, alarm off.
     * WB: covers !doorState and else branch ("Closed door").
     */
    @Test
    @DisplayName("R3 EC7: Occupied + door closed + alarm off -> normal")
    void testR3_EC7_Occupied_DoorClosed_AlarmOff_Normal() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.DOOR_STATE, false);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE),
            "Door should remain CLOSED");
        assertTrue(log.toString().contains("Closed door"),
            "Log should indicate door is closed");
    }

    // ========== EC8: Occupied + Door Closed + Alarm On -> Break-in ==========

    /**
     * EC8: PS=true, DS=false, AS=true.
     * Break-in via closed door: someone is detected inside with alarm on.
     * WB: covers !doorState and alarmState and proximityState branch.
     */
    @Test
    @DisplayName("R3 EC8: Occupied + door closed + alarm on -> break-in detected")
    void testR3_EC8_Occupied_DoorClosed_AlarmOn_BreakIn() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.DOOR_STATE, false);
        state.put(IoTValues.ALARM_STATE, true);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.ALARM_ACTIVE),
            "Alarm should activate (break-in: someone inside with alarm on and door closed)");
        assertTrue(log.toString().contains("Break in detected"),
            "Log should record break-in detection");
    }

    // ========== Interaction: R3 + R1 (Vacant + Door Open + Light On) ==========

    /**
     * Interaction test: When house is vacant with door open and light on,
     * R3 closes the door AND R1 turns off the light.
     */
    @Test
    @DisplayName("R3+R1 Interaction: Vacant + door open + light on -> door closes + light off")
    void testR3_R1_Interaction_Vacant_DoorOpen_LightOn() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE),
            "R3: Door should be CLOSED when house is vacant");
        assertFalse((Boolean) newState.get(IoTValues.LIGHT_STATE),
            "R1: Light should be OFF when house is vacant");
    }

    // ========== Interaction: R3 + R12 (Vacant + Door Open + Away Timer) ==========

    /**
     * Interaction test: When house is vacant with door open,
     * R3 closes the door AND R12 starts the away timer.
     */
    @Test
    @DisplayName("R3+R12 Interaction: Vacant + door open -> door closes AND away timer starts")
    void testR3_R12_Interaction_Vacant_DoorOpen_AwayTimerStarts() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.AWAY_TIMER, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE),
            "R3: Door should be CLOSED when house is vacant");
        assertTrue((Boolean) newState.get(IoTValues.AWAY_TIMER),
            "R12: Away timer should start when house is vacant");
        assertTrue(log.toString().contains("Closed door because house vacant"),
            "R3: Log should record door closing");
        assertTrue(log.toString().contains("House is vacant, starting away timer"),
            "R12: Log should record away timer start");
    }

    // ========== Interaction: R3 + Away Timer (Timer forces door closed) ==========

    /**
     * Interaction test: Away timer triggers with door open.
     * The away timer itself forces door=false, light=false, alarm=true.
     */
    @Test
    @DisplayName("R3+AwayTimer Interaction: Away timer forces door closed")
    void testR3_AwayTimer_Interaction_ForcesDoorClosed() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.AWAY_TIMER, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE),
            "Away timer should force door CLOSED");
        assertFalse((Boolean) newState.get(IoTValues.AWAY_TIMER),
            "Away timer should be reset after triggering");
        assertTrue((Boolean) newState.get(IoTValues.ALARM_STATE),
            "Alarm should be enabled by away timer");
    }

    // ========== BVA: Boundary between occupied and vacant with door open ==========

    /**
     * BVA: proximityState=true (occupied boundary) with door open.
     * Door stays open because house is occupied.
     */
    @Test
    @DisplayName("R3 BVA: Occupied boundary + door open -> door stays open")
    void testR3_BVA_OccupiedBoundary_DoorOpen() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, true);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertTrue((Boolean) newState.get(IoTValues.DOOR_STATE),
            "BVA: Door should stay OPEN at occupied boundary");
    }

    /**
     * BVA: proximityState=false (vacant boundary) with door open.
     * R3 fires: door closes.
     */
    @Test
    @DisplayName("R3 BVA: Vacant boundary + door open -> door closes")
    void testR3_BVA_VacantBoundary_DoorOpen() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE),
            "BVA: Door should be CLOSED at vacant boundary");
    }

    // ========== Interaction: All vacant rules combined ==========

    /**
     * Full interaction: Vacant + door open + light on + alarm off + away timer off.
     * R1: light off, R3: door closes, R12: away timer starts.
     * Verifies all three vacancy rules fire together consistently.
     */
    @Test
    @DisplayName("R3+R1+R12 Full Interaction: All vacancy rules fire together")
    void testR3_R1_R12_FullInteraction_AllVacancyRules() {
        Map<String, Object> state = TestStateFactory.baseStateCopy();
        state.put(IoTValues.PROXIMITY_STATE, false);
        state.put(IoTValues.DOOR_STATE, true);
        state.put(IoTValues.LIGHT_STATE, true);
        state.put(IoTValues.ALARM_STATE, false);
        state.put(IoTValues.AWAY_TIMER, false);

        Map<String, Object> newState = evaluator.evaluateState(state, log);

        assertFalse((Boolean) newState.get(IoTValues.LIGHT_STATE),
            "R1: Light should be OFF");
        assertFalse((Boolean) newState.get(IoTValues.DOOR_STATE),
            "R3: Door should be CLOSED");
        assertTrue((Boolean) newState.get(IoTValues.AWAY_TIMER),
            "R12: Away timer should be started");
        assertTrue(log.toString().contains("Cannot turn on light because user not home"),
            "R1 log present");
        assertTrue(log.toString().contains("Closed door because house vacant"),
            "R3 log present");
        assertTrue(log.toString().contains("House is vacant, starting away timer"),
            "R12 log present");
    }
}
