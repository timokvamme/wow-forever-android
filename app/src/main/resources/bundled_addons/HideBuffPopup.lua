-- Hides the gamepad buff popup. With "Hide all buff popups" off in its options (/fpopup), only
-- the buffs listed in BLOCK are hidden.
local addonName = ...
local db
local OPTIONS = {
  { key = "hideAll", label = "Hide all buff popups", default = true },
}
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
  if (not db or db.hideAll) or hasBlockedText(tt) then tt:Hide() end
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
f:RegisterEvent("ADDON_LOADED")
f:RegisterEvent("PLAYER_LOGIN")
f:SetScript("OnEvent", function(_, event, name)
  if event == "ADDON_LOADED" then
    if name ~= addonName then return end
    HideBuffPopupDB = HideBuffPopupDB or {}
    db = HideBuffPopupDB
    ForeverTweaks.buildSettings("Hide Buff Popup", "/fpopup", db, OPTIONS)
  end
  hook()
end)
hook()
