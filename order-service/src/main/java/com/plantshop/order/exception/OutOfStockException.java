package com.plantshop.order.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class OutOfStockException extends RuntimeException {

    private final List<Long> plantIds;

    public OutOfStockException(List<Long> plantIds) {
        super("Not enough stock for plants " + plantIds);
        this.plantIds = plantIds;
    }
}
