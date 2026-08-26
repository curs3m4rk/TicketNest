package com.ticketnest.common.logging;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void missingRequestId_generatesUuidAndMakesItAvailableDuringRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/shows");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdDuringChain = new AtomicReference<>();

        filter.doFilter(request, response,
                (servletRequest, servletResponse) ->
                        requestIdDuringChain.set(MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY)));

        String responseRequestId = response.getHeader(CorrelationIdFilter.REQUEST_ID_HEADER);
        assertEquals(responseRequestId, requestIdDuringChain.get());
        assertEquals(responseRequestId, UUID.fromString(responseRequestId).toString());
        assertNull(MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY));
    }

    @Test
    void validRequestId_isReused() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/shows");
        request.addHeader(CorrelationIdFilter.REQUEST_ID_HEADER, "gateway-request_123.abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertEquals("gateway-request_123.abc",
                        MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY)));

        assertEquals("gateway-request_123.abc",
                response.getHeader(CorrelationIdFilter.REQUEST_ID_HEADER));
        assertNull(MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY));
    }

    @Test
    void unsafeRequestId_isReplaced() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/shows");
        request.addHeader(CorrelationIdFilter.REQUEST_ID_HEADER, "unsafe request id\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        String responseRequestId = response.getHeader(CorrelationIdFilter.REQUEST_ID_HEADER);
        assertNotEquals("unsafe request id\nvalue", responseRequestId);
        assertEquals(responseRequestId, UUID.fromString(responseRequestId).toString());
    }

    @Test
    void downstreamFailure_stillClearsMdc() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/shows");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(ServletException.class, () ->
                filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                    throw new ServletException("test failure");
                }));

        assertNull(MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY));
    }
}
