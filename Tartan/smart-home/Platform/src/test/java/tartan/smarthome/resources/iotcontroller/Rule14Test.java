package tartan.smarthome.resources.iotcontroller;

import org.junit.jupiter.api.Test;
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
 * 6 (or 8) cases
 * 1 - valid password
 * 2 - too short
 * 3 - no uppercase
 * 4 - no number
 * 5 - no symbol
 * 6 - password is null
 * 7 - password of length 0
 * might not need to check either not needed or not really something to think of at this point
 * 8 - strong password - case of many
 *
 *
 */
public class Rule14Test{

    //test 1
    @Test
    public void testR14_ValidPassword_MeetsAllRequirements() {
        // Arrange & Act
        UserLoginInfo user = new UserLoginInfo("admin", "1VPass!b");

        // Assert
        assertNotNull(user);
        assertEquals("admin", user.getUserName());
        assertEquals("1VPass!b", user.getPassword());
    }
    //test 8
    // this test case exists to test the case of many
    @Test
    public void testR14_strongValidPassword() {
        // Arrange & Act
        UserLoginInfo user = new UserLoginInfo("admin", "ABCDEFabcdef123456!@#$%^wowthisisalongpasswordIhopeitisaccepted" +
                "wouldsuckifitwasn'thjhfjakhsdkjlfhkjalshfkjahsdkjfhsakjdhfkjsahfkjhssdkjfhkjsa");

        // Assert
        assertNotNull(user);
        assertEquals("admin", user.getUserName());
        assertEquals("ABCDEFabcdef123456!@#$%^wowthisisalongpasswordIhopeitisaccepted" +
                "wouldsuckifitwasn'thjhfjakhsdkjlfhkjalshfkjahsdkjfhsakjdhfkjsahfkjhssdkjfhkjsa", user.getPassword());
    }

    //test2
    @Test
    public void testR14_InvalidPassword_TooShort() {
        // Arrange, Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new UserLoginInfo("admin", "Short1!")
        );

        assertTrue(exception.getMessage().contains("8"));
    }

    //test3
    @Test
    public void testR14_InvalidPassword_NoUppercase() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", "nouppercase1!");
        });
    }

    //test4
    @Test
    public void testR14_InvalidPassword_NoNumber() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", "NoNumber!");
        });
    }

    //test5
    @Test
    public void testR14_InvalidPassword_NoSymbol() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", "NoSymbol1");
        });
    }

    //test6
    @Test
    public void testR14_InvalidPassword_Null() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", null);
        });
    }

    //test 7
    @Test
    public void testR14_InvalidPassword_EmptyString() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new UserLoginInfo("admin", "");
        });
    }
}