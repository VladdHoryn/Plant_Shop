package com.plantshop.user.controller;

import com.plantshop.user.dto.ConsentOverview;
import com.plantshop.user.dto.ConsentResponse;
import com.plantshop.user.dto.GrantConsentRequest;
import com.plantshop.user.dto.RevokeConsentRequest;
import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.security.AppUserPrincipal;
import com.plantshop.user.service.ConsentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Explicit GRANT and REVOKE per purpose; an unknown purpose is rejected with 400. */
@RestController
@RequestMapping("/api/users/{id}/consents")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    @GetMapping
    public ConsentOverview overview(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        return consentService.overview(actor, id);
    }

    @PostMapping("/{purpose}/grant")
    public ConsentResponse grant(@PathVariable Long id, @PathVariable ConsentPurpose purpose,
                                 @Valid @RequestBody GrantConsentRequest request,
                                 @AuthenticationPrincipal AppUserPrincipal actor) {
        return consentService.grant(actor, id, purpose, request);
    }

    @PostMapping("/{purpose}/revoke")
    public ConsentResponse revoke(@PathVariable Long id, @PathVariable ConsentPurpose purpose,
                                  @Valid @RequestBody RevokeConsentRequest request,
                                  @AuthenticationPrincipal AppUserPrincipal actor) {
        return consentService.revoke(actor, id, purpose, request);
    }
}
