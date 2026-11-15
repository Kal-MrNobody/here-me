// Sign library.
// ASL = American Sign Language (English). ISL = Indian Sign Language (used by the Deaf community in India),
// shown here with Hindi names/translations.
//
// Each sign:
//   hands  : hand placement used to draw it AND to build the recognizer's reference pose
//   motion : optional movement (drawn as an animated arrow; required by the recognizer unless demo:true)
//   touch  : contact points that must be close on camera, e.g. ['R8','L4', tolerance]
//            landmark numbers follow MediaPipe: 4 thumb tip, 8 index tip, 12 middle tip, 16 ring tip, 20 little tip,
//            0 wrist, 'palm' = palm centre. R = your dominant (right) hand, L = the other hand.
//   rec    : recognizer options {orient, looseL, nearest}

const P = (f, t, sp = 'tog', extra = {}) => ({ f, t, sp, ...extra });

// ---------- common building blocks ----------
const one = (pose, rot = [0, 0, 0]) => [{ side: 'R', pose, rot, at: [0, 0] }];
const L_OPEN = { side: 'L', pose: P('XXXX', 'five', 'open'), rot: [0, 0, -35], at: [0, 0] };
const L_FLAT = { side: 'L', pose: P('XXXX', 'par'), rot: [0, 0, -10], at: [0, 0] };
const L_INDEX = { side: 'L', pose: P('XCCC', 'across'), rot: [0, 0, 0], at: [0, 0] };
const R_POINT_TO = (to, rz = 135) => ({ side: 'R', pose: P('XCCC', 'across'), rot: [0, 0, rz], attach: { pt: 8, to: ['L', to], off: [0.05, 0] } });
const TAP = { hand: 'R', path: [[0.28, -0.22], [0, 0]], dur: 650, demo: true };
const TAP_DOWN = { hand: 'R', path: [[0.2, 0.3], [0, 0]], dur: 650, demo: true };

const T = (en, hi) => ({ en, hi });

