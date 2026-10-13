/**
 * Renderiza un subconjunto de Markdown (títulos, listas, tablas, negrita y código)
 * construyendo elementos de React: nunca se inyecta HTML, así que el texto del
 * usuario no puede ejecutar scripts.
 */

const TABLE_SEPARATOR = /^\|?\s*:?-{3,}/;

export function parseBlocks(text) {
  const blocks = [];
  let current = null;

  const flush = () => {
    // Una fila separadora suelta ("|---|") deja una tabla sin filas: no hay nada para mostrar.
    const emptyTable = current?.type === 'table' && current.rows.length === 0;
    if (current && !emptyTable) blocks.push(current);
    current = null;
  };

  for (const rawLine of text.replace(/\r\n/g, '\n').split('\n')) {
    const line = rawLine.trimEnd();
    const heading = line.match(/^(#{1,6})\s+(.*)$/);
    const listItem = line.match(/^\s*[-*]\s+(.*)$/);

    if (!line.trim()) {
      flush();
    } else if (heading) {
      flush();
      blocks.push({ type: 'heading', level: heading[1].length, text: heading[2] });
    } else if (line.trimStart().startsWith('|')) {
      if (current?.type !== 'table') {
        flush();
        current = { type: 'table', rows: [] };
      }
      if (!TABLE_SEPARATOR.test(line.trim())) {
        current.rows.push(line.trim().replace(/^\||\|$/g, '').split('|').map((cell) => cell.trim()));
      }
    } else if (listItem) {
      if (current?.type !== 'list') {
        flush();
        current = { type: 'list', items: [] };
      }
      current.items.push(listItem[1]);
    } else {
      if (current?.type !== 'paragraph') {
        flush();
        current = { type: 'paragraph', lines: [] };
      }
      current.lines.push(line);
    }
  }
  flush();
  return blocks;
}

function Inline({ text }) {
  const parts = text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g).filter(Boolean);
  return parts.map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**')) return <strong key={i}>{part.slice(2, -2)}</strong>;
    if (part.startsWith('`') && part.endsWith('`')) return <code key={i}>{part.slice(1, -1)}</code>;
    return part;
  });
}

export default function Markdown({ text }) {
  return (
    <div className="markdown">
      {parseBlocks(text).map((block, i) => {
        switch (block.type) {
          case 'heading':
            return block.level <= 2 ? (
              <h3 key={i}><Inline text={block.text} /></h3>
            ) : (
              <h4 key={i}><Inline text={block.text} /></h4>
            );
          case 'list':
            return (
              <ul key={i}>
                {block.items.map((item, j) => (
                  <li key={j}><Inline text={item} /></li>
                ))}
              </ul>
            );
          case 'table': {
            const [header, ...rows] = block.rows;
            return (
              <div key={i} className="table-scroll">
                <table className="md-table">
                  <thead>
                    <tr>{header.map((cell, j) => <th key={j}><Inline text={cell} /></th>)}</tr>
                  </thead>
                  <tbody>
                    {rows.map((row, r) => (
                      <tr key={r}>{row.map((cell, j) => <td key={j}><Inline text={cell} /></td>)}</tr>
                    ))}
                  </tbody>
                </table>
              </div>
            );
          }
          default:
            return (
              <p key={i}>
                {block.lines.map((line, j) => (
                  <span key={j}>
                    {j > 0 && <br />}
                    <Inline text={line} />
                  </span>
                ))}
              </p>
            );
        }
      })}
    </div>
  );
}
