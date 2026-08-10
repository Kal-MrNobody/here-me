# KeeperHub per-workflow MCP scoping — verification attempt

**Date:** 2026-08-10
**Question under test:** Is an agent connected to a KeeperHub *per-workflow* MCP
endpoint structurally limited to that one workflow (boundary enforced by the
connection), or is the limit advisory (enforced by policy/convention, with
generic write tools still reachable)?

## Verdict

**UNDETERMINED — could not be tested from this environment.** The verification
was blocked before any endpoint could be reached. See "Blocker" below. No tool
enumeration, red-team, or key-scoping result in this document is claimed,
because none could be produced without inventing it. **I did not, and will not,
answer "structural vs. advisory" without a live connection — doing so would be
fabrication on a security boundary.**

This means the design premise ("the capability boundary is enforced by the
connection, not by policy code") remains **unverified**. Treat it as an open
risk, not a confirmed property, until the steps below are actually run.

## Blocker (reproducible)

Two independent blockers, either of which alone stops the task:

### 1. Network egress to all KeeperHub hosts is denied by org policy (403)

This session's outbound HTTPS goes through a policy-enforcing egress proxy.
Every KeeperHub host is rejected at CONNECT time with HTTP 403 (policy denial).

Commands run and their results:

```
# Reachability probe — each host returns 000 (connection never established)
for h in keeperhub.com docs.keeperhub.com mcp.keeperhub.com api.keeperhub.com; do
  echo -n "$h -> "
  curl -sS -o /dev/null -w "%{http_code}" --max-time 15 "https://$h/"; echo
done
# keeperhub.com      -> 000
# docs.keeperhub.com -> 000
# mcp.keeperhub.com  -> 000
# api.keeperhub.com  -> 000

# Proxy's own record of WHY (the reason curl hides on a failed CONNECT):
curl -sS "$HTTPS_PROXY/__agentproxy/status" | python3 -c \
  "import sys,json;print(json.dumps(json.load(sys.stdin)['recentRelayFailures'],indent=2))"
# Each host: kind="connect_rejected",
#            detail="gateway answered 403 to CONNECT (policy denial or upstream failure)"
```

The `WebFetch` tool returns the same block for the docs page:

```
WebFetch https://docs.keeperhub.com/ai-tools/mcp-server
# -> EGRESS_BLOCKED: "Access to docs.keeperhub.com is blocked by the network egress proxy."
```

Per the proxy's own rules (`/root/.ccr/README.md`), a 403 policy denial must be
reported, **not** retried or routed around. TLS verification was not disabled
and `HTTPS_PROXY` was not unset.

### 2. No credentials

Even with egress open, connecting to a per-workflow MCP endpoint requires a
KeeperHub API key. None was provided to this session, so the connect / enumerate
/ red-team steps could not run regardless of the network policy.

## What each requested step needs, and its current status

| # | Requested step | Status | Why blocked |
|---|---|---|---|
| 1 | Read docs (MCP server, per-workflow mode, key scoping) | **Blocked** | `docs.keeperhub.com` → 403 |
| 2 | Connect to a per-workflow endpoint, enumerate every tool | **Blocked** | `mcp.keeperhub.com` → 403; also no API key |
| 3 | Red-team ≥5 escape routes (transfer, approve, arbitrary call, cross-workflow, org escalation) | **Not attempted** | Depends on step 2 |
| 4 | Test whether a key can be scoped to one workflow vs. reaching the whole org | **Not attempted** | Depends on steps 1–2 + a key |

## Indirect signal (NOT verification — do not rely on this)

A web *search* index surfaced a paraphrase suggesting the docs describe a
per-workflow model where "the AI's tool inventory gains exactly one tool per
workflow ... with no `search_workflows` step and no `call_workflow`
indirection." This is a search-engine summary, not the primary source, and it
was **not** read from the docs directly (the docs are blocked). It describes the
*intended tool surface* only. It says nothing about:

- whether the API key behind that endpoint is workflow-scoped or org-wide,
- whether any generic/admin write tool remains reachable at the same endpoint,
- what the endpoint does with a request that names a *different* workflow.

Those three unknowns are exactly the security-relevant questions, and none is
answerable without a live connection. **Do not treat this paragraph as evidence
the scoping is structural.** It is a lead to confirm, nothing more.

## The five escape routes still to run (spec for the on-camera test)

Once a live per-workflow endpoint + key are available, run these against it and
record each raw request/response:

1. **Direct transfer** — ask the connected agent / call the endpoint to transfer
   an ERC-20. Expect: no such tool exists in the enumerated list.
2. **Approve a spender** — attempt an `approve` on an arbitrary token/spender.
3. **Arbitrary contract call** — attempt a raw `eth_sendTransaction` / generic
   "call this contract with this calldata" tool.
4. **Cross-workflow reach** — from workflow A's endpoint, try to read, list, or
   trigger workflow B (by name and by ID). Expect: not found / not exposed.
5. **Org-wide escalation** — try to reach the hosted org-wide MCP endpoint or an
   admin/list-workflows tool using the per-workflow key.

For each: record whether the tool was even present in `tools/list`, and if
invoked anyway, the exact error. **Absence from `tools/list` is the structural
claim; a permission error from a present-but-denied tool is the advisory claim.**
That distinction is the whole answer.

## What I need to actually complete this

Provide **either** path:

- **A. Unblock this environment** — have an admin allowlist `keeperhub.com`,
  `docs.keeperhub.com`, `mcp.keeperhub.com`, and `api.keeperhub.com` in the
  environment's network egress policy
  (https://claude.ai/admin-settings/claude-in-slack for the GitHub-app case, or
  the environment's network-policy setting), **and** supply a per-workflow
  endpoint URL + a scoped test API key (ideally a testnet workflow, so the
  red-team's write attempts are harmless).

- **B. Run it where egress is open** — run these same commands from your own
  machine (or any environment that can reach KeeperHub) and paste the raw
  `tools/list` output plus the five red-team request/response pairs back here;
  I'll analyze them and fill in the verdict.

Until one of those happens, the premise stays **unverified**, and no code should
be written that assumes the boundary is structural.
