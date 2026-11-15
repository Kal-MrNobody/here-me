// Procedural 3D hand model.
// One definition of a sign is used for two things:
//   1. drawing the hand illustration that teaches the sign (SVG), and
//   2. producing "ideal" landmarks that the recognizer compares the webcam against.
//
// Coordinate system ("mirror view", what you see in a mirror / the mirrored webcam):
//   x → right, y → up, z → towards the viewer. Units: 1 = palm length (wrist → middle knuckle).
// The model is a RIGHT hand with the palm facing the viewer and fingers up, so the thumb is on the left.
// Left hands are produced by mirroring x.

const D2R = Math.PI / 180;

// Finger shape codes → [MCP, PIP, DIP] flexion in degrees.
export const FCODE = {
  X: [0, 0, 0],        // straight / extended
  C: [90, 100, 65],    // curled into a fist
  B: [85, 5, 5],       // bent 90° at the knuckle, finger straight
  H: [5, 95, 70],      // hooked (like the letter X)
  c: [40, 40, 30],     // curved, as in a C
  o: [55, 60, 45],     // rounded, as in an O (tip meets the thumb)
  e: [60, 105, 65],    // tightly bent, tips down (as in E)
  h: [30, 30, 20],     // slightly relaxed
};

const MCP = [[-0.30, 0.90, 0], [-0.08, 0.97, 0], [0.13, 0.92, 0], [0.31, 0.80, 0]];
const LEN = [[0.42, 0.25, 0.20], [0.47, 0.29, 0.21], [0.44, 0.27, 0.20], [0.33, 0.20, 0.18]];
const WIDTH = [[0.17, 0.155, 0.14], [0.175, 0.16, 0.145], [0.165, 0.15, 0.135], [0.145, 0.135, 0.12]];
const THUMB_W = [0.2, 0.18, 0.16];

// Sideways angle of each finger in degrees (positive = towards the thumb side).
export const SPREAD = {
  tog: [3, 0, -3, -8],
  open: [16, 3, -12, -26],
  v: [13, -11, -4, -9],
  w: [16, 0, -16, -9],
  cross: [-9, 11, -4, -9],
  y: [3, 0, -3, -28],
};

const CMC = [-0.22, 0.18, 0.05];
// Thumb presets: [MCP, IP, TIP] in local coordinates.
const THUMB = {
  out: [[-0.42, 0.36, 0.10], [-0.62, 0.52, 0.12], [-0.80, 0.64, 0.12]],
  five: [[-0.42, 0.34, 0.06], [-0.62, 0.47, 0.08], [-0.78, 0.58, 0.08]],
  side: [[-0.40, 0.40, 0.14], [-0.45, 0.62, 0.22], [-0.45, 0.82, 0.26]],
  up: [[-0.40, 0.40, 0.14], [-0.44, 0.70, 0.18], [-0.44, 1.00, 0.18]],
  par: [[-0.40, 0.42, 0.12], [-0.48, 0.65, 0.14], [-0.52, 0.88, 0.12]],
  across: [[-0.38, 0.38, 0.22], [-0.30, 0.60, 0.48], [-0.10, 0.66, 0.52]],
  tuck: [[-0.32, 0.34, 0.12], [-0.15, 0.46, 0.18], [0.03, 0.52, 0.17]],
  under: [[-0.36, 0.38, 0.15], [-0.25, 0.55, 0.25], [-0.08, 0.62, 0.30]],
  t: [[-0.38, 0.40, 0.20], [-0.36, 0.66, 0.42], [-0.19, 0.82, 0.50]],
  n: [[-0.38, 0.40, 0.20], [-0.25, 0.68, 0.50], [0.02, 0.80, 0.50]],
  m: [[-0.38, 0.40, 0.20], [-0.15, 0.68, 0.52], [0.22, 0.74, 0.48]],
  curve: [[-0.42, 0.36, 0.25], [-0.50, 0.55, 0.45], [-0.45, 0.72, 0.60]],
  k: [[-0.40, 0.40, 0.15], [-0.33, 0.72, 0.25], [-0.18, 1.02, 0.18]],
};

// Extra outline points (indices 21+) for the palm and forearm.
const PALM_OUTLINE = [[-0.20, -0.02, 0], [-0.25, 0.18, 0.03], [-0.36, 0.50, 0.03], [-0.36, 0.86, 0], [-0.30, 0.97, 0],
  [-0.08, 1.03, 0], [0.14, 0.98, 0], [0.34, 0.86, 0], [0.37, 0.70, 0], [0.30, 0.25, 0], [0.22, -0.02, 0]];
