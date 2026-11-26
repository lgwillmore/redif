# Roadmap

Status: active backlog, last revised on 2026-11-26 when the command groups beyond strings and keys were added.
Current implementation rules live in [`AGENTS.md`](../AGENTS.md).

## Remaining work

1. **Deleting a key only if it still holds your value**, which is how a lock is released safely: compare, then
   delete, as one command. Redis 8.4 has it natively as `DELEX key IFEQ value`, and before that it takes `EVAL`.
   Redif's floor is Redis 7.0, and `RedifInMemory` cannot run a script, so neither is in. It goes in as `delex`, a
   command like any other, when the floor can move to 8.4; a scripting group only one implementation can honour
   does not.

## Decided against

- **Streams and pub/sub** (2026-11-26). They reach beyond what the facade exists for: a consumer group or a
  subscription is a long-lived conversation with Redis, with its own connections, not a request and a reply.
