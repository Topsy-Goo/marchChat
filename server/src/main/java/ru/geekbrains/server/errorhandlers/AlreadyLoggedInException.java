package ru.geekbrains.server.errorhandlers;

public class AlreadyLoggedInException extends RuntimeException {

    public AlreadyLoggedInException () {
        super();
    }

    public AlreadyLoggedInException (Throwable cause) {
        super (cause);
    }

    public AlreadyLoggedInException (String message) {
        super(message);
    }

    public AlreadyLoggedInException (String message, Throwable cause) {
        super(message, cause);
    }
}
