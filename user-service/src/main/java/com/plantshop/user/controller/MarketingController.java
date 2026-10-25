package com.plantshop.user.controller;

import com.plantshop.user.dto.GatedActionResponse;
import com.plantshop.user.dto.MarketingEmailRequest;
import com.plantshop.user.service.MarketingEmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Back-office endpoint for the MARKETING role (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class MarketingController {

    private final MarketingEmailService marketingEmailService;

    /** 200 either way: a denied send is a normal business outcome, reported in "decision". */
    @PostMapping("/emails")
    public GatedActionResponse send(@Valid @RequestBody MarketingEmailRequest request) {
        return marketingEmailService.send(request);
    }
}
