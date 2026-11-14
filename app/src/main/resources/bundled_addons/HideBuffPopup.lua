-- Hides every gamepad buff popup. Set HIDE_ALL to false to hide only the buffs listed in BLOCK.
local HIDE_ALL = true
local BLOCK = {
  "Plainsrunning",
  "Lightning Shield",
}

local function isBlocked(text)
  -- drop color codes and textures, then look for a listed name anywhere in the line
  text = text:gsub("|c%x%x%x%x%x%x%x%x", ""):gsub("|r", ""):gsub("|T.-|t", "")
  for _, name in ipairs(BLOCK) do
    if text:find(name, 1, true) then return true end
  end
  return false
end

local function hasBlockedText(frame)
  for _, r in ipairs({ frame:GetRegions() }) do
    if r.GetText then
      local t = r:GetText()
      if type(t) == "string" and isBlocked(t) then return true end
    end
  end
  for _, child in ipairs({ frame:GetChildren() }) do
    if hasBlockedText(child) then return true end
  end
  return false
end

local function check(tt)
  if HIDE_ALL or hasBlockedText(tt) then tt:Hide() end
end

local function hook()
  local tt = _G.BuffFrameTooltip
  if not tt or tt.__hbpHooked then return end
  tt.__hbpHooked = true
  tt:HookScript("OnShow", function(self)
    check(self)
    -- the text is sometimes filled in a frame later
    C_Timer.After(0, function() if self:IsShown() then check(self) end end)
  end)
end

local f = CreateFrame("Frame")
f:RegisterEvent("PLAYER_LOGIN")
f:SetScript("OnEvent", hook)
hook()
