package info.unterrainer.presserl.bootstrap;

/**
 * A bootstrap attempt failed for a reason the operator must know about.
 */
public class BootstrapException extends RuntimeException {

    public BootstrapException(String message) {
        super(message);
    }
}