// =========================================================================================
// ASL alphabet
// =========================================================================================
const ASL_ALPHA = [
  ['A', one(P('CCCC', 'side')), T(['Make a fist.', 'Rest your thumb straight up against the side of your index finger.'],
    ['मुट्ठी बनाएँ।', 'अँगूठे को सीधा तर्जनी (पहली उंगली) के बगल में रखें।'])],
  ['B', one(P('XXXX', 'tuck')), T(['Hold four fingers straight up, pressed together.', 'Fold your thumb across your palm.'],
    ['चारों उंगलियाँ सीधी ऊपर, आपस में सटी हुई।', 'अँगूठा हथेली पर मोड़ लें।'])],
  ['C', one(P('cccc', 'curve'), [0, -70, 0]), T(['Curve your fingers and thumb into the shape of a C.', 'Turn your palm to the side so the C is visible.'],
    ['उंगलियों और अँगूठे को मोड़कर C का आकार बनाएँ।', 'हथेली बगल की ओर रखें ताकि C दिखे।'])],
  ['D', one(P('Xooo', 'touch12')), T(['Point your index finger straight up.', 'Touch the other fingertips to your thumb to make a circle.'],
    ['तर्जनी सीधी ऊपर करें।', 'बाकी उंगलियों के सिरे अँगूठे से मिलाकर गोला बनाएँ।'])],
  ['E', one(P('eeee', 'under')), T(['Bend all four fingers down tightly.', 'Tuck your thumb under the fingertips.'],
    ['चारों उंगलियाँ नीचे की ओर कसकर मोड़ें।', 'अँगूठा उंगलियों के सिरों के नीचे रखें।'])],
  ['F', one(P('oXXX', 'touch8', [3, 4, -10, -22])), T(['Touch your index fingertip to your thumb tip.', 'Keep the other three fingers straight and spread.'],
    ['तर्जनी के सिरे को अँगूठे के सिरे से मिलाएँ।', 'बाकी तीन उंगलियाँ सीधी और फैली हुई रखें।'])],
  ['G', one(P('XCCC', 'par'), [0, 180, 90]), T(['Point your index finger sideways.', 'Your thumb points the same way, just above it. Other fingers closed.'],
    ['तर्जनी को बगल की ओर इशारा करें।', 'अँगूठा भी उसी दिशा में, उसके ठीक ऊपर। बाकी उंगलियाँ बंद।']), { orientW: 1.4 }],
  ['H', one(P('XXCC', 'tuck'), [0, 180, 90]), T(['Point your index and middle fingers sideways, together.', 'Other fingers closed.'],
    ['तर्जनी और मध्यमा को साथ में बगल की ओर करें।', 'बाकी उंगलियाँ बंद।']), { orientW: 1.4 }],
  ['I', one(P('CCCX', 'across')), T(['Make a fist.', 'Raise only your little finger.'],
    ['मुट्ठी बनाएँ।', 'सिर्फ़ छोटी उंगली (कनिष्ठा) ऊपर उठाएँ।'])],
  ['J', one(P('CCCX', 'across')), T(['Make the letter I (little finger up).', 'Draw a J in the air with your little finger: down, then hook.'],
    ['I का आकार बनाएँ (छोटी उंगली ऊपर)।', 'छोटी उंगली से हवा में J बनाएँ: नीचे, फिर मोड़।']),
    { motion: { hand: 'R', ref: 20, path: [[0, 0], [0, -0.6], [-0.15, -0.85], [-0.45, -0.75]], dur: 1300 }, rec: { motionPt: 20, motionMin: 0.6 } }],
  ['K', one(P('XXCC', 'k', 'v')), T(['Raise index and middle fingers in a V.', 'Put your thumb tip between them, against the middle finger.'],
    ['तर्जनी और मध्यमा से V बनाएँ।', 'अँगूठे का सिरा दोनों के बीच, मध्यमा से लगाएँ।'])],
  ['L', one(P('XCCC', 'out')), T(['Index finger up, thumb straight out to the side: an L.', 'Other fingers closed.'],
    ['तर्जनी ऊपर, अँगूठा बगल में सीधा: L का आकार।', 'बाकी उंगलियाँ बंद।'])],
  ['M', one(P('CCCC', 'm')), T(['Fold your thumb under the first three fingers.', 'The thumb tip peeks out between the ring and little finger.'],
    ['अँगूठे को पहली तीन उंगलियों के नीचे दबाएँ।', 'अँगूठे का सिरा अनामिका और छोटी उंगली के बीच से दिखे।'])],
  ['N', one(P('CCCC', 'n')), T(['Fold your thumb under the first two fingers.', 'The thumb tip peeks out between the middle and ring finger.'],
    ['अँगूठे को पहली दो उंगलियों के नीचे दबाएँ।', 'अँगूठे का सिरा मध्यमा और अनामिका के बीच से दिखे।'])],
  ['O', one(P('oooo', 'touch8'), [0, -60, 0]), T(['Curve all fingers so the tips touch your thumb tip.', 'It looks like the letter O from the side.'],
    ['सभी उंगलियाँ मोड़कर उनके सिरे अँगूठे से मिलाएँ।', 'बगल से देखने पर O जैसा दिखे।'])],
  ['P', one(P('XXCC', 'k', 'v'), [0, 0, 165]), T(['Make the letter K.', 'Turn your hand so the fingers point down.'],
    ['K का आकार बनाएँ।', 'हाथ घुमाएँ ताकि उंगलियाँ नीचे की ओर हों।']), { orientW: 1.4 }],
  ['Q', one(P('XCCC', 'par'), [0, 0, 180]), T(['Make the letter G.', 'Point the index finger and thumb down.'],
    ['G का आकार बनाएँ।', 'तर्जनी और अँगूठा नीचे की ओर करें।']), { orientW: 1.4 }],
  ['R', one(P('XXCC', 'tuck', 'cross', { zoff: [0.08, 0, 0, 0] })), T(['Cross your index and middle fingers.', 'Other fingers closed.'],
    ['तर्जनी और मध्यमा को एक-दूसरे पर क्रॉस करें।', 'बाकी उंगलियाँ बंद।']), { rec: { crossed: true } }],
  ['S', one(P('CCCC', 'across')), T(['Make a fist.', 'Wrap your thumb across the front of your fingers.'],
    ['मुट्ठी बनाएँ।', 'अँगूठा उंगलियों के सामने से लपेटें।'])],
  ['T', one(P('CCCC', 't')), T(['Make a fist.', 'Tuck your thumb between your index and middle fingers.'],
    ['मुट्ठी बनाएँ।', 'अँगूठा तर्जनी और मध्यमा के बीच दबाएँ।'])],
  ['U', one(P('XXCC', 'tuck')), T(['Index and middle fingers straight up, together.', 'Other fingers closed.'],
    ['तर्जनी और मध्यमा सीधी ऊपर, साथ में।', 'बाकी उंगलियाँ बंद।'])],
  ['V', one(P('XXCC', 'tuck', 'v')), T(['Index and middle fingers up and apart (a V).', 'Other fingers closed.'],
    ['तर्जनी और मध्यमा ऊपर और अलग-अलग (V)।', 'बाकी उंगलियाँ बंद।'])],
  ['W', one(P('XXXC', 'tuck', 'w')), T(['Raise index, middle and ring fingers, spread apart.', 'Thumb holds down the little finger.'],
    ['तर्जनी, मध्यमा और अनामिका ऊपर, फैली हुई।', 'अँगूठा छोटी उंगली को दबाए।'])],
  ['X', one(P('HCCC', 'across')), T(['Raise your index finger and bend it into a hook.', 'Other fingers closed.'],
    ['तर्जनी उठाकर हुक की तरह मोड़ें।', 'बाकी उंगलियाँ बंद।'])],
  ['Y', one(P('CCCX', 'out', 'y')), T(['Stick out your thumb and little finger.', 'Other fingers closed (like a "call me" sign).'],
    ['अँगूठा और छोटी उंगली बाहर निकालें।', 'बाकी उंगलियाँ बंद ("फ़ोन करना" जैसा)।'])],
  ['Z', one(P('XCCC', 'across')), T(['Point with your index finger.', 'Draw a Z in the air: across, diagonally down, across.'],
    ['तर्जनी से इशारा करें।', 'हवा में Z बनाएँ: सीधा, तिरछा नीचे, फिर सीधा।']),
    { motion: { hand: 'R', ref: 8, path: [[0, 0], [0.7, 0], [0, -0.6], [0.7, -0.6]], dur: 1600 }, rec: { motionPt: 8, motionMin: 1.1 } }],
];

// =========================================================================================
// ISL alphabet (two-handed, common ISLRTC-style chart). R = dominant hand, L = other hand.
// =========================================================================================
const VOWEL = (target, en, hi) => ({
  hands: [L_OPEN, R_POINT_TO(target)], motion: TAP_DOWN, touch: [['R8', 'L' + target, 0.4]],
  rec: { looseL: true, nearest: { a: 'R8', set: [4, 8, 12, 16, 20], b: target }, orient: false }, en, hi,
});
const ON_PALM = (pose, pt, rz = 70) => ({ side: 'R', pose, rot: [0, 0, rz], attach: { pt, to: ['L', 'palm'], off: [0, 0] } });

