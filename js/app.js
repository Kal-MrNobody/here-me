import { SIGNS, BY_ID, signsFor, letterSign, LANGS, practiceWords } from './signs.js';
import { renderSign, animateSign } from './handmodel.js';
import { Matcher, bestGuess, templateVector } from './recognizer.js';
import { Camera } from './camera.js';
import { STR, WANT } from './i18n.js';

// ---------------------------------------------------------------- storage
const LS = {
  get(k, d) { try { const v = localStorage.getItem('haathse.' + k); return v ? JSON.parse(v) : d; } catch { return d; } },
  set(k, v) { try { localStorage.setItem('haathse.' + k, JSON.stringify(v)); } catch { /* storage unavailable */ } },
};
const DEFAULTS = { uiLang: 'en', signLang: 'isl', holdMs: 400, threshold: 0.7, leftHanded: false, swapHands: false, skeleton: true, autoNext: true, hints: true, mode: 'letters', level: 'easy', duration: 60 };
const settings = { ...DEFAULTS, ...LS.get('settings', {}) };
const saveSettings = () => LS.set('settings', settings);
const progress = LS.get('progress', {});        // signId -> { ok }
const history = LS.get('history', []);          // practice sessions
const signStats = LS.get('signStats', {});      // signId -> { n, ms }
const templates = LS.get('templates', {});      // signId -> [vector]

const t = k => (STR[settings.uiLang] || STR.en)[k] ?? STR.en[k] ?? k;
const L = obj => (obj && (obj[settings.uiLang] ?? obj.en)) || '';
const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const $ = (sel, root = document) => root.querySelector(sel);
const camTexts = () => ({ loading: t('camLoading'), allow: t('camAllow'), denied: t('camDenied'), failed: t('camFailed') });

const cam = new Camera(() => settings);
const isLearned = id => (progress[id]?.ok || 0) > 0;

// ---------------------------------------------------------------- shell
const main = $('#main');
let cleanup = null;

function header() {
  const route = (location.hash || '#home').slice(1).split('/')[0];
  const nav = ['home', 'learn', 'practice', 'progress', 'settings'];
  $('#nav').innerHTML = nav.map(n => `<a href="#${n}" class="${route === n ? 'on' : ''}">${t(n)}</a>`).join('');
  $('#brand').textContent = t('appName');
  $('#langSwitch').innerHTML = Object.entries(LANGS).map(([k, v]) =>
    `<button data-sl="${k}" class="${settings.signLang === k ? 'on' : ''}" title="${esc(L(v.name))}">${v.short}</button>`).join('');
  $('#uiSwitch').innerHTML = `<button data-ui="en" class="${settings.uiLang === 'en' ? 'on' : ''}">EN</button><button data-ui="hi" class="${settings.uiLang === 'hi' ? 'on' : ''}">हिं</button>`;
  document.documentElement.lang = settings.uiLang;
  document.title = t('appName');
}
document.addEventListener('click', e => {
  const sl = e.target.closest('[data-sl]');
  if (sl) {
    settings.signLang = sl.dataset.sl; saveSettings();
    const parts = location.hash.split('/');
    if (parts[0] === '#learn' && parts[1]) location.hash = '#learn'; else render();
  }
  const ui = e.target.closest('[data-ui]');
  if (ui) { settings.uiLang = ui.dataset.ui; saveSettings(); render(); }
});

function render() {
  if (cleanup) { try { cleanup(); } catch (e) { console.error(e); } cleanup = null; }
  header();
  const [route, arg] = (location.hash || '#home').slice(1).split('/');
  const views = { home: viewHome, learn: arg ? viewLesson : viewLearn, practice: viewPractice, progress: viewProgress, settings: viewSettings };
  const usesCam = (route === 'learn' && arg) || route === 'practice';
  if (!usesCam) cam.stop();
  cleanup = (views[route] || viewHome)(arg ? decodeURIComponent(arg) : null);
  window.scrollTo(0, 0);
}
window.addEventListener('hashchange', render);

function mountCamera(slot) {
  slot.appendChild(cam.el);
  cam.start(camTexts()).catch(() => {
    const b = document.createElement('button');
    b.className = 'btn'; b.textContent = t('camStart');
    b.onclick = () => { b.remove(); mountCamera(slot); };
    slot.appendChild(b);
  });
}

function signSVG(sign, cls = 'sign-svg') {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', cls);
  svg.setAttribute('role', 'img');
  svg.setAttribute('aria-label', L(sign.name));
  renderSign(svg, sign, sign.motion ? 300 : 0);
  return svg;
}

