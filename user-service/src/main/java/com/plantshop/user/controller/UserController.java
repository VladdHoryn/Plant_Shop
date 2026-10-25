package com.plantshop.user.controller;

import com.plantshop.user.dto.MessageResponse;
import com.plantshop.user.dto.PasswordResetRequest;
import com.plantshop.user.dto.RegisterUserRequest;
import com.plantshop.user.dto.UserResponse;
import com.plantshop.user.service.PasswordResetService;
import com.plantshop.user.service.RegistrationService;
import com.plantshop.user.service.RequestOrigin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterUserRequest request, HttpServletRequest http) {
        return registrationService.register(request, origin(http));
    }

    /** Always 202 with the same text, whether or not the account exists. */
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                HttpServletRequest http) {
        passwordResetService.requestReset(request.email(), origin(http));
        return new MessageResponse("If the account exists, a password reset link has been sent");
    }

    private static RequestOrigin origin(HttpServletRequest http) {
        return new RequestOrigin(http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }
}
