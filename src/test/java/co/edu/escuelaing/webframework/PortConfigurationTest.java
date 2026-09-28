package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PortConfigurationTest {

    @Test
    void defaultsTo8080WhenPortIsNotSet() {
        assertEquals(8080, WebFramework.resolvePort(null));
        assertEquals(8080, WebFramework.resolvePort("  "));
    }

    @Test
    void usesPortFromEnvironmentValue() {
        assertEquals(6000, WebFramework.resolvePort("6000"));
        assertEquals(35000, WebFramework.resolvePort(" 35000 "));
    }

    @Test
    void rejectsNonNumericPort() {
        assertThrows(IllegalArgumentException.class, () -> WebFramework.resolvePort("abc"));
    }

    @Test
    void rejectsPortOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> WebFramework.resolvePort("0"));
        assertThrows(IllegalArgumentException.class, () -> WebFramework.resolvePort("70000"));
    }
}