// ---------------------------------------------------------------- home
function viewHome() {
  const lang = settings.signLang;
  const all = signsFor(lang);
  const done = all.filter(s => isLearned(s.id)).length;
  const best = Math.max(0, ...history.filter(h => h.lang === lang).map(h => h.wpm));
  main.innerHTML = `
  <section class="hero">
    <div class="hero-text">
      <h1>${t('heroTitle')}</h1>
      <p>${t('heroSub')}</p>
      <div class="lang-cards">
        ${Object.entries(LANGS).map(([k, v]) => `<button class="lang-card ${lang === k ? 'on' : ''}" data-sl="${k}">
          <b>${v.short}</b><span>${esc(L(v.name))}</span><small>${esc(L(v.note))}</small></button>`).join('')}
      </div>
      <div class="hero-actions">
        <a class="btn primary big" href="#learn">${t('startLearning')} →</a>
        <a class="btn big" href="#practice">${t('startPractice')} ⚡</a>
      </div>
    </div>
    <div class="hero-art"><div class="hero-svgs"></div></div>
  </section>
  <section class="stats-row">
    <div class="stat"><b>${done}<small> / ${all.length}</small></b><span>${t('mastered')}</span></div>
    <div class="stat"><b>${best ? best.toFixed(1) : '—'}</b><span>${t('bestWpm')}</span></div>
    <div class="stat"><b>${history.filter(h => h.lang === lang).length}</b><span>${t('sessions')}</span></div>
  </section>
  <section class="card tips"><h3>${t('tipsTitle')}</h3><ul>${t('tips').map(x => `<li>${esc(x)}</li>`).join('')}</ul></section>`;
  const holder = $('.hero-svgs');
  const word = lang === 'isl' ? ['isl-w-namaste', 'isl-A', 'isl-w-good'] : ['asl-w-iloveyou', 'asl-A', 'asl-L'];
  word.map(id => BY_ID[id]).filter(Boolean).forEach(s => holder.appendChild(signSVG(s, 'mini-svg')));
}

// ---------------------------------------------------------------- learn index
function viewLearn() {
  const lang = settings.signLang;
  const section = (cat, title) => {
    const list = signsFor(lang, cat);
    const done = list.filter(s => isLearned(s.id)).length;
    return `<section class="card"><div class="sec-head"><h2>${title}</h2><span class="pill">${done} ${t('of')} ${list.length} ${t('learned')}</span></div>
      <div class="bar"><i style="width:${list.length ? done / list.length * 100 : 0}%"></i></div>
      <div class="tiles ${cat}">${list.map(s => `<a class="tile ${isLearned(s.id) ? 'done' : ''}" href="#learn/${encodeURIComponent(s.id)}" data-id="${s.id}">
        <span class="tile-art"></span><b>${esc(s.cat === 'alpha' ? s.char : L(s.name))}</b>${s.cat === 'word' && settings.uiLang === 'en' ? `<small>${esc(s.name.hi)}</small>` : ''}${s.cat === 'word' && settings.uiLang === 'hi' ? `<small>${esc(s.name.en)}</small>` : ''}</a>`).join('')}</div></section>`;
  };
  main.innerHTML = `<div class="page-head"><h1>${t('learn')} · ${esc(L(LANGS[lang].name))}</h1>
    ${lang === 'isl' ? `<p class="note">${t('islNote')}</p>` : ''}</div>
    ${section('alpha', t('alphabet'))}${section('word', t('words'))}`;
  // draw thumbnails lazily
  const tiles = [...main.querySelectorAll('.tile')];
  let i = 0;
  const step = () => {
    for (let k = 0; k < 6 && i < tiles.length; k++, i++) tiles[i].querySelector('.tile-art').appendChild(signSVG(BY_ID[tiles[i].dataset.id], 'tile-svg'));
    if (i < tiles.length) raf = requestAnimationFrame(step);
  };
  let raf = requestAnimationFrame(step);
  return () => cancelAnimationFrame(raf);
}

// ---------------------------------------------------------------- feedback helpers
function feedbackHTML(r) {
  if (!r) return '';
  if (r.missing) return `<li class="warn">✋ ${r.missing === 'two' ? t('showTwo') : t('showHands')}</li>`;
  const want = WANT[settings.uiLang] || WANT.en;
  const bad = r.parts.filter(p => p.s < 0.6).sort((a, b) => a.s - b.s).slice(0, 3);
  let html = bad.map(p => `<li class="bad">✗ <b>${esc(p.label)}</b> — ${esc(want[p.want] || '')}</li>`).join('');
  if (r.motion != null && r.motion < 1) html += `<li class="warn">↝ ${t('motionNeeded')}</li>`;
  if (!html) html = `<li class="good">✓ ${settings.uiLang === 'hi' ? 'आकार सही है — थोड़ी देर पकड़े रखें' : 'Shape looks right — hold it'}</li>`;
  return html;
}
function meterHTML() {
  return `<div class="meter"><div class="ring"><svg viewBox="0 0 36 36"><circle class="bg" cx="18" cy="18" r="15.5"/><circle class="fg" cx="18" cy="18" r="15.5" pathLength="100" stroke-dasharray="0 100"/></svg><span>0%</span></div>
    <div class="meter-text"><div class="mlabel">${t('match')}</div><div class="mbar"><i></i></div><div class="isee"></div></div></div>`;
}
function updateMeter(root, r) {
  const pct = Math.round((r?.smooth || 0) * 100);
  $('.mbar i', root).style.width = pct + '%';
  $('.mbar i', root).className = pct >= settings.threshold * 100 ? 'ok' : '';
  $('.ring span', root).textContent = pct + '%';
  $('.ring .fg', root).setAttribute('stroke-dasharray', `${Math.round((r?.hold || 0) * 100)} 100`);
}

