package com.biddingapp.web;

import com.biddingapp.domain.User;
import com.biddingapp.service.UserService;
import com.biddingapp.web.dto.ErrorResponse;
import com.biddingapp.web.dto.LoginRequest;
import com.biddingapp.web.dto.MeResponse;
import com.biddingapp.web.dto.RegisterForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthApiController(UserService userService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
                "token", token.getToken(),
                "headerName", token.getHeaderName()
        );
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        User user = AuthSupport.currentUser(authentication, userService);
        if (user == null) {
            return MeResponse.anonymous();
        }
        return MeResponse.of(user.getId(), user.getUsername(), user.getRole().name());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username() == null ? "" : request.username().trim(),
                            request.password()
                    )
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            User user = AuthSupport.currentUser(authentication, userService);
            return ResponseEntity.ok(MeResponse.of(user.getId(), user.getUsername(), user.getRole().name()));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Username or password was not recognized."));
        }
    }

    @PostMapping("/register")
    public MeResponse register(@Valid @RequestBody RegisterForm form) {
        User user = userService.register(form);
        return MeResponse.of(user.getId(), user.getUsername(), user.getRole().name());
    }
}
