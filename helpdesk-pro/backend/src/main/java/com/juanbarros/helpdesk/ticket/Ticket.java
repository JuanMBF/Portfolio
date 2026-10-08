package com.juanbarros.helpdesk.ticket;

import com.juanbarros.helpdesk.common.BusinessRuleException;
import com.juanbarros.helpdesk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Entidad principal. Las reglas de negocio (transiciones de estado, cálculo de SLA)
 * viven en la propia entidad para que sean fáciles de testear sin levantar Spring.
 */
@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_ticket_status", columnList = "status"),
        @Index(name = "idx_ticket_requester", columnList = "requester_id"),
        @Index(name = "idx_ticket_assignee", columnList = "assignee_id")
})
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant resolvedAt;

    @Column(nullable = false)
    private Instant slaDueAt;

    /** Bloqueo optimista: si dos técnicos editan el mismo ticket a la vez, el segundo recibe un 409. */
    @Version
    private Long version;

    protected Ticket() {
    }

    public Ticket(String title, String description, TicketCategory category, TicketPriority priority,
                  User requester, Instant createdAt) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.requester = requester;
        this.status = TicketStatus.OPEN;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.slaDueAt = createdAt.plus(priority.getSla());
    }

    public void changeStatus(TicketStatus newStatus, Instant now) {
        if (status == newStatus) {
            throw new BusinessRuleException("El ticket ya está en estado " + newStatus);
        }
        if (!status.canTransitionTo(newStatus)) {
            throw new BusinessRuleException("No se puede pasar de " + status + " a " + newStatus);
        }
        if (newStatus == TicketStatus.RESOLVED) {
            resolvedAt = now;
        } else if (newStatus.isActive()) {
            resolvedAt = null;
        }
        status = newStatus;
        updatedAt = now;
    }

    /** Al cambiar la prioridad, el vencimiento del SLA se recalcula desde la creación del ticket. */
    public void changePriority(TicketPriority newPriority, Instant now) {
        priority = newPriority;
        slaDueAt = createdAt.plus(newPriority.getSla());
        updatedAt = now;
    }

    public void assignTo(User agent, Instant now) {
        if (agent != null && !agent.getRole().isStaff()) {
            throw new BusinessRuleException("Solo se puede asignar a técnicos o administradores");
        }
        assignee = agent;
        updatedAt = now;
    }

    public void touch(Instant now) {
        updatedAt = now;
    }

    /**
     * Un ticket incumple el SLA si se resolvió después del vencimiento, o si sigue
     * activo y el vencimiento ya pasó. Un ticket cerrado sin resolverse (cancelado) no cuenta.
     */
    public boolean isSlaBreached(Instant now) {
        Instant end = resolvedAt != null ? resolvedAt : (status.isActive() ? now : null);
        return end != null && end.isAfter(slaDueAt);
    }

    public boolean isRequestedBy(User user) {
        return requester.getId().equals(user.getId());
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public User getRequester() {
        return requester;
    }

    public User getAssignee() {
        return assignee;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getSlaDueAt() {
        return slaDueAt;
    }
}
