package com.moviebooking.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    @DisplayName("Should create 200 OK response with static factory")
    void testOkResponse() {
        ApiResponse<String> response = ApiResponse.ok("test-data");

        assertTrue(response.isSuccess());
        assertEquals(200, response.getStatusCode());
        assertEquals("test-data", response.getData());
        assertNotNull(response.getTimestamp());
    }

    @Test
    @DisplayName("Should create 201 Created response with message")
    void testCreatedResponse() {
        ApiResponse<String> response = ApiResponse.created("created-data", "Resource Created");

        assertTrue(response.isSuccess());
        assertEquals(201, response.getStatusCode());
        assertEquals("Resource Created", response.getMessage());
        assertEquals("created-data", response.getData());
    }

    @Test
    @DisplayName("Should create Error response with status code and details")
    void testErrorResponse() {
        ApiResponse<Void> response = ApiResponse.error(404, "Not Found", "Detail error message");

        assertFalse(response.isSuccess());
        assertEquals(404, response.getStatusCode());
        assertEquals("Not Found", response.getMessage());
        assertEquals("Detail error message", response.getError());
    }
}
