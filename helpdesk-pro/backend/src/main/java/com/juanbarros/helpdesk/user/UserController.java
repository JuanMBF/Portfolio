package com.juanbarros.helpdesk.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Usuarios")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista todos los usuarios (solo ADMIN)")
    public List<UserResponse> findAll() {
        return userService.findAll();
    }

    @GetMapping("/agents")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    @Operation(summary = "Lista los técnicos a los que se les puede asignar un ticket")
    public List<UserSummary> findAgents() {
        return userService.findAgents();
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cambia el rol de un usuario (solo ADMIN)")
    public UserResponse updateRole(@PathVariable Long id,
                                   @Valid @RequestBody UpdateRoleRequest request,
                                   @AuthenticationPrincipal User currentUser) {
        return userService.updateRole(id, request.role(), currentUser);
    }
}