// ---------------------------------------------------------------- lesson
function viewLesson(id) {
  const sign = BY_ID[id];
  if (!sign) { location.hash = '#learn'; return; }
  if (sign.lang !== settings.signLang) { settings.signLang = sign.lang; saveSettings(); header(); }
  const list = signsFor(sign.lang, sign.cat);
  const idx = list.indexOf(sign);
  const prev = list[idx - 1], next = list[idx + 1];
  const letters = signsFor(sign.lang, 'alpha');
  const sub = sign.cat === 'alpha' ? (settings.uiLang === 'hi' ? `अक्षर ${sign.char}` : `Letter ${sign.char}`) : (settings.uiLang === 'hi' ? sign.name.en : sign.name.hi);
  main.innerHTML = `
  <div class="crumbs"><a href="#learn">← ${t('learn')}</a> <span>${sign.cat === 'alpha' ? t('alphabet') : t('words')} · ${idx + 1}/${list.length}</span></div>
  <div class="lesson">
    <section class="card demo">
      <div class="sign-head"><h1>${esc(sign.cat === 'alpha' ? sign.char : L(sign.name))}</h1><div class="sub">${esc(sub)}</div>
        ${isLearned(sign.id) ? '<span class="pill ok">✓</span>' : ''}</div>
      <div class="svg-wrap"></div>
      <p class="mirror-note">${t('mirrorNote')}</p>
      <h3>${t('howTo')}</h3>
      <ol class="steps">${(sign.steps[settings.uiLang] || sign.steps.en).map(s => `<li>${esc(s)}</li>`).join('')}</ol>
      ${settings.uiLang === 'hi' ? '' : `<details class="hi-steps"><summary>हिंदी में</summary><ol>${sign.steps.hi.map(s => `<li>${esc(s)}</li>`).join('')}</ol></details>`}
      <div class="nav-btns">
        ${prev ? `<a class="btn" href="#learn/${prev.id}">← ${t('prev')}</a>` : '<span></span>'}
        <button class="btn" id="replay">↻ ${t('replay')}</button>
        ${next ? `<a class="btn primary" href="#learn/${next.id}">${t('next')} →</a>` : `<a class="btn primary" href="#practice">${t('practice')} ⚡</a>`}
      </div>
    </section>
    <section class="card try">
      <h3>${t('tryIt')}</h3>
      <div class="cam-slot"></div>
      ${meterHTML()}
      <ul class="feedback"></ul>
      <div class="success" hidden>${t('great')}</div>
      <div class="row small">
        <label><input type="checkbox" id="autonext" ${settings.autoNext ? 'checked' : ''}> ${t('autoNext')}</label>
      </div>
      <div class="row small rec-row">
        <button class="btn sm" id="rec">● ${t('record')}</button>
        <button class="btn sm ghost" id="clr" ${templates[sign.id]?.length ? '' : 'hidden'}>${t('clearRec')}</button>
        <span class="rec-msg muted">${t('recordHelp')}</span>
      </div>
    </section>
  </div>`;
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', 'sign-svg big');
  $('.svg-wrap').appendChild(svg);
  let stopAnim = animateSign(svg, sign);
  $('#replay').onclick = () => { stopAnim(); stopAnim = animateSign(svg, sign); };
  $('#autonext').onchange = e => { settings.autoNext = e.target.checked; saveSettings(); };

  mountCamera($('.cam-slot'));
  cam.highlight = (sign.touch || []).map(x => x[1]).concat((sign.touch || []).map(x => x[0]));
  const matcher = new Matcher(() => settings, sid => templates[sid]);
  matcher.reset(sign);
  const root = $('.try');
  let frameN = 0, recording = null, advanceTimer = null;

  const off = cam.onFrame(frame => {
    frameN++;
    if (recording) {
      const v = templateVector(sign, frame);
      if (v) recording.push(v);
      return;
    }
    const r = matcher.update(frame, frame.now, settings.uiLang);
    updateMeter(root, r);
    if (frameN % 3 === 0) {
      $('.feedback', root).innerHTML = feedbackHTML(r);
      if (sign.cat === 'alpha' && frame.count) {
        const g = bestGuess(frame, letters, settings.uiLang);
        $('.isee', root).textContent = g && g.score > 0.5 ? `${t('iSee')}: ${g.sign.char} (${Math.round(g.score * 100)}%)` : '';
      } else $('.isee', root).textContent = '';
    }
    if (r.accepted) {
      progress[sign.id] = { ok: (progress[sign.id]?.ok || 0) + 1 };
      LS.set('progress', progress);
      const s = $('.success', root); s.hidden = false; s.classList.remove('pop'); void s.offsetWidth; s.classList.add('pop');
      if (settings.autoNext && next && !advanceTimer) advanceTimer = setTimeout(() => { location.hash = `#learn/${next.id}`; }, 1100);
    }
  });

  $('#rec').onclick = () => {
    if (recording) return;
    recording = [];
    $('.rec-msg').textContent = t('recording');
    setTimeout(() => {
      const vs = recording; recording = null;
      if (vs.length < 5) { $('.rec-msg').textContent = sign.hands.length > 1 ? t('showTwo') : t('showHands'); return; }
      const avg = vs[0].map((_, i) => vs.reduce((a, v) => a + v[i], 0) / vs.length);
      templates[sign.id] = [...(templates[sign.id] || []), avg].slice(-3);
      LS.set('templates', templates);
      $('.rec-msg').textContent = t('recorded');
      $('#clr').hidden = false;
    }, 1500);
  };
  $('#clr').onclick = () => { delete templates[sign.id]; LS.set('templates', templates); $('#clr').hidden = true; $('.rec-msg').textContent = t('recordHelp'); };

  const onKey = e => {
    if (e.target.tagName === 'INPUT') return;
    if (e.key === 'ArrowRight' && next) location.hash = `#learn/${next.id}`;
    if (e.key === 'ArrowLeft' && prev) location.hash = `#learn/${prev.id}`;
  };
  window.addEventListener('keydown', onKey);
  return () => { off(); stopAnim(); clearTimeout(advanceTimer); window.removeEventListener('keydown', onKey); cam.highlight = null; };
}