const ISL_ALPHA = [
  ['A', VOWEL(4, ['Open your left hand, palm facing you.', 'Touch the tip of your left THUMB with your right index finger.'],
    ['बायाँ हाथ खुला रखें।', 'दाएँ हाथ की तर्जनी से बाएँ हाथ के अँगूठे का सिरा छुएँ।'])],
  ['B', { hands: [{ side: 'L', pose: P('oCCC', 'touch8'), rot: [0, 0, -25], at: [0, 0] },
    { side: 'R', pose: P('oCCC', 'touch8'), rot: [0, 0, 25], attach: { pt: 8, to: ['L', 8], off: [0.28, 0] } }],
  touch: [['R8', 'L8', 0.7]], rec: { orient: false },
  en: ['Make a ring with thumb and index finger on BOTH hands.', 'Put the two rings side by side, touching.'],
  hi: ['दोनों हाथों में अँगूठे और तर्जनी से छल्ला बनाएँ।', 'दोनों छल्लों को साथ मिलाएँ।'] }],
  ['C', { hands: one(P('cccc', 'curve'), [0, -70, 0]), en: ['One hand: curve your fingers and thumb into a C.'], hi: ['एक हाथ से उंगलियों और अँगूठे को मोड़कर C बनाएँ।'] }],
  ['D', { hands: [L_INDEX, { side: 'R', pose: P('cCCC', 'curve'), rot: [0, -70, 0], attach: { pt: 8, to: ['L', 7], off: [0.1, 0] } }],
    touch: [['R8', 'L7', 0.6]], rec: { orient: false },
    en: ['Left index finger points up.', 'Curve your right thumb and index finger into a half circle against it, like a D.'],
    hi: ['बाएँ हाथ की तर्जनी ऊपर।', 'दाएँ अँगूठे और तर्जनी से आधा गोला बनाकर उसके साथ लगाएँ (D जैसा)।'] }],
  ['E', VOWEL(8, ['Open your left hand.', 'Touch the tip of your left INDEX finger with your right index finger.'],
    ['बायाँ हाथ खुला रखें।', 'दाएँ हाथ की तर्जनी से बाएँ हाथ की तर्जनी का सिरा छुएँ।'])],
  ['F', { hands: [{ side: 'L', pose: P('XXCC', 'tuck'), rot: [0, 0, -80], at: [0, 0] },
    { side: 'R', pose: P('XXCC', 'tuck'), rot: [0, 0, 140], attach: { pt: 6, to: ['L', 6], off: [0, 0] } }],
  touch: [['R6', 'L6', 0.7]], rec: { orient: false },
  en: ['Index and middle fingers out on both hands.', 'Lay your right two fingers across your left two fingers.'],
  hi: ['दोनों हाथों में तर्जनी और मध्यमा बाहर।', 'दाएँ हाथ की दोनों उंगलियाँ बाएँ हाथ की उंगलियों के ऊपर आड़ी रखें।'] }],
  ['G', { hands: [{ side: 'L', pose: P('CCCC', 'across'), at: [0, 0] },
    { side: 'R', pose: P('CCCC', 'across'), attach: { pt: 0, to: ['L', 9], off: [0, 0.12] } }],
  touch: [['R0', 'L9', 0.7]], rec: { orient: false },
  en: ['Make fists with both hands.', 'Put your right fist on top of your left fist.'],
  hi: ['दोनों हाथों की मुट्ठी बनाएँ।', 'दाईं मुट्ठी को बाईं मुट्ठी के ऊपर रखें।'] }],
  ['H', { hands: [L_FLAT, { side: 'R', pose: P('XXXX', 'tuck'), rot: [0, 180, 80], attach: { pt: 'palm', to: ['L', 'palm'], off: [0.4, 0.1] } }],
    motion: { hand: 'R', ref: 9, path: [[0, 0], [-0.8, -0.1]], dur: 900 }, touch: [['Rpalm', 'Lpalm', 0.9]],
    rec: { orient: false, motionPt: 9, motionMin: 0.5, looseL: true },
    en: ['Hold your left hand flat.', 'Brush your right palm across your left palm.'],
    hi: ['बायाँ हाथ सपाट रखें।', 'दाईं हथेली को बाईं हथेली पर रगड़ते हुए ले जाएँ।'] }],
  ['I', VOWEL(12, ['Open your left hand.', 'Touch the tip of your left MIDDLE finger with your right index finger.'],
    ['बायाँ हाथ खुला रखें।', 'दाएँ हाथ की तर्जनी से बाएँ हाथ की मध्यमा का सिरा छुएँ।'])],
  ['J', { hands: [L_FLAT, ON_PALM(P('XCCC', 'across'), 8)],
    motion: { hand: 'R', ref: 8, path: [[0, 0], [0, -0.5], [0.2, -0.7], [0.4, -0.55]], dur: 1200 }, touch: [['R8', 'Lpalm', 0.7]],
    rec: { orient: false, motionPt: 8, motionMin: 0.5, looseL: true },
    en: ['Hold your left hand flat.', 'Draw a J on your left palm with your right index finger.'],
    hi: ['बायाँ हाथ सपाट रखें।', 'दाएँ हाथ की तर्जनी से बाईं हथेली पर J बनाएँ।'] }],
  ['K', { hands: [L_INDEX, { side: 'R', pose: P('HCCC', 'across'), rot: [0, 0, 60], attach: { pt: 8, to: ['L', 7], off: [0.06, 0] } }],
    touch: [['R8', 'L7', 0.55]], rec: { orient: false },
    en: ['Left index finger points up.', 'Hook your right index finger and rest it on the top of the left index.'],
    hi: ['बाएँ हाथ की तर्जनी ऊपर।', 'दाएँ हाथ की तर्जनी मोड़कर (हुक) बाईं तर्जनी के ऊपर रखें।'] }],
  ['L', { hands: [L_FLAT, ON_PALM(P('XCCC', 'across'), 8)], touch: [['R8', 'Lpalm', 0.6]], rec: { orient: false, looseL: true },
    en: ['Hold your left hand flat, palm facing you.', 'Place your straight right index finger on the left palm.'],
    hi: ['बायाँ हाथ सपाट, हथेली आपकी ओर।', 'दाएँ हाथ की सीधी तर्जनी बाईं हथेली पर रखें।'] }],
  ['M', { hands: [L_FLAT, ON_PALM(P('XXXC', 'tuck'), 12)], touch: [['R12', 'Lpalm', 0.6]], rec: { orient: false, looseL: true },
    en: ['Hold your left hand flat.', 'Place THREE right fingers (index, middle, ring) on the left palm.'],
    hi: ['बायाँ हाथ सपाट रखें।', 'दाएँ हाथ की तीन उंगलियाँ (तर्जनी, मध्यमा, अनामिका) बाईं हथेली पर रखें।'] }],
  ['N', { hands: [L_FLAT, ON_PALM(P('XXCC', 'tuck'), 12)], touch: [['R12', 'Lpalm', 0.6]], rec: { orient: false, looseL: true },
    en: ['Hold your left hand flat.', 'Place TWO right fingers (index, middle) on the left palm.'],
    hi: ['बायाँ हाथ सपाट रखें।', 'दाएँ हाथ की दो उंगलियाँ (तर्जनी, मध्यमा) बाईं हथेली पर रखें।'] }],
  ['O', VOWEL(16, ['Open your left hand.', 'Touch the tip of your left RING finger with your right index finger.'],
    ['बायाँ हाथ खुला रखें।', 'दाएँ हाथ की तर्जनी से बाएँ हाथ की अनामिका का सिरा छुएँ।'])],
  ['P', { hands: [L_INDEX, { side: 'R', pose: P('oCCC', 'touch8'), rot: [0, 0, 40], attach: { pt: 8, to: ['L', 6], off: [0.08, 0] } }],
    touch: [['R8', 'L6', 0.6]], rec: { orient: false },
    en: ['Left index finger points up.', 'Make a ring with right thumb and index and touch it to the left index finger.'],
    hi: ['बाएँ हाथ की तर्जनी ऊपर।', 'दाएँ अँगूठे और तर्जनी का छल्ला बनाकर बाईं तर्जनी से लगाएँ।'] }],
  ['Q', { hands: [L_FLAT, { side: 'R', pose: P('oCCC', 'touch8'), rot: [0, 0, -30], attach: { pt: 8, to: ['L', 0], off: [0.1, -0.12] } }],
    touch: [['R8', 'L0', 0.6]], rec: { orient: false, looseL: true },
    en: ['Hold your left hand flat.', 'Make a ring with right thumb and index and hook it under the heel of the left hand.'],
    hi: ['बायाँ हाथ सपाट रखें।', 'दाएँ अँगूठे और तर्जनी का छल्ला बनाकर बाएँ हाथ की कलाई के पास नीचे लगाएँ।'] }],
  ['R', { hands: [L_FLAT, ON_PALM(P('HCCC', 'across'), 8)], touch: [['R8', 'Lpalm', 0.6]], rec: { orient: false, looseL: true },
    en: ['Hold your left hand flat.', 'Curl your right index finger and rest the bent finger on the left palm.'],
    hi: ['बायाँ हाथ सपाट रखें।', 'दाएँ हाथ की तर्जनी मोड़कर बाईं हथेली पर रखें।'] }],
  ['S', { hands: [{ side: 'L', pose: P('CCCH', 'across'), at: [0, 0] },
    { side: 'R', pose: P('CCCH', 'across'), attach: { pt: 20, to: ['L', 20], off: [0.05, 0.05] } }],
  touch: [['R20', 'L20', 0.5]], rec: { orient: false },
  en: ['Make fists with both hands, little fingers bent.', 'Hook your two little fingers together.'],
  hi: ['दोनों हाथों की मुट्ठी, छोटी उंगली मुड़ी हुई।', 'दोनों छोटी उंगलियों को आपस में फँसाएँ।'] }],
  ['T', { hands: [L_FLAT, { side: 'R', pose: P('XCCC', 'across'), rot: [0, 0, 60], attach: { pt: 8, to: ['L', 0], off: [0.08, 0.06] } }],
    touch: [['R8', 'L0', 0.5]], rec: { orient: false, looseL: true },
    en: ['Hold your left hand flat.', 'Touch the bottom of your left palm (near the wrist) with your right index tip.'],
    hi: ['बायाँ हाथ सपाट रखें।', 'दाएँ हाथ की तर्जनी से बाईं हथेली के निचले हिस्से (कलाई के पास) को छुएँ।'] }],
  ['U', VOWEL(20, ['Open your left hand.', 'Touch the tip of your left LITTLE finger with your right index finger.'],
    ['बायाँ हाथ खुला रखें।', 'दाएँ हाथ की तर्जनी से बाएँ हाथ की छोटी उंगली का सिरा छुएँ।'])],
  ['V', { hands: one(P('XXCC', 'tuck', 'v')), en: ['One hand: index and middle fingers up and apart (V).'], hi: ['एक हाथ: तर्जनी और मध्यमा ऊपर और अलग (V)।'] }],
  ['W', { hands: [L_OPEN, { side: 'R', pose: P('XXXX', 'five', 'open'), rot: [0, 180, 0], attach: { pt: 'palm', to: ['L', 'palm'], off: [0.12, 0.25] } }],
    touch: [['Rpalm', 'Lpalm', 0.8]], rec: { orient: false },
    en: ['Spread the fingers of both hands.', 'Lock them together (interlace the fingers).'],
    hi: ['दोनों हाथों की उंगलियाँ फैलाएँ।', 'उंगलियों को आपस में फँसाएँ।'] }],
  ['X', { hands: [{ side: 'L', pose: P('XCCC', 'across'), rot: [0, 0, -40], at: [0, 0] },
    { side: 'R', pose: P('XCCC', 'across'), rot: [0, 0, 40], attach: { pt: 6, to: ['L', 6], off: [0, 0] } }],
  touch: [['R6', 'L6', 0.55]], rec: { orient: false },
  en: ['Point both index fingers up.', 'Cross them over each other to form an X.'],
  hi: ['दोनों हाथों की तर्जनी ऊपर करें।', 'दोनों को क्रॉस करके X बनाएँ।'] }],
  ['Y', { hands: one(P('CCCX', 'out', 'y')), en: ['One hand: thumb and little finger out, other fingers closed.', 'Some regions use a two-hand Y; this one-hand form is widely understood.'],
    hi: ['एक हाथ: अँगूठा और छोटी उंगली बाहर, बाकी बंद।', 'कुछ क्षेत्रों में दो हाथ वाला Y भी चलता है।'] }],
  ['Z', { hands: one(P('XCCC', 'across')), motion: { hand: 'R', ref: 8, path: [[0, 0], [0.7, 0], [0, -0.6], [0.7, -0.6]], dur: 1600 },
    rec: { motionPt: 8, motionMin: 1.1 },
    en: ['Point with your right index finger.', 'Draw a Z in the air.'], hi: ['दाएँ हाथ की तर्जनी से इशारा करें।', 'हवा में Z बनाएँ।'] }],
];

