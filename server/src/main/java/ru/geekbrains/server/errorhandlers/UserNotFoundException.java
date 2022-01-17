package ru.geekbrains.server.errorhandlers;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException () {
        super();
    }

    public UserNotFoundException (Throwable cause) {
        super (cause);
    }

    public UserNotFoundException (String message) {
        super(message);
    }

    public UserNotFoundException (String message, Throwable cause) {
        super(message, cause);
    }
}
