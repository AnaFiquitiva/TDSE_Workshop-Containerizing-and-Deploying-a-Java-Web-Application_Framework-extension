package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestTest {

    @Test
    void parsesPathWithoutQueryString() {
        Request request = Request.parse("GET /pi HTTP/1.1");
        assertEquals("GET", request.getMethod());
        assertEquals("/pi", request.getPath());
        assertNull(request.getValue("name"));
    }

    @Test
    void parsesSingleQueryParam() {
        Request request = Request.parse("GET /hello?name=Pedro HTTP/1.1");
        assertEquals("/hello", request.getPath());
        assertEquals("Pedro", request.getValue("name"));
    }

    @Test
    void parsesMultipleQueryParams() {
        Request request = Request.parse("GET /hello?name=Pedro&language=en HTTP/1.1");
        assertEquals("Pedro", request.getValue("name"));
        assertEquals("en", request.getValue("language"));
    }

    @Test
    void missingParamReturnsNullInsteadOfFailing() {
        Request request = Request.parse("GET /hello?name=Pedro HTTP/1.1");
        assertNull(request.getValue("language"));
    }

    @Test
    void decodesUrlEncodedValues() {
        Request request = Request.parse("GET /hello?name=Juan%20Perez HTTP/1.1");
        assertEquals("Juan Perez", request.getValue("name"));
    }

    @Test
    void rejectsMalformedRequestLine() {
        assertThrows(IllegalArgumentException.class, () -> Request.parse("GARBAGE"));
    }

    @Test
    void rejectsBlankRequestLine() {
        assertThrows(IllegalArgumentException.class, () -> Request.parse("   "));
    }
}
