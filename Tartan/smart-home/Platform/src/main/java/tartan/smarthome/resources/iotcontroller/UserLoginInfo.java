package tartan.smarthome.resources.iotcontroller;

/**
 * Represents user login information
 */
public class UserLoginInfo {

    /** the user credentials */
    private String userName, password;

    public UserLoginInfo(String userName, String password) {
        if (!isValidPassword(password)) {  // needed for rule 14
            throw new IllegalArgumentException(
                    "Password must be 8+ chars with uppercase, number, and symbol"
            );
        }
        this.userName = userName;
        this.password = password;
    }

    //adding this as there doesn't seem to be a password verification anywhere
    // I have checked the entire iotcontroller file, and more at this point I am confident this is a bug
    // I also had openai, chatgpt 5.2, 2026-01-24, "Check these files for password verification, I am unable to find any
    // give your suggestion on where to put the verification, assuming it doesn't exist"
    /**
     * verify that the users password is valid
     *  longer than 8 chars
     *  1 uppercase
     *  1 symbol
     *  1 letter
     * @param password users password
     * @return Boolean value if password fits requirements
     */
    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        boolean hasUppercase = false;
        boolean hasNumber = false;
        boolean hasSymbol = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUppercase = true;
            if (Character.isDigit(c)) hasNumber = true;
            if (!Character.isLetterOrDigit(c)) hasSymbol = true;
        }
        return hasUppercase && hasNumber && hasSymbol;
    }

    /**
     *  Get the username
     * @return the user name
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Set the username
     * @param userName
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Get password
     * @return
     */
    public String getPassword() {
        return password;
    }

    /**
     * Set password
     * @param password
     */
    public void setPassword(String password) {
        if (!isValidPassword(password)) { // again this is just for rule 14
            throw new IllegalArgumentException(
                    "Password must be at least 8 characters and contain " +
                            "at least one uppercase letter, one number, and one symbol"
            );
        }
        this.password = password;
    }
}