// ---------------------------------------------------------------- practice
function buildQueue(lang, mode, level, only) {
  const words = [];
  if (mode === 'signs') {
    const list = signsFor(lang, 'word');
    for (let i = 0; i < 80; i++) words.push([{ sign: list[Math.floor(Math.random() * list.length)] }]);
    for (let i = 1; i < words.length; i++) if (words[i][0].sign === words[i - 1][0].sign) words[i][0].sign = list[(list.indexOf(words[i][0].sign) + 1) % list.length];
    return words.map(w => w.map(u => ({ ...u, label: L(u.sign.name) })));
  }
  if (mode === 'letters') {
    const pool = only?.length ? only : 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');
    const levelPool = !only && level === 'easy' ? (lang === 'isl' ? 'ACEILMNOUV' : 'ABCDEILOUVWY').split('') : pool;
    let last = '';
    for (let w = 0; w < 60; w++) {
      const word = [];
      for (let k = 0; k < 4; k++) {
        let ch; do { ch = levelPool[Math.floor(Math.random() * levelPool.length)]; } while (ch === last && levelPool.length > 1);
        last = ch; word.push(ch);
      }
      words.push(word);
    }
  } else {
    const list = practiceWords(lang, level);
    let prev = '';
    for (let i = 0; i < 60; i++) {
      let w; do { w = list[Math.floor(Math.random() * list.length)]; } while (w === prev && list.length > 1);
      prev = w; words.push(w.toUpperCase().split(''));
    }
  }
  return words.map(w => w.map(ch => ({ ch, sign: letterSign(lang, ch), label: ch })));
}

