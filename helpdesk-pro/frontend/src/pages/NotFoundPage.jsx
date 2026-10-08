import { Link } from 'react-router-dom';
import { EmptyState } from '../components/Feedback.jsx';

export default function NotFoundPage() {
  return (
    <EmptyState title="Página no encontrada">
      <Link to="/">Volver al inicio</Link>
    </EmptyState>
  );
}
