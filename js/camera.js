// Webcam + MediaPipe hand tracking.
// Uses local copies in ./vendor if you ran `python download_offline.py`, otherwise the CDN.
import { handFeatures } from './recognizer.js';

const VER = '0.10.14';
const CDN = {
  bundle: `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${VER}/vision_bundle.mjs`,
  wasm: `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${VER}/wasm`,
  model: 'https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task',
};
const LOCAL = { bundle: './vendor/vision_bundle.mjs', wasm: './vendor/wasm', model: './vendor/hand_landmarker.task' };

const CONNECTIONS = [[0, 1], [1, 2], [2, 3], [3, 4], [0, 5], [5, 6], [6, 7], [7, 8], [5, 9], [9, 10], [10, 11], [11, 12],
  [9, 13], [13, 14], [14, 15], [15, 16], [13, 17], [17, 18], [18, 19], [19, 20], [0, 17]];

async function exists(url) {
  try { const r = await fetch(url, { method: 'HEAD' }); return r.ok; } catch { return false; }
}

export class Camera {
  constructor(getSettings) {
    this.getSettings = getSettings;
    this.el = document.createElement('div');
    this.el.className = 'cam';
    this.el.innerHTML = `<video playsinline muted></video><canvas></canvas>
      <div class="cam-msg"></div><div class="cam-badge"></div>`;
    this.video = this.el.querySelector('video');
    this.canvas = this.el.querySelector('canvas');
    this.ctx = this.canvas.getContext('2d');
    this.msg = this.el.querySelector('.cam-msg');
    this.badge = this.el.querySelector('.cam-badge');
    this.listeners = new Set();
    this.running = false;
    this.landmarker = null;
    this.highlight = null; // e.g. ['R8', 'L4'] contact points to highlight
    this.lastFrame = null;
  }

  onFrame(cb) { this.listeners.add(cb); return () => this.listeners.delete(cb); }
  setMessage(t) { this.msg.textContent = t || ''; this.msg.style.display = t ? 'flex' : 'none'; }

  async start(texts) {
    if (this.running) return;
    this.running = true;
    try {
      if (!this.landmarker) {
        this.setMessage(texts.loading);
        const local = await exists(LOCAL.model);
        const src = local ? LOCAL : CDN;
        const vision = await import(src.bundle);
        const fileset = await vision.FilesetResolver.forVisionTasks(new URL(src.wasm, location.href).href.replace(/\/$/, ''));
        const opts = (delegate) => ({
          baseOptions: { modelAssetPath: src.model, delegate },
          runningMode: 'VIDEO', numHands: 2,
          minHandDetectionConfidence: 0.5, minHandPresenceConfidence: 0.5, minTrackingConfidence: 0.5,
        });
        try { this.landmarker = await vision.HandLandmarker.createFromOptions(fileset, opts('GPU')); }
        catch { this.landmarker = await vision.HandLandmarker.createFromOptions(fileset, opts('CPU')); }
      }
      if (!this.stream) {
        this.setMessage(texts.allow);
        this.stream = await navigator.mediaDevices.getUserMedia({ video: { width: { ideal: 960 }, height: { ideal: 540 }, facingMode: 'user' }, audio: false });
        this.video.srcObject = this.stream;
        await this.video.play();
      }
      this.setMessage('');
      this.loop();
    } catch (err) {
      this.running = false;
      console.error(err);
      const denied = err && (err.name === 'NotAllowedError' || err.name === 'NotFoundError');
      this.setMessage(denied ? texts.denied : texts.failed + ' ' + (err?.message || err));
      throw err;
    }
  }

  stop() {
    this.running = false;
    if (this.stream) { this.stream.getTracks().forEach(t => t.stop()); this.stream = null; this.video.srcObject = null; }
  }

