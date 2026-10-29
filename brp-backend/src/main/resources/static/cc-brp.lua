-- cc-brp.lua — the BRP desk for CraftOS (docs/13 §10).
--
-- Install: drop this file on a computer (e.g. `wget http://<backend>:8080/cc-brp.lua brp`),
-- set the endpoint and token once, then ask the desk before every run:
--
--   local brp = dofile("brp")
--   brp.configure("http://127.0.0.1:8080", "cc-secret-token", "my-tenant")
--   if brp.canRun() then startTheFarm() end
--
-- One poll per call, no history, no side effects: the desk reports, the
-- turtle decides. HTTP must be allowed for the backend host in CC's config.

local M = {}

local endpoint = nil
local token = nil
local tenant = nil
local cache = nil
local cacheUntil = 0

function M.configure(url, bearerToken, tenantId)
    endpoint = url:gsub("/$", "")
    token = bearerToken
    tenant = tenantId
end

--- Poll the desk. Cached for 5 seconds — a turtle asks more often than a grid moves.
function M.state()
    if endpoint == nil or tenant == nil then
        return nil, "brp: not configured"
    end
    local now = os.epoch("local") / 1000
    if cache and now < cacheUntil then
        return cache
    end
    local handle = http.get(endpoint .. "/api/v1/cc/" .. tenant .. "/state",
                            { Authorization = "Bearer " .. (token or "") })
    if handle == nil then
        return nil, "brp: endpoint unreachable"
    end
    local body = handle.readAll()
    handle.close()
    local parsed = textutils.unserialiseJSON(body)
    if parsed == nil then
        return nil, "brp: unparsable state"
    end
    cache = parsed
    cacheUntil = now + 5
    return parsed
end

--- True when the production lines may run: the grid has headroom, the
--- credit desk has not cut this tenant off, and (if the line is DR-tagged)
--- a window is open.
function M.canRun(drJobId)
    local state, err = M.state()
    if state == nil then
        return false, err
    end
    if state.creditStatus == "RESTRICTED" or state.creditStatus == "DEFAULTED" then
        return false, "credit desk holds the tenant"
    end
    if not state.schedulable then
        return false, "grid over budget"
    end
    if drJobId then
        for _, window in ipairs(state.windows or {}) do
            if window.jobId == tostring(drJobId) and window.status == "RUNNING" then
                return true
            end
        end
        return false, "no open DR window"
    end
    return true
end

return M
