package com.juanbarros.helpdesk.config;

import com.juanbarros.helpdesk.ticket.Ticket;
import com.juanbarros.helpdesk.ticket.TicketCategory;
import com.juanbarros.helpdesk.ticket.TicketComment;
import com.juanbarros.helpdesk.ticket.TicketCommentRepository;
import com.juanbarros.helpdesk.ticket.TicketEvent;
import com.juanbarros.helpdesk.ticket.TicketEventRepository;
import com.juanbarros.helpdesk.ticket.TicketEventType;
import com.juanbarros.helpdesk.ticket.TicketPriority;
import com.juanbarros.helpdesk.ticket.TicketRepository;
import com.juanbarros.helpdesk.ticket.TicketStatus;
import com.juanbarros.helpdesk.user.Role;
import com.juanbarros.helpdesk.user.User;
import com.juanbarros.helpdesk.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Carga usuarios y tickets de ejemplo la primera vez que arranca la aplicación,
 * para que la demo tenga datos realistas (con SLA cumplidos, vencidos y en curso).
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final TicketCommentRepository commentRepository;
    private final TicketEventRepository eventRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DataSeeder(UserRepository userRepository, TicketRepository ticketRepository,
                      TicketCommentRepository commentRepository, TicketEventRepository eventRepository,
                      PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.eventRepository = eventRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        User admin = user("Carla Méndez", "admin@helpdesk.dev", "Admin123!", Role.ADMIN);
        User lucia = user("Lucía Fernández", "lucia@helpdesk.dev", "Agent123!", Role.AGENT);
        User martin = user("Martín Gómez", "martin@helpdesk.dev", "Agent123!", Role.AGENT);
        User sofia = user("Sofía Ramírez", "sofia@helpdesk.dev", "User123!", Role.USER);
        User diego = user("Diego Pérez", "diego@helpdesk.dev", "User123!", Role.USER);
        User tomas = user("Tomás Álvarez", "tomas@helpdesk.dev", "User123!", Role.USER);

        // Ordenados del más antiguo al más reciente para que los ids sigan la cronología.
        ticket("Pedido de mouse y teclado para recepción",
                "Necesitamos un mouse y un teclado nuevos para la PC de recepción.",
                TicketCategory.HARDWARE, TicketPriority.LOW, tomas, 150)
                .after(2).comment(lucia, "Los pedidos de insumos se gestionan por Compras. Cierro el ticket y "
                        + "te paso el formulario por mail.")
                .status(lucia, TicketStatus.CLOSED);

        ticket("Licencia de Office vencida en PC de administración",
                "Word muestra el cartel de 'producto sin licencia' y no deja editar documentos.",
                TicketCategory.SOFTWARE, TicketPriority.MEDIUM, tomas, 130)
                .after(3).status(lucia, TicketStatus.IN_PROGRESS)
                .after(17).comment(lucia, "Se reasignó una licencia del pool institucional.")
                .status(lucia, TicketStatus.RESOLVED)
                .after(20).status(lucia, TicketStatus.CLOSED);

        ticket("El proyector del aula magna no detecta HDMI",
                "Conecto la notebook y el proyector dice 'sin señal'. El acto empieza en una hora.",
                TicketCategory.HARDWARE, TicketPriority.CRITICAL, sofia, 100)
                .after(0.5).status(martin, TicketStatus.IN_PROGRESS)
                .after(2).comment(martin, "El cable HDMI estaba dañado. Se reemplazó y se probó con dos notebooks.")
                .status(martin, TicketStatus.RESOLVED)
                .after(21).status(martin, TicketStatus.CLOSED);

        ticket("Solicitud de cuenta institucional para docente nuevo",
                "Se incorpora un docente a la cátedra de Bases de Datos y necesita cuenta de correo y campus.",
                TicketCategory.ACCOUNT_ACCESS, TicketPriority.LOW, sofia, 70)
                .after(4).status(lucia, TicketStatus.IN_PROGRESS)
                .after(6).comment(lucia, "Cuenta creada. Le enviamos las credenciales al correo personal.")
                .status(lucia, TicketStatus.RESOLVED)
                .after(20).status(lucia, TicketStatus.CLOSED);

        ticket("Lentitud general en las PCs del laboratorio 1",
                "Las computadoras tardan varios minutos en iniciar sesión y abrir el navegador.",
                TicketCategory.HARDWARE, TicketPriority.MEDIUM, diego, 60)
                .after(2).status(martin, TicketStatus.IN_PROGRESS)
                .after(28).comment(martin, "Se amplió la RAM a 16 GB y se deshabilitaron programas de inicio.")
                .status(martin, TicketStatus.RESOLVED)
                .after(20).status(martin, TicketStatus.CLOSED);

        ticket("Sin conexión WiFi en la biblioteca",
                "En el primer piso de la biblioteca la red aparece pero no navega.",
                TicketCategory.NETWORK, TicketPriority.HIGH, diego, 50)
                .after(1).status(martin, TicketStatus.IN_PROGRESS)
                .after(2).comment(martin, "¿El problema es con la red de alumnos o con la de invitados?")
                .status(martin, TicketStatus.WAITING_ON_USER)
                .after(2).userReply(diego, "Con la de alumnos. La de invitados anda bien.")
                .after(7).note(martin, "El access point del primer piso tenía el DHCP colgado.")
                .comment(martin, "Se reinició el access point del primer piso. Ya deberías poder navegar.")
                .status(martin, TicketStatus.RESOLVED);

        ticket("Outlook no sincroniza el correo en el celular",
                "Desde ayer no me llegan los mails al celular. En la computadora sí aparecen.",
                TicketCategory.EMAIL, TicketPriority.MEDIUM, tomas, 40);

        ticket("La impresora de la sala de profesores imprime hojas en blanco",
                "La impresora saca las hojas sin nada impreso. Ya la apagué y prendí.",
                TicketCategory.HARDWARE, TicketPriority.MEDIUM, tomas, 30)
                .after(2).assign(lucia, martin).status(martin, TicketStatus.IN_PROGRESS)
                .after(4).comment(martin, "Se reemplazó el tóner y se limpió el cabezal. Quedó funcionando.")
                .status(martin, TicketStatus.RESOLVED)
                .after(14).status(martin, TicketStatus.CLOSED);

        ticket("Instalar Python y VS Code en el laboratorio 3",
                "Para la materia de Programación I necesitamos Python 3.12 y VS Code en las 25 PCs del laboratorio.",
                TicketCategory.SOFTWARE, TicketPriority.LOW, sofia, 26)
                .after(5).status(martin, TicketStatus.IN_PROGRESS)
                .note(martin, "Armo un script de instalación desatendida para no hacerlo PC por PC.");

        ticket("Error al subir entregas en el campus (archivos de más de 10 MB)",
                "Cuando intento subir el trabajo práctico me sale 'error inesperado'. El archivo pesa 14 MB.",
                TicketCategory.VIRTUAL_CLASSROOM, TicketPriority.HIGH, diego, 20)
                .after(1).status(lucia, TicketStatus.IN_PROGRESS)
                .after(1).comment(lucia, "¿Qué navegador usás y en qué formato está el archivo?")
                .status(lucia, TicketStatus.WAITING_ON_USER);

        ticket("Alta en la plataforma de videoconferencias para un taller",
                "Necesito una sala con capacidad para 150 personas para el taller del jueves.",
                TicketCategory.VIDEOCONFERENCE, TicketPriority.LOW, sofia, 12)
                .after(1).assign(admin, lucia);

        ticket("VPN no conecta desde casa",
                "El cliente de VPN se queda en 'conectando...' y después da timeout. Desde la oficina anda bien.",
                TicketCategory.NETWORK, TicketPriority.MEDIUM, tomas, 8);

        ticket("Micrófono no funciona en Google Meet",
                "En Meet no me toma el micrófono, pero en otras aplicaciones sí funciona.",
                TicketCategory.VIDEOCONFERENCE, TicketPriority.MEDIUM, diego, 5)
                .after(0.3).status(lucia, TicketStatus.IN_PROGRESS)
                .after(0.7).comment(lucia, "Era el permiso del navegador bloqueado para el sitio. "
                        + "Lo habilitamos desde la configuración de Chrome y ya funciona.")
                .status(lucia, TicketStatus.RESOLVED);

        ticket("Zoom se cierra al compartir pantalla en el aula 204",
                "Cada vez que intento compartir pantalla durante la clase, Zoom se cierra solo. "
                        + "Tengo clase de nuevo en dos horas.",
                TicketCategory.VIDEOCONFERENCE, TicketPriority.CRITICAL, sofia, 3)
                .after(0.5).status(lucia, TicketStatus.IN_PROGRESS)
                .comment(lucia, "Hola Sofía, ya estamos revisando la PC del aula. Te aviso en breve.")
                .after(0.2).note(lucia, "Reproducido en la PC del aula. Parece conflicto con el driver de video; "
                        + "pruebo actualizarlo antes de la próxima clase.");

        ticket("No puedo ingresar al campus virtual",
                "Desde esta mañana me aparece 'usuario o contraseña incorrectos'. Ya probé restablecer la "
                        + "contraseña pero no me llega el mail.",
                TicketCategory.ACCOUNT_ACCESS, TicketPriority.HIGH, diego, 2);

        ticket("No llegan los mails de recuperación de contraseña",
                "Varios alumnos me avisan que piden recuperar la contraseña del campus y el mail nunca llega, "
                        + "ni siquiera en spam.",
                TicketCategory.EMAIL, TicketPriority.HIGH, sofia, 1);

        log.info("Datos de demo cargados: {} usuarios y {} tickets", userRepository.count(), ticketRepository.count());
    }

    private User user(String name, String email, String password, Role role) {
        return userRepository.save(new User(name, email, passwordEncoder.encode(password), role));
    }

    private Script ticket(String title, String description, TicketCategory category, TicketPriority priority,
                          User requester, double hoursAgo) {
        Instant createdAt = clock.instant().minus(hours(hoursAgo));
        Ticket ticket = ticketRepository.save(new Ticket(title, description, category, priority, requester, createdAt));
        eventRepository.save(new TicketEvent(ticket, requester, TicketEventType.CREATED,
                "Ticket creado con prioridad " + priority, createdAt));
        return new Script(ticket, createdAt);
    }

    private static Duration hours(double hours) {
        return Duration.ofMinutes(Math.round(hours * 60));
    }

    /** Pequeño DSL para describir la historia de cada ticket de ejemplo paso a paso. */
    private final class Script {

        private final Ticket ticket;
        private Instant cursor;

        private Script(Ticket ticket, Instant start) {
            this.ticket = ticket;
            this.cursor = start;
        }

        Script after(double hoursLater) {
            cursor = cursor.plus(hours(hoursLater));
            return this;
        }

        Script assign(User actor, User agent) {
            ticket.assignTo(agent, cursor);
            event(actor, TicketEventType.ASSIGNED, "Asignado a " + agent.getFullName());
            return this;
        }

        Script status(User agent, TicketStatus status) {
            TicketStatus previous = ticket.getStatus();
            ticket.changeStatus(status, cursor);
            event(agent, TicketEventType.STATUS_CHANGED, previous + " → " + status);
            if (status == TicketStatus.IN_PROGRESS && ticket.getAssignee() == null) {
                ticket.assignTo(agent, cursor);
                event(agent, TicketEventType.ASSIGNED, "Asignado automáticamente a " + agent.getFullName());
            }
            return this;
        }

        Script comment(User author, String body) {
            commentRepository.save(new TicketComment(ticket, author, body, false, cursor));
            return this;
        }

        Script note(User author, String body) {
            commentRepository.save(new TicketComment(ticket, author, body, true, cursor));
            return this;
        }

        Script userReply(User author, String body) {
            comment(author, body);
            ticket.changeStatus(TicketStatus.IN_PROGRESS, cursor);
            event(author, TicketEventType.STATUS_CHANGED, "WAITING_ON_USER → IN_PROGRESS (respuesta del usuario)");
            return this;
        }

        private void event(User actor, TicketEventType type, String detail) {
            eventRepository.save(new TicketEvent(ticket, actor, type, detail, cursor));
        }
    }
}
