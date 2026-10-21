// QA del sitio (../sitio) servido bajo /Portfolio/, como en GitHub Pages.
//
//   node qa.mjs [--capturas]
//
// En 375/768/1280 px y en modo claro y oscuro: sin errores de consola, sin scroll
// horizontal, imágenes cargadas, anclas válidas y auditoría de accesibilidad con axe.

import { createServer } from "node:http";
import { readFile, mkdir, stat } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const require = createRequire(import.meta.url);
const AQUI = path.dirname(fileURLToPath(import.meta.url));
const SITIO = path.resolve(AQUI, "../sitio");
const CAPTURAS = path.resolve(AQUI, "capturas");
const BASE = "/Portfolio/";
const ANCHOS = [375, 768, 1280];
const TEMAS = ["light", "dark"];
const conCapturas = process.argv.includes("--capturas");
const TIPOS = {
  ".html": "text/html; charset=utf-8", ".css": "text/css", ".js": "text/javascript", ".svg": "image/svg+xml",
  ".png": "image/png", ".webp": "image/webp", ".xml": "application/xml", ".txt": "text/plain",
};

const http = createServer(async (req, res) => {
  const url = decodeURIComponent(new URL(req.url, "http://x").pathname);
  if (!url.startsWith(BASE)) return res.writeHead(404).end();
  let archivo = path.join(SITIO, url.slice(BASE.length));
  try {
    if ((await stat(archivo)).isDirectory()) archivo = path.join(archivo, "index.html");
    res.writeHead(200, { "content-type": TIPOS[path.extname(archivo)] || "application/octet-stream" });
    res.end(await readFile(archivo));
  } catch {
    res.writeHead(404, { "content-type": TIPOS[".html"] }).end(await readFile(path.join(SITIO, "404.html")));
  }
});
await new Promise((ok) => http.listen(0, "127.0.0.1", ok));
const origen = `http://127.0.0.1:${http.address().port}`;
const navegador = await chromium.launch();
const axe = await readFile(require.resolve("axe-core/axe.min.js"), "utf8");
const fallas = [];
const fallar = (m) => (fallas.push(m), console.log("  ✗ " + m));

async function revisar(ruta, ancho, tema) {
  const nombre = `${ruta || "inicio"} ${ancho}px ${tema}`;
  const contexto = await navegador.newContext({
    viewport: { width: ancho, height: 900 }, colorScheme: tema, deviceScaleFactor: ancho <= 768 ? 2 : 1, reducedMotion: "reduce",
  });
  await contexto.route(/^https?:\/\/(?!127\.0\.0\.1)/, (r) => r.abort());
  const pagina = await contexto.newPage();
  const errores = [];
  pagina.on("console", (m) => m.type() === "error" && errores.push(m.text()));
  pagina.on("pageerror", (e) => errores.push(e.message));
  await pagina.goto(origen + BASE + ruta, { waitUntil: "networkidle" });
  // QA_FUENTE="DejaVu Sans" reproduce las fuentes más anchas de los runners de Ubuntu.
  if (process.env.QA_FUENTE) {
    await pagina.addStyleTag({ content: `*{font-family:"${process.env.QA_FUENTE}"!important}` });
  }
  // Las imágenes con loading="lazy" se cargan al recorrer la página.
  await pagina.evaluate(async () => {
    for (const img of document.images) img.loading = "eager";
    await Promise.all([...document.images].map((i) => i.decode().catch(() => null)));
  });

  const desborde = await pagina.evaluate(() => {
    const exceso = document.documentElement.scrollWidth - window.innerWidth;
    // El primer elemento que se pasa del borde sin un contenedor con scroll propio.
    const culpable = [...document.querySelectorAll("body *")].find((e) => {
      if (e.getBoundingClientRect().right <= window.innerWidth + 1) return false;
      for (let a = e.parentElement; a && a !== document.body; a = a.parentElement) {
        if (getComputedStyle(a).overflowX !== "visible") return false;
      }
      return true;
    });
    return { exceso, culpable: culpable ? `${culpable.tagName.toLowerCase()}.${culpable.className}` : "" };
  });
  if (desborde.exceso > 1) fallar(`${nombre}: scroll horizontal de ${desborde.exceso}px (${desborde.culpable})`);
  const rotas = await pagina.$$eval("img", (imgs) => imgs.filter((i) => !i.naturalWidth).map((i) => i.src));
  if (rotas.length) fallar(`${nombre}: imágenes que no cargan: ${rotas.join(", ")}`);
  const anclas = await pagina.$$eval('a[href^="#"]', (as) =>
    as.map((a) => a.getAttribute("href").slice(1)).filter((id) => id && !document.getElementById(id))
  );
  if (anclas.length) fallar(`${nombre}: anclas sin destino: ${anclas.join(", ")}`);

  await pagina.addScriptTag({ content: axe });
  const r = await pagina.evaluate(() => window.axe.run(document, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag21aa"] } }));
  for (const v of r.violations) fallar(`${nombre}: axe ${v.id} (${v.impact}): ${v.nodes[0].target.join(" ")}`);
  const relevantes = errores.filter((e) => !(ruta === "no-existe/" && e.includes("404")));
  if (relevantes.length) fallar(`${nombre}: errores de consola: ${relevantes.join(" | ")}`);

  if (conCapturas) {
    await mkdir(CAPTURAS, { recursive: true });
    await pagina.screenshot({ path: path.join(CAPTURAS, `${(ruta || "inicio").replace("/", "")}-${ancho}-${tema}.png`), fullPage: true });
  }
  await contexto.close();
}

try {
  for (const ruta of ["", "no-existe/"]) {
    for (const ancho of ANCHOS) for (const tema of TEMAS) await revisar(ruta, ancho, tema);
    console.log(`  ✓ ${BASE}${ruta}`);
  }
  const contexto = await navegador.newContext();
  const pagina = await contexto.newPage();
  for (const recurso of ["", "estilos.css", "tema.js", "img/favicon.svg", "img/og.png", "img/apple-touch-icon.png", "robots.txt", "sitemap.xml"]) {
    const r = await pagina.request.get(origen + BASE + recurso);
    if (r.status() !== 200) fallar(`recurso faltante: ${BASE}${recurso} (HTTP ${r.status()})`);
  }
  if ((await pagina.request.get(origen + BASE + "no-existe/")).status() !== 404) fallar("una ruta inexistente no devuelve 404");
  await contexto.close();
  console.log("  ✓ recursos y 404");
} finally {
  await navegador.close();
  http.close();
}

if (fallas.length) {
  console.log(`\n${fallas.length} problema(s).`);
  process.exit(1);
}
console.log("\nQA del sitio: todo en orden.");
