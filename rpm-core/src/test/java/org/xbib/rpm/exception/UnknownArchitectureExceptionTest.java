package org.xbib.rpm.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 *
 */
public class UnknownArchitectureExceptionTest {

    @Test
    public void exception() {
        UnknownArchitectureException ex
                = new UnknownArchitectureException("unknown");
        assertEquals("Unknown architecture 'unknown'", ex.getMessage());
    }

    @Test
    public void exceptionWithCause() {
        Exception cause = new Exception("cause");
        UnknownArchitectureException ex = new UnknownArchitectureException("unknown", cause);
        assertEquals("Unknown architecture 'unknown'", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
}