const ARM = [[-0.21, 0.02, -0.02], [0.23, 0.02, -0.02], [0.27, -0.95, -0.02], [-0.25, -0.95, -0.02]];
const PALM_START = 21, ARM_START = PALM_START + PALM_OUTLINE.length;

const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const mul = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
const lerp = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
const cross3 = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const dot3 = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];

function rotAxis(v, k, ang) { // Rodrigues rotation of v about unit axis k
  const c = Math.cos(ang), s = Math.sin(ang);
  const kxv = cross3(k, v), kdv = dot3(k, v);
  return [v[0] * c + kxv[0] * s + k[0] * kdv * (1 - c),
    v[1] * c + kxv[1] * s + k[1] * kdv * (1 - c),
    v[2] * c + kxv[2] * s + k[2] * kdv * (1 - c)];
}

function fingerChain(i, code, splayDeg, zoff = 0) {
  const ang = Array.isArray(code) ? code : (FCODE[code] || FCODE.X);
  const s = splayDeg * D2R;
  const d0 = [-Math.sin(s), Math.cos(s), 0];
  const ax = [Math.cos(s), Math.sin(s), 0];
  let p = MCP[i].slice();
  const pts = [p];
  let cum = 0;
  for (let k = 0; k < 3; k++) {
    cum += ang[k] * D2R;
    const dir = rotAxis(d0, ax, cum);
    p = add(p, mul(dir, LEN[i][k]));
    pts.push(add(p, [0, 0, zoff * (k + 1) / 3]));
  }
  return pts;
}

/** Local 21-landmark pose (MediaPipe ordering) for a pose spec {f, t, sp, zoff}. */
export function localPose(pose) {
  const f = (pose.f || 'XXXX').split('');
  const sp = Array.isArray(pose.sp) ? pose.sp : (SPREAD[pose.sp] || SPREAD.tog);
  const zoff = pose.zoff || [0, 0, 0, 0];
  const pts = new Array(21);
  pts[0] = [0, 0, 0];
  for (let i = 0; i < 4; i++) {
    const ch = fingerChain(i, f[i], sp[i], zoff[i]);
    for (let k = 0; k < 4; k++) pts[5 + i * 4 + k] = ch[k];
  }
  pts[1] = CMC.slice();
  const t = pose.t || 'tuck';
  if (t.startsWith('touch')) {
    const idx = parseInt(t.slice(5), 10);
    const tip = add(pts[idx], [-0.03, -0.02, 0.03]);
    const m = [-0.38, 0.40, 0.18];
    pts[2] = m;
    pts[3] = add(lerp(m, tip, 0.5), [-0.08, 0, 0.08]);
    pts[4] = tip;
  } else {
    const th = THUMB[t] || THUMB.tuck;
    pts[2] = th[0].slice(); pts[3] = th[1].slice(); pts[4] = th[2].slice();
  }
  return pts.concat(PALM_OUTLINE.map(p => p.slice()), ARM.map(p => p.slice()));
}

function rotate(p, [rx, ry, rz]) {
  let [x, y, z] = p;
  if (rx) { const c = Math.cos(rx * D2R), s = Math.sin(rx * D2R); [y, z] = [y * c - z * s, y * s + z * c]; }
  if (ry) { const c = Math.cos(ry * D2R), s = Math.sin(ry * D2R); [x, z] = [x * c + z * s, -x * s + z * c]; }
  if (rz) { const c = Math.cos(rz * D2R), s = Math.sin(rz * D2R); [x, y] = [x * c - y * s, x * s + y * c]; }
  return [x, y, z];
}

// ---- motion ------------------------------------------------------------------------------
const ease = t => t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;

function pathPoint(path, u) {
  if (path.length === 1) return path[0];
  const segs = [];
  let total = 0;
  for (let i = 1; i < path.length; i++) {
    const l = Math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1]);
    segs.push(l); total += l;
  }
  let d = u * total;
  for (let i = 0; i < segs.length; i++) {
    if (d <= segs[i] || i === segs.length - 1) {
      const t = segs[i] ? Math.min(1, d / segs[i]) : 0;
      return [path[i][0] + (path[i + 1][0] - path[i][0]) * t, path[i][1] + (path[i + 1][1] - path[i][1]) * t];
    }
    d -= segs[i];
  }
  return path[path.length - 1];
}

