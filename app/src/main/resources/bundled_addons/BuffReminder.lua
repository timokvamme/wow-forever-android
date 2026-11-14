-- BuffReminder: shows a big pulsing icon above your character when one of your own buffs is
-- missing, so you know to cast it again.

local addonName = ...
local db

-- Which buff each class is reminded about. Add more here; each gets an on/off option.
local REMINDERS = {
  { key = "battleShout", class = "WARRIOR", spell = "Battle Shout" },
  { key = "lightningShield", class = "SHAMAN", spell = "Lightning Shield" },
}

-- Options shown in Options > AddOns > Buff Reminder (and /fbuff).
local OPTIONS = {
  { key = "battleShout", label = "Battle Shout (warrior)", default = true },
  { key = "lightningShield", label = "Lightning Shield (shaman)", default = true },
  { key = "iconSize", label = "Icon size", default = 56, min = 24, max = 128, step = 4 },
  { key = "offsetY", label = "Height above your character", default = 140, min = -300, max = 400, step = 10 },
  { key = "onlyInCombat", label = "Only in combat", default = false },
}

local _, playerClass = UnitClass("player")

local frame = CreateFrame("Frame", "BuffReminderFrame", UIParent)
frame:SetSize(56, 56)
frame:SetPoint("CENTER", UIParent, "CENTER", 0, 140)
frame:SetFrameStrata("HIGH")
frame:Hide()

local icon = frame:CreateTexture(nil, "ARTWORK")
icon:SetAllPoints()

local label = frame:CreateFontString(nil, "OVERLAY", "GameFontNormalSmall")
label:SetPoint("TOP", frame, "BOTTOM", 0, -2)

local pulse = frame:CreateAnimationGroup()
pulse:SetLooping("BOUNCE")
local fade = pulse:CreateAnimation("Alpha")
fade:SetFromAlpha(1)
fade:SetToAlpha(0.35)
fade:SetDuration(0.6)

local function knows(spell)
  local ok, info = pcall(C_Spell.GetSpellInfo, spell)
  return ok and info ~= nil
end

local function hasBuff(spell)
  local ok, aura = pcall(C_UnitAuras.GetAuraDataBySpellName, "player", spell, "HELPFUL")
  if not ok then return true end -- buffs can't be read right now; don't nag
  return aura ~= nil
end

local function missing()
  if UnitIsDeadOrGhost("player") or UnitOnTaxi("player") or (IsMounted and IsMounted()) then return nil end
  if db.onlyInCombat and not UnitAffectingCombat("player") then return nil end
  for _, reminder in ipairs(REMINDERS) do
    if db[reminder.key] ~= false and reminder.class == playerClass and knows(reminder.spell) and not hasBuff(reminder.spell) then
      return reminder.spell
    end
  end
  return nil
end

local function layout()
  frame:SetSize(db.iconSize, db.iconSize)
  frame:ClearAllPoints()
  frame:SetPoint("CENTER", UIParent, "CENTER", 0, db.offsetY)
end

local function update()
  if not db then return end
  local spell = missing()
  if spell then
    local texture = C_Spell.GetSpellTexture(spell)
    icon:SetTexture(texture or 134400)
    label:SetText(spell)
    if not frame:IsShown() then
      frame:Show()
      pulse:Play()
    end
  elseif frame:IsShown() then
    pulse:Stop()
    frame:Hide()
  end
end

local events = CreateFrame("Frame")
events:RegisterEvent("ADDON_LOADED")
events:RegisterEvent("PLAYER_ENTERING_WORLD")
events:RegisterUnitEvent("UNIT_AURA", "player")
events:RegisterEvent("SPELLS_CHANGED")
events:RegisterEvent("PLAYER_REGEN_DISABLED")
events:RegisterEvent("PLAYER_REGEN_ENABLED")
events:RegisterEvent("PLAYER_MOUNT_DISPLAY_CHANGED")
events:RegisterEvent("PLAYER_DEAD")
events:RegisterEvent("PLAYER_ALIVE")
events:RegisterEvent("PLAYER_UNGHOST")
events:SetScript("OnEvent", function(_, event, name)
  if event == "ADDON_LOADED" then
    if name ~= addonName then return end
    BuffReminderDB = BuffReminderDB or {}
    db = BuffReminderDB
    ForeverTweaks.buildSettings("Buff Reminder", "/fbuff", db, OPTIONS, function()
      layout()
      update()
    end)
    layout()
  end
  update()
end)