// =========================================================================================
// Words. Positions use a body outline: chin ≈ (0, 2.1), mouth ≈ (0, 2.6), forehead ≈ (0, 3.8), chest ≈ (0, 0.5).
// Your right side appears on the right (mirror view).
// =========================================================================================
const W = (id, name, hands, en, hi, extra = {}) => ({ id, name, hands, en, hi, body: true, ...extra });
const flat = P('XXXX', 'tuck');
const fist = P('CCCC', 'across');
const point = P('XCCC', 'across');
const flatO = P('oooo', 'touch8');
const L_PALM_UP = { side: 'L', pose: flat, rot: [-70, 0, -30], at: [-0.5, -0.1] };

const SHARED_WORDS = {
  thankyou: (name) => W('thankyou', name, [{ side: 'R', pose: flat, attach: { pt: 12, to: [0.05, 2.25] } }],
    ['Flat hand, fingertips touch your chin.', 'Move the hand forward and down towards the person.'],
    ['हाथ सपाट, उंगलियों के सिरे ठोड़ी पर।', 'हाथ को सामने वाले व्यक्ति की ओर आगे-नीचे ले जाएँ।'],
    { motion: { hand: 'R', ref: 12, path: [[0, 0], [0.4, -0.9]], dur: 1000 }, rec: { motionPt: 12, motionMin: 0.5 } }),
  yes: (name) => W('yes', name, [{ side: 'R', pose: fist, at: [1.3, 0.4] }],
    ['Make a fist.', 'Nod it up and down like a head nodding "yes".'],
    ['मुट्ठी बनाएँ।', 'उसे सिर हिलाने ("हाँ") की तरह ऊपर-नीचे हिलाएँ।'],
    { motion: { hand: 'R', ref: 5, path: [[0, 0], [0, -0.3], [0, 0], [0, -0.3], [0, 0]], dur: 1100 }, rec: { motionPt: 5, motionMin: 0.35 } }),
  help: (name) => W('help', name, [L_PALM_UP, { side: 'R', pose: P('CCCC', 'up'), attach: { pt: 0, to: ['L', 'palm'], off: [0, 0.1] } }],
    ['Left hand flat, palm up.', 'Rest your right fist (thumb up) on it and lift both hands together.'],
    ['बायाँ हाथ सपाट, हथेली ऊपर।', 'उस पर दाईं मुट्ठी (अँगूठा ऊपर) रखें और दोनों हाथ साथ ऊपर उठाएँ।'],
    { motion: { hand: 'R', ref: 4, path: [[0, 0], [0, 0.6]], dur: 900 }, motion2: { hand: 'L', ref: 8, path: [[0, 0], [0, 0.6]], dur: 900 },
      touch: [['R0', 'Lpalm', 0.8]], rec: { motionPt: 0, motionMin: 0.35, looseL: true } }),
  friend: (name) => W('friend', name, [{ side: 'L', pose: P('HCCC', 'across'), rot: [0, 0, -90], at: [-0.5, 0.6] },
    { side: 'R', pose: P('HCCC', 'across'), rot: [0, 0, 90], attach: { pt: 8, to: ['L', 8], off: [0, 0.05] } }],
  ['Hook your two index fingers together.', 'Then swap which one is on top.'],
  ['दोनों हाथों की तर्जनी को मोड़कर आपस में फँसाएँ।', 'फिर ऊपर-नीचे बदलें।'], { touch: [['R8', 'L8', 0.7]] }),
  me: (name) => W('me', name, [{ side: 'R', pose: point, rot: [0, 0, 160], attach: { pt: 8, to: [0.15, 0.6] } }],
    ['Point your index finger at your own chest.'], ['तर्जनी से अपनी छाती की ओर इशारा करें।']),
  you: (name) => W('you', name, [{ side: 'R', pose: point, rot: [60, 0, 0], at: [1.2, 0.4] }],
    ['Point your index finger at the person you are talking to.'], ['जिससे बात कर रहे हैं, उसकी ओर तर्जनी से इशारा करें।']),
  deaf: (name) => W('deaf', name, [{ side: 'R', pose: point, rot: [0, 0, 20], attach: { pt: 8, to: [0.95, 3.1] } }],
    ['Touch your index finger near your ear.', 'Then move it to touch near your mouth.'],
    ['तर्जनी को कान के पास लगाएँ।', 'फिर उसे मुँह के पास ले जाएँ।'],
    { motion: { hand: 'R', ref: 8, path: [[0, 0], [-0.75, -0.5]], dur: 900 }, rec: { motionPt: 8, motionMin: 0.45 } }),
};

