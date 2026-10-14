package com.plantshop.order.service;

import com.plantshop.order.client.CatalogClient;
import com.plantshop.order.client.CatalogClient.PlantDto;
import com.plantshop.order.client.InventoryClient;
import com.plantshop.order.client.InventoryClient.StockCheckRequest;
import com.plantshop.order.client.InventoryClient.StockCheckResponse;
import com.plantshop.order.dto.CreateOrderRequest;
import com.plantshop.order.dto.OrderItemRequest;
import com.plantshop.order.dto.OrderResponse;
import com.plantshop.order.entity.Order;
import com.plantshop.order.entity.OrderItem;
import com.plantshop.order.exception.InvalidOrderException;
import com.plantshop.order.exception.OutOfStockException;
import com.plantshop.order.exception.ResourceNotFoundException;
import com.plantshop.order.mapper.OrderMapper;
import com.plantshop.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CatalogClient catalogClient;
    private final InventoryClient inventoryClient;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    /**
     * Validates prices and availability synchronously, stores the order as PENDING and starts the
     * saga. Stock is only reserved later by inventory-service, so the up-front check is a fast-fail,
     * not a guarantee. Remote calls run outside the DB transaction to avoid holding a connection.
     */
    public OrderResponse create(CreateOrderRequest request) {
        Map<Long, Integer> quantities = request.items().stream()
                .collect(Collectors.toMap(OrderItemRequest::plantId, OrderItemRequest::quantity, Integer::sum,
                        LinkedHashMap::new));

        Map<Long, PlantDto> plants = catalogClient.findByIds(quantities.keySet()).stream()
                .collect(Collectors.toMap(PlantDto::id, Function.identity()));
        for (Long plantId : quantities.keySet()) {
            PlantDto plant = plants.get(plantId);
            if (plant == null || !plant.active()) {
                throw new InvalidOrderException("Plant " + plantId + " is not available for sale");
            }
        }

        List<StockCheckRequest> stockRequests = quantities.entrySet().stream()
                .map(e -> new StockCheckRequest(e.getKey(), e.getValue()))
                .toList();
        List<Long> missing = inventoryClient.check(stockRequests).stream()
                .filter(r -> !r.inStock())
                .map(StockCheckResponse::plantId)
                .toList();
        if (!missing.isEmpty()) {
            throw new OutOfStockException(missing);
        }

        Order order = new Order();
        order.setCustomerEmail(request.customerEmail());
        order.setShippingAddress(request.shippingAddress());
        order.setPaymentMethod(request.paymentMethod());
        quantities.forEach((plantId, quantity) -> {
            PlantDto plant = plants.get(plantId);
            order.addItem(new OrderItem(plantId, plant.name(), quantity, plant.price()));
        });

        return transactionTemplate.execute(status -> {
            Order saved = orderRepository.save(order);
            eventPublisher.publishEvent(OrderMapper.toCreatedEvent(saved));
            log.info("Order {} created for {} with total {}", saved.getId(), saved.getCustomerEmail(),
                    saved.getTotalAmount());
            return OrderMapper.toResponse(saved);
        });
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return orderRepository.findWithItemsById(id)
                .map(OrderMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findByCustomer(String email) {
        return orderRepository.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(email).stream()
                .map(OrderMapper::toResponse)
                .toList();
    }

    @Transactional
    public void confirm(Long orderId) {
        orderRepository.findById(orderId).ifPresentOrElse(
                order -> {
                    if (order.confirm()) {
                        log.info("Order {} confirmed", orderId);
                    }
                },
                () -> log.warn("Received confirmation for unknown order {}", orderId));
    }

    @Transactional
    public void cancel(Long orderId, String reason) {
        orderRepository.findById(orderId).ifPresentOrElse(
                order -> {
                    if (order.cancel(reason)) {
                        log.info("Order {} cancelled: {}", orderId, reason);
                    }
                },
                () -> log.warn("Received cancellation for unknown order {}", orderId));
    }
}