  loop() {
    let lastTime = -1;
    const tick = () => {
      if (!this.running) return;
      const v = this.video;
      if (v.readyState >= 2 && v.currentTime !== lastTime) {
        lastTime = v.currentTime;
        const now = performance.now();
        let res = null;
        try { res = this.landmarker.detectForVideo(v, now); } catch (e) { console.warn(e); }
        if (res) this.process(res, now);
      }
      requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  }

  process(res, now) {
    const st = this.getSettings();
    const W = this.video.videoWidth, H = this.video.videoHeight;
    const raw = (res.landmarks || []).map((lm, i) => {
      const label = res.handedness?.[i]?.[0]?.categoryName || res.handednesses?.[i]?.[0]?.categoryName || 'Right';
      // MediaPipe labels assume a mirrored (selfie) image; we feed the raw camera frame, so "Left" is your right hand.
      let side = label === 'Left' ? 'R' : 'L';
      if (st.swapHands) side = side === 'R' ? 'L' : 'R';
      const disp = lm.map(p => ({ x: (1 - p.x) * W, y: p.y * H, z: p.z * W }));
      return { side, disp };
    });
    if (raw.length === 2 && raw[0].side === raw[1].side) {
      // labels disagree with reality: decide by position (in the mirror your right hand is on the right)
      const [a, b] = raw[0].disp[0].x > raw[1].disp[0].x ? [raw[0], raw[1]] : [raw[1], raw[0]];
      a.side = 'R'; b.side = 'L';
    }
    const hands = {}, alt = {};
    for (const h of raw) {
      let lm = h.disp, side = h.side;
      if (st.leftHanded) { lm = lm.map(p => ({ x: W - p.x, y: p.y, z: p.z })); side = side === 'R' ? 'L' : 'R'; }
      hands[side] = handFeatures(lm, side === 'L');
      hands[side].disp = h.disp;
      // the same hand with the opposite left/right label, in case the tracker got it wrong
      const other = side === 'R' ? 'L' : 'R';
      alt[other] = handFeatures(lm, other === 'L');
      alt[other].disp = h.disp;
    }
    const frame = { hands, alt, now, W, H, count: raw.length };
    this.lastFrame = frame;
    this.draw(frame);
    for (const cb of this.listeners) cb(frame);
  }

  draw(frame) {
    const { W, H } = frame;
    const c = this.canvas, ctx = this.ctx;
    if (c.width !== W || c.height !== H) { c.width = W; c.height = H; }
    ctx.clearRect(0, 0, W, H);
    if (!this.getSettings().skeleton) return;
    const s = Math.max(W, H) / 640;
    for (const side of ['L', 'R']) {
      const h = frame.hands[side];
      if (!h) continue;
      const P = h.disp;
      const col = side === 'R' ? '#ff8a3d' : '#3db8ff';
      ctx.lineWidth = 3 * s; ctx.strokeStyle = col; ctx.lineCap = 'round';
      ctx.beginPath();
      for (const [a, b] of CONNECTIONS) { ctx.moveTo(P[a].x, P[a].y); ctx.lineTo(P[b].x, P[b].y); }
      ctx.stroke();
      ctx.fillStyle = '#fff';
      for (const p of P) { ctx.beginPath(); ctx.arc(p.x, p.y, 3.2 * s, 0, Math.PI * 2); ctx.fill(); }
    }
    if (this.highlight) {
      for (const ref of this.highlight) {
        const h = frame.hands[ref[0]]; if (!h) continue;
        const k = ref.slice(1);
        let p;
        if (k === 'palm') { const ids = [0, 5, 9, 13, 17]; p = ids.reduce((a, i) => ({ x: a.x + h.disp[i].x / 5, y: a.y + h.disp[i].y / 5 }), { x: 0, y: 0 }); }
        else p = h.disp[+k];
        ctx.strokeStyle = '#ffe14d'; ctx.lineWidth = 3 * s;
        ctx.beginPath(); ctx.arc(p.x, p.y, 12 * s, 0, Math.PI * 2); ctx.stroke();
      }
    }
  }
}
