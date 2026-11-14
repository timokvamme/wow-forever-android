-- CompactQuests: a smaller quest tracker that tracks the quests that matter right now.
-- Edit these to tweak.

local addonName = ...
local db

-- Options shown in Options > AddOns > Compact Quests (and /fquests).
-- How quests are picked when auto tracking is on, best first:
--   1. a quest that made progress in the last few minutes (the mobs you are killing)
--   2. the quest whose objective or turn-in is closest to you
-- Quests on another continent go last. Manual tracking is replaced while it is on.
local OPTIONS = {
  { key = "scale", label = "Tracker size (%)", default = 80, min = 40, max = 150, step = 5 },
  { key = "maxQuests", label = "Quests shown", default = 4, min = 1, max = 15 },
  { key = "autoTrack", label = "Track nearest and most active quests", default = true },
  { key = "recentMinutes", label = "Keep quests with progress on top (minutes)", default = 5, min = 0, max = 30 },
  { key = "hideHeaders", label = "Hide the tracker headers", default = true },
  { key = "compactSpacing", label = "Tighter spacing", default = true },
}

---------------------------------------------------------------------------------------------------

local lastProgress = {}
local pending = false

local function questScore(questID)
  local score = 1e14
  local ok, distanceSq, onContinent = pcall(C_QuestLog.GetDistanceSqToQuest, questID)
  if ok and distanceSq and (not canaccessvalue or canaccessvalue(distanceSq)) then
    score = onContinent and distanceSq or (1e13 + distanceSq)
  end
  local progressed = lastProgress[questID]
  if progressed and GetTime() - progressed < db.recentMinutes * 60 then
    -- most recent progress first, ahead of everything else
    score = -1e9 + (GetTime() - progressed)
  end
  return score
end

local function candidateQuests()
  local quests = {}
  for i = 1, C_QuestLog.GetNumQuestLogEntries() do
    local info = C_QuestLog.GetInfo(i)
    if info and not info.isHeader and not info.isHidden and not info.isTask and not info.isBounty and info.questID then
      table.insert(quests, { id = info.questID, score = questScore(info.questID) })
    end
  end
  table.sort(quests, function(a, b) return a.score < b.score end)
  return quests
end

local function currentWatches()
  local list = {}
  for i = 1, C_QuestLog.GetNumQuestWatches() do
    local questID = C_QuestLog.GetQuestIDForQuestWatchIndex(i)
    if questID then table.insert(list, questID) end
  end
  return list
end

local function retrack()
  pending = false
  if not db or not db.autoTrack then return end
  if InCombatLockdown() then
    pending = true
    return
  end
  local wanted = {}
  for i, quest in ipairs(candidateQuests()) do
    if i > db.maxQuests then break end
    table.insert(wanted, quest.id)
  end
  local current = currentWatches()
  local same = #current == #wanted
  if same then
    for i = 1, #wanted do
      if current[i] ~= wanted[i] then same = false break end
    end
  end
  if same then return end
  -- The tracker lists quests in watch order, so re-add them best first.
  for _, questID in ipairs(current) do C_QuestLog.RemoveQuestWatch(questID) end
  for _, questID in ipairs(wanted) do C_QuestLog.AddQuestWatch(questID) end
end

local scheduled = false
local function scheduleRetrack(delay)
  if scheduled then return end
  scheduled = true
  C_Timer.After(delay or 1, function()
    scheduled = false
    retrack()
  end)
end

local function hideHeader(header, hide)
  if not header then return end
  local alpha = hide and 0 or 1
  if header.Text then header.Text:SetAlpha(alpha) end
  if header.Background then header.Background:SetAlpha(alpha) end
end

local defaultSpacing = {}

local function styleTracker()
  local tracker = _G.ObjectiveTrackerFrame
  if not tracker or not db then return end
  if InCombatLockdown() then
    pending = true
    return
  end
  tracker:SetScale(db.scale / 100)
  hideHeader(tracker.Header, db.hideHeaders)
  for _, name in ipairs({ "QuestObjectiveTracker", "CampaignQuestObjectiveTracker", "AchievementObjectiveTracker",
      "BonusObjectiveTracker", "WorldQuestObjectiveTracker", "ProfessionsRecipeTracker" }) do
    local module = _G[name]
    if module then hideHeader(module.Header, db.hideHeaders) end
  end
  for _, name in ipairs({ "QuestObjectiveTracker", "CampaignQuestObjectiveTracker" }) do
    local module = _G[name]
    if module then
      local saved = defaultSpacing[name]
      if not saved then
        saved = { module.headerHeight, module.fromHeaderOffsetY, module.lineSpacing, module.blockOffsetX }
        defaultSpacing[name] = saved
      end
      if db.compactSpacing then
        module.headerHeight = db.hideHeaders and 2 or 18
        module.fromHeaderOffsetY = db.hideHeaders and 0 or -4
        module.lineSpacing = 1
        module.blockOffsetX = 8
      else
        module.headerHeight, module.fromHeaderOffsetY, module.lineSpacing, module.blockOffsetX = saved[1], saved[2], saved[3], saved[4]
      end
    end
  end
  if ObjectiveTrackerManager and ObjectiveTrackerManager.UpdateAll then
    pcall(ObjectiveTrackerManager.UpdateAll, ObjectiveTrackerManager)
  end
end

local events = CreateFrame("Frame")
events:RegisterEvent("ADDON_LOADED")
events:RegisterEvent("PLAYER_ENTERING_WORLD")
events:RegisterEvent("ZONE_CHANGED")
events:RegisterEvent("ZONE_CHANGED_NEW_AREA")
events:RegisterEvent("QUEST_LOG_UPDATE")
events:RegisterEvent("QUEST_ACCEPTED")
events:RegisterEvent("QUEST_REMOVED")
events:RegisterEvent("QUEST_WATCH_UPDATE")
events:RegisterEvent("PLAYER_REGEN_ENABLED")
events:SetScript("OnEvent", function(_, event, questID)
  if event == "ADDON_LOADED" then
    if questID ~= addonName then return end
    CompactQuestsDB = CompactQuestsDB or {}
    db = CompactQuestsDB
    ForeverTweaks.buildSettings("Compact Quests", "/fquests", db, OPTIONS, function()
      styleTracker()
      scheduleRetrack(0.2)
    end)
  elseif not db then
    return
  elseif event == "PLAYER_ENTERING_WORLD" then
    styleTracker()
    C_Timer.After(2, styleTracker)
    scheduleRetrack(2)
  elseif event == "QUEST_WATCH_UPDATE" then
    if questID then lastProgress[questID] = GetTime() end
    scheduleRetrack(0.5)
  elseif event == "PLAYER_REGEN_ENABLED" then
    if pending then
      styleTracker()
      scheduleRetrack(0.5)
    end
  else
    scheduleRetrack(1)
  end
end)

-- Distance changes as you move, so check again every few seconds.
C_Timer.NewTicker(8, function() scheduleRetrack(0) end)

-- Edit Mode re-applies its own layout when it closes.
if EditModeManagerFrame then
  EditModeManagerFrame:HookScript("OnHide", function() C_Timer.After(0.5, styleTracker) end)
end
