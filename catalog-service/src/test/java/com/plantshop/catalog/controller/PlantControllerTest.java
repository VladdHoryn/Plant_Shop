package com.plantshop.catalog.controller;

import com.plantshop.catalog.service.PlantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Contract drift found by Schemathesis (Lab 11): a huge page number gave 500 instead of a documented 400. */
@ExtendWith(MockitoExtension.class)
class PlantControllerTest {

    @Mock
    private PlantService plantService;
    @InjectMocks
    private PlantController controller;

    @Test
    void pageWhoseOffsetOverflowsTheDatabaseLimitIsABadRequest() {
        Pageable tooFar = PageRequest.of(339_360_973, 197);

        assertThatThrownBy(() -> controller.search(null, null, null, null, tooFar))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(plantService);
    }

    @Test
    void lastPageWithinTheLimitIsSearched() {
        Pageable lastAllowed = PageRequest.of(Integer.MAX_VALUE, 1);
        when(plantService.search(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        assertThat(controller.search(null, null, null, null, lastAllowed)).isEmpty();
    }
}
