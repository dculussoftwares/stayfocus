// Runs the live app screens (components/Screen.astro). A screen "plays" once when it first scrolls into view: CSS
// entrances key off the `play` class, and this file drives the parts CSS can't (counting numbers, typing, clocks).
// Screens marked data-manual are played by their page instead (the pinned Kids story replays them on each step).
// Without script, or with reduced motion, every screen shows its final state.

type Stop = () => void;

const reduced = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const running = new WeakMap<HTMLElement, Stop[]>();
const pad = (n: number) => String(n).padStart(2, "0");
const easeOut = (t: number) => 1 - Math.pow(1 - t, 3);

const format = (value: number, kind: string | undefined) =>
  kind === "clock" ? `${Math.floor(value / 60)}:${pad(value % 60)}` : String(value);

function later(stops: Stop[], ms: number, run: () => void) {
  const id = window.setTimeout(run, ms);
  stops.push(() => window.clearTimeout(id));
}

/** `data-count="153" data-format="clock"`: counts up from `data-from` (default 0) to the value. */
function countUp(el: HTMLElement, stops: Stop[]) {
  const to = Number(el.dataset.count);
  const from = Number(el.dataset.from ?? 0);
  const kind = el.dataset.format;
  if (reduced()) {
    el.textContent = format(to, kind);
    return;
  }
  const delay = Number(el.dataset.delay ?? 200);
  const duration = Number(el.dataset.dur ?? 1400);
  el.textContent = format(from, kind);
  let frame = 0;
  later(stops, delay, () => {
    const start = performance.now();
    const step = (now: number) => {
      const t = Math.min(1, Math.max(0, (now - start) / duration)); // a frame can be stamped before `start`
      el.textContent = format(Math.round(from + (to - from) * easeOut(t)), kind);
      if (t < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
  });
  stops.push(() => cancelAnimationFrame(frame));
}

/** `data-type`: the text is typed out once, as if someone is filling in the field. */
function typeOnce(el: HTMLElement, stops: Stop[]) {
  const text = el.dataset.text ?? el.textContent ?? "";
  el.dataset.text = text;
  if (reduced()) {
    el.textContent = text;
    return;
  }
  el.textContent = "";
  let i = 0;
  const next = () => {
    el.textContent = text.slice(0, ++i);
    if (i < text.length) later(stops, 45 + Math.random() * 70, next);
  };
  later(stops, Number(el.dataset.delay ?? 600), next);
}

/**
 * `data-type-loop='["…","…"]'`: a placeholder that takes turns with typed example sentences, like the home screen's
 * "Describe a block in your words" box. Expects `.ph` (placeholder) and `.tx` (typed text) children.
 */
function typeLoop(el: HTMLElement, stops: Stop[]) {
  if (reduced()) return;
  const lines = JSON.parse(el.dataset.typeLoop ?? "[]") as string[];
  const tx = el.querySelector<HTMLElement>(".tx");
  if (!tx || !lines.length) return;
  let line = 0;
  const cycle = () => {
    const text = lines[line++ % lines.length]!;
    let i = 0;
    el.classList.add("typing");
    tx.textContent = "";
    const type = () => {
      tx.textContent = text.slice(0, ++i);
      if (i < text.length) later(stops, 40 + Math.random() * 60, type);
      else
        later(stops, 2200, () => {
          el.classList.remove("typing");
          tx.textContent = "";
          later(stops, 2400, cycle);
        });
    };
    later(stops, 300, type);
  };
  later(stops, Number(el.dataset.delay ?? 2600), cycle);
}

/** `data-countdown="292"`: a m:ss clock that counts down every second and starts again at 5:00. */
function countdown(el: HTMLElement, stops: Stop[]) {
  let left = Number(el.dataset.countdown);
  el.textContent = `${Math.floor(left / 60)}:${pad(left % 60)}`;
  const id = window.setInterval(() => {
    left = left <= 0 ? 300 : left - 1;
    el.textContent = `${Math.floor(left / 60)}:${pad(left % 60)}`;
  }, 1000);
  stops.push(() => window.clearInterval(id));
}

export function stop(screen: HTMLElement) {
  running.get(screen)?.forEach((s) => s());
  running.delete(screen);
}

/** Plays a screen from the start (again, if it already played). */
export function play(screen: HTMLElement) {
  stop(screen);
  const stops: Stop[] = [];
  screen.classList.remove("play");
  void screen.offsetWidth; // restart the CSS animations
  screen.classList.add("play");
  screen.querySelectorAll<HTMLElement>("[data-count]").forEach((el) => countUp(el, stops));
  screen.querySelectorAll<HTMLElement>("[data-type]").forEach((el) => typeOnce(el, stops));
  screen.querySelectorAll<HTMLElement>("[data-type-loop]").forEach((el) => typeLoop(el, stops));
  screen.querySelectorAll<HTMLElement>("[data-countdown]").forEach((el) => countdown(el, stops));
  running.set(screen, stops);
}

/** Dates and the status bar clock show the visitor's real day and time. */
function stampNow() {
  const now = new Date();
  document.querySelectorAll<HTMLElement>("[data-now]").forEach((el) => {
    el.textContent = `${now.getHours()}:${pad(now.getMinutes())}`;
  });
  document.querySelectorAll<HTMLElement>("[data-today]").forEach((el) => {
    el.textContent =
      el.dataset.today === "short"
        ? `${pad(now.getDate())} ${now.toLocaleDateString("en-GB", { month: "short" }).toUpperCase()}`
        : `${now.toLocaleDateString("en-GB", { weekday: "long" })}, ${now.getDate()} ${now.toLocaleDateString("en-GB", { month: "long" })}`;
  });
}

function init() {
  stampNow();
  window.setInterval(stampNow, 20_000);

  const screens = [...document.querySelectorAll<HTMLElement>("[data-live]")];
  if (!("IntersectionObserver" in window)) {
    screens.forEach((s) => s.classList.add("play"));
    return;
  }
  // A screen plays once a quarter of it is in view. Loops pause while it is off screen.
  const visible = new IntersectionObserver(
    (entries) => {
      for (const { target, isIntersecting, intersectionRatio } of entries) {
        const screen = target as HTMLElement;
        screen.classList.toggle("paused", !isIntersecting);
        if (intersectionRatio >= 0.25 && !screen.classList.contains("play") && !screen.hasAttribute("data-manual")) {
          play(screen);
        }
      }
    },
    { threshold: [0, 0.25] },
  );
  screens.forEach((s) => visible.observe(s));
}

init();
