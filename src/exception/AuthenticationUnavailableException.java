package exception;

/** Indicates that authentication could not be completed because an infrastructure dependency failed. */
public class AuthenticationUnavailableException extends AuthenticationException {
    public AuthenticationUnavailableException(String message) {
        super(message);
    }
}
