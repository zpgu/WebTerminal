'use strict';

const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const staticDir = path.join(root, 'src', 'main', 'resources', 'static');

const copies = [
  ['node_modules/@xterm/xterm/lib/xterm.js', 'js/xterm.js'],
  ['node_modules/@xterm/xterm/css/xterm.css', 'css/xterm.css'],
  ['node_modules/@xterm/addon-fit/lib/addon-fit.js', 'js/xterm-addon-fit.js']
];

for (const [source, destination] of copies) {
  const from = path.join(root, source);
  const to = path.join(staticDir, destination);

  if (!fs.existsSync(from)) {
    console.error(`Missing ${source}. Run "npm install" first.`);
    process.exit(1);
  }

  fs.mkdirSync(path.dirname(to), { recursive: true });
  fs.copyFileSync(from, to);
  console.log(`Copied ${destination}`);
}

const pkg = require(path.join(root, 'node_modules', '@xterm', 'xterm', 'package.json'));
const fitPkg = require(path.join(root, 'node_modules', '@xterm', 'addon-fit', 'package.json'));
console.log(`Vendored @xterm/xterm@${pkg.version}, @xterm/addon-fit@${fitPkg.version}`);
