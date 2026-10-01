# System 2 functional integration prototype

System 2 observes Java detection/protection. It does not generate traffic or make security decisions.

## Two-laptop test (PowerShell)

1. On Laptop A, open PowerShell in the ANVEX repository. Build the frontend:
   `npm.cmd --prefix frontend run build`
   If the default Vite config loader encounters a directory-access restriction, use:
   `npm.cmd --prefix frontend run build -- --configLoader runner`
2. If the backend JAR is missing or outdated, run `mvn.cmd package`.
3. Set the LAN binding and a nonempty token in the same PowerShell window:
   `$env:ANVEX_HOST = '0.0.0.0'`
   `$env:ANVEX_API_TOKEN = '<your chosen nonempty token>'`
4. Start Java from the repository root:
   `java -jar target/anvex-1.0-SNAPSHOT.jar 9090 9091`
   Explicit port arguments avoid the existing launcher overriding ANVEX_API_PORT.
5. Find Laptop A's active Wi-Fi/Ethernet IPv4 address with `ipconfig`.
   Both laptops must be on the same LAN. Allow inbound TCP 9091 for Java on the private network if Windows Firewall blocks it. Browser simulations use HTTP; port 9090 is only needed for TCP clients.
6. On Laptop A open `http://<Laptop-A-IP>:9091/lab` and sign in with that token.
   Use Remember me if opening another tab on the same origin; unchecked login stores a token only in that tab's session.
7. On Laptop B open `http://<Laptop-A-IP>:9091/dashboard`, enter the same token, and click Connect.
   Confirm BACKEND CONNECTED, API port 9091 and SSE connected. Tokens must be entered on each laptop; browser storage is local to that browser/origin.
8. On Laptop A select CREATE. Set Attempts to 30, Interval (seconds) to 0.3, Target User to lab_target and use one source. Click NEXT TARGET to start the existing simulator.
9. On Laptop B observe the run, live login events, backend source score/level, evidence and protection phase. HIGH should expose the backend's delay message; CRITICAL may produce SOURCE_BLOCKED and LOGIN_BLOCKED. Actual states depend on backend configuration and timing.
10. Allow the run to complete, or stop it from System 1. Observe falling scores, THREAT_DEESCALATED, SOURCE_RELEASED and READY when the backend emits them. Default block cooldown is 60 seconds.
11. Restart Java while leaving the dashboard open. Verify disconnected/reconnecting status, then automatic recovery without manual refresh. Reload the dashboard and verify canonical history returns. In-memory source tracking resets on backend restart; persisted events remain visible.

Use Java's same-origin static hosting for this physical test, not a second Vite server on Laptop B. Browser requests use /api paths. Vite's development-only loopback proxy does not enter the production bundle.

## Implementation

- DashboardApp: connection/token form, current source, ThreatIndicator, source selector, run status and EventStream.
- useDashboard: GET /api/status, /api/sources, /api/runs, /api/metrics, /api/events?after=ID&limit=200. Source list already includes the full backend detail/evidence, so extra per-source requests are unnecessary.
- useSse: authenticated fetch reader for GET /api/events/stream; bearer token matches REST. It handles split frames/multiline data, retries after loss and aborts its sole reader on unmount or token revision. Native EventSource cannot send the Authorization header this backend requires.
- REST reconciliation every 5 seconds, after live events (150ms debounce), and after SSE connection. Canonical IDs prevent replay duplication; provisional live frames are replaced by persisted equivalents.
- Backend SSE currently omits timestamp, score, ID and runId. The UI shows missing metadata as a dash until authoritative REST history supplies it. Java files and detection/protection algorithms are unchanged.
- Shared API client reads the existing local token or System 1's session token. No token appears in URLs or source code.
- Frontend derives only source focus/sorting, visual intensity and a falling-score recovery label. It does not calculate security scores, levels, evidence, delays or block expiry.

## Local validation on 1 October 2026

Started the current packaged Java backend on isolated ports 29090/29091 with a nonempty test token and an isolated database outside the repository's normal data directory. Opened /lab and /dashboard, authenticated, and launched a real 30-attempt System 1 run at 0.3-second intervals.

Observed SUSPICIOUS 34, HIGH 55 with PROTECTION_ENABLED and a 3000ms delay, CRITICAL 81, SOURCE_BLOCKED and 15 LOGIN_BLOCKED events. After traffic stopped: THREAT_DEESCALATED to SUSPICIOUS 36, then NORMAL 21; SOURCE_RELEASED after cooldown; source reached score 0, NORMAL, phase READY, blockedUntil null.

Validated live dashboard source/event updates, evidence, completed run/30 attempts/15 blocked attempts, reload, empty-source state, invalid/missing token response, backend shutdown and automatic reconnection after restart. Desktop document and viewport widths both measured 1272px (no horizontal overflow); browser console inspection found no React errors or warnings. A separate authenticated zero-event backend response was not available because backend startup automatically emits RUN_STARTED; the empty event UI was inspected before authentication.

Frontend build passed with npm.cmd run build -- --configLoader runner. Plain build hit a filesystem restriction in the default config loader. No Java files changed, so backend test suite was not rerun. Physical two-laptop connectivity remains to be tested by the steps above.

Existing backend limitation: the metrics duration returned for this run was 33736ms, shorter than the wall-clock interval. The dashboard displays that backend value unchanged; it does not correct backend metrics in React.
