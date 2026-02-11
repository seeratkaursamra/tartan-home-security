package tartan.smarthome.resources;

/**
 * Smart door lock: supports Electronic Operation (lock/unlock from access panel
 * with passcode validation).
 *
 * Lock state (locked/unlocked) is separate from door physical state (open/closed).
 * The deadbolt can be locked or unlocked independently of whether the door is open or closed.
 */
public class SmartDoorLock {

    private final String lockPasscode;
    private boolean locked;

    /**
     * Create a new SmartDoorLock. Starts locked by default.
     * @param lockPasscode the passcode required to lock/unlock
     */
    public SmartDoorLock(String lockPasscode) {
        this.lockPasscode = lockPasscode != null ? lockPasscode : "";
        this.locked = true;
    }

    /**
     * @return true if the lock is currently locked
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * Request to unlock from the access panel. Validates the passcode first.
     * @param givenPasscode the passcode entered by the user
     * @return a message for the access panel log
     */
    public String requestUnlock(String givenPasscode) {
        if (!isValidPasscode(givenPasscode)) {
            return "Invalid passcode. Unlock denied.";
        }

        if (!locked) {
            return "Door lock is already unlocked.";
        }

        locked = false;
        return "Door lock unlocked successfully.";
    }

    /**
     * Request to lock from the access panel. Validates the passcode first.
     * @param givenPasscode the passcode entered by the user
     * @return a message for the access panel log
     */
    public String requestLock(String givenPasscode) {
        if (!isValidPasscode(givenPasscode)) {
            return "Invalid passcode. Lock denied.";
        }

        if (locked) {
            return "Door lock is already locked.";
        }

        locked = true;
        return "Door lock locked successfully.";
    }

    /**
     * Check whether the given passcode matches the lock's passcode.
     */
    private boolean isValidPasscode(String givenPasscode) {
        if (givenPasscode == null || givenPasscode.isEmpty()) {
            return false;
        }
        return lockPasscode.equals(givenPasscode);
    }
}