const ASL_WORDS = [
  W('hello', T('HELLO', 'नमस्ते / हैलो'), [{ side: 'R', pose: flat, rot: [0, 0, 15], attach: { pt: 8, to: [0.75, 3.75] } }],
    ['Flat hand, fingertips near your forehead (like a salute).', 'Move the hand out and away from your head.'],
    ['सपाट हाथ, उंगलियाँ माथे के पास (सलाम जैसा)।', 'हाथ को सिर से दूर बाहर की ओर ले जाएँ।'],
    { motion: { hand: 'R', ref: 8, path: [[0, 0], [0.9, -0.15]], dur: 900 }, rec: { motionPt: 8, motionMin: 0.5 } }),
  SHARED_WORDS.thankyou(T('THANK YOU', 'धन्यवाद')),
  W('please', T('PLEASE', 'कृपया'), [{ side: 'R', pose: flat, rot: [0, 180, 80], attach: { pt: 'palm', to: [0.1, 0.5] } }],
    ['Flat hand on your chest.', 'Rub it in a circle.'], ['सपाट हाथ छाती पर रखें।', 'उसे गोल-गोल घुमाएँ।'],
    { motion: { hand: 'R', ref: 9, path: [[0, 0], [0.35, 0.3], [0.7, 0], [0.35, -0.3], [0, 0]], dur: 1400 }, rec: { motionPt: 9, motionMin: 0.7 } }),
  W('sorry', T('SORRY', 'माफ़ कीजिए'), [{ side: 'R', pose: P('CCCC', 'side'), rot: [0, 180, 0], attach: { pt: 'palm', to: [0.1, 0.5] } }],
    ['Make a fist (letter A).', 'Rub it in a circle on your chest.'], ['मुट्ठी बनाएँ (A)।', 'छाती पर गोल-गोल घुमाएँ।'],
    { motion: { hand: 'R', ref: 9, path: [[0, 0], [0.35, 0.3], [0.7, 0], [0.35, -0.3], [0, 0]], dur: 1400 }, rec: { motionPt: 9, motionMin: 0.7 } }),
  SHARED_WORDS.yes(T('YES', 'हाँ')),
  W('no', T('NO', 'नहीं'), [{ side: 'R', pose: P('ooCC', 'touch8'), rot: [0, -40, 0], at: [1.3, 0.6] }],
    ['Hold index and middle fingers out with the thumb.', 'Snap them closed onto the thumb, twice.'],
    ['तर्जनी, मध्यमा और अँगूठा बाहर।', 'उन्हें दो बार अँगूठे पर बंद करें (चोंच जैसा)।'], { motion: TAP }),
  W('iloveyou', T('I LOVE YOU', 'मैं तुमसे प्यार करता/करती हूँ'), [{ side: 'R', pose: P('XCCX', 'out', 'y'), at: [1.3, 0.6] }],
    ['Thumb, index finger and little finger out.', 'Middle and ring fingers closed. Palm faces the person.'],
    ['अँगूठा, तर्जनी और छोटी उंगली बाहर।', 'मध्यमा और अनामिका बंद। हथेली सामने वाले की ओर।'], { rec: { orient: true } }),
  W('good', T('GOOD', 'अच्छा'), [{ side: 'R', pose: flat, attach: { pt: 12, to: [0.05, 2.2] } }],
    ['Flat hand, fingertips at your chin.', 'Bring it down in front of you.'], ['सपाट हाथ, उंगलियाँ ठोड़ी पर।', 'हाथ नीचे सामने की ओर लाएँ।'],
    { motion: { hand: 'R', ref: 12, path: [[0, 0], [0.1, -1.1]], dur: 1000 }, rec: { motionPt: 12, motionMin: 0.5 } }),
  SHARED_WORDS.help(T('HELP', 'मदद')),
  W('name', T('NAME', 'नाम'), [{ side: 'L', pose: P('XXCC', 'tuck'), rot: [0, 0, -90], at: [-0.6, 0.4] },
    { side: 'R', pose: P('XXCC', 'tuck'), rot: [0, 0, 120], attach: { pt: 7, to: ['L', 7], off: [0, 0.05] } }],
  ['Index and middle fingers out on both hands (letter H).', 'Tap the right fingers on the left fingers twice.'],
  ['दोनों हाथों में तर्जनी और मध्यमा बाहर (H)।', 'दाईं उंगलियों से बाईं उंगलियों पर दो बार थपथपाएँ।'],
  { motion: { hand: 'R', ref: 8, path: [[0, 0.25], [0, 0]], dur: 500, demo: true }, touch: [['R7', 'L7', 0.7]] }),
  W('water', T('WATER', 'पानी'), [{ side: 'R', pose: P('XXXC', 'tuck', 'w'), attach: { pt: 8, to: [0.15, 2.2] } }],
    ['Make the letter W.', 'Tap your index finger on your chin twice.'], ['W का आकार बनाएँ।', 'तर्जनी से ठोड़ी पर दो बार थपथपाएँ।'], { motion: TAP }),
  W('eat', T('EAT / FOOD', 'खाना'), [{ side: 'R', pose: flatO, rot: [0, -60, 0], attach: { pt: 8, to: [0.1, 2.55] } }],
    ['Bunch your fingertips together with the thumb.', 'Tap them to your mouth a couple of times.'],
    ['उंगलियों के सिरे अँगूठे से मिलाएँ।', 'मुँह पर दो-तीन बार लगाएँ।'], { motion: TAP }),
  W('more', T('MORE', 'और'), [{ side: 'L', pose: flatO, rot: [0, 60, 0], at: [-0.5, 0.2] },
    { side: 'R', pose: flatO, rot: [0, -60, 0], attach: { pt: 8, to: ['L', 8], off: [0.12, 0] } }],
  ['Bunch the fingertips of both hands.', 'Tap the fingertips of both hands together.'],
  ['दोनों हाथों की उंगलियों के सिरे मिलाएँ।', 'दोनों हाथों के सिरों को आपस में थपथपाएँ।'],
  { motion: { hand: 'R', ref: 8, path: [[0.35, 0], [0, 0]], dur: 500, demo: true }, touch: [['R8', 'L8', 0.7]] }),
  SHARED_WORDS.friend(T('FRIEND', 'दोस्त')),
  W('stop', T('STOP', 'रुको'), [L_PALM_UP, { side: 'R', pose: flat, rot: [0, -80, 0], attach: { pt: 0, to: ['L', 'palm'], off: [0.15, 0.1] } }],
    ['Left hand flat, palm up.', 'Chop the edge of your right hand down onto the left palm.'],
    ['बायाँ हाथ सपाट, हथेली ऊपर।', 'दाएँ हाथ के किनारे से बाईं हथेली पर काटने की तरह मारें।'],
    { motion: { hand: 'R', ref: 12, path: [[0, 0.6], [0, 0]], dur: 500, demo: true }, touch: [['R0', 'Lpalm', 0.9]], rec: { looseL: true } }),
  SHARED_WORDS.deaf(T('DEAF', 'बधिर')),
  SHARED_WORDS.me(T('I / ME', 'मैं')),
  SHARED_WORDS.you(T('YOU', 'तुम / आप')),
  W('fine', T('FINE', 'ठीक'), [{ side: 'R', pose: P('XXXX', 'five', 'open'), attach: { pt: 4, to: [0.15, 0.7] } }],
    ['Open hand, fingers spread.', 'Tap your thumb on your chest.'], ['हाथ खुला, उंगलियाँ फैली हुई।', 'अँगूठे से छाती पर थपथपाएँ।'], { motion: TAP }),
];

