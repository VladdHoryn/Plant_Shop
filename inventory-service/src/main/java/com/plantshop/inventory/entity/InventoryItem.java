package com.plantshop.inventory.entity;

import com.plantshop.inventory.exception.InvalidStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
public class InventoryItem {

    @Id
    @Column(name = "plant_id")
    private Long plantId;

    @Column(nullable = false)
    private int quantity;

    /** Units held for orders awaiting payment; they cannot be sold to anyone else. */
    @Column(nullable = false)
    private int reserved;

    @Version
    private Long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public InventoryItem(Long plantId) {
        this.plantId = plantId;
    }

    public int getAvailable() {
        return quantity - reserved;
    }

    public void reserve(int amount) {
        if (amount > getAvailable()) {
            throw new InvalidStockException("Not enough stock for plant " + plantId);
        }
        reserved += amount;
    }

    public void release(int amount) {
        reserved -= amount;
    }

    /** Reserved units have been paid for and leave the warehouse. */
    public void commit(int amount) {
        reserved -= amount;
        quantity -= amount;
    }

    public void changeQuantity(int newQuantity) {
        if (newQuantity < reserved) {
            throw new InvalidStockException(
                    "Quantity " + newQuantity + " is below the " + reserved + " units reserved for plant " + plantId);
        }
        quantity = newQuantity;
    }
}
