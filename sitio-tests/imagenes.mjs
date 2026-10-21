// Genera ../sitio/img/og.png (1200×630) y apple-touch-icon.png (180×180).
//
//   node imagenes.mjs

import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const DESTINO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../sitio/img");

const og = `<!doctype html><html><head><meta charset="utf-8"><style>
  body{margin:0;width:1200px;height:630px;background:#f7f7f4;font-family:system-ui,"Segoe UI",Roboto,sans-serif;color:#121211}
  .marco{height:100%;padding:72px 80px;box-sizing:border-box;display:flex;flex-direction:column;justify-content:center}
  .logo{width:84px;height:84px;border-radius:20px;background:#1f63b8;color:#fff;display:grid;place-items:center;font-weight:750;font-size:34px;margin-bottom:36px}
  .ante{color:#1f63b8;font-weight:700;letter-spacing:.06em;text-transform:uppercase;font-size:22px;margin:0 0 14px}
  h1{font-size:76px;letter-spacing:-.03em;margin:0 0 18px;line-height:1}
  p{font-size:32px;line-height:1.35;margin:0;color:#4c4b47;max-width:960px}
  .chips{display:flex;gap:12px;margin-top:34px}
  .chips span{border:2px solid rgba(0,0,0,.12);border-radius:999px;padding:8px 20px;font-size:22px;font-weight:600;background:#fdfdfb}
</style></head><body><div class="marco">
  <div class="logo">JB</div>
  <p class="ante">Analista de Datos Trainee / Junior · Buenos Aires</p>
  <h1>Juan Barros</h1>
  <p>Convierto datos en respuestas que se pueden usar.</p>
  <div class="chips"><span>SQL</span><span>Python</span><span>Excel</span><span>QA de datos</span></div>
</div></body></html>`;

const icono = `<!doctype html><html><body style="margin:0"><div style="width:180px;height:180px;background:#1f63b8;color:#fff;display:grid;place-items:center;font:700 78px system-ui,'Segoe UI',Arial,sans-serif;letter-spacing:-2px">JB</div></body></html>`;

const navegador = await chromium.launch();
const pagina = await navegador.newPage({ viewport: { width: 1200, height: 630 } });
await pagina.setContent(og);
await pagina.screenshot({ path: path.join(DESTINO, "og.png") });
await pagina.setViewportSize({ width: 180, height: 180 });
await pagina.setContent(icono);
await pagina.screenshot({ path: path.join(DESTINO, "apple-touch-icon.png") });
await navegador.close();
console.log("Imágenes generadas en sitio/img/");
