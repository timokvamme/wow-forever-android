-- RestedXP: a small blue label on a black background with the rested XP you have stored, as a
-- percentage of a level (it can reach 150%). Drag it anywhere; it remembers where you put it.

local addonName = ...
local db

-- Options shown in Options > AddOns > Rested XP (and /frested).
local OPTIONS = {
  { key = "locked", label = "Lock in place (no dragging)", default = false },
  { key = "fontSize", label = "Text size", default = 12, min = 8, max = 24 },
  { key = "byMinimap", label = "Sit under the minimap instead", default = false },
  { key = "hideWhenNone", label = "Hide when there's no rested XP", default = false },
}

---------------------------------------------------------------------------------------------------

local label = CreateFrame("Frame", "RestedXPLabel", UIParent, "BackdropTemplate")
label:SetFrameStrata("MEDIUM")
label:SetClampedToScreen(true)
label:SetMovable(true)
label:RegisterForDrag("LeftButton")
label:SetBackdrop({ bgFile = "Interface\\Buttons\\WHITE8X8" })
label:SetBackdropColor(0, 0, 0, 0.7)

local text = label:CreateFontString(nil, "OVERLAY")
text:SetPoint("CENTER")
text:SetTextColor(0.35, 0.65, 1)

local function place()
  label:ClearAllPoints()
  if db.byMinimap and Minimap then
    label:SetPoint("TOP", Minimap, "BOTTOM", 0, -4)
  elseif db.point then
    label:SetPoint(db.point, UIParent, db.point, db.x, db.y)
  else
    label:SetPoint("TOP", UIParent, "TOP", 0, -40)
  end
end

local function update()
  if not db then return end
  text:SetFont(STANDARD_TEXT_FONT, db.fontSize, "OUTLINE")
  local maxXP = UnitXPMax("player") or 0
  local atMax = (IsPlayerAtEffectiveMaxLevel and IsPlayerAtEffectiveMaxLevel())
    or (GetMaxPlayerLevel and UnitLevel("player") >= GetMaxPlayerLevel())
  local rested = GetXPExhaustion() or 0
  if atMax or maxXP <= 0 or (db.hideWhenNone and rested <= 0) then
    label:Hide()
    return
  end
  text:SetText(string.format("Rested %d%%", math.floor(rested / maxXP * 100 + 0.5)))
  label:SetSize(text:GetStringWidth() + 10, text:GetStringHeight() + 6)
  label:EnableMouse(not db.locked and not db.byMinimap)
  label:Show()
end

label:SetScript("OnDragStart", function(self)
  if not db.locked and not db.byMinimap then self:StartMoving() end
end)
label:SetScript("OnDragStop", function(self)
  self:StopMovingOrSizing()
  local point, _, _, x, y = self:GetPoint(1)
  db.point, db.x, db.y = point, x, y
  place()
end)

local events = CreateFrame("Frame")
events:RegisterEvent("ADDON_LOADED")
events:RegisterEvent("PLAYER_ENTERING_WORLD")
events:RegisterEvent("PLAYER_XP_UPDATE")
events:RegisterEvent("PLAYER_LEVEL_UP")
events:RegisterEvent("UPDATE_EXHAUSTION")
events:SetScript("OnEvent", function(_, event, name)
  if event == "ADDON_LOADED" then
    if name ~= addonName then return end
    RestedXPDB = RestedXPDB or {}
    db = RestedXPDB
    ForeverTweaks.buildSettings("Rested XP", "/frested", db, OPTIONS, function()
      place()
      update()
    end)
    place()
  end
  update()
end)
