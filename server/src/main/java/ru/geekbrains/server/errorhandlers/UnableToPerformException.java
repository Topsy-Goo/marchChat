package ru.geekbrains.server.errorhandlers;

public class UnableToPerformException extends RuntimeException {

    public UnableToPerformException () {
        super();
    }

    public UnableToPerformException (Throwable cause) {
        super (cause);
    }

    public UnableToPerformException (String message) {
        super(message);
    }

    public UnableToPerformException (String message, Throwable cause) {
        super(message, cause);
    }
}