function viewPractice() {
  const lang = settings.signLang;
  let phase = 'setup';
  let state = null;
  let timers = [];
  let offFrame = null;
  const clearTimers = () => { timers.forEach(clearTimeout); timers.forEach(clearInterval); timers = []; };

  const setup = (only) => {
    phase = 'setup';
    clearTimers();
    const opt = (name, val, label) => `<label class="seg-opt"><input type="radio" name="${name}" value="${val}" ${String(settings[name]) === String(val) ? 'checked' : ''}><span>${label}</span></label>`;
    main.innerHTML = `
    <div class="page-head"><h1>${t('practice')} · ${LANGS[lang].short}</h1></div>
    <div class="practice-setup card">
      <div class="field"><div class="flabel">${t('mode')}</div><div class="seg">${opt('mode', 'letters', t('modeLetters'))}${opt('mode', 'words', t('modeWords'))}${opt('mode', 'signs', t('modeSigns'))}</div></div>
      <div class="field lvl"><div class="flabel">${t('level')}</div><div class="seg">${opt('level', 'easy', t('easy'))}${opt('level', 'medium', t('medium'))}${opt('level', 'hard', t('hard'))}</div></div>
      <div class="field"><div class="flabel">${t('duration')}</div><div class="seg">${[30, 60, 120].map(d => opt('duration', d, d + ' ' + t('sec'))).join('')}</div></div>
      <label class="check"><input type="checkbox" id="hints" ${settings.hints ? 'checked' : ''}> ${t('hints')}</label>
      ${only ? `<p class="note">${t('practiseWeak')}: <b>${only.join(' ')}</b></p>` : ''}
      <button class="btn primary big" id="go">${t('start')} ▶</button>
    </div>
    <div class="practice-cam-preview card"><div class="cam-slot"></div><ul class="tips-inline">${t('tips').map(x => `<li>${esc(x)}</li>`).join('')}</ul></div>`;
    main.querySelectorAll('input[type=radio]').forEach(r => r.onchange = () => { settings[r.name] = r.name === 'duration' ? +r.value : r.value; saveSettings(); });
    $('#hints').onchange = e => { settings.hints = e.target.checked; saveSettings(); };
    $('#go').onclick = () => run(only);
    mountCamera($('.cam-slot'));
  };

  const run = (only) => {
    phase = 'run';
    const queue = buildQueue(lang, settings.mode, settings.level, only);
    state = { queue, w: 0, u: 0, log: [], started: 0, shownAt: 0, ends: 0, letters: 0, wordsDone: 0, skips: 0 };
    main.innerHTML = `
    <div class="run">
      <div class="run-top">
        <div class="kpi"><b id="kTime">${settings.duration}</b><span>${t('time')}</span></div>
        <div class="kpi"><b id="kWpm">0</b><span>${settings.mode === 'signs' ? t('spm') : t('wpm')}</span></div>
        <div class="kpi"><b id="kOk">0</b><span>${t('wordsDone')}</span></div>
        <div class="kpi"><b id="kAcc">100%</b><span>${t('acc')}</span></div>
        <div class="run-actions"><button class="btn sm" id="skip">${t('skip')}</button><button class="btn sm ghost" id="stop">${t('stop')}</button></div>
      </div>
      <div class="stream" id="stream"></div>
      <div class="run-main">
        <div class="target card"><div class="tlabel">${t('target')}</div><div class="tbig" id="tbig"></div><div class="tsub" id="tsub"></div><div class="hint-wrap" id="hint"></div></div>
        <div class="card run-cam"><div class="cam-slot"></div>${meterHTML()}<ul class="feedback"></ul></div>
      </div>
      <div class="countdown" id="cd">${t('get')}</div>
      <p class="muted center">${t('keyHint')}</p>
    </div>`;
    mountCamera($('.cam-slot'));
    const matcher = new Matcher(() => settings, sid => templates[sid]);
    const root = $('.run-cam');
    let frameN = 0, stopHint = null;

    const cur = () => state.queue[state.w]?.[state.u];
    const drawStream = () => {
      const html = [];
      for (let i = Math.max(0, state.w - 1); i < Math.min(state.queue.length, state.w + 6); i++) {
        const word = state.queue[i];
        const units = word.map((u, j) => {
          let cls = 'u';
          if (i < state.w || (i === state.w && j < state.u)) cls += u.skipped ? ' skip' : ' done';
          else if (i === state.w && j === state.u) cls += ' cur';
          return `<span class="${cls}">${esc(u.label)}</span>`;
        }).join(settings.mode === 'signs' ? '' : '');
        html.push(`<span class="word ${i < state.w ? 'past' : ''} ${i === state.w ? 'now' : ''}">${units}</span>`);
      }
      $('#stream').innerHTML = html.join('');
    };
    const showTarget = () => {
      const u = cur();
      if (!u) return;
      $('#tbig').textContent = u.label;
      $('#tsub').textContent = settings.mode === 'signs' ? (settings.uiLang === 'hi' ? u.sign.name.en : u.sign.name.hi) : (settings.mode === 'words' ? state.queue[state.w].map(x => x.ch).join('') : '');
      if (stopHint) stopHint();
      $('#hint').innerHTML = '';
      const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      svg.setAttribute('class', 'sign-svg');
      $('#hint').appendChild(svg);
      stopHint = animateSign(svg, u.sign);
      $('#hint').classList.toggle('hidden', !settings.hints);
      cam.highlight = (u.sign.touch || []).map(x => x[1]);
      matcher.reset(u.sign);
      state.shownAt = performance.now();
      drawStream();
    };
    const advance = (ok) => {
      const u = cur();
      const now = performance.now();
      state.log.push({ id: u.sign.id, ms: now - state.shownAt, ok });
      if (!ok) { u.skipped = true; state.skips++; } else state.letters++;
      const prevSign = u.sign;
      state.u++;
      if (state.u >= state.queue[state.w].length) { state.u = 0; state.w++; if (ok) state.wordsDone++; }
      if (!cur()) return finish();
      showTarget();
      if (ok && cur().sign === prevSign) matcher.lock(now); // double letter: release first
      updateKpis();
    };
    const elapsedMin = () => Math.max(1 / 60, (Math.min(performance.now(), state.ends) - state.started) / 60000);
    const calc = () => {
      const min = elapsedMin();
      const correct = state.log.filter(x => x.ok).length;
      const total = state.log.length;
      const wpm = settings.mode === 'signs' ? correct / min : (state.letters + state.wordsDone) / 5 / min;
      return { wpm, spm: correct / min, acc: total ? correct / total : 1, correct, total };
    };
    const updateKpis = () => {
      const c = calc();
      $('#kWpm').textContent = (settings.mode === 'signs' ? c.spm : c.wpm).toFixed(1);
      $('#kOk').textContent = settings.mode === 'signs' ? c.correct : state.letters;
      $('#kAcc').textContent = Math.round(c.acc * 100) + '%';
    };

    // countdown then start
    let n = 3;
    const cd = $('#cd');
    const tickCd = () => {
      if (phase !== 'run') return;
      if (n > 0) { cd.textContent = n; cd.classList.remove('pop'); void cd.offsetWidth; cd.classList.add('pop'); n--; timers.push(setTimeout(tickCd, 800)); return; }
      cd.remove();
      state.started = performance.now();
      state.ends = state.started + settings.duration * 1000;
      showTarget();
      timers.push(setInterval(() => {
        const left = Math.max(0, (state.ends - performance.now()) / 1000);
        $('#kTime').textContent = Math.ceil(left);
        updateKpis();
        if (left <= 0) finish();
      }, 200));
      offFrame = cam.onFrame(frame => {
        if (phase !== 'run' || !cur()) return;
        frameN++;
        const r = matcher.update(frame, frame.now, settings.uiLang);
        updateMeter(root, r);
        if (frameN % 3 === 0) $('.feedback', root).innerHTML = feedbackHTML(r);
        if (!settings.hints) $('#hint').classList.toggle('hidden', performance.now() - state.shownAt < 5000);
        if (r.accepted) { $('.tbig').classList.remove('flash'); void $('.tbig').offsetWidth; $('.tbig').classList.add('flash'); advance(true); }
      });
    };
    timers.push(setTimeout(tickCd, 500));
    drawStream();
    $('#skip').onclick = () => state.started && cur() && advance(false);
    $('#stop').onclick = () => (state.started ? finish() : setup(only));
    state.stopHint = () => stopHint && stopHint();
  };

  const finish = () => {
    if (phase !== 'run') return;
    phase = 'done';
    clearTimers();
    if (offFrame) { offFrame(); offFrame = null; }
    state.stopHint?.();
    cam.highlight = null;
    state.ends = Math.min(state.ends || performance.now(), performance.now());
    const min = Math.max(1 / 60, (state.ends - state.started) / 60000);
    const correct = state.log.filter(x => x.ok).length;
    const total = state.log.length;
    const wpm = settings.mode === 'signs' ? correct / min : (state.letters + state.wordsDone) / 5 / min;
    const res = { ts: Date.now(), lang, mode: settings.mode, level: settings.level, dur: Math.round(min * 60), wpm, spm: correct / min, acc: total ? correct / total : 1, n: correct, skips: state.skips };
    const prevBest = Math.max(0, ...history.filter(h => h.lang === lang && h.mode === res.mode).map(h => h.wpm));
    if (total > 0) {
      history.push(res); LS.set('history', history.slice(-300));
      for (const x of state.log) {
        const s = signStats[x.id] || { n: 0, ms: 0, miss: 0 };
        if (x.ok) { s.n++; s.ms += x.ms; } else s.miss = (s.miss || 0) + 1;
        signStats[x.id] = s;
        if (x.ok) progress[x.id] = { ok: (progress[x.id]?.ok || 0) + 1 };
      }
      LS.set('signStats', signStats); LS.set('progress', progress);
    }
    // weakest signs in this run
    const per = {};
    for (const x of state.log) { const p = per[x.id] || (per[x.id] = { ms: 0, n: 0, miss: 0 }); p.n++; p.ms += x.ok ? x.ms : 8000; if (!x.ok) p.miss++; }
    const weak = Object.entries(per).map(([id, p]) => ({ id, avg: p.ms / p.n, miss: p.miss })).sort((a, b) => b.avg - a.avg).slice(0, 6);
    const isBest = total > 0 && res.wpm > prevBest;
    main.innerHTML = `
    <div class="page-head"><h1>${t('results')}</h1></div>
    <div class="results card">
      ${isBest ? `<div class="best-banner">${t('newBest')}</div>` : ''}
      <div class="res-kpis">
        <div class="kpi hero-kpi"><b>${(settings.mode === 'signs' ? res.spm : res.wpm).toFixed(1)}</b><span>${settings.mode === 'signs' ? t('spm') : t('wpm')}</span></div>
        <div class="kpi"><b>${res.spm.toFixed(1)}</b><span>${t('spm')}</span></div>
        <div class="kpi"><b>${Math.round(res.acc * 100)}%</b><span>${t('acc')}</span></div>
        <div class="kpi"><b>${res.n}</b><span>${t('wordsDone')}</span></div>
        <div class="kpi"><b>${res.skips}</b><span>${t('skipped')}</span></div>
        <div class="kpi"><b>${Math.max(prevBest, res.wpm).toFixed(1)}</b><span>${t('best')}</span></div>
      </div>
      ${weak.length ? `<h3>${t('slowest')}</h3><div class="weak">${weak.map(w => `<a class="tile" href="#learn/${w.id}" data-id="${w.id}"><span class="tile-art"></span><b>${esc(BY_ID[w.id].cat === 'alpha' ? BY_ID[w.id].char : L(BY_ID[w.id].name))}</b><small>${(w.avg / 1000).toFixed(1)}s${w.miss ? ' · ' + w.miss + '✗' : ''}</small></a>`).join('')}</div>` : ''}
      <div class="hero-actions">
        <button class="btn primary big" id="again">↻ ${t('restart')}</button>
        ${weak.length && settings.mode !== 'signs' ? `<button class="btn big" id="weak">${t('practiseWeak')}</button>` : ''}
        <a class="btn big ghost" href="#progress">${t('progress')} →</a>
      </div>
    </div>`;
    main.querySelectorAll('.weak .tile').forEach(el => el.querySelector('.tile-art').appendChild(signSVG(BY_ID[el.dataset.id], 'tile-svg')));
    $('#again').onclick = () => setup();
    const wb = $('#weak');
    if (wb) wb.onclick = () => { settings.mode = 'letters'; setup(weak.map(w => BY_ID[w.id].char).filter(Boolean)); };
  };

  const onKey = e => {
    if (phase !== 'run' || !state) return;
    if (e.code === 'Space') { e.preventDefault(); if (state.started && state.queue[state.w]) advanceSkip(); }
    if (e.key === 'Escape') finish();
  };
  const advanceSkip = () => $('#skip')?.click();
  window.addEventListener('keydown', onKey);
  setup();
  return () => { phase = 'gone'; clearTimers(); if (offFrame) offFrame(); state?.stopHint?.(); window.removeEventListener('keydown', onKey); cam.highlight = null; };
}

