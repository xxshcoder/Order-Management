package com.example.orderplatform.inventory.service;

import com.example.orderplatform.inventory.domain.Product;
import com.example.orderplatform.inventory.domain.Stock;
import com.example.orderplatform.inventory.repository.ProductRepository;
import com.example.orderplatform.inventory.repository.StockRepository;
import com.example.orderplatform.inventory.web.ConflictException;
import com.example.orderplatform.inventory.web.NotFoundException;
import com.example.orderplatform.inventory.web.dto.ProductRequest;
import com.example.orderplatform.inventory.web.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private StockRepository stockRepository;
    @InjectMocks private InventoryService service;

    private final ProductRequest request =
            new ProductRequest("SKU-1", "Widget", "A widget", new BigDecimal("9.99"), 10);

    @Test
    void createsProductAndInitialStock() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(101L);
            return p;
        });

        ProductResponse response = service.createProduct(request);

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.available()).isEqualTo(10);
        ArgumentCaptor<Stock> stockCaptor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(stockCaptor.capture());
        assertThat(stockCaptor.getValue().getAvailable()).isEqualTo(10);
    }

    @Test
    void rejectsDuplicateSku() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(true);
        assertThatThrownBy(() -> service.createProduct(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("SKU-1");
    }

    @Test
    void returnsNotFoundForUnknownProduct() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getProduct(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsProductsWithAvailableQuantity() {
        Product product = Product.builder().id(1L).sku("SKU-1").name("Widget")
                .price(new BigDecimal("9.99")).build();
        when(productRepository.findAll(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 20), 1));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(new Stock(1L, 4, 0)));

        var page = service.listProducts(PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).available()).isEqualTo(4);
        assertThat(page.totalElements()).isEqualTo(1);
    }
}