/** Offset of the moving hand at animation time t (ms). null t → rest position [0,0]. */
export function motionOffset(motion, t) {
  if (!motion || t == null) return [0, 0];
  const dur = motion.dur || 1400;
  const pause = 650;
  const cycle = dur + pause;
  const u = ease(Math.min(1, Math.max(0, ((t % cycle) - pause / 2) / dur)));
  return pathPoint(motion.path, u);
}

// ---- scene -------------------------------------------------------------------------------
/**
 * Build all hands of a sign in world coordinates.
 * sign.hands: [{side:'R'|'L', pose, rot:[rx,ry,rz], at:[x,y], scale, attach:{pt, to:['L',idx] | [x,y], off:[dx,dy]}}]
 * Returns { R: pts[], L: pts[], order: ['R','L'] }
 */
export function buildScene(sign, t = null) {
  const out = { hands: {}, order: [] };
  for (const h of sign.hands) {
    let pts = localPose(h.pose);
    if (h.side === 'L') pts = pts.map(([x, y, z]) => [-x, y, z]);
    const sc = h.scale || 1;
    pts = pts.map(p => rotate(mul(p, sc), h.rot || [0, 0, 0]));
    let shift = [...(h.at || [0, 0]), h.z || 0];
    if (h.attach) {
      const a = h.attach;
      let target;
      if (Array.isArray(a.to) && typeof a.to[0] === 'string') {
        const other = out.hands[a.to[0]];
        target = a.to[1] === 'palm' ? palmCenter(other) : other[a.to[1]];
      } else target = [a.to[0], a.to[1], 0];
      const off = a.off || [0, 0];
      const src = a.pt === 'palm' ? palmCenter(pts) : pts[a.pt];
      shift = [target[0] + off[0] - src[0], target[1] + off[1] - src[1], (target[2] || 0) + (a.dz ?? 0.06) - src[2]];
    }
    pts = pts.map(p => add(p, shift));
    if (sign.motion && (sign.motion.hand || 'R') === h.side) {
      const [dx, dy] = motionOffset(sign.motion, t);
      pts = pts.map(p => [p[0] + dx, p[1] + dy, p[2]]);
    }
    if (sign.motion2 && sign.motion2.hand === h.side) {
      const [dx, dy] = motionOffset(sign.motion2, t);
      pts = pts.map(p => [p[0] + dx, p[1] + dy, p[2]]);
    }
    out.hands[h.side] = pts;
    out.order.push(h.side);
  }
  return out;
}

export function palmCenter(pts) {
  const ids = [0, 5, 9, 13, 17];
  const s = ids.reduce((acc, i) => add(acc, pts[i]), [0, 0, 0]);
  return mul(s, 1 / ids.length);
}

/** Ideal landmarks in the same format the camera produces: {x, y (down), z (towards camera = negative)}. */
export function syntheticLandmarks(sign) {
  const scene = buildScene(sign, null);
  const res = {};
  for (const side of scene.order) {
    res[side] = scene.hands[side].slice(0, 21).map(([x, y, z]) => ({ x: x * 100, y: -y * 100, z: -z * 100 }));
  }
  return res;
}

// ---- SVG rendering -----------------------------------------------------------------------
const f2 = n => (Math.round(n * 1000) / 1000).toString();
const pt2 = p => `${f2(p[0])},${f2(-p[1])}`;

function handDrawables(pts, side, secondary) {
  const items = [];
  const palm = pts.slice(PALM_START, ARM_START);
  const arm = pts.slice(ARM_START, ARM_START + 4);
  // which side faces the viewer?
  const u = sub(pts[17], pts[5]), v = sub(pts[9], pts[0]);
  let n = cross3(u, v);
  if (side === 'L') n = mul(n, -1);
  const palmFacing = n[2] >= 0;
  const cls = secondary ? ' sec' : '';
  const avgZ = arr => arr.reduce((s, p) => s + p[2], 0) / arr.length;
  items.push({ z: avgZ(arm) - 0.3, svg: `<polygon class="arm${cls}" points="${arm.map(pt2).join(' ')}"/>` });
  items.push({ z: avgZ(palm), svg: `<polygon class="palm${cls}${palmFacing ? ' front' : ' back'}" points="${palm.map(pt2).join(' ')}"/>` });
  const chain = (ids, widths) => {
    for (let k = 0; k < ids.length - 1; k++) {
      const a = pts[ids[k]], b = pts[ids[k + 1]];
      const w = widths[k];
      const z = (a[2] + b[2]) / 2 + 0.02;
      items.push({
        z,
        svg: `<line class="so${cls}" x1="${f2(a[0])}" y1="${f2(-a[1])}" x2="${f2(b[0])}" y2="${f2(-b[1])}" stroke-width="${f2(w + 0.04)}"/>` +
          `<line class="sf${cls}" x1="${f2(a[0])}" y1="${f2(-a[1])}" x2="${f2(b[0])}" y2="${f2(-b[1])}" stroke-width="${f2(w)}"/>`,
      });
    }
  };
  chain([1, 2, 3, 4], THUMB_W);
  for (let i = 0; i < 4; i++) chain([5 + i * 4, 6 + i * 4, 7 + i * 4, 8 + i * 4], WIDTH[i]);
  return items;
}