// ---------------------------------------------------------------- progress
function viewProgress() {
  const lang = settings.signLang;
  const hist = history.filter(h => h.lang === lang);
  const all = signsFor(lang);
  const done = all.filter(s => isLearned(s.id)).length;
  const best = Math.max(0, ...hist.map(h => h.wpm));
  const letters = signsFor(lang, 'alpha').map(s => ({ s, st: signStats[s.id] })).filter(x => x.st && x.st.n).map(x => ({ ...x, avg: x.st.ms / x.st.n / 1000 }));
  letters.sort((a, b) => b.avg - a.avg);
  const maxAvg = Math.max(1, ...letters.map(x => x.avg));
  const fmtDate = ts => new Date(ts).toLocaleDateString(settings.uiLang === 'hi' ? 'hi-IN' : 'en-IN', { day: 'numeric', month: 'short' });
  const modeName = m => ({ letters: t('modeLetters'), words: t('modeWords'), signs: t('modeSigns') }[m] || m);
  main.innerHTML = `
  <div class="page-head"><h1>${t('progress')} · ${LANGS[lang].short}</h1></div>
  <section class="stats-row">
    <div class="stat"><b>${hist.length}</b><span>${t('sessions')}</span></div>
    <div class="stat"><b>${best ? best.toFixed(1) : '—'}</b><span>${t('bestWpm')}</span></div>
    <div class="stat"><b>${done}<small> / ${all.length}</small></b><span>${t('mastered')}</span></div>
  </section>
  ${hist.length ? `
  <section class="card"><div class="sec-head"><h2>${t('lastSessions')}</h2><label class="small"><input type="checkbox" id="asTable"> ${t('table')}</label></div>
    <div class="chart" id="chart"></div>
    <table class="tbl" id="tbl" hidden><thead><tr><th>${t('date')}</th><th>${t('mode')}</th><th>${t('wpm')}</th><th>${t('spm')}</th><th>${t('acc')}</th></tr></thead>
    <tbody>${hist.slice().reverse().map(h => `<tr><td>${fmtDate(h.ts)}</td><td>${modeName(h.mode)}</td><td>${h.wpm.toFixed(1)}</td><td>${h.spm.toFixed(1)}</td><td>${Math.round(h.acc * 100)}%</td></tr>`).join('')}</tbody></table>
  </section>` : `<section class="card"><p class="muted">${t('noHistory')}</p><a class="btn primary" href="#practice">${t('startPractice')} ⚡</a></section>`}
  ${letters.length ? `<section class="card"><h2>${t('letterSpeed')}</h2><div class="hbars">${letters.map(x => `
    <a class="hbar" href="#learn/${x.s.id}" title="${x.s.char}: ${x.avg.toFixed(2)}s"><span class="hl">${x.s.char}</span><span class="ht"><i style="width:${x.avg / maxAvg * 100}%"></i></span><span class="hv">${x.avg.toFixed(1)}</span></a>`).join('')}</div></section>` : ''}`;
  if (hist.length) {
    drawLineChart($('#chart'), hist.slice(-40).map((h, i) => ({ x: i + 1, y: h.wpm, label: `${fmtDate(h.ts)} · ${modeName(h.mode)}` })), t('wpm'));
    $('#asTable').onchange = e => { $('#tbl').hidden = !e.target.checked; $('#chart').hidden = e.target.checked; };
  }
}