const ISL_WORDS = [
  W('namaste', T('NAMASTE / HELLO', 'नमस्ते'), [{ side: 'L', pose: flat, rot: [0, 80, 0], at: [-0.1, 0.5] },
    { side: 'R', pose: flat, rot: [0, -80, 0], attach: { pt: 'palm', to: ['L', 'palm'], off: [0.12, 0] } }],
  ['Press both palms together in front of your chest.', 'Fingers point up. You may bow your head slightly.'],
  ['दोनों हथेलियाँ छाती के सामने जोड़ें।', 'उंगलियाँ ऊपर की ओर। सिर थोड़ा झुका सकते हैं।'], { touch: [['Rpalm', 'Lpalm', 0.8]] }),
  SHARED_WORDS.thankyou(T('THANK YOU', 'धन्यवाद / शुक्रिया')),
  SHARED_WORDS.yes(T('YES', 'हाँ')),
  W('no', T('NO', 'नहीं'), [{ side: 'R', pose: flat, at: [1.3, 0.6] }],
    ['Open hand, palm facing out.', 'Wave it side to side.'], ['हाथ खुला, हथेली सामने की ओर।', 'उसे दाएँ-बाएँ हिलाएँ।'],
    { motion: { hand: 'R', ref: 12, path: [[0, 0], [0.35, 0], [-0.35, 0], [0, 0]], dur: 1000 }, rec: { motionPt: 12, motionMin: 0.6 } }),
  W('good', T('GOOD', 'अच्छा'), [{ side: 'R', pose: P('CCCC', 'up'), at: [1.3, 0.6] }],
    ['Make a fist with your thumb pointing up (thumbs up).'], ['मुट्ठी बनाकर अँगूठा ऊपर करें।'], { rec: { orient: true } }),
  W('bad', T('BAD', 'बुरा / खराब'), [{ side: 'R', pose: P('CCCC', 'out'), rot: [0, 0, 90], at: [1.9, 0.9] }],
    ['Make a fist with your thumb pointing down (thumbs down).'], ['मुट्ठी बनाकर अँगूठा नीचे करें।'], { rec: { orient: true } }),
  W('water', T('WATER / DRINK', 'पानी'), [{ side: 'R', pose: P('CCCC', 'up'), rot: [0, 0, 60], attach: { pt: 4, to: [0.2, 2.6] } }],
    ['Make a fist with the thumb out.', 'Point the thumb to your mouth, like drinking.'], ['मुट्ठी बनाकर अँगूठा बाहर रखें।', 'अँगूठा मुँह की ओर, जैसे पानी पी रहे हों।'],
    { motion: TAP, rec: { orient: true } }),
  W('food', T('FOOD / EAT', 'खाना'), [{ side: 'R', pose: flatO, rot: [0, -60, 0], attach: { pt: 8, to: [0.1, 2.55] } }],
    ['Bunch your fingertips together with the thumb.', 'Bring them to your mouth a few times.'],
    ['उंगलियों के सिरे अँगूठे से मिलाएँ।', 'उन्हें दो-तीन बार मुँह तक लाएँ।'], { motion: TAP }),
  W('home', T('HOME / HOUSE', 'घर'), [{ side: 'L', pose: flat, rot: [0, 0, -40], at: [-0.9, 1.0] },
    { side: 'R', pose: flat, rot: [0, 0, 40], attach: { pt: 12, to: ['L', 12], off: [0.05, 0] } }],
  ['Both hands flat.', 'Touch the fingertips together to make a roof.'], ['दोनों हाथ सपाट।', 'उंगलियों के सिरे मिलाकर छत बनाएँ।'],
  { touch: [['R12', 'L12', 0.6]] }),
  SHARED_WORDS.friend(T('FRIEND', 'दोस्त')),
  SHARED_WORDS.help(T('HELP', 'मदद')),
  SHARED_WORDS.me(T('I / ME', 'मैं')),
  SHARED_WORDS.you(T('YOU', 'तुम / आप')),
  SHARED_WORDS.deaf(T('DEAF', 'बधिर')),
  W('love', T('LOVE', 'प्यार'), [{ side: 'L', pose: fist, rot: [0, 180, -35], at: [0, 0.2] },
    { side: 'R', pose: fist, rot: [0, 180, 35], attach: { pt: 0, to: ['L', 0], off: [0, 0.05] } }],
  ['Make fists with both hands.', 'Cross your arms over your chest, like a hug.'], ['दोनों हाथों की मुट्ठी बनाएँ।', 'बाँहों को छाती पर क्रॉस करें, जैसे गले लगा रहे हों।'],
  { touch: [['R0', 'L0', 0.8]] }),
  W('ok', T('OK', 'ठीक है'), [{ side: 'R', pose: P('oXXX', 'touch8', [3, 4, -10, -22]), at: [1.3, 0.6] }],
    ['Touch your index tip to your thumb tip (a ring).', 'Other fingers up and spread.'], ['तर्जनी और अँगूठे का छल्ला बनाएँ।', 'बाकी उंगलियाँ ऊपर और फैली हुई।']),
  W('wait', T('WAIT / STOP', 'रुको'), [{ side: 'R', pose: flat, at: [1.3, 0.7] }],
    ['Hold an open hand up, palm facing out, and keep it still.'], ['खुला हाथ उठाएँ, हथेली सामने की ओर, और स्थिर रखें।']),
];

