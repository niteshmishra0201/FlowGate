-- KEYS[1] = the Redis key for this client's bucket (e.g. "rate_limit:client123")
-- ARGV[1] = bucket capacity (max tokens)
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = current timestamp (seconds, as a float)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- Fetch existing state, or assume a full bucket if this client is new
local bucket = redis.call("HMGET", key, "tokens", "last_check")
local tokens = tonumber(bucket[1])
local last_check = tonumber(bucket[2])

if tokens == nil then
    tokens = capacity
    last_check = now
end

-- Refill based on elapsed time, capped at capacity
local elapsed = math.max(0, now - last_check)
tokens = math.min(capacity, tokens + (elapsed * refill_rate))

local allowed = 0
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
end

-- Persist new state
redis.call("HMSET", key, "tokens", tokens, "last_check", now)
redis.call("EXPIRE", key, 3600) -- cleanup: forget clients inactive for over an hour

return { allowed, tokens }