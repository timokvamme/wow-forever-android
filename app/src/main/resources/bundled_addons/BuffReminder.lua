-- BuffReminder: shows a big pulsing icon above your character when one of your own buffs is
-- missing, so you know to cast it again. Edit these to tweak.

local REMINDERS = {
  { class = "WARRIOR", spell = "Battle Shout" },
  { class = "SHAMAN", spell = "Lightning Shield" },
}

local ICON_SIZE = 56     -- icon size in pixels
local OFFSET_Y = 140     -- how far above the middle of the screen (your character)
local ONLY_IN_COMBAT = false

---------------------------------------------------------------------------------------------------

local _, playerClass = UnitClass("player")

local frame = CreateFrame("Frame", "BuffReminderFrame", UIParent)
frame:SetSize(ICON_SIZE, ICON_SIZE)
frame:SetPoint("CENTER", UIParent, "CENTER", 0, OFFSET_Y)
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
  if ONLY_IN_COMBAT and not UnitAffectingCombat("player") then return nil end
  for _, reminder in ipairs(REMINDERS) do
    if reminder.class == playerClass and knows(reminder.spell) and not hasBuff(reminder.spell) then
      return reminder.spell
    end
  end
  return nil
end

local function update()
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
events:RegisterEvent("PLAYER_ENTERING_WORLD")
events:RegisterUnitEvent("UNIT_AURA", "player")
events:RegisterEvent("SPELLS_CHANGED")
events:RegisterEvent("PLAYER_REGEN_DISABLED")
events:RegisterEvent("PLAYER_REGEN_ENABLED")
events:RegisterEvent("PLAYER_MOUNT_DISPLAY_CHANGED")
events:RegisterEvent("PLAYER_DEAD")
events:RegisterEvent("PLAYER_ALIVE")
events:RegisterEvent("PLAYER_UNGHOST")
events:SetScript("OnEvent", update)
