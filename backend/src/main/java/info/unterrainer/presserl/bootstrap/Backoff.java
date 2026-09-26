package info.unterrainer.presserl.bootstrap;

import java.time.Duration;

/**
 * Exponential backoff: the delay doubles from {@code initial} up to {@code max}.
 */
public class Backoff {

    private final Duration max;
    private Duration next;

    public Backoff(Duration initial, Duration max) {
        this.next = initial;
        this.max = max;
    }

    public Duration next() {
        Duration current = next;
        Duration doubled = next.multipliedBy(2);
        next = doubled.compareTo(max) > 0 ? max : doubled;
        return current;
    }
}
