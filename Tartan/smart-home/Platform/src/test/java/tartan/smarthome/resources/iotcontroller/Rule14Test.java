package tartan.smarthome.resources.iotcontroller;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Rule 14:
 * Login requires username/password; password policy (new users):
 * - length >= 8
 * - >= 1 uppercase
 * - >= 1 number
 * - >= 1 symbol
 * Legacy users can bypass validation via (validate=false) constructor.
 *
 * Openai, chatgpt 5.2 "I am lazy please add comments for how this document adheres to white and black box testing"
 * BLACK-BOX DESIGN:
 * - Boundary Value Analysis (BVA) on password length: 7 vs 8 characters.
 * - Equivalence Class Partitioning (ECP) on password composition:
 *   - Missing uppercase / missing number / missing symbol / null password.
 * - Feature interaction: "legacy users" path bypasses policy, but setPassword() enforces policy.
 *
 * WHITE-BOX INTENT:
 * - Cover the branch in constructor:
 *     if (validate && !isValidPassword(password)) throw ...
 * - Cover isValidPassword branches:
 *     null/length check, loop setting hasUppercase/hasNumber/hasSymbol
 * - Cover setPassword() failure branch and success branch.
 */
public class Rule14Test {

    /**
     * BVA (length == 8) + ECP (all required character classes present).
     * WB: drives isValidPassword() through the loop and returns true.
     */
    @Test
    public void testR14_ValidPassword_MinimumRequirements() {
        System.out.println("R14: Testing valid password with minimum requirements (8 chars)");

        UserLoginInfo user = new UserLoginInfo("admin", "Pass123!");
        assertNotNull(user);
        assertEquals("admin", user.getUserName());
        assertEquals("Pass123!", user.getPassword());
    }

    /**
     * BVA: length boundary - 1 (7 chars).
     * WB: hits (password == null || password.length() < 8) early return false,
     * then constructor throws IllegalArgumentException.
     */
    @Test
    public void testR14_InvalidPassword_TooShort() {
        System.out.println("R14: Testing password validation - too short");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new UserLoginInfo("admin", "Pass12!")
        );

        assertTrue(exception.getMessage().contains("8") ||
                        exception.getMessage().toLowerCase().contains("char"),
                "Error message should mention minimum length requirement");
    }

    /**
     * ECP: missing uppercase class.
     * WB: isValidPassword loop never sets hasUppercase => returns false => constructor throws.
     */
    @Test
    public void testR14_InvalidPassword_NoUppercase() {
        System.out.println("R14: Testing password validation - no uppercase");

        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", "password123!");
        });
    }

    /**
     * ECP: missing number class.
     * WB: isValidPassword loop never sets hasNumber => returns false => constructor throws.
     */
    @Test
    public void testR14_InvalidPassword_NoNumber() {
        System.out.println("R14: Testing password validation - no number");

        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", "Password!");
        });
    }

    /**
     * ECP: missing symbol class.
     * WB: isValidPassword loop never sets hasSymbol => returns false => constructor throws.
     */
    @Test
    public void testR14_InvalidPassword_NoSymbol() {
        System.out.println("R14: Testing password validation - no symbol");

        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", "Password1");
        });
    }

    /**
     * Edge case / robustness:
     * ECP: password = null.
     * WB: hits null check branch in isValidPassword and throws via constructor.
     */
    @Test
    public void testR14_InvalidPassword_Null() {
        System.out.println("R14: Testing password validation - null password");

        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", null);
        });
    }

    /**
     * Legacy user bypass (interaction case):
     * - BLACK-BOX: validate=false should bypass password policy for backwards compatibility.
     * - WHITE-BOX: covers constructor path where (validate && ...) is false, so no exception.
     */
    @Test
    public void testR14_LegacyUser_BypassValidation() {
        System.out.println("R14: Testing legacy user with weak password (validation bypassed)");

        UserLoginInfo legacyUser = new UserLoginInfo("legacyuser", "weak", false);

        assertNotNull(legacyUser);
        assertEquals("legacyuser", legacyUser.getUserName());
        assertEquals("weak", legacyUser.getPassword());
    }

    /**
     * setPassword interaction:
     * - BLACK-BOX: even if legacy users exist, updating passwords must meet the rule.
     * - WHITE-BOX: covers setPassword failure branch (throws) and success branch (updates).
     */
    @Test
    public void testR14_SetPassword_EnforcesValidation() {
        System.out.println("R14: Testing setPassword enforces validation");

        UserLoginInfo user = new UserLoginInfo("admin", "Initial1!");
        String originalPassword = user.getPassword();

        assertThrows(IllegalArgumentException.class, () -> {
            user.setPassword("weak");
        });

        assertEquals(originalPassword, user.getPassword(),
                "Password should remain unchanged after validation failure");

        user.setPassword("NewPass2@");
        assertEquals("NewPass2@", user.getPassword(),
                "Password should update when valid");
    }

    /**
     * Username setter:
     * - BLACK-BOX: setter should update stored username.
     * - WHITE-BOX: trivial path coverage for setUserName/getUserName.
     */
    @Test
    public void testR14_SetUserName() {
        System.out.println("R14: Testing setUserName");

        UserLoginInfo user = new UserLoginInfo("admin", "Password1!");
        assertEquals("admin", user.getUserName());

        user.setUserName("newadmin");

        assertEquals("newadmin", user.getUserName(),
                "Username should be updated to new value");
    }
}