function bodySVG() {
  return `<g class="body">
    <path d="M-2.9,1.6 C-2.6,-0.6 -1.6,-0.9 -0.45,-1.25 L-0.4,-1.85 L0.4,-1.85 L0.45,-1.25 C1.6,-0.9 2.6,-0.6 2.9,1.6 Z"/>
    <ellipse cx="0" cy="-3.15" rx="0.95" ry="1.22"/>
    <ellipse class="feat" cx="-0.34" cy="-3.3" rx="0.09" ry="0.06"/><ellipse class="feat" cx="0.34" cy="-3.3" rx="0.09" ry="0.06"/>
    <path class="featl" d="M-0.25,-2.62 Q0,-2.5 0.25,-2.62"/>
  </g>`;
}

/** Render a sign into an <svg> element at animation time t (ms). */
export function renderSign(svg, sign, t = 0, opts = {}) {
  const scene = buildScene(sign, t);
  const rest = sign.motion ? buildScene(sign, null) : scene;
  let items = [];
  const all = [];
  const dom = sign.hands.some(h => h.side === 'R') ? 'R' : sign.hands[0]?.side; // right hand drawn lighter
  for (const side of scene.order) {
    const pts = scene.hands[side];
    items = items.concat(handDrawables(pts, side, sign.hands.length > 1 && side !== dom));
    all.push(...pts.slice(0, ARM_START + 2));
  }
  items.sort((a, b) => a.z - b.z);

  let arrows = '';
  const motions = [sign.motion, sign.motion2].filter(Boolean);
  for (const m of motions) {
    if (m.path.length < 2) continue;
    const refPts = rest.hands[m.hand || 'R'];
    const ref = refPts[m.ref ?? 8];
    const pp = m.path.map(([dx, dy]) => [ref[0] + dx, ref[1] + dy]);
    all.push(...pp.map(p => [p[0], p[1], 0]));
    arrows += `<polyline class="motion" points="${pp.map(pt2).join(' ')}" marker-end="url(#ah)"/>`;
  }
  let marks = '';
  for (const tch of (sign.touch || [])) {
    const [a, b] = tch;
    const side = b[0], idx = b.slice(1);
    const P = scene.hands[side];
    if (!P) continue;
    const p = idx === 'palm' ? palmCenter(P) : P[+idx];
    marks += `<circle class="touch" cx="${f2(p[0])}" cy="${f2(-p[1])}" r="0.11"/>`;
  }

  let vb;
  if (sign.body) vb = [-3.1, -4.65, 6.2, 6.3];
  else {
    let x0 = Infinity, x1 = -Infinity, y0 = Infinity, y1 = -Infinity;
    for (const p of all) { x0 = Math.min(x0, p[0]); x1 = Math.max(x1, p[0]); y0 = Math.min(y0, p[1]); y1 = Math.max(y1, p[1]); }
    // keep only a stub of forearm in view
    y0 = Math.max(y0, y1 - 3.2);
    const pad = 0.28;
    let w = x1 - x0 + 2 * pad, h = y1 - y0 + 2 * pad;
    const s = Math.max(w, h, 2.2);
    const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
    vb = [cx - s / 2, -(cy + s / 2), s, s];
  }
  svg.setAttribute('viewBox', vb.map(f2).join(' '));
  svg.innerHTML = `<defs><marker id="ah" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" class="ahead"/></marker></defs>` +
    (sign.body ? bodySVG() : '') + items.map(i => i.svg).join('') + marks + arrows;
}

/** Animate a sign in an <svg>. Returns a stop() function. */
export function animateSign(svg, sign, opts = {}) {
  let raf = 0, start = performance.now(), last = 0;
  const loop = now => {
    if (now - last > 30) { renderSign(svg, sign, now - start, opts); last = now; }
    if (sign.motion || sign.motion2) raf = requestAnimationFrame(loop);
  };
  raf = requestAnimationFrame(loop);
  return () => cancelAnimationFrame(raf);
}
