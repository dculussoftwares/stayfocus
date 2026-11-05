// Build-time helpers for the live app screens (src/components/screens): chart curves, the QR pattern, seeded noise.
// Everything is deterministic, so every build renders the same markup.

/** Small seeded PRNG (mulberry32). */
export function seeded(seed: number) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const f = (n: number) => Math.round(n * 10) / 10;

export interface Chart {
  line: string;
  area: string;
  points: [number, number][];
}

/**
 * A smooth line through hourly values (0..1), drawn the way the app's day chart is: Catmull-Rom curves in a
 * `width` x `height` box, with `pad` kept free at the top and bottom.
 */
export function dayChart(values: number[], width: number, height: number, pad = 6): Chart {
  const step = width / (values.length - 1);
  const points = values.map((v, i) => [i * step, height - pad - v * (height - pad * 2)] as [number, number]);
  let line = `M${f(points[0]![0])},${f(points[0]![1])}`;
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[i - 1] ?? points[i]!;
    const p1 = points[i]!;
    const p2 = points[i + 1]!;
    const p3 = points[i + 2] ?? p2;
    const c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
    const c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
    line += ` C${f(c1[0]!)},${f(c1[1]!)} ${f(c2[0]!)},${f(c2[1]!)} ${f(p2[0])},${f(p2[1])}`;
  }
  const area = `${line} L${width},${height} L0,${height} Z`;
  return { line, area, points };
}

/**
 * A QR-looking pattern (finder squares, timing lines, an alignment square and seeded modules) as one SVG path in a
 * `size` x `size` grid. It is a picture of a code, not a scannable one.
 */
export function qrPath(seed = 11, size = 25): string {
  const rnd = seeded(seed);
  const on: boolean[][] = Array.from({ length: size }, () => Array<boolean>(size).fill(false));
  const fixed: boolean[][] = Array.from({ length: size }, () => Array<boolean>(size).fill(false));

  const square = (x0: number, y0: number, n: number, ring: (dx: number, dy: number) => boolean) => {
    for (let dy = -1; dy <= n; dy++) {
      for (let dx = -1; dx <= n; dx++) {
        const x = x0 + dx;
        const y = y0 + dy;
        if (x < 0 || y < 0 || x >= size || y >= size) continue;
        fixed[y]![x] = true;
        on[y]![x] = dx >= 0 && dy >= 0 && dx < n && dy < n && ring(dx, dy);
      }
    }
  };
  const finder = (dx: number, dy: number) =>
    dx === 0 || dy === 0 || dx === 6 || dy === 6 || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4);
  square(0, 0, 7, finder);
  square(size - 7, 0, 7, finder);
  square(0, size - 7, 7, finder);
  square(size - 9, size - 9, 5, (dx, dy) => dx === 0 || dy === 0 || dx === 4 || dy === 4 || (dx === 2 && dy === 2));

  for (let i = 8; i < size - 8; i++) {
    fixed[6]![i] = true;
    fixed[i]![6] = true;
    on[6]![i] = i % 2 === 0;
    on[i]![6] = i % 2 === 0;
  }
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      if (!fixed[y]![x]) on[y]![x] = rnd() > 0.52;
    }
  }

  let d = "";
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      if (on[y]![x]) d += `M${x} ${y}h1v1h-1z`;
    }
  }
  return d;
}

let ids = 0;
/** A unique id for SVG gradients, since a screen can appear more than once on a page. */
export const uid = (prefix: string) => `${prefix}-${++ids}`;
