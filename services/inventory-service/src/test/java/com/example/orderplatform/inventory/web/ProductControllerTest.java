package com.example.orderplatform.inventory.web;

import com.example.orderplatform.inventory.service.InventoryService;
import com.example.orderplatform.inventory.web.dto.ProductPage;
import com.example.orderplatform.inventory.web.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private InventoryService inventoryService;

    @Test
    void createsProductWith201() throws Exception {
        when(inventoryService.createProduct(any()))
                .thenReturn(new ProductResponse(1L, "SKU-1", "Widget", null, new BigDecimal("9.99"), 10, null));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-1","name":"Widget","price":9.99,"initialStock":10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.available").value(10));
    }

    @Test
    void returns400ProblemDetailsOnValidationError() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"","name":"","price":-1,"initialStock":-5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void returns404ProblemDetailsForMissingProduct() throws Exception {
        when(inventoryService.getProduct(99L)).thenThrow(new NotFoundException("Product 99 not found"));

        mockMvc.perform(get("/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Product 99 not found"));
    }

    @Test
    void listsProducts() throws Exception {
        when(inventoryService.listProducts(any()))
                .thenReturn(new ProductPage(List.of(), 0, 20, 0));

        mockMvc.perform(get("/products").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
