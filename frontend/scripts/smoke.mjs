/**
 * Bundle scripts/smoke-render.jsx rồi chạy bằng Node. Cần bước bundle vì file dùng JSX và import
 * theo đường dẫn tương đối — Node không tự hiểu cả hai.
 */
import { build } from 'esbuild';
import { rmSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

// Dựng đường dẫn tuyệt đối rồi mới đổi sang file://. Ghép chuỗi kiểu URL trên Windows sinh ra
// những thứ như /C:/C:/... và Node không tìm thấy file.
const outfile = resolve(process.cwd(), 'node_modules/.cache/smoke-render.cjs');

await build({
  entryPoints: [resolve(process.cwd(), 'scripts/smoke-render.jsx')],
  bundle: true,
  platform: 'node',
  format: 'cjs',
  jsx: 'automatic',
  outfile,
  logLevel: 'error',
  // Render một trang thì kéo theo cả api/client.js, mà file đó đọc `import.meta.env` của Vite —
  // thứ không tồn tại khi bundle sang CJS cho Node. Không có dòng này, thêm một `check(...)` cho
  // component nằm trong pages/ là làm cả bước smoke vỡ ngay lúc nạp module, trước khi render.
  define: { 'import.meta.env': JSON.stringify({}) },
});

// react-dom/server cảnh báo useLayoutEffect trên mỗi lần render; ở đây là nhiễu thuần túy.
const originalError = console.error;
console.error = (...args) => {
  if (typeof args[0] === 'string' && args[0].includes('useLayoutEffect does nothing on the server')) return;
  originalError(...args);
};

try {
  await import(pathToFileURL(outfile).href);
} finally {
  rmSync(outfile, { force: true });
}
