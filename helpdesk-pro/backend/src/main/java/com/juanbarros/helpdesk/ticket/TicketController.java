package com.juanbarros.helpdesk.ticket;

import com.juanbarros.helpdesk.common.PageResponse;
import com.juanbarros.helpdesk.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un ticket a nombre del usuario autenticado")
    public TicketResponse create(@Valid @RequestBody CreateTicketRequest request,
                                 @AuthenticationPrincipal User currentUser) {
        return ticketService.create(request, currentUser);
    }

    @GetMapping
    @Operation(summary = "Lista tickets con filtros, búsqueda y paginación",
            description = "Los usuarios solo ven sus tickets. Ordenable por createdAt, updatedAt o slaDueAt.")
    public PageResponse<TicketResponse> search(@ParameterObject TicketFilter filter,
                                               @ParameterObject
                                               @PageableDefault(size = 10, sort = "createdAt",
                                                       direction = Sort.Direction.DESC) Pageable pageable,
                                               @AuthenticationPrincipal User currentUser) {
        return ticketService.search(filter, pageable, currentUser);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un ticket")
    public TicketResponse findById(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        return ticketService.findById(id, currentUser);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    @Operation(summary = "Cambia el estado respetando el flujo permitido")
    public TicketResponse changeStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request,
                                       @AuthenticationPrincipal User currentUser) {
        return ticketService.changeStatus(id, request.status(), currentUser);
    }

    @PatchMapping("/{id}/priority")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    @Operation(summary = "Cambia la prioridad y recalcula el vencimiento del SLA")
    public TicketResponse changePriority(@PathVariable Long id, @Valid @RequestBody UpdatePriorityRequest request,
                                         @AuthenticationPrincipal User currentUser) {
        return ticketService.changePriority(id, request.priority(), currentUser);
    }

    @PatchMapping("/{id}/assignee")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    @Operation(summary = "Asigna el ticket a un técnico (assigneeId null para desasignar)")
    public TicketResponse assign(@PathVariable Long id, @RequestBody AssignTicketRequest request,
                                 @AuthenticationPrincipal User currentUser) {
        return ticketService.assign(id, request.assigneeId(), currentUser);
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "Comentarios del ticket (las notas internas solo las ve el staff)")
    public List<CommentResponse> findComments(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        return ticketService.findComments(id, currentUser);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agrega un comentario o una nota interna")
    public CommentResponse addComment(@PathVariable Long id, @Valid @RequestBody CreateCommentRequest request,
                                      @AuthenticationPrincipal User currentUser) {
        return ticketService.addComment(id, request, currentUser);
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "Historial de cambios del ticket")
    public List<EventResponse> findEvents(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        return ticketService.findEvents(id, currentUser);
    }
}
