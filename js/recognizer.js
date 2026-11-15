// Hand-sign recognizer.
// Live webcam landmarks (MediaPipe, 21 points per hand) are turned into features
// (finger bend, thumb position, spread, hand angle, contacts between hands) and compared
// with the features of the ideal pose generated from the sign definition.
import { syntheticLandmarks } from './handmodel.js';

const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const len = a => Math.hypot(a[0], a[1], a[2]);
const norm = a => { const l = len(a) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const angle = (a, b) => Math.acos(Math.max(-1, Math.min(1, dot(norm(a), norm(b)))));
const gauss = (d, tol) => Math.exp(-((d / tol) ** 2));
const angDiff = (a, b) => { let d = Math.abs(a - b) % 360; return d > 180 ? 360 - d : d; };

const FINGER_IDS = [[5, 6, 7, 8], [9, 10, 11, 12], [13, 14, 15, 16], [17, 18, 19, 20]];
export const FINGER_NAMES = {
  en: ['Index finger', 'Middle finger', 'Ring finger', 'Little finger'],
  hi: ['तर्जनी', 'मध्यमा', 'अनामिका', 'छोटी उंगली'],
};

/**
 * Features of one hand.
 * lm: 21 points {x, y (down), z (negative = towards camera)} in a common pixel-like scale.
 * mirror: true for a left hand, so it can be treated like a right hand.
 */
export function handFeatures(lm, mirror = false) {
  const S = lm.map(p => [p.x, -p.y, -p.z]);            // shared space, y up
  const P = mirror ? S.map(p => [-p[0], p[1], p[2]]) : S; // right-hand-like
  const palm = len(sub(P[9], P[0])) || 1;
  const curl = FINGER_IDS.map(([a, b, c, d]) => {
    const v1 = sub(P[a], P[0]), v2 = sub(P[b], P[a]), v3 = sub(P[c], P[b]), v4 = sub(P[d], P[c]);
    return (angle(v1, v2) + angle(v2, v3) + angle(v3, v4)) / (250 * Math.PI / 180);
  });
  const u = norm(sub(P[17], P[5]));
  const v0 = sub(P[9], P[0]);
  const v = norm(sub(v0, [u[0] * dot(v0, u), u[1] * dot(v0, u), u[2] * dot(v0, u)]));
  const n = cross(u, v);
  const local = P.map(p => { const d = sub(p, P[0]); return [dot(d, u) / palm, dot(d, v) / palm, dot(d, n) / palm]; });
  const ld = (i, j) => Math.hypot(local[i][0] - local[j][0], local[i][1] - local[j][1], local[i][2] - local[j][2]);
  const spread = [ld(8, 12), ld(12, 16), ld(16, 20)];
  const orient = Math.atan2(P[9][1] - P[0][1], P[9][0] - P[0][0]) * 180 / Math.PI;
  const cross8 = local[8][0] - local[12][0]; // > 0 when the index tip crosses past the middle tip
  const palmC = [0, 5, 9, 13, 17].reduce((a, i) => [a[0] + S[i][0] / 5, a[1] + S[i][1] / 5, a[2] + S[i][2] / 5], [0, 0, 0]);
  return { S, P, palm, curl, local, spread, orient, cross8, palmC };
}

// ---------------------------------------------------------------------------------------
// Reference ("expected") features for each sign, built from the illustrated pose.
const expCache = new Map();
export function expected(sign) {
  if (expCache.has(sign.id)) return expCache.get(sign.id);
  const syn = syntheticLandmarks(sign);
  const e = { sides: Object.keys(syn), hands: {} };
  for (const side of e.sides) e.hands[side] = handFeatures(syn[side], side === 'L');
  const f = sign.hands.find(h => h.side === 'R')?.pose.f || sign.hands[0].pose.f;
  e.fingerCodes = { R: f, L: sign.hands.find(h => h.side === 'L')?.pose.f };
  expCache.set(sign.id, e);
  return e;
}

function pointOf(frameHands, ref) {
  const side = ref[0], rest = ref.slice(1);
  const h = frameHands[side];
  if (!h) return null;
  return rest === 'palm' ? h.palmC : h.S[+rest];
}

/** Score how well one live hand matches the expected hand. */
function scoreHand(live, exp, opts, lang, prefix) {
  const parts = [];
  const names = FINGER_NAMES[lang] || FINGER_NAMES.en;
  for (let i = 0; i < 4; i++) {
    const t = exp.curl[i];
    let d = live.curl[i] - t;
    if (t > 0.85 && d > 0) d = 0;
    if (t < 0.15 && d < 0) d = 0;
    const s = gauss(d, 0.24);
    const want = t < 0.2 ? 'straight' : t > 0.8 ? 'curled' : 'bent';
    parts.push({ key: prefix + 'f' + i, w: 1, s, label: names[i], want, dir: d > 0 ? 'less' : 'more' });
  }
  if (!opts.loose) {
    const a = live.local[4], b = exp.local[4];
    const d = Math.hypot(a[0] - b[0], a[1] - b[1], (a[2] - b[2]) * 0.6);
    // In fist-like signs (A, E, M, N, S, T) the thumb position is the only difference, so be stricter.
    const fistLike = exp.curl.every(c => c > 0.6);
    parts.push({ key: prefix + 'thumb', w: fistLike ? 2.5 : 1.5, s: gauss(d, fistLike ? 0.2 : 0.32), label: lang === 'hi' ? 'अँगूठे की जगह' : 'Thumb position', want: 'thumb' });
    for (let k = 0; k < 3; k++) {
      if (exp.curl[k] < 0.25 && exp.curl[k + 1] < 0.25) {
        const dd = live.spread[k] - exp.spread[k];
        parts.push({ key: prefix + 'sp' + k, w: 0.8, s: gauss(dd, 0.17), label: lang === 'hi' ? 'उंगलियों का फैलाव' : 'Finger spread', want: dd > 0 ? 'closer' : 'apart' });
      }
    }
    if (opts.orient) {
      const dA = angDiff(live.orient, exp.orient);
      parts.push({ key: prefix + 'or', w: opts.orientW || 0.6, s: gauss(dA, 48), label: lang === 'hi' ? 'हाथ का कोण' : 'Hand angle', want: 'angle' });
    }
    if (opts.crossed) {
      parts.push({ key: prefix + 'x', w: 1.2, s: live.cross8 > 0 ? 1 : gauss(live.cross8, 0.1), label: lang === 'hi' ? 'उंगलियाँ क्रॉस' : 'Fingers crossed', want: 'cross' });
    } else if (exp.curl[0] < 0.25 && exp.curl[1] < 0.25) {
      parts.push({ key: prefix + 'nx', w: 0.8, s: live.cross8 < 0.02 ? 1 : gauss(live.cross8 - 0.02, 0.08), label: lang === 'hi' ? 'उंगलियाँ क्रॉस न करें' : 'Fingers not crossed', want: 'uncross' });
    }
  }
  for (const p of parts) if (prefix) p.label = prefix + p.label;
  return parts;
}

function combine(parts) {
  let sw = 0, sl = 0, mn = 1;
  for (const p of parts) { sw += p.w; sl += p.w * Math.log(Math.max(p.s, 0.03)); mn = Math.min(mn, p.s); }
  // weighted geometric mean, plus a penalty when any single part is clearly wrong
  return sw ? Math.exp(sl / sw) * Math.pow(Math.max(mn, 0.03), 0.3) : 0;
}

/**
 * Score a sign against a frame.
 * frame.hands: { R?: features, L?: features } (L features already mirrored)
 * Returns { score, parts, missing }
 */
export function scoreSign(sign, frame, lang = 'en') {
  const e = expected(sign);
  if (e.sides.length > 1) {
    // try the detected left/right labels, and the swapped version (labels from the tracker are not always right)
    let best = scoreWith(sign, e, frame.hands, lang);
    if (frame.alt) { const r = scoreWith(sign, e, frame.alt, lang); if (r.score > best.score) best = r; }
    return best;
  }
  // one-handed sign: accept it from either hand, whichever way the tracker labelled it
  const opts = [frame.hands.R, frame.hands.L, frame.alt?.R, frame.alt?.L].filter(Boolean);
  if (!opts.length) return { score: 0, parts: [], missing: 'one' };
  let best = null;
  for (const h of opts) { const r = scoreWith(sign, e, { R: h }, lang); if (!best || r.score > best.score) best = r; }
  return best;
}

function scoreWith(sign, e, hands, lang) {
  const rec = sign.rec || {};
  const twoHands = e.sides.length > 1;
  if (twoHands) {
    if (!hands.R || !hands.L) return { score: 0, parts: [], missing: 'two' };
    const lab = lang === 'hi' ? { R: 'दायाँ: ', L: 'बायाँ: ' } : { R: 'Right: ', L: 'Left: ' };
    let parts = scoreHand(hands.R, e.hands.R, { orient: rec.orient, orientW: rec.orientW, crossed: rec.crossed }, lang, lab.R);
    parts = parts.concat(scoreHand(hands.L, e.hands.L, { loose: true }, lang, lab.L));
    const pal = (hands.R.palm + hands.L.palm) / 2;
    for (const [a, b, tol = 0.6] of sign.touch || []) {
      const pa = pointOf(hands, a), pb = pointOf(hands, b);
      if (!pa || !pb) continue;
      const d = Math.hypot(pa[0] - pb[0], pa[1] - pb[1]) / pal;
      parts.push({ key: 't' + a + b, w: 1.6, s: gauss(Math.max(0, d - tol * 0.4), tol * 0.6), label: lang === 'hi' ? 'हाथों का स्पर्श' : 'Hands touching', want: 'touch' });
    }
    if (rec.nearest) {
      const { a, set, b } = rec.nearest;
      const pa = pointOf(hands, a);
      let best = null, bd = Infinity;
      for (const i of set) {
        const q = hands.L.S[i];
        const d = Math.hypot(pa[0] - q[0], pa[1] - q[1]);
        if (d < bd) { bd = d; best = i; }
      }
      parts.push({ key: 'near', w: 2, s: best === b ? 1 : 0.15, label: lang === 'hi' ? 'सही उंगली छुएँ' : 'Touch the right finger', want: 'target' });
    }
    return { score: combine(parts), parts };
  }
  const h = hands.R;
  const parts = scoreHand(h, e.hands.R, { orient: rec.orient, orientW: rec.orientW, crossed: rec.crossed }, lang, '');
  return { score: combine(parts), parts };
}

// ---------------------------------------------------------------------------------------
// Personal templates: record your own version of a sign; also counted as a match.
export function templateVector(sign, frame) {
  const e = expected(sign);
  const v = [];
  if (e.sides.length > 1) {
    if (!frame.hands.R || !frame.hands.L) return null;
    for (const s of ['R', 'L']) for (const p of frame.hands[s].local) v.push(...p);
    const pal = (frame.hands.R.palm + frame.hands.L.palm) / 2;
    const d = sub(frame.hands.L.palmC, frame.hands.R.S[0]);
    v.push(d[0] / pal, d[1] / pal);
  } else {
    const h = frame.hands.R || frame.hands.L;
    if (!h) return null;
    for (const p of h.local) v.push(...p);
  }
  return v;
}
export function templateScore(vecs, v) {
  if (!vecs || !vecs.length || !v) return 0;
  let best = 0;
  for (const t of vecs) {
    if (t.length !== v.length) continue;
    let s = 0;
    for (let i = 0; i < v.length; i++) s += (t[i] - v[i]) ** 2;
    const rms = Math.sqrt(s / v.length);
    best = Math.max(best, gauss(rms, 0.16));
  }
  return best;
}

// ---------------------------------------------------------------------------------------
/** Tracks a target sign over time: smoothing, hold-to-confirm, motion, repeat-lock. */
export class Matcher {
  constructor(getSettings, getTemplates) {
    this.getSettings = getSettings;
    this.getTemplates = getTemplates || (() => null);
    this.reset(null);
  }
  reset(sign) {
    this.sign = sign;
    this.smooth = 0;
    this.holdStart = null;
    this.hist = [];
    this.lockUntil = 0;
    this.locked = false;
  }
  /** Require the user to release the sign before it can be accepted again (for double letters). */
  lock(now) { this.locked = true; this.lockUntil = now + 900; this.holdStart = null; this.hist = []; }

  update(frame, now, lang) {
    const st = this.getSettings();
    const thr = st.threshold;
    if (!this.sign) return null;
    const r = scoreSign(this.sign, frame, lang);
    const tv = this.getTemplates(this.sign.id);
    if (tv && tv.length) r.score = Math.max(r.score, templateScore(tv, templateVector(this.sign, frame)));
    this.smooth = this.smooth * 0.45 + r.score * 0.55;
    if (this.locked) {
      if (this.smooth < thr - 0.15 || now > this.lockUntil) this.locked = false;
      return { ...r, smooth: this.smooth, hold: 0, accepted: false, locked: true };
    }
    let accepted = false, hold = 0;
    const rec = this.sign.rec || {};
    const needsMotion = this.sign.motion && !this.sign.motion.demo && rec.motionMin;
    if (needsMotion) {
      const side = (this.sign.hands.length > 1) ? 'R' : (frame.hands.R ? 'R' : 'L');
      const h = frame.hands[side];
      if (h) this.hist.push({ t: now, p: h.S[rec.motionPt ?? 8], palm: h.palm, s: r.score });
      while (this.hist.length && now - this.hist[0].t > 1700) this.hist.shift();
      let path = 0, ssum = 0;
      for (let i = 1; i < this.hist.length; i++) {
        const a = this.hist[i - 1], b = this.hist[i];
        path += Math.hypot(b.p[0] - a.p[0], b.p[1] - a.p[1]) / b.palm;
      }
      for (const x of this.hist) ssum += x.s;
      const avg = this.hist.length ? ssum / this.hist.length : 0;
      hold = Math.min(1, path / rec.motionMin) * Math.min(1, avg / thr);
      r.motion = Math.min(1, path / rec.motionMin);
      if (this.hist.length >= 6 && avg >= thr * 0.92 && path >= rec.motionMin) accepted = true;
    } else {
      if (this.smooth >= thr) {
        if (this.holdStart == null) this.holdStart = now;
        hold = Math.min(1, (now - this.holdStart) / st.holdMs);
        if (hold >= 1) accepted = true;
      } else this.holdStart = null;
    }
    if (accepted) this.lock(now);
    return { ...r, smooth: this.smooth, hold, accepted };
  }
}

/** Best guess among candidate signs (for the "I see: …" readout). */
export function bestGuess(frame, candidates, lang) {
  let best = null;
  for (const s of candidates) {
    const r = scoreSign(s, frame, lang);
    // with two hands in view, prefer two-handed signs
    if (frame.hands.R && frame.hands.L && expected(s).sides.length === 1) r.score *= 0.85;
    if (!best || r.score > best.score) best = { sign: s, score: r.score };
  }
  return best;
}