function drawLineChart(el, data, yName) {
  const W = 720, H = 240, m = { l: 40, r: 16, t: 16, b: 28 };
  const ymax = Math.max(1, ...data.map(d => d.y)) * 1.15;
  const nice = ymax <= 5 ? 1 : ymax <= 12 ? 2 : ymax <= 30 ? 5 : 10;
  const top = Math.ceil(ymax / nice) * nice;
  const xs = i => m.l + (data.length === 1 ? (W - m.l - m.r) / 2 : i * (W - m.l - m.r) / (data.length - 1));
  const ys = v => m.t + (1 - v / top) * (H - m.t - m.b);
  let grid = '';
  for (let v = 0; v <= top; v += nice) grid += `<line class="grid" x1="${m.l}" x2="${W - m.r}" y1="${ys(v)}" y2="${ys(v)}"/><text class="axis" x="${m.l - 8}" y="${ys(v) + 4}" text-anchor="end">${v}</text>`;
  const pts = data.map((d, i) => `${xs(i)},${ys(d.y)}`).join(' ');
  el.innerHTML = `<svg viewBox="0 0 ${W} ${H}" class="line-chart" role="img" aria-label="${esc(yName)}">
    ${grid}<text class="axis" x="${W - m.r}" y="${H - 6}" text-anchor="end">#${data.length}</text><text class="axis" x="${m.l}" y="${H - 6}">#1</text>
    <polyline class="series" points="${pts}"/>
    ${data.map((d, i) => `<circle class="dot" cx="${xs(i)}" cy="${ys(d.y)}" r="4"/>`).join('')}
    <line class="cross" y1="${m.t}" y2="${H - m.b}" x1="-10" x2="-10"/><circle class="hdot" r="6" cx="-10" cy="-10"/>
    <rect class="hit" x="${m.l}" y="0" width="${W - m.l - m.r}" height="${H}"/></svg><div class="tip" hidden></div>`;
  const svg = el.querySelector('svg'), tip = el.querySelector('.tip');
  const hit = svg.querySelector('.hit');
  hit.addEventListener('mousemove', e => {
    const r = svg.getBoundingClientRect();
    const x = (e.clientX - r.left) / r.width * W;
    let bi = 0, bd = Infinity;
    data.forEach((d, i) => { const dd = Math.abs(xs(i) - x); if (dd < bd) { bd = dd; bi = i; } });
    const d = data[bi];
    svg.querySelector('.cross').setAttribute('x1', xs(bi)); svg.querySelector('.cross').setAttribute('x2', xs(bi));
    svg.querySelector('.hdot').setAttribute('cx', xs(bi)); svg.querySelector('.hdot').setAttribute('cy', ys(d.y));
    tip.hidden = false;
    tip.innerHTML = `<b>${d.y.toFixed(1)} ${esc(yName)}</b><span>${esc(d.label)}</span>`;
    const px = xs(bi) / W * r.width, py = ys(d.y) / H * r.height;
    tip.style.left = Math.min(r.width - 150, Math.max(0, px - 70)) + 'px';
    tip.style.top = Math.max(0, py - 58) + 'px';
  });
  hit.addEventListener('mouseleave', () => { tip.hidden = true; svg.querySelector('.cross').setAttribute('x1', -10); svg.querySelector('.cross').setAttribute('x2', -10); svg.querySelector('.hdot').setAttribute('cy', -10); });
}

