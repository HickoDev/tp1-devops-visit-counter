// Captures de test, dans un navigateur isole limite a localhost.
// PLAYWRIGHT_MODULE peut designer le paquet Playwright de votre installation.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const ev = path.join(root, 'evidence');
const escape = text => text.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');

(async () => {
  const browser = await chromium.launch({headless: true});
  const context = await browser.newContext({viewport: {width: 1360, height: 900}, deviceScaleFactor: 1});
  await context.route('**/*', route => {
    const host = new URL(route.request().url()).hostname;
    return host === 'localhost' || host === '127.0.0.1' ? route.continue() : route.abort();
  });
  const page = await context.newPage();
  await page.goto('http://localhost/', {waitUntil: 'networkidle'});
  await page.screenshot({path: path.join(ev, 'app-first.png'), fullPage: true});
  await page.getByRole('link', {name: 'Nouvelle visite'}).click();
  await page.waitForLoadState('networkidle');
  await page.screenshot({path: path.join(ev, 'app-next.png'), fullPage: true});
  await page.setViewportSize({width: 1100, height: 120});
  for (const [file, title] of [
    ['02-images', 'Taille de l’image Docker'],
    ['23-network-summary', 'Réseau app-network : conteneurs connectés'],
    ['14-persistence', 'Suppression de Redis : vérification de la persistance'],
    ['19-compose-ps', 'Docker Compose : trois instances web'],
    ['21-scaling', 'Répartition des requêtes et compteur partagé'],
    ['24-tests', 'Tests unitaires : résultat du build réel'],
    ['25-ci', 'Pipeline GitHub Actions : état vérifié'],
    ['26-push', 'Publication de l’image Docker Hub'],
    ['27-cleanup', 'Nettoyage des ressources du TP']
  ]) {
    const input = path.join(ev, file + '.txt');
    if (!fs.existsSync(input)) continue;
    const output = fs.readFileSync(input, 'utf8');
    const html = `<!doctype html><html lang="fr"><meta charset="utf-8"><style>
      body{margin:0;background:#fff;color:#13283b;font:18px Arial;padding:34px}
      h1{font-size:25px;margin:0 0 12px}p{font-size:15px;color:#536579;margin:0 0 20px}
      pre{font:19px/1.6 Consolas,monospace;white-space:pre-wrap;overflow-wrap:anywhere;
      background:#f4f6f8;border:1px solid #bdc9d4;padding:24px;margin:0}
      </style><h1>${title}</h1><p>Sortie réelle enregistrée • TP1 GRP1 • 13 octobre 2026</p><pre>${escape(output)}</pre></html>`;
    // La capture restitue la sortie brute ; elle n'imite pas un terminal Windows.
    await page.setContent(html);
    await page.screenshot({path: path.join(ev, file + '.png'), fullPage: true});
  }
  await browser.close();
  console.log('Captures enregistrées dans evidence/');
})().catch(error => {console.error(error); process.exit(1);});
