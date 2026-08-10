# KeeperHub Adoption Tooling — Positioning, Name, Install Spec, First Capability, Growth Loop, and an Honest Attack on the Premise

Role: Principal Product Designer / founder advisor. No code, no architecture. The metric everything below is optimized for: **KeeperHub organisations created, and transactions executed by strangers who installed the bundle.**

---

## 1. The one-sentence positioning

Five candidates:

1. "Give your agent one onchain job it literally cannot exceed."
2. "Installable onchain capabilities for AI agents: one job, one permission — everything else simply isn't in the tool list."
3. "The safest way to let an agent transact: install a single capability with no path to anything else."
4. "One-command installs that hand any agent exactly one onchain capability — scoped by construction, not by policy."
5. "npm for onchain agent capabilities — every package is one job with one permission."

**Pick #2**, tightened:

> **One onchain job per install. Your agent gets a single scoped capability — everything else isn't in its tool list.**

Why this one wins:

- **It's a subtraction pitch in an addition market.** Every "AI agent" product promises more capability. You are selling *less*, on purpose. That alone stops it sounding like another agent — agents *do* things; this *bounds* things. Candidates 1 and 3 gesture at this but hide the mechanism.
- **It's falsifiable in ten seconds.** A skeptical developer opens the MCP tool list and counts: one tool. That is the whole security argument, and it's inspectable. "Scoped by policy" claims require trust; "not in the tool list" requires only eyes. Never trade this concreteness for a smoother sentence.
- **Candidate 5 is the trap.** "npm for X" is the most seductive and the most dishonest framing here — it implies you own a registry ecosystem you don't have yet, and it recenters the pitch on *you* instead of on the developer's fear. The fear is the market: people don't let agents touch wallets because the downside is unbounded. Position against the fear.
- One rule for all future copy: **never lead with AI, always lead with the bound.** "AI agent" in the first clause makes you one of ten thousand; "cannot exceed" in the first clause makes you the only one.

---

## 2. The name

Constraint honored: nothing that implies you built the execution layer. KeeperHub owns the building; you issue passes to single rooms.

| Name | Reasoning | Risk |
|---|---|---|
| **Errands** | An errand is one bounded task: you go out, do the thing, come back. It's humble (nobody who "runs errands" claims to own the city), it's countable ("14,000 errands run" is a metric *and* a headline), and it verbs: "run an errand," `errands add`, `errands receipt`. | Slightly domestic; some devs may find it unserious. That's survivable — memorable beats serious. |
| **Keycard** | Issued access to exactly one door in someone else's building — the best pure metaphor for "scoped capability against infrastructure I didn't build." | "Key" collides with *private keys* in crypto. In this market that collision is expensive; you'd spend your first paragraph disclaiming custody. |
| **One Job** | The literal promise. "It has one job" is instantly understood, and the meme adjacency ("you had ONE job") makes it sticky. | Jokey; hard to say with a straight face in a partnership email. Weak as a CLI word. |
| **Blinkers** | Horse blinders: safety through deliberately limited vision. The most *honest* name — it names the mechanism, not the benefit. | Reads negative ("blinkered thinking"); you'd be explaining the horse every time. |
| **Skillet** | Skill + bundle; a skillet cooks one dish at a time. Great CLI ergonomics (`npx skillet add weth-wrap`), npm-era energy. | Cute without content — it doesn't encode *scoped*, which is your entire differentiation. |

**Pick Errands.** The decisive test is not the logo, it's the terminal and the tweet: `npx errands add hello-chain` reads cleanly, and "I ran my first errand onchain — here's the receipt" is a sentence a stranger will actually post. Crucially, the name defines your **unit of adoption**: one errand run = one transaction executed = your metric. When your name, your CLI, and your KPI are the same noun, every mention of the product is a mention of the number you're paid on.

---

## 3. The install path (README as spec)

This is the artifact you build toward. Rules first, steps second.

