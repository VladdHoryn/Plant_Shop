package com.plantshop.user.controller;

import com.plantshop.user.dto.ErasureResponse;
import com.plantshop.user.dto.PersonalDataExport;
import com.plantshop.user.security.AppUserPrincipal;
import com.plantshop.user.service.ErasureService;
import com.plantshop.user.service.PersonalDataExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Data-subject rights: access (export) and erasure. Who may call them is decided in the services. */
@RestController
@RequestMapping("/api/users/{id}")
@RequiredArgsConstructor
public class PrivacyController {

    private final PersonalDataExportService exportService;
    private final ErasureService erasureService;

    @GetMapping("/personal-data")
    public PersonalDataExport exportPersonalData(@PathVariable Long id,
                                                 @AuthenticationPrincipal AppUserPrincipal actor) {
        return exportService.export(actor, id);
    }

    /** POST only: an irreversible command must never run on a GET (a link, a prefetch, a crawler). */
    @PostMapping("/anonymize")
    public ErasureResponse anonymize(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        return erasureService.anonymize(actor, id);
    }
}
