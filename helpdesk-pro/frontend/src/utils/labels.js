export const STATUS_LABELS = {
  OPEN: 'Abierto',
  IN_PROGRESS: 'En progreso',
  WAITING_ON_USER: 'Esperando usuario',
  RESOLVED: 'Resuelto',
  CLOSED: 'Cerrado',
};

/** Texto del botón para pasar a cada estado (depende del estado actual en el caso de la reapertura). */
export function transitionLabel(from, to) {
  if (to === 'IN_PROGRESS') {
    if (from === 'OPEN') return 'Iniciar atención';
    if (from === 'RESOLVED') return 'Reabrir';
    return 'Retomar';
  }
  return {
    OPEN: 'Devolver a la cola',
    WAITING_ON_USER: 'Esperar respuesta del usuario',
    RESOLVED: 'Marcar como resuelto',
    CLOSED: from === 'OPEN' ? 'Cancelar ticket' : 'Cerrar ticket',
  }[to];
}

export const PRIORITY_LABELS = {
  LOW: 'Baja',
  MEDIUM: 'Media',
  HIGH: 'Alta',
  CRITICAL: 'Crítica',
};

export const PRIORITY_SLA_HOURS = { LOW: 72, MEDIUM: 24, HIGH: 8, CRITICAL: 4 };

export const CATEGORY_LABELS = {
  HARDWARE: 'Hardware',
  SOFTWARE: 'Software',
  NETWORK: 'Redes y conectividad',
  ACCOUNT_ACCESS: 'Cuentas y accesos',
  EMAIL: 'Correo',
  VIRTUAL_CLASSROOM: 'Campus virtual',
  VIDEOCONFERENCE: 'Videoconferencia',
  OTHER: 'Otro',
};

export const ROLE_LABELS = {
  USER: 'Usuario',
  AGENT: 'Técnico',
  ADMIN: 'Administrador',
};

export const EVENT_LABELS = {
  CREATED: 'Creación',
  STATUS_CHANGED: 'Cambio de estado',
  PRIORITY_CHANGED: 'Cambio de prioridad',
  ASSIGNED: 'Asignación',
};

/** Reemplaza los códigos de enum (OPEN, HIGH...) por su etiqueta en español dentro de un texto. */
export function humanizeCodes(text) {
  const all = { ...STATUS_LABELS, ...PRIORITY_LABELS };
  return text.replace(/\b[A-Z_]{3,}\b/g, (code) => all[code] ?? code);
}

export function ticketCode(id) {
  return `#${String(id).padStart(4, '0')}`;
}

export function initials(name = '') {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');
}
