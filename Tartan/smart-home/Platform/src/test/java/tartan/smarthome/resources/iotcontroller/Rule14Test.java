package tartan.smarthome.resources.iotcontroller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for rule 14:
 * R14: The IoT Controller shall require the user to login to the house control panel using a username and password.
 * The password has the following requirements:
 * Minimum length: 8 characters
 * At least one uppercase character
 * At least one number
 * At least one symbol
 *
 * a test for new users, and legacy users
 *  - new users have to follow the strong password guidelines
 *  - legacy, can have any password so don't really need to be tested
 *
 * 11 total tests
 *  - 8 validated ones
 *  testR14_ValidPassword_MeetsAllRequirements
 *  testR14_strongValidPassword
 *  testR14_InvalidPassword_TooShort
 *  testR14_InvalidPassword_NoUppercase
 *  testR14_InvalidPassword_NoNumber
 *  testR14_InvalidPassword_NoSymbol
 *  testR14_InvalidPassword_Null
 *  testR14_InvalidPassword_EmptyString
 *
 *  - 3 unvalidated ones
 *  testR14_LegacyUser_WeakPasswordAllowed
 *  testR14_ConfigUser_NoValidation
 *  testR14_BypassValidation_StrongPasswordStillWorks
 *
 * OpenAI, chatgpt 5.2 was used 2026-01-25, "Although I can see my tests are running in the build/~/index.html file
 * it is not printing anything to the console please add code to do so."
 *  - it added both a @displayname
 *  - and the system.out.println
 *      - but only the system print was necessary to log it, hence removed the display name
 *  - was also used to quickly add legacy password testing
 */
public class Rule14Test {

    // ========== VALIDATED PASSWORD TESTS (New Users) ==========

    @Test
    public void testR14_ValidPassword_MeetsAllRequirements() {
        System.out.println("Testing valid password with all requirements");
        // Arrange & Act
        UserLoginInfo user = new UserLoginInfo("admin", "1VPass!b");

        // Assert
        assertNotNull(user);
        assertEquals("admin", user.getUserName());
        assertEquals("1VPass!b", user.getPassword());
    }

    @Test
    public void testR14_strongValidPassword() {
        System.out.println("Testing strong valid password");
        // Arrange & Act
        UserLoginInfo user = new UserLoginInfo("admin", "ABCDEFabcdef123456!@#$%^wowthisisalongpasswordIhopeitisaccepted" +
                "wouldsuckifitwasn'thjhfjakhsdkjlfhkjalshfkjahsdkjfhsakjdhfkjsahfkjhssdkjfhkjsa");

        // Assert
        assertNotNull(user);
        assertEquals("admin", user.getUserName());
    }

    @Test
    public void testR14_InvalidPassword_TooShort() {
        System.out.println("Testing password validation: too short");
        // Arrange, Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new UserLoginInfo("admin", "Short1!")
        );

        assertTrue(exception.getMessage().contains("8"));
    }

    @Test
    public void testR14_InvalidPassword_NoUppercase() {
        System.out.println("Testing password validation: no uppercase");
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", "nouppercase1!");
        });
    }

    @Test
    public void testR14_InvalidPassword_NoNumber() {
        System.out.println("Testing password validation: no number");
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", "NoNumber!");
        });
    }

    @Test
    public void testR14_InvalidPassword_NoSymbol() {
        System.out.println("Testing password validation: no symbol");
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", "NoSymbol1");
        });
    }

    @Test
    public void testR14_InvalidPassword_Null() {
        System.out.println("Testing password validation: null password");
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", null);
        });
    }

    @Test
    @DisplayName("R14: Invalid password - empty string")
    public void testR14_InvalidPassword_EmptyString() {
        System.out.println("Testing password validation: empty string");
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", "");
        });
    }

    // ========== BYPASS VALIDATION TESTS (Legacy/Config Users) ==========
    // ie constructor uses a false

    @Test
    public void testR14_LegacyUser_WeakPasswordAllowed() {
        System.out.println("un:Testing legacy user creation with weak password (validation bypassed)");
        // Arrange & Act
        UserLoginInfo legacyUser = new UserLoginInfo("dockeruser", "weak", false);

        // Assert
        assertNotNull(legacyUser);
        assertEquals("dockeruser", legacyUser.getUserName());
        assertEquals("weak", legacyUser.getPassword());
    }

    @Test
    public void testR14_ConfigUser_NoValidation() {
        System.out.println("un:Testing config user creation without validation");
        // Arrange & Act
        UserLoginInfo configUser = new UserLoginInfo("config", "password123", false);

        // Assert
        assertNotNull(configUser);
        assertEquals("config", configUser.getUserName());
        assertEquals("password123", configUser.getPassword());
    }

    @Test
    public void testR14_BypassValidation_StrongPasswordStillWorks() {
        System.out.println("un:Testing that bypass mode still accepts strong passwords");
        // Arrange & Act
        UserLoginInfo user = new UserLoginInfo("admin", "StrongPass123!", false);

        // Assert
        assertNotNull(user);
        assertEquals("admin", user.getUserName());
        assertEquals("StrongPass123!", user.getPassword());
    }
}