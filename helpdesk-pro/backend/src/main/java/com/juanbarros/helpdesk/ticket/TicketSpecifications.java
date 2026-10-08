package com.juanbarros.helpdesk.ticket;

import com.juanbarros.helpdesk.user.User;
import org.springframework.data.jpa.domain.Specification;

/** Filtros combinables para el listado de tickets (Specification de Spring Data JPA). */
final class TicketSpecifications {

    private TicketSpecifications() {
    }

    static Specification<Ticket> hasStatus(TicketStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    static Specification<Ticket> hasPriority(TicketPriority priority) {
        return (root, query, cb) -> priority == null ? null : cb.equal(root.get("priority"), priority);
    }

    static Specification<Ticket> hasCategory(TicketCategory category) {
        return (root, query, cb) -> category == null ? null : cb.equal(root.get("category"), category);
    }

    static Specification<Ticket> requestedBy(User user) {
        return (root, query, cb) -> cb.equal(root.get("requester").get("id"), user.getId());
    }

    static Specification<Ticket> assignedTo(Long assigneeId) {
        return (root, query, cb) -> assigneeId == null ? null : cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    static Specification<Ticket> unassigned(boolean onlyUnassigned) {
        return (root, query, cb) -> onlyUnassigned ? cb.isNull(root.get("assignee")) : null;
    }

    /** Busca en título y descripción; si el texto es un número (o "#123"), también busca por id. */
    static Specification<Ticket> matchesText(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return null;
            }
            String normalized = text.trim().toLowerCase();
            String pattern = "%" + normalized.replace("%", "\\%").replace("_", "\\_") + "%";
            var byText = cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.get("description")), pattern, '\\'));

            String digits = normalized.startsWith("#") ? normalized.substring(1) : normalized;
            if (digits.matches("\\d{1,18}")) {
                return cb.or(byText, cb.equal(root.get("id"), Long.parseLong(digits)));
            }
            return byText;
        };
    }
}
