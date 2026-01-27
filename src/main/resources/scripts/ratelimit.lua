-- keys[1]: request_key (ex: rate_limit:127.0.0.1:getProducts)
-- argv[1]: capacity (버킷 크기)
-- argv[2]: refill_rate (초당 충전량)
-- argv[3]: now (현재 시간 - 초)
-- argv[4]: requested (소모할 토큰 수)

local keys = KEYS
local argv = ARGV

local key = keys[1]
local capacity = tonumber(argv[1])
local rate = tonumber(argv[2])
local now = tonumber(argv[3])
local requested = tonumber(argv[4])

-- Redis에서 현재 토큰량과 마지막 갱신 시간 조회
local info = redis.call('hmget', key, 'tokens', 'last_refill')
local tokens = tonumber(info[1])
local last_refill = tonumber(info[2])

-- 값이 없으면 초기화 (최대 용량으로 시작)
if tokens == nil then
    tokens = capacity
    last_refill = now
end

-- 토큰 충전 로직 (Time-based Refill)
-- 경과 시간 * 충전 속도 만큼 토큰을 채움 (최대 capacity를 넘지 않음)
local delta = math.max(0, now - last_refill)
local filled_tokens = math.min(capacity, tokens + (delta * rate))

local allowed = false

-- 토큰 차감 가능 여부 확인
if filled_tokens >= requested then
    filled_tokens = filled_tokens - requested
    allowed = true
    -- 상태 업데이트 (토큰 감소, 시간 업데이트)
    redis.call('hmset', key, 'tokens', filled_tokens, 'last_refill', now)
else
    allowed = false
    -- 차단됨. (토큰은 충전된 상태로 업데이트하고 시간도 갱신할지, 아니면 그대로 둘지 선택)
    -- 여기서는 최신 상태 유지를 위해 업데이트함 (선택 사항)
    redis.call('hmset', key, 'tokens', filled_tokens, 'last_refill', now)
end

-- 키 만료 시간 설정 (1분간 요청 없으면 자동 삭제)
redis.call('expire', key, 60)

return allowed