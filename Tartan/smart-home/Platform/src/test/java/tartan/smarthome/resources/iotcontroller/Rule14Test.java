package tartan.smarthome.resources.iotcontroller;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for rule 14:
 * R14: The IoT Controller shall require the user to login to the house control panel using a username and password.
 * The password has the following requirements:
 * - Minimum length: 8 characters
 * - At least one uppercase character
 * - At least one number
 * - At least one symbol
 *
 * new users have to follow the guidlines
 * legacy users do not
 *
 * also tests the set password and set username to ensure they work
 *
 * OpenAI, chatgpt 5.2 was used 2026-01-25, "Although I can see my tests are running in the build/~/index.html file
 * it is not printing anything to the console please add code to do so."
 *  - Added system.out.println for test logging
 *  - Used to quickly add legacy password testing
 *
 * OpenAI, chatgpt 5.2 was used 2026-02-09, "My tests are a mess please go through and remove superflous tests, and organize the tests"
 */
public class Rule14Test {
    // New Users

    /**
     * Test valid password at boundary (exactly 8 characters)
     * This covers BVA and tests that a minimal valid password works
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
     * Test password that's too short (7 characters)
     * BVA: boundary - 1
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
     * Test password without uppercase letter
     * Tests one of the 4 requirements
     */
    @Test
    public void testR14_InvalidPassword_NoUppercase() {
        System.out.println("R14: Testing password validation - no uppercase");

        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", "password123!");
        });
    }

    /**
     * Test password without number
     * Tests one of the 4 requirements
     */
    @Test
    public void testR14_InvalidPassword_NoNumber() {
        System.out.println("R14: Testing password validation - no number");

        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", "Password!");
        });
    }

    /**
     * Test password without symbol
     * Tests one of the 4 requirements
     */
    @Test
    public void testR14_InvalidPassword_NoSymbol() {
        System.out.println("R14: Testing password validation - no symbol");

        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", "Password1");
        });
    }

    /**
     * Test null password (edge case)
     * Ensures robust handling of invalid input
     */
    @Test
    public void testR14_InvalidPassword_Null() {
        System.out.println("R14: Testing password validation - null password");

        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", null);
        });
    }

    // Legacy Users

    /**
     * Test that legacy users can bypass password validation
     * This is required for backwards compatibility with existing weak passwords
     */
    @Test
    public void testR14_LegacyUser_BypassValidation() {
        System.out.println("R14: Testing legacy user with weak password (validation bypassed)");

        UserLoginInfo legacyUser = new UserLoginInfo("legacyuser", "weak", false);

        assertNotNull(legacyUser);
        assertEquals("legacyuser", legacyUser.getUserName());
        assertEquals("weak", legacyUser.getPassword());
    }

    // Update password, or username

    /**
     * Test that setPassword() enforces validation
     * This ensures password can be updated but must meet requirements
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
     * Test setUserName() method
     * Ensures username can be changed after user creation
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