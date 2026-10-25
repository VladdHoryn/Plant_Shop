package com.plantshop.user.controller;

import com.plantshop.user.dto.AnalyticsEventRequest;
import com.plantshop.user.dto.GatedActionResponse;
import com.plantshop.user.security.AppUserPrincipal;
import com.plantshop.user.service.AnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /** Tracks the signed-in customer only; without OPTIONAL_ANALYTICS consent the event is dropped. */
    @PostMapping("/events")
    public GatedActionResponse track(@Valid @RequestBody AnalyticsEventRequest request,
                                     @AuthenticationPrincipal AppUserPrincipal actor) {
        return analyticsService.track(actor.getId(), request);
    }
}
