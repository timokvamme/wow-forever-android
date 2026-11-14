-- CompactQuests: Blizzard's quest tracker, smaller and without the "All Objectives" and "Quests"
-- headers taking space. It only resizes and moves Blizzard's frames after Blizzard lays them out;
-- it doesn't change which quests are tracked or read quest data.

local addonName = ...
local db

-- Options shown in Options > AddOns > Compact Quests (and /fquests).
local OPTIONS = {
  { key = "scale", label = "Tracker size (%)", default = 80, min = 40, max = 150, step = 5 },
  { key = "hideHeaders", label = "Hide the headers and their space", default = true },
}

---------------------------------------------------------------------------------------------------

local tracker = _G.ObjectiveTrackerFrame
local pending = false

local function setHeaderShown(header, shown)
  if not header then return end
  header:SetAlpha(shown and 1 or 0)
  if header.EnableMouse then header:EnableMouse(shown) end
  if header.MinimizeButton then header.MinimizeButton:EnableMouse(shown) end
end

-- After Blizzard stacks the sections, pull each one up over its own (hidden) header.
local function relayout()
  if not tracker or not db then return end
  if InCombatLockdown() then
    pending = true
    return
  end
  pending = false
  tracker:SetScale(db.scale / 100)
  setHeaderShown(tracker.Header, not db.hideHeaders)
  if not tracker.modules then return end
  local previous
  for _, module in ipairs(tracker.modules) do
    local used = module.GetContentsHeight and module:GetContentsHeight() or 0
    if used > 0 and module:IsShown() then
      setHeaderShown(module.Header, not db.hideHeaders)
      if db.hideHeaders then
        local headerHeight = module.headerHeight or 25
        module:ClearAllPoints()
        if previous then
          module:SetPoint("TOP", previous, "BOTTOM", 0, headerHeight - (tracker.moduleSpacing or 0))
        else
          module:SetPoint("TOP", tracker, "TOP", 0, headerHeight - 2)
        end
        module:SetPoint("LEFT", tracker, "LEFT", module.leftMargin or 0, 0)
      end
      previous = module
    end
  end
end

local events = CreateFrame("Frame")
events:RegisterEvent("ADDON_LOADED")
events:RegisterEvent("PLAYER_ENTERING_WORLD")
events:RegisterEvent("PLAYER_REGEN_ENABLED")
events:SetScript("OnEvent", function(_, event, name)
  if event == "ADDON_LOADED" then
    if name ~= addonName then return end
    CompactQuestsDB = CompactQuestsDB or {}
    db = CompactQuestsDB
    ForeverTweaks.buildSettings("Compact Quests", "/fquests", db, OPTIONS, relayout)
  elseif event == "PLAYER_REGEN_ENABLED" then
    if pending then relayout() end
  else
    relayout()
  end
end)

if tracker and tracker.Update then
  hooksecurefunc(tracker, "Update", relayout)
end
if EditModeManagerFrame then
  EditModeManagerFrame:HookScript("OnHide", function() C_Timer.After(0.2, relayout) end)
end
