package com.example.orderplatform.inventory.service;

import com.example.orderplatform.inventory.domain.Product;
import com.example.orderplatform.inventory.domain.Stock;
import com.example.orderplatform.inventory.repository.ProductRepository;
import com.example.orderplatform.inventory.repository.StockRepository;
import com.example.orderplatform.inventory.web.ConflictException;
import com.example.orderplatform.inventory.web.NotFoundException;
import com.example.orderplatform.inventory.web.dto.ProductPage;
import com.example.orderplatform.inventory.web.dto.ProductRequest;
import com.example.orderplatform.inventory.web.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;

    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException("Product with SKU '" + request.sku() + "' already exists");
        }
        Product product = Product.builder()
                .sku(request.sku())
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .build();
        Product saved = productRepository.saveAndFlush(product);
        stockRepository.save(Stock.builder()
                .productId(saved.getId())
                .available(request.initialStock())
                .build());
        return ProductResponse.of(saved, request.initialStock());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "'page:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public ProductPage listProducts(Pageable pageable) {
        Page<Product> page = productRepository.findAll(pageable);
        java.util.List<ProductResponse> content = page.getContent().stream()
                .map(p -> ProductResponse.of(p, availableFor(p.getId())))
                .toList();
        return new ProductPage(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
        return ProductResponse.of(product, availableFor(id));
    }

    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateStock(Long id, int available) {
        Stock stock = stockRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Stock for product " + id + " not found"));
        stock.setAvailable(available);
        stockRepository.save(stock);
        return getProductUncached(id);
    }

    private ProductResponse getProductUncached(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
        return ProductResponse.of(product, availableFor(id));
    }

    private int availableFor(Long productId) {
        return stockRepository.findById(productId).map(Stock::getAvailable).orElse(0);
    }
}