// ---------- assemble ----------
function alphaEntry(lang, [ch, a, b, c]) {
  let def;
  if (Array.isArray(a)) def = { hands: a, en: b.en, hi: b.hi, ...(c || {}) };
  else def = a;
  const rec = { orient: true, ...(def.rec || {}) };
  if (def.orientW) rec.orientW = def.orientW;
  return {
    id: `${lang}-${ch}`, lang, cat: 'alpha', char: ch, label: ch, name: { en: ch, hi: ch },
    hands: def.hands, motion: def.motion, motion2: def.motion2, touch: def.touch || [], rec,
    steps: { en: def.en, hi: def.hi },
  };
}
function wordEntry(lang, w) {
  return {
    id: `${lang}-w-${w.id}`, lang, cat: 'word', label: w.name.en, name: w.name,
    hands: w.hands, body: true, motion: w.motion, motion2: w.motion2, touch: w.touch || [],
    rec: { orient: false, ...(w.rec || {}) }, steps: { en: w.en, hi: w.hi },
  };
}

export const SIGNS = [
  ...ASL_ALPHA.map(a => alphaEntry('asl', a)),
  ...ISL_ALPHA.map(a => alphaEntry('isl', a)),
  ...ASL_WORDS.map(w => wordEntry('asl', w)),
  ...ISL_WORDS.map(w => wordEntry('isl', w)),
];
export const BY_ID = Object.fromEntries(SIGNS.map(s => [s.id, s]));
export const signsFor = (lang, cat) => SIGNS.filter(s => s.lang === lang && (!cat || s.cat === cat));
export const letterSign = (lang, ch) => BY_ID[`${lang}-${ch.toUpperCase()}`];

