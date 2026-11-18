# Host tuning

Optional changes to the Hermes host that cut token spend and keep a small box (4 cores, 8 GB) from freezing.
Measured on Hermes Agent v0.22.5 with Anthropic OAuth sign-in. Back up first:
```bash
cp -p ~/.hermes/config.yaml ~/.hermes/config.yaml.bak
cp -p ~/.hermes/hermes-agent/agent/auxiliary_client.py ~/.hermes/auxiliary_client.py.bak
```

## 1. Fix context compression with Anthropic OAuth (biggest saving)
The gateway and the dashboard share `~/.hermes/auth.json`. When one of them refreshes the OAuth token, Anthropic
revokes the old one, and the other process keeps using it. Its retry only looks for the old token in the pool,
so every context compression fails with `401 OAuth access token has been revoked` (look for
`Failed to generate context summary` in `~/.hermes/logs/errors.log`).

The chat that suffers most is **Pulse**: one session that never ends, 7 turns a day hours apart, so each turn
re-sends the whole history without a prompt cache. With compression broken, ours grew to ~830k tokens per turn;
after the fix the next turn compressed it to ~94k.

[`patches/auxiliary_client.patch`](patches/auxiliary_client.patch) fixes it, plus a second bug: Pulse memory
asks for `json_object`, which Hermes sends to Anthropic as a bare `{"type": "object"}` schema that Anthropic
rejects (400 `additionalProperties must be explicitly set to false`).
```bash
cd ~/.hermes/hermes-agent && git apply /path/to/alfatherapp/host/patches/auxiliary_client.patch
systemctl --user restart hermes-dashboard hermes-gateway
```
A Hermes update reverts the patch: re-apply it if the errors come back.

## 2. Cheaper auxiliary models
Auxiliary tasks without their own `auxiliary.<task>` entry use your main model. Compression and the post-turn
review (`background_review`) are the expensive ones; Sonnet does them well (off the main model, the review also
replays a compact digest instead of the whole chat):
```bash
hermes config set auxiliary.compression.provider anthropic
hermes config set auxiliary.compression.model claude-sonnet-5-5
hermes config set auxiliary.background_review.provider anthropic
hermes config set auxiliary.background_review.model claude-sonnet-5-5
hermes config set agent.reasoning_effort medium
```
`config set` calls `agent.reasoning_effort` unrecognized; Hermes reads it anyway. The app can still raise the
effort per chat. Leave `prompt_caching.cache_ttl` at `5m`: interactive chats already hit the cache, and a 1 h
write costs twice as much for turns that come hours apart.

## 3. Keep the box responsive
- **Kanban workers**: each one is a full agent. The default derives the cap from free memory (8 on an 8 GB
  box); 2 is plenty:
  `hermes config set kanban.max_in_progress 2` (read by the gateway).
- **Terminal sandbox**: `hermes config set terminal.container_memory 2048` (default 5 GB). A sandbox that already
  exists keeps its old limit: `docker update --memory 2g --memory-swap 2560m <container>`.
- **Other containers** on the same box (browsers, media servers) have no memory limit by default:
  `docker update --memory 1g --memory-swap 1536m <container>`.
- **Services**: cap the dashboard and gateway units so a runaway tool job is killed instead of the box:
  ```bash
  systemctl --user set-property hermes-dashboard MemoryHigh=4G MemoryMax=4.5G MemorySwapMax=512M
  systemctl --user set-property hermes-gateway MemoryHigh=1.5G MemoryMax=2G MemorySwapMax=512M
  ```
- **earlyoom**: under memory pressure Linux can thrash in swap for minutes (ssh stops answering) without the
  kernel OOM killer ever firing. earlyoom kills the largest process first:
  `sudo apt install earlyoom` (Debian/Ubuntu; it enables itself).

Restart the gateway only when nobody is using its Discord, WhatsApp or other bots.

## Check it worked
- Compression: no new `Failed to generate context summary` lines after the next long chat.
- Pulse: `hermes conduit-push pulse probe`, then look for `context compression done` in `~/.hermes/logs/agent.log`.
- Models: `grep "Auxiliary compression: using" ~/.hermes/logs/agent.log | tail -1` names the model.