// ---------------------------------------------------------------- settings
function viewSettings() {
  main.innerHTML = `
  <div class="page-head"><h1>${t('settings')}</h1></div>
  <section class="card settings">
    <label class="field"><span>${t('hold')}: <b id="holdV">${settings.holdMs} ms</b></span><input type="range" id="hold" min="150" max="1200" step="50" value="${settings.holdMs}"></label>
    <label class="field"><span>${t('strict')}: <b id="thrV">${Math.round(settings.threshold * 100)}%</b></span>
      <div class="range-row"><small>${t('lenient')}</small><input type="range" id="thr" min="0.5" max="0.9" step="0.01" value="${settings.threshold}"><small>${t('strictL')}</small></div></label>
    <label class="check"><input type="checkbox" id="lh" ${settings.leftHanded ? 'checked' : ''}> ${t('leftHanded')}</label>
    <label class="check"><input type="checkbox" id="sw" ${settings.swapHands ? 'checked' : ''}> ${t('swap')}</label>
    <label class="check"><input type="checkbox" id="sk" ${settings.skeleton ? 'checked' : ''}> ${t('skeleton')}</label>
    <hr><button class="btn danger" id="reset">${t('reset')}</button>
  </section>`;
  $('#hold').oninput = e => { settings.holdMs = +e.target.value; $('#holdV').textContent = settings.holdMs + ' ms'; saveSettings(); };
  $('#thr').oninput = e => { settings.threshold = +e.target.value; $('#thrV').textContent = Math.round(settings.threshold * 100) + '%'; saveSettings(); };
  $('#lh').onchange = e => { settings.leftHanded = e.target.checked; saveSettings(); };
  $('#sw').onchange = e => { settings.swapHands = e.target.checked; saveSettings(); };
  $('#sk').onchange = e => { settings.skeleton = e.target.checked; saveSettings(); };
  $('#reset').onclick = () => {
    if (!confirm(t('resetConfirm'))) return;
    for (const k of ['progress', 'history', 'signStats', 'templates']) LS.set(k, k === 'history' ? [] : {});
    location.reload();
  };
}

render();
