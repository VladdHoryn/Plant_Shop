package com.plantshop.inventory.service;

import com.plantshop.inventory.dto.InventoryResponse;
import com.plantshop.inventory.dto.StockCheckRequest;
import com.plantshop.inventory.dto.StockCheckResponse;
import com.plantshop.inventory.entity.InventoryItem;
import com.plantshop.inventory.entity.ReservationStatus;
import com.plantshop.inventory.entity.StockReservation;
import com.plantshop.inventory.event.InventoryFailedEvent;
import com.plantshop.inventory.event.InventoryReservedEvent;
import com.plantshop.inventory.event.OrderCreatedEvent;
import com.plantshop.inventory.exception.ResourceNotFoundException;
import com.plantshop.inventory.mapper.InventoryMapper;
import com.plantshop.inventory.repository.InventoryItemRepository;
import com.plantshop.inventory.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository itemRepository;
    private final StockReservationRepository reservationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<InventoryResponse> findAll() {
        return itemRepository.findAll().stream().map(InventoryMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InventoryResponse findByPlantId(Long plantId) {
        return itemRepository.findById(plantId)
                .map(InventoryMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item for plant", plantId));
    }

    /** Sets the absolute on-hand quantity (restock or stock-take); creates the item if it does not exist yet. */
    @Transactional
    public InventoryResponse setQuantity(Long plantId, int quantity) {
        InventoryItem item = itemRepository.findById(plantId).orElseGet(() -> new InventoryItem(plantId));
        item.changeQuantity(quantity);
        return InventoryMapper.toResponse(itemRepository.save(item));
    }

    @Transactional
    public void createIfAbsent(Long plantId) {
        if (!itemRepository.existsById(plantId)) {
            itemRepository.save(new InventoryItem(plantId));
            log.info("Created empty inventory item for new plant {}", plantId);
        }
    }

    @Transactional(readOnly = true)
    public List<StockCheckResponse> check(List<StockCheckRequest> requests) {
        Map<Long, InventoryItem> items = itemRepository
                .findAllByPlantIdIn(requests.stream().map(StockCheckRequest::plantId).toList())
                .stream()
                .collect(Collectors.toMap(InventoryItem::getPlantId, Function.identity()));
        return requests.stream()
                .map(r -> {
                    InventoryItem item = items.get(r.plantId());
                    int available = item == null ? 0 : item.getAvailable();
                    return new StockCheckResponse(r.plantId(), r.quantity(), available, available >= r.quantity());
                })
                .toList();
    }

    /**
     * Reserves every line of the order or none of them. The outcome is published as an
     * {@link InventoryReservedEvent} or {@link InventoryFailedEvent} after commit.
     */
    @Transactional
    public void reserve(OrderCreatedEvent order) {
        if (reservationRepository.existsByOrderId(order.orderId())) {
            log.warn("Order {} already processed, ignoring duplicate event", order.orderId());
            return;
        }

        Map<Long, Integer> requested = order.items().stream()
                .collect(Collectors.toMap(OrderCreatedEvent.Item::plantId, OrderCreatedEvent.Item::quantity, Integer::sum));
        Map<Long, InventoryItem> items = itemRepository.findAllForUpdate(requested.keySet()).stream()
                .collect(Collectors.toMap(InventoryItem::getPlantId, Function.identity()));

        for (Map.Entry<Long, Integer> line : requested.entrySet()) {
            InventoryItem item = items.get(line.getKey());
            if (item == null || item.getAvailable() < line.getValue()) {
                eventPublisher.publishEvent(new InventoryFailedEvent(order.orderId(),
                        "Insufficient stock for plant " + line.getKey()));
                return;
            }
        }

        requested.forEach((plantId, quantity) -> {
            items.get(plantId).reserve(quantity);
            reservationRepository.save(new StockReservation(order.orderId(), plantId, quantity));
        });
        eventPublisher.publishEvent(new InventoryReservedEvent(
                order.orderId(), order.customerEmail(), order.totalAmount(), order.paymentMethod()));
    }

    @Transactional
    public void commitReservations(Long orderId) {
        settle(orderId, ReservationStatus.COMMITTED);
    }

    @Transactional
    public void releaseReservations(Long orderId) {
        settle(orderId, ReservationStatus.RELEASED);
    }

    private void settle(Long orderId, ReservationStatus outcome) {
        List<StockReservation> reservations =
                reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);
        if (reservations.isEmpty()) {
            return;
        }
        Map<Long, InventoryItem> items = itemRepository
                .findAllForUpdate(reservations.stream().map(StockReservation::getPlantId).toList()).stream()
                .collect(Collectors.toMap(InventoryItem::getPlantId, Function.identity()));
        for (StockReservation reservation : reservations) {
            InventoryItem item = items.get(reservation.getPlantId());
            if (outcome == ReservationStatus.COMMITTED) {
                item.commit(reservation.getQuantity());
            } else {
                item.release(reservation.getQuantity());
            }
            reservation.setStatus(outcome);
        }
        log.info("Reservations for order {} marked {}", orderId, outcome);
    }
}
