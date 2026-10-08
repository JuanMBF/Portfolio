import { render, screen } from '@testing-library/react';
import Markdown, { parseBlocks } from './Markdown.jsx';

const REPORT = `Desde la mañana no navega.

## Diagnóstico de pc-aula-204
### Hallazgos
- ⚠ **Sin DNS**: probar con 8.8.8.8
- ✔ Disco OK

| Prueba | Resultado |
|---|---|
| DNS | ✘ no resuelve |
| HTTPS | ✔ ok |`;

describe('Markdown', () => {
  it('separa el texto en bloques', () => {
    expect(parseBlocks(REPORT).map((b) => b.type)).toEqual(['paragraph', 'heading', 'heading', 'list', 'table']);
  });

  it('ignora la fila separadora de las tablas', () => {
    const table = parseBlocks(REPORT).find((b) => b.type === 'table');
    expect(table.rows).toEqual([
      ['Prueba', 'Resultado'],
      ['DNS', '✘ no resuelve'],
      ['HTTPS', '✔ ok'],
    ]);
  });

  it('renderiza títulos, listas, negritas y tablas', () => {
    render(<Markdown text={REPORT} />);

    expect(screen.getByRole('heading', { name: 'Diagnóstico de pc-aula-204' })).toBeInTheDocument();
    expect(screen.getByText('Sin DNS').tagName).toBe('STRONG');
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
    expect(screen.getByRole('columnheader', { name: 'Prueba' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '✘ no resuelve' })).toBeInTheDocument();
  });

  it('no interpreta HTML: el texto del usuario se muestra tal cual', () => {
    const { container } = render(<Markdown text={'<img src=x onerror="alert(1)">'} />);

    expect(container.querySelector('img')).toBeNull();
    expect(container).toHaveTextContent('<img src=x onerror="alert(1)">');
  });
});