export const LANGS = {
  isl: { short: 'ISL', name: { en: 'Indian Sign Language', hi: 'भारतीय सांकेतिक भाषा' }, note: { en: 'Used by Deaf people in India · Hindi', hi: 'भारत के बधिर लोगों की भाषा · हिंदी' } },
  asl: { short: 'ASL', name: { en: 'American Sign Language', hi: 'अमेरिकन सांकेतिक भाषा' }, note: { en: 'English fingerspelling & words', hi: 'अंग्रेज़ी वर्तनी और शब्द' } },
};

// Words to fingerspell in practice mode
const EN_WORDS = {
  easy: ['hi', 'yes', 'no', 'me', 'we', 'go', 'up', 'ok', 'cat', 'dog', 'sun', 'day', 'fun', 'hat', 'map', 'pen', 'cup', 'box', 'red', 'eat', 'sit', 'big', 'car', 'bus', 'tea', 'egg', 'you', 'and', 'the', 'my'],
  medium: ['love', 'help', 'good', 'name', 'sign', 'hand', 'deaf', 'home', 'book', 'milk', 'food', 'fish', 'bird', 'tree', 'star', 'moon', 'rain', 'cake', 'ball', 'door', 'walk', 'talk', 'read', 'jump', 'kind', 'nice', 'work', 'time', 'word', 'baby', 'girl', 'water', 'happy', 'smile', 'house', 'light', 'music', 'zebra', 'juice', 'pizza'],
  hard: ['friend', 'thank', 'please', 'sorry', 'learn', 'teach', 'world', 'great', 'quiet', 'quick', 'school', 'family', 'mother', 'father', 'sister', 'brother', 'people', 'listen', 'answer', 'question', 'together', 'language', 'welcome', 'morning', 'evening', 'birthday', 'beautiful', 'computer', 'practice', 'journey'],
};
const HI_ROMAN_WORDS = {
  easy: ['maa', 'dil', 'aam', 'dal', 'kal', 'aaj', 'ghar', 'chai', 'pani', 'dost', 'raja', 'rani', 'diya', 'holi', 'roti', 'papa', 'bhai', 'didi', 'yoga', 'guru'],
  medium: ['khana', 'accha', 'delhi', 'desh', 'pyar', 'shanti', 'bharat', 'mumbai', 'diwali', 'namaste', 'kitab', 'bazaar', 'mausam', 'sapna', 'khushi'],
  hard: ['shukriya', 'dhanyavad', 'parivaar', 'vidyalaya', 'hindustan', 'zindagi', 'jaankari', 'dosti', 'swagat', 'aashirwad'],
};
export function practiceWords(lang, level) {
  const en = EN_WORDS[level] || EN_WORDS.easy;
  if (lang === 'isl') return en.concat(HI_ROMAN_WORDS[level] || []);
  return en;
}
