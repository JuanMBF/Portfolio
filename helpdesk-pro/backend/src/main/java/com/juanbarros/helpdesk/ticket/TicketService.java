package com.juanbarros.helpdesk.ticket;

import com.juanbarros.helpdesk.common.BusinessRuleException;
import com.juanbarros.helpdesk.common.NotFoundException;
import com.juanbarros.helpdesk.common.PageResponse;
import com.juanbarros.helpdesk.user.User;
import com.juanbarros.helpdesk.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.juanbarros.helpdesk.ticket.TicketSpecifications.assignedTo;
import static com.juanbarros.helpdesk.ticket.TicketSpecifications.hasCategory;
import static com.juanbarros.helpdesk.ticket.TicketSpecifications.hasPriority;
import static com.juanbarros.helpdesk.ticket.TicketSpecifications.hasStatus;
import static com.juanbarros.helpdesk.ticket.TicketSpecifications.matchesText;
import static com.juanbarros.helpdesk.ticket.TicketSpecifications.requestedBy;
import static com.juanbarros.helpdesk.ticket.TicketSpecifications.unassigned;

@Service
public class TicketService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "updatedAt", "slaDueAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository commentRepository;
    private final TicketEventRepository eventRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public TicketService(TicketRepository ticketRepository,
                         TicketCommentRepository commentRepository,
                         TicketEventRepository eventRepository,
                         UserRepository userRepository,
                         Clock clock) {
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request, User requester) {
        Instant now = clock.instant();
        Ticket ticket = ticketRepository.save(new Ticket(
                request.title().trim(), request.description().trim(),
                request.category(), request.priority(), requester, now));
        record(ticket, requester, TicketEventType.CREATED,
                "Ticket creado con prioridad " + request.priority(), now);
        return TicketResponse.from(ticket, now);
    }

    /**
     * Un USER solo ve sus propios tickets, sin importar qué filtros mande.
     * Los técnicos ven todo y pueden filtrar por asignado, "mis tickets" o "sin asignar".
     */
    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> search(TicketFilter filter, Pageable pageable, User currentUser) {
        Specification<Ticket> spec = Specification.where(hasStatus(filter.status()))
                .and(hasPriority(filter.priority()))
                .and(hasCategory(filter.category()))
                .and(matchesText(filter.q()));

        if (currentUser.getRole().isStaff()) {
            Long assigneeId = filter.mine() ? currentUser.getId() : filter.assigneeId();
            spec = spec.and(assignedTo(assigneeId)).and(unassigned(filter.unassigned()));
        } else {
            spec = spec.and(requestedBy(currentUser));
        }

        Instant now = clock.instant();
        return PageResponse.from(ticketRepository.findAll(spec, sanitize(pageable)),
                ticket -> TicketResponse.from(ticket, now));
    }

    @Transactional(readOnly = true)
    public TicketResponse findById(Long id, User currentUser) {
        return TicketResponse.from(loadVisibleTicket(id, currentUser), clock.instant());
    }

    @Transactional
    public TicketResponse changeStatus(Long id, TicketStatus newStatus, User agent) {
        Instant now = clock.instant();
        Ticket ticket = loadTicket(id);
        TicketStatus previous = ticket.getStatus();
        ticket.changeStatus(newStatus, now);
        record(ticket, agent, TicketEventType.STATUS_CHANGED, previous + " → " + newStatus, now);

        // Si un técnico empieza a trabajar un ticket sin dueño, se lo asigna automáticamente.
        if (newStatus == TicketStatus.IN_PROGRESS && ticket.getAssignee() == null) {
            ticket.assignTo(agent, now);
            record(ticket, agent, TicketEventType.ASSIGNED, "Asignado automáticamente a " + agent.getFullName(), now);
        }
        return TicketResponse.from(ticket, now);
    }

    @Transactional
    public TicketResponse changePriority(Long id, TicketPriority newPriority, User agent) {
        Instant now = clock.instant();
        Ticket ticket = loadTicket(id);
        if (!ticket.getStatus().isActive()) {
            throw new BusinessRuleException("Solo se puede cambiar la prioridad de tickets activos");
        }
        TicketPriority previous = ticket.getPriority();
        if (previous == newPriority) {
            throw new BusinessRuleException("El ticket ya tiene prioridad " + newPriority);
        }
        ticket.changePriority(newPriority, now);
        record(ticket, agent, TicketEventType.PRIORITY_CHANGED, previous + " → " + newPriority, now);
        return TicketResponse.from(ticket, now);
    }

    @Transactional
    public TicketResponse assign(Long id, Long assigneeId, User agent) {
        Instant now = clock.instant();
        Ticket ticket = loadTicket(id);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleException("No se puede reasignar un ticket cerrado");
        }
        User assignee = assigneeId == null ? null : userRepository.findById(assigneeId)
                .orElseThrow(() -> new NotFoundException("Técnico no encontrado"));
        ticket.assignTo(assignee, now);
        String detail = assignee == null ? "Se quitó la asignación" : "Asignado a " + assignee.getFullName();
        record(ticket, agent, TicketEventType.ASSIGNED, detail, now);
        return TicketResponse.from(ticket, now);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> findComments(Long ticketId, User currentUser) {
        Ticket ticket = loadVisibleTicket(ticketId, currentUser);
        List<TicketComment> comments = currentUser.getRole().isStaff()
                ? commentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                : commentRepository.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(ticket.getId());
        return comments.stream().map(CommentResponse::from).toList();
    }

    @Transactional
    public CommentResponse addComment(Long ticketId, CreateCommentRequest request, User author) {
        Instant now = clock.instant();
        Ticket ticket = loadVisibleTicket(ticketId, author);

        if (request.internal() && !author.getRole().isStaff()) {
            throw new AccessDeniedException("Solo el equipo de soporte puede dejar notas internas");
        }
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleException("El ticket está cerrado y no admite comentarios");
        }

        TicketComment comment = commentRepository.save(
                new TicketComment(ticket, author, request.body().trim(), request.internal(), now));

        // Cuando el usuario responde a un ticket que esperaba su respuesta, vuelve a la cola del técnico.
        if (ticket.getStatus() == TicketStatus.WAITING_ON_USER && ticket.isRequestedBy(author)) {
            ticket.changeStatus(TicketStatus.IN_PROGRESS, now);
            record(ticket, author, TicketEventType.STATUS_CHANGED,
                    "WAITING_ON_USER → IN_PROGRESS (respuesta del usuario)", now);
        } else {
            ticket.touch(now);
        }
        return CommentResponse.from(comment);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> findEvents(Long ticketId, User currentUser) {
        Ticket ticket = loadVisibleTicket(ticketId, currentUser);
        return eventRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId())
                .stream().map(EventResponse::from).toList();
    }

    private Ticket loadTicket(Long id) {
        return ticketRepository.findWithPeopleById(id)
                .orElseThrow(() -> new NotFoundException("No existe el ticket #" + id));
    }

    /** Para un USER, un ticket ajeno "no existe": devolvemos 404 en lugar de 403 para no filtrar información. */
    private Ticket loadVisibleTicket(Long id, User currentUser) {
        Ticket ticket = loadTicket(id);
        if (!currentUser.getRole().isStaff() && !ticket.isRequestedBy(currentUser)) {
            throw new NotFoundException("No existe el ticket #" + id);
        }
        return ticket;
    }

    private void record(Ticket ticket, User actor, TicketEventType type, String detail, Instant now) {
        eventRepository.save(new TicketEvent(ticket, actor, type, detail, now));
    }

    /** Solo permite ordenar por campos conocidos; cualquier otro se ignora. */
    private static Pageable sanitize(Pageable pageable) {
        List<Sort.Order> orders = pageable.getSort().stream()
                .filter(order -> SORTABLE_FIELDS.contains(order.getProperty()))
                .toList();
        Sort sort = orders.isEmpty() ? DEFAULT_SORT : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