Rules: total time under seven minutes; **zero file editing** (nobody hand-edits MCP JSON — that's where strangers die); zero wallet setup (see §4); every step names its expected duration and its success signal, because silence is where the second half of strangers quit.

> ### Errand: `hello-chain`
> **What it does:** your agent executes one real onchain transaction through KeeperHub's execution layer, and can do nothing else.
> **You need:** an MCP-capable agent (Claude Code, Claude Desktop, or Cursor). No wallet. No funds. No code.
> **Time:** ~6 minutes.
>
> 1. **Create a KeeperHub account** → `keeperhub.xyz/signup?ref=errands`
>    Sign in with GitHub, name your organisation anything. You land on your dashboard. *(~90s)*
> 2. **Create your scoped key.** From the dashboard: **API Keys → New key → scope: `hello-chain` workflow only** → copy. *(~45s)*
>    *(This deep link takes you to the exact page: `keeperhub.xyz/org/keys/new?workflow=hello-chain`.)*
> 3. **Install the errand** — one command:
>    ```
>    npx errands add hello-chain
>    ```
>    It asks you to paste the key, detects which agent host you're running, and writes the MCP entry itself. You edit nothing. *(~60s)*
> 4. **Reload your agent** (restart Claude Code / reload MCP servers). You'll see exactly one new tool: `hello_chain`. *(~30s)*
> 5. **Ask for the job.** Paste this, verbatim: *"Run my hello-chain errand."*
> 6. **Success looks like this:** the agent replies with a transaction hash and a block-explorer link. Open it. That transaction was initiated by your agent, landed by KeeperHub, using a capability that has no path to anything else. *(~90s)*
> 7. **Get your receipt:** `npx errands receipt` prints a shareable card with your tx link. Post it — every receipt links back to this page.

**Friction audit — the quit points, ranked by body count:**

1. **Step 2 is the step you don't control and the step that IS your metric.** If KeeperHub's signup demands email verification, a company name, or a plan selection before showing a dashboard, you lose half your funnel there. If workflow-scoped keys don't exist in their UI, step 2 becomes a lie (see §6). Both are asks to make of the platform team *now*, and both are asks they should welcome — they told you they're paying for exactly this funnel.
2. **Manual MCP config is the historical killer.** Three hosts, three config formats, one typo = silent failure. The `npx errands add` installer is not sugar; it *is* the product's UX. Build it before anything else.
3. **The reload step (4) fails silently.** Print the expected tool name so the user can check. "You'll see exactly one new tool" doubles as your positioning demo.
4. **Ambiguity at step 5.** Give the exact sentence to paste. Strangers don't improvise prompts; they abandon.
5. **Anywhere money appears, you lose everyone** — which is why the first capability must need no funds (§4).

---

## 4. The first capability — against `wrap ETH → WETH`

**Your own constraint disqualifies your own pick.** You said: no pre-existing onchain position, running within ten minutes. Wrapping ETH *requires ETH* — that is a pre-existing position. On mainnet, a stranger will not fund a wallet to run a demo; on testnet, you've added a faucet (captchas, rate limits, dead faucets) as step zero — the worst UX in crypto as your front door. Wrap is also **sterile**: it exercises none of KeeperHub's actual differentiators (congestion-aware gas, MEV-protected routing matter for swaps under contention, not for a wrap), and it produces nothing worth posting. "I wrapped 0.01 ETH" is not a story.

**Lead instead with `hello-chain`: a gas-sponsored mint of a small onchain attestation** — "this organisation ran its first errand" — on a cheap chain (Base). Why:

- **Zero funds, zero wallet, zero faucet.** The ten-minute promise becomes real. This needs KeeperHub (or you, initially — sponsoring a Base transaction costs fractions of a cent) to cover gas. It is the single highest-leverage ask you can make of the platform, and it's aligned: they're buying signups, this is the cost of a signup.
- **The demo is the telemetry.** Every first run writes a public onchain record. This one choice also solves your visibility problem in §5 — you cannot see their accounts, but you can read your own contract's events forever.
- **It produces the shareable artifact** (the receipt) that drives §5.
- Wrap ETH becomes errand #2 — the first one with stakes, for people already converted. Good second date; bad first impression.

Fallback if gas sponsorship is truly unavailable: testnet `hello-chain` with the faucet call *bundled into the installer* (the CLI requests testnet funds, the user never sees a faucet page). Accept that this fallback costs you real conversion, and keep negotiating for sponsorship.

---

## 5. The growth loop

You can't see other people's accounts — so route adoption through surfaces you *can* read:

1. **The attestation contract is your analytics.** Every first-run mints an event with an anonymous org tag. Your public count of orgs and errands is now onchain, enumerable by you, verifiable by anyone — **including KeeperHub when they decide what your referrals were worth.** You are paid on a number someone else's dashboard computes; an onchain mirror of that number is your negotiating position.
2. **A ref-tagged signup link** (`?ref=errands`) in step 1 of every README. Cheap ask to the platform; even if their attribution is crude, it's a second counter you can cite.
3. **The receipt is the unit of virality.** Success ends with a shareable card: tx link, "executed by an agent with exactly one permission," and the one-line install command. The loop is: *see a receipt → click → one command + signup → run → get a receipt → post it.* The install command must fit in a tweet — that's a design constraint, not a nice-to-have.
4. **A public directory with live counts** — errands.dev listing every skill with its onchain run count. Live numbers are social proof for strangers *and* a scoreboard that pressures you to keep counts moving. Adding a new errand is a PR to the directory: contributors become the second loop.
5. **Publish the number weekly.** "Errands ran N times for M orgs this week," with the contract link. Countable-in-public is the whole strategy: it recruits users, disciplines you, and pre-writes your report to the platform.

---

## 6. The attack on the premise (asked for, delivered)

**The scoping mechanism is not yours.** KeeperHub's per-workflow MCP mode — one workflow exposed at its own endpoint — *is* capability scoping. The honest sentence is: for a developer who already knows KeeperHub, your bundle is one config line plus a README they could have written. If you privately believe your moat is the scoping idea, stop believing it today; it ships in their product.

**Worse: your security claim currently rests on their key model.** "No path to anything else because it's not in the tool list" is true of the *agent's* surface — but if the API key behind the endpoint is org-wide, a leaked key does everything, and your one-permission story is cosmetic. Verify whether KeeperHub issues workflow-scoped credentials. If yes, say "scoped endpoint + scoped key" loudly. If no, either get them to build it (platform ask #2) or honestly downgrade the claim to "scoped surface, not scoped credential" before someone on Hacker News does it for you.

**What is genuinely yours:**

1. **The last mile.** 110 templates is a catalog; a catalog converts nobody. The distance between "possible in the docs" and "done in six minutes by a stranger" is the entire product, and nobody at an infrastructure company ever owns it. The installer, the exact-sentence prompts, the deep links, the receipt — that's you.
2. **The distribution and the naming.** Canonical skill names, a directory, a one-command install that works across agent hosts with divergent MCP configs. If "errands" becomes what people call scoped agent capabilities on KeeperHub, the registry is the moat — thin, but real, and defensible only by moving fast.
3. **The measurement layer.** Onchain attestation + receipts + public counts. KeeperHub has no growth loop for stranger adoption; you are building the one they'll wish they had.
4. **The trust framing.** The positioning in §1 — subtraction, inspectable, fear-first — is marketing work the platform's own "execution layer" framing will never do.

**The endgame risk, stated plainly:** the day KeeperHub adds an "Install in Claude" button to every template page, most of this is absorbed. Given your stated goal — bringing them developers, measured in their metric — that is not a failure mode; it's the good ending. Build so that absorbing you means adopting your names, your funnel, and your numbers. The fastest way to be indispensable to a platform is to own the funnel they're already paying for.

---

### The three asks to make of the KeeperHub team this week

1. Gas-sponsored `hello-chain` workflow (they're buying signups; this is the cost of one).
2. Workflow-scoped API keys, if they don't exist (your security claim depends on it).
3. A `?ref=` parameter on signup (your attribution depends on it).
