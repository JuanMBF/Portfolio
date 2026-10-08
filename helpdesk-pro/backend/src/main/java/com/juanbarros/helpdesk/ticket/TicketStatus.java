package com.juanbarros.helpdesk.ticket;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida de un ticket. Las transiciones permitidas están definidas acá,
 * en un solo lugar, para que ninguna capa pueda saltearse el flujo:
 *
 * <ul>
 *   <li>OPEN → IN_PROGRESS, CLOSED (cancelado)</li>
 *   <li>IN_PROGRESS → OPEN, WAITING_ON_USER, RESOLVED</li>
 *   <li>WAITING_ON_USER → IN_PROGRESS, RESOLVED</li>
 *   <li>RESOLVED → IN_PROGRESS (reapertura), CLOSED</li>
 *   <li>CLOSED → (estado final)</li>
 * </ul>
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_ON_USER,
    RESOLVED,
    CLOSED;

    private static final Map<TicketStatus, EnumSet<TicketStatus>> TRANSITIONS = Map.of(
            OPEN, EnumSet.of(IN_PROGRESS, CLOSED),
            IN_PROGRESS, EnumSet.of(OPEN, WAITING_ON_USER, RESOLVED),
            WAITING_ON_USER, EnumSet.of(IN_PROGRESS, RESOLVED),
            RESOLVED, EnumSet.of(IN_PROGRESS, CLOSED),
            CLOSED, EnumSet.noneOf(TicketStatus.class));

    public static final Set<TicketStatus> ACTIVE =
            Collections.unmodifiableSet(EnumSet.of(OPEN, IN_PROGRESS, WAITING_ON_USER));

    public boolean canTransitionTo(TicketStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public Set<TicketStatus> allowedTransitions() {
        return TRANSITIONS.get(this).clone();
    }

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
