import { initials } from '../utils/labels.js';

export default function Avatar({ user, size = 'md' }) {
  if (!user) return null;
  return (
    <span className={`avatar avatar-${size} avatar-${user.role}`} title={user.fullName} aria-hidden="true">
      {initials(user.fullName)}
    </span>
  );
}
