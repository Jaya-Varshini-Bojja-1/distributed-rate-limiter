-- Atomic Token Bucket. Redis TIME is the only clock. Java must not send "now".
-- KEYS[1]  bucket key rl:bucket:<clientId>:<policyId>
-- ARGV[1]  capacity
-- ARGV[2]  refill tokens
-- ARGV[3]  refill period in milliseconds
-- ARGV[4]  tokens requested (1 today)
--
-- Returns four integers: allowed, remaining, retryAfterMs, resetMs.
-- Writes the hash and PEXPIRE only when the request is allowed.

local TTL_BUFFER_MS = 1000

local capacity = tonumber(ARGV[1])
local refill_tokens = tonumber(ARGV[2])
local period_ms = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

local stored = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens
local ts
if stored[1] == false or stored[1] == nil then
    tokens = capacity
    ts = now
else
    tokens = tonumber(stored[1])
    ts = tonumber(stored[2])
end

local rate = refill_tokens / period_ms
local elapsed = math.max(0, now - ts)
local available = math.min(capacity, tokens + elapsed * rate)

local allowed = 0
local logical = available
if available >= requested then
    allowed = 1
    logical = available - requested
    redis.call('HSET', KEYS[1],
        'tokens', string.format('%.17g', logical),
        'ts', string.format('%.0f', now))
    local ttl = math.ceil((capacity * period_ms) / refill_tokens) + TTL_BUFFER_MS
    redis.call('PEXPIRE', KEYS[1], ttl)
end

local remaining = math.floor(logical)
local retry_after_ms = 0
if allowed == 0 then
    retry_after_ms = math.ceil((requested - available) / rate)
end

local reset_ms = 0
local deficit = capacity - logical
if deficit > 0 then
    reset_ms = math.ceil(deficit / rate)
end

return { allowed, remaining, retry_after_ms, reset_ms }
