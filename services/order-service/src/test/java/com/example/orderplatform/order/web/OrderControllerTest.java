package com.example.orderplatform.order.web;

import com.example.orderplatform.order.service.OrderService;
import com.example.orderplatform.order.web.dto.OrderPage;
import com.example.orderplatform.order.web.dto.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private OrderService orderService;
    @MockBean private CurrentUser currentUser;

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void identity() {
        when(currentUser.userId(any())).thenReturn("user-1");
        when(currentUser.isAdmin(any())).thenReturn(false);
    }

    @Test
    void createsOrderAndPassesIdempotencyKey() throws Exception {
        when(orderService.create(eq("user-1"), eq("idem-1"), any()))
                .thenReturn(new OrderResponse(orderId, "user-1", "PENDING",
                        new BigDecimal("9.99"), List.of(), null, null));

        mockMvc.perform(post("/orders")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":1,"quantity":1,"unitPrice":9.99}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(orderService).create(eq("user-1"), eq("idem-1"), any());
    }

    @Test
    void returns400ForEmptyItems() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("Idempotency-Key", "idem-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404ProblemDetailsForMissingOrder() throws Exception {
        when(orderService.get(eq(orderId), eq("user-1"), eq(false)))
                .thenThrow(new NotFoundException("Order " + orderId + " not found"));

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void listsOrders() throws Exception {
        when(orderService.list(eq("user-1"), any()))
                .thenReturn(new OrderPage(List.of(), 0, 20, 0));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
