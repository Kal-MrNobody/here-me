// Consistency test: every sign's ideal pose must score as a match for itself,
// and should be distinguishable from the other signs of the same group.
import { SIGNS } from '../js/signs.js';
import { syntheticLandmarks } from '../js/handmodel.js';
import { handFeatures, scoreSign } from '../js/recognizer.js';

const THR = 0.72;
let fail = 0, confusions = [];
const frameOf = s => { const syn = syntheticLandmarks(s); const hands = {}; for (const k in syn) hands[k] = handFeatures(syn[k], k === 'L'); return { hands }; };
for (const lang of ['asl', 'isl']) for (const cat of ['alpha', 'word']) {
  const group = SIGNS.filter(s => s.lang === lang && s.cat === cat);
  for (const s of group) {
    const fr = frameOf(s);
    const self = scoreSign(s, fr).score;
    if (self < 0.95) { fail++; console.log(`FAIL self ${s.id}: ${self.toFixed(2)}`); }
    for (const o of group) {
      if (o === s) continue;
      const sc = scoreSign(o, fr).score;
      if (sc >= THR) confusions.push(`${s.id} looks like ${o.id} (${sc.toFixed(2)})`);
    }
  }
}
console.log(confusions.length ? 'Confusable pairs (ideal pose scores above threshold):\n  ' + confusions.join('\n  ') : 'No confusions');
console.log(fail ? `${fail} self-match failures` : 'All signs match their own ideal pose');
process.exit(fail ? 1 : 0);
