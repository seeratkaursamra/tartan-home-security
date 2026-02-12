package tartan.smarthome.resources;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Map;

import tartan.smarthome.resources.iotcontroller.IoTValues;

/**
 * Openai, chatpgt 5.2 "I have a bug in this code, please add easy to read documentation, and find and fix the bug."
 *
 * Keyless Entry logic for the Smart Door Lock.
 *
 * This class is intentionally limited to Keyless Entry only.
 * Intruder Defence and Night Lock priority handling remains in StaticTartanStateEvaluator.
 *
 * Inputs (read from inState with defaults):
 *  - IoTValues.KEYLESS_ENABLED (Boolean, default false)
 *  - IoTValues.AUTHORIZED_APPROACH (Boolean, default false)
 *
 * Output:
 *  - returns updated lockState + whether keyless triggered this evaluation tick.
 *
 * Logging:
 *  - Appends timestamped log entries compatible with the existing STSE format.
 */
public final class KeylessEntry {

    private KeylessEntry() {
        // utility class
    }

    /** Result wrapper so STSE can know whether Keyless triggered (for rule ordering). */
    public static final class Result {
        public final boolean lockState;
        public final boolean triggered;

        public Result(boolean lockState, boolean triggered) {
            this.lockState = lockState;
            this.triggered = triggered;
        }
    }

    /** Same log formatting style as StaticTartanStateEvaluator (timestamp + message + newline). */
    private static String formatLogEntry(String entry) {
        Long timeStamp = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd,yyyy HH:mm");
        return "[" + sdf.format(new Date(timeStamp)) + "]: " + entry + "\n";
    }

    /**
     * Apply Keyless Entry behavior.
     *
     * @param inState    state map (may omit keyless keys; defaults are used)
     * @param lockState  current lock state (true=locked, false=unlocked)
     * @param log        system log buffer (shown on frontend)
     * @return Result containing updated lockState and whether keyless triggered
     */
    public static Result apply(Map<String, Object> inState, boolean lockState, StringBuffer log) {
        boolean keylessEnabled = (Boolean) inState.getOrDefault(IoTValues.KEYLESS_ENABLED, false);
        boolean authorizedApproach = (Boolean) inState.getOrDefault(IoTValues.AUTHORIZED_APPROACH, false);

        if (authorizedApproach && !keylessEnabled) {
            log.append(formatLogEntry("Keyless entry disabled: ignoring authorized approach"));
            return new Result(lockState, false);
        }

        // Only acts when keyless is enabled AND an authorized resident is detected approaching.
        if (keylessEnabled && authorizedApproach) {
            if (lockState) {
                log.append(formatLogEntry("Keyless entry: unlocking door"));
                return new Result(false, true);
            } else {
                log.append(formatLogEntry("Keyless entry: door already unlocked"));
                return new Result(false, true);
            }
        }

        // No keyless action taken.
        return new Result(lockState, false);
    }
}
