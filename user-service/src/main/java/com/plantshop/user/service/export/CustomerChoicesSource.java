package com.plantshop.user.service.export;

/** Port: the customer's preferences and consent decisions. */
public interface CustomerChoicesSource {

    CustomerChoices read(Long subjectId);
}
