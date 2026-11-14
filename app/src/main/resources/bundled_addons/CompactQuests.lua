-- CompactQuests: a smaller quest tracker that tracks the quests that matter right now.
-- Edit these to tweak.

local SCALE = 0.8            -- size of the whole tracker (1 = Blizzard's size)
local MAX_QUESTS = 4         -- how many quests the tracker shows
local HIDE_HEADERS = true    -- hide the "All Objectives" and "Quests" header text and art
local COMPACT_SPACING = true -- tighter spacing between lines and quests
local AUTO_TRACK = true      -- pick and order the tracked quests automatically (see below)

-- How quests are picked when AUTO_TRACK is on, best first:
--   1. a quest that made progress in the last few minutes (the mobs you are killing)
--   2. the quest whose objective or turn-in is closest to you
-- Quests on another continent go last. Manual tracking is replaced while AUTO_TRACK is on.
local RECENT_SECONDS = 300

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
  if progressed and GetTime() - progressed < RECENT_SECONDS then
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
  if not AUTO_TRACK then return end
  if InCombatLockdown() then
    pending = true
    return
  end
  local wanted = {}
  for i, quest in ipairs(candidateQuests()) do
    if i > MAX_QUESTS then break end
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

local function hideHeader(header)
  if not header then return end
  if header.Text then header.Text:SetAlpha(0) end
  if header.Background then header.Background:SetAlpha(0) end
end

local function styleTracker()
  local tracker = _G.ObjectiveTrackerFrame
  if not tracker then return end
  tracker:SetScale(SCALE)
  if HIDE_HEADERS then
    hideHeader(tracker.Header)
    for _, name in ipairs({ "QuestObjectiveTracker", "CampaignQuestObjectiveTracker", "AchievementObjectiveTracker",
        "BonusObjectiveTracker", "WorldQuestObjectiveTracker", "ProfessionsRecipeTracker" }) do
      local module = _G[name]
      if module then hideHeader(module.Header) end
    end
  end
  if COMPACT_SPACING then
    for _, name in ipairs({ "QuestObjectiveTracker", "CampaignQuestObjectiveTracker" }) do
      local module = _G[name]
      if module then
        module.headerHeight = HIDE_HEADERS and 2 or 18
        module.fromHeaderOffsetY = HIDE_HEADERS and 0 or -4
        module.lineSpacing = 1
        module.blockOffsetX = 8
      end
    end
    if ObjectiveTrackerManager and ObjectiveTrackerManager.UpdateAll and not InCombatLockdown() then
      pcall(ObjectiveTrackerManager.UpdateAll, ObjectiveTrackerManager)
    end
  end
end

local events = CreateFrame("Frame")
events:RegisterEvent("PLAYER_ENTERING_WORLD")
events:RegisterEvent("ZONE_CHANGED")
events:RegisterEvent("ZONE_CHANGED_NEW_AREA")
events:RegisterEvent("QUEST_LOG_UPDATE")
events:RegisterEvent("QUEST_ACCEPTED")
events:RegisterEvent("QUEST_REMOVED")
events:RegisterEvent("QUEST_WATCH_UPDATE")
events:RegisterEvent("PLAYER_REGEN_ENABLED")
events:SetScript("OnEvent", function(_, event, questID)
  if event == "PLAYER_ENTERING_WORLD" then
    styleTracker()
    C_Timer.After(2, styleTracker)
    scheduleRetrack(2)
  elseif event == "QUEST_WATCH_UPDATE" then
    if questID then lastProgress[questID] = GetTime() end
    scheduleRetrack(0.5)
  elseif event == "PLAYER_REGEN_ENABLED" then
    if pending then scheduleRetrack(0.5) end
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
