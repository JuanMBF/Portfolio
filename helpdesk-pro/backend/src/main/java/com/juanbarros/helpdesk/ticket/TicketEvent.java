package com.juanbarros.helpdesk.ticket;

import com.juanbarros.helpdesk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** Historial de auditoría: quién cambió qué y cuándo en cada ticket. */
@Entity
@Table(name = "ticket_events")
public class TicketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketEventType type;

    @Column(nullable = false, length = 500)
    private String detail;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected TicketEvent() {
    }

    public TicketEvent(Ticket ticket, User actor, TicketEventType type, String detail, Instant createdAt) {
        this.ticket = ticket;
        this.actor = actor;
        this.type = type;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getActor() {
        return actor;
    }

    public TicketEventType getType() {
        return type;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
