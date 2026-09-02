# Here Me

A floating balance you can watch drain.

UPI makes money abstract. Paying ₹112 with GPay feels like nothing, because nothing
visibly leaves you. Paying ₹112 in cash feels like something, because you watch the note
leave your wallet.

This app puts that back. A bubble floats above every other app holding a number you set
by hand. When a payment lands, the number does not jump to its new value — it **drains**,
and the drain takes as long as the payment was big relative to what you had. ₹20 out of
₹5,000 is a blink. ₹2,000 out of ₹5,000 takes an uncomfortably long moment, with a haptic
tick as each digit rolls.

Personal project, sideload only. No bank login, no account linking, no UPI PIN, no
backend. Everything stays on the phone.

---

## How it knows a payment happened

It reads the confirmation notification the payment app already posts
(`NotificationListenerService`), not what you type inside the app.

The original idea was to read the amount out of GPay's input field as you type it, so the
number could drain live. That needs an `AccessibilityService` reading another app's text
fields — the same API a keylogger uses. Payment apps actively detect that pattern for
anti-fraud reasons and may refuse to run, and it breaks on every payment-app redesign.
The notification arrives about a second later and keeps working.

That one second is the whole trade.

## The number is a fiction, and the app says so

It is a value you typed. It drifts on every ATM withdrawal, salary credit, autopay,
refund and cash spend. A tracker that is quietly wrong teaches you to ignore it, which
defeats the entire purpose — so:

- **Credits count too.** Money received and salary credits refill the balance, so drift
  does not only ever compound in one direction.
- **A daily check-in** asks *"Still ₹4,888?"* — confirm it, or put the real number in.
- **After three days unconfirmed** the bubble shows a staleness dot and stops presenting
  itself as fact.
- **Corrections are recorded as their own ledger entry**, so how far the tracking drifted
  stays visible in history instead of being quietly absorbed.

## Using it

1. Set your opening balance.
2. Grant *draw over other apps*, *notification access*, and — importantly — the battery
   optimisation exemption, or the bubble is killed overnight on most Indian OEM builds.
3. Turn the bubble on.

Drag it to move (it snaps to the nearest edge), tap to open, long-press to resize. After
an automatic payment the bubble opens for ten seconds showing what it read, with an
**Undo**. Cash and anything the app cannot see go in by hand from the balance screen.

---

## Project layout

| Module | What it is |
|---|---|
| `:core` | Plain Kotlin/JVM. Notification parsing, money arithmetic, drain timing, dedupe. No Android dependencies. |
| `:app`  | Android. Overlay service, notification listener, Room, Compose UI. |

The split is deliberate. Everything most likely to be wrong — the direction of a payment,
rupee arithmetic, deduplicating a re-posted notification — lives in `:core`, where it can
be unit tested without an Android toolchain.

`settings.gradle.kts` only includes `:app` when an Android SDK is actually present, so a
bare checkout can still run the `:core` tests.

## Building and testing

```bash
./gradlew :core:test          # 46 tests: parser corpus, money, ledger, drain, dedupe
./gradlew :app:assembleDebug  # needs an Android SDK (Android Studio, or ANDROID_HOME)
```

Money is `Long` paise everywhere, never a floating point rupee value. `Money.format`
groups the Indian way: `1,00,000`, not `100,000`.

### The parser is the risky part

`PaymentParser` turns notification text into a balance change, and getting the direction
wrong *adds* money when it should take it away. It is tested against a corpus of real
notification shapes — GPay, PhonePe, Paytm, WhatsApp, bank SMS — including the traps:

- a refund worded like a payment (must be a credit)
- a payment with a cashback tagline (must still be a debit)
- a bank debit SMS whose closing balance appears after the transaction amount
- failed payments, money requests, bill reminders, offers, scratch cards (all ignored)
- a reference number that looks like an amount (ignored)

If a notification format changes, add it to `PaymentParserTest` first.
