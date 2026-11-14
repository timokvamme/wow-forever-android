-- CompactChat: shorter chat lines for a small screen. Links, player names and channel tags stay
-- clickable; only the text around them is shortened. Edit the tables below to tweak.

-- Channel tags: [1. General - Mulgore] becomes [1]; the named ones below get these short tags.
local SHORT_TAGS = {
  ["Party"] = "P",
  ["Party Leader"] = "PL",
  ["Raid"] = "R",
  ["Raid Leader"] = "RL",
  ["Raid Warning"] = "RW",
  ["Guild"] = "G",
  ["Officer"] = "O",
  ["Instance"] = "I",
  ["Instance Leader"] = "IL",
}

-- Color player names by class in every kind of chat.
local CLASS_COLORS = true

-- Let the chat window go all the way into the screen corners.
local NO_EDGE_GAP = true

-- System lines rewritten into short ones. Each entry is the game's own text (by its global name,
-- so it works in any language) and the short version, where %1, %2 are the parts that vary.
local REWRITES = {
  { "SKILL_RANK_UP", "%1 skill %2" },                    -- Your skill in Fishing has increased to 55.
  { "LOOT_ITEM_SELF_MULTIPLE", "Loot: %1 x%2" },          -- You receive loot: [Linen Cloth]x2.
  { "LOOT_ITEM_SELF", "Loot: %1" },                       -- You receive loot: [Linen Cloth].
  { "LOOT_ITEM_PUSHED_SELF_MULTIPLE", "Item: %1 x%2" },   -- You receive item: [Bread]x5.
  { "LOOT_ITEM_PUSHED_SELF", "Item: %1" },                -- You receive item: [Bread].
  { "LOOT_ITEM_CREATED_SELF_MULTIPLE", "Made: %1 x%2" },  -- You create: [Bandage]x2.
  { "LOOT_ITEM_CREATED_SELF", "Made: %1" },               -- You create: [Bandage].
  { "LOOT_MONEY_SPLIT", "Loot: %1" },                     -- Your share of the loot is 5 Copper.
  { "YOU_LOOT_MONEY", "Loot: %1" },                       -- You loot 5 Copper
  { "CURRENCY_GAINED_MULTIPLE", "Got: %1 x%2" },
  { "CURRENCY_GAINED", "Got: %1" },
  { "COMBATLOG_XPGAIN_FIRSTPERSON", "+%2 XP" },           -- Wolf dies, you gain 45 experience.
  { "COMBATLOG_XPGAIN_FIRSTPERSON_UNNAMED", "+%1 XP" },   -- You gain 45 experience.
  { "FACTION_STANDING_INCREASED", "%1 +%2" },             -- Reputation with Thunder Bluff increased by 25.
  { "FACTION_STANDING_DECREASED", "%1 -%2" },
  { "ERR_QUEST_OBJECTIVE_COMPLETE_S", "Done: %1" },
}

-- Channel notices: "Changed Channel: [5. Trade]" becomes "Joined [5]".
local NOTICES = {
  { "CHAT_YOU_CHANGED_NOTICE", "Joined " },
  { "CHAT_YOU_JOINED_NOTICE", "Joined " },
  { "CHAT_YOU_LEFT_NOTICE", "Left " },
}

-- Words between a player's name and their message.
local SPEECH = {
  { " says: ", ": " },
  { " yells: ", " (yell): " },
  { " whispers: ", " (w): " },
}

---------------------------------------------------------------------------------------------------

local function escape(text)
  return (text:gsub("([%(%)%.%+%-%*%?%[%]%^])", "%%%1"))
end

-- Turns one of the game's format strings ("Your skill in %s has increased to %d.") into a pattern.
local function toPattern(format)
  local pattern = escape(format)
  pattern = pattern:gsub("%%s", "(.+)")
  pattern = pattern:gsub("%%d", "(%%d+)")
  return pattern
end

local rewrites = {}
for _, rule in ipairs(REWRITES) do
  local format = _G[rule[1]]
  if type(format) == "string" and format ~= "" then
    table.insert(rewrites, { pattern = toPattern(format), out = rule[2] })
  end
end

local notices = {}
for _, rule in ipairs(NOTICES) do
  local format = _G[rule[1]]
  local prefix = type(format) == "string" and format:match("^(.-)|H")
  if prefix and prefix ~= "" then
    table.insert(notices, { prefix = prefix, out = rule[2] })
  end
end

local function shortTag(link, name, close)
  local number = name:match("^(%d+)%.")
  if number then return link .. "[" .. number .. "]" .. close end
  local short = SHORT_TAGS[name]
  if short then return link .. "[" .. short .. "]" .. close end
  return nil
end

local function shorten(text)
  for _, rule in ipairs(notices) do
    if text:sub(1, #rule.prefix) == rule.prefix then
      text = rule.out .. text:sub(#rule.prefix + 1)
      break
    end
  end

  text = text:gsub("(|Hchannel:[^|]*|h)%[([^%]]*)%](|h)", shortTag)

  for _, rule in ipairs(rewrites) do
    local first, last, a, b = text:find(rule.pattern)
    if first then
      local out = rule.out:gsub("%%1", function() return a or "" end):gsub("%%2", function() return b or "" end)
      text = text:sub(1, first - 1) .. out .. text:sub(last + 1)
      break
    end
  end

  for _, rule in ipairs(SPEECH) do
    local first, last = text:find(rule[1], 1, true)
    if first then
      text = text:sub(1, first - 1) .. rule[2] .. text:sub(last + 1)
      break
    end
  end
  return text
end

local function hookFrame(frame)
  if not frame or frame.compactChatHooked or not frame.AddMessage then return end
  frame.compactChatHooked = true
  local original = frame.AddMessage
  frame.AddMessage = function(self, text, ...)
    -- Some messages are hidden from addons (secret values); those pass through untouched.
    if (not canaccessvalue or canaccessvalue(text)) and type(text) == "string" then
      local ok, short = pcall(shorten, text)
      if ok and short then text = short end
    end
    return original(self, text, ...)
  end
end

local function setupFrames()
  for i = 1, (NUM_CHAT_WINDOWS or 10) do
    local frame = _G["ChatFrame" .. i]
    hookFrame(frame)
    if NO_EDGE_GAP and frame and frame.SetClampRectInsets then
      frame:SetClampRectInsets(0, 0, 0, 0)
    end
  end
  if CHAT_FRAMES then
    for _, name in ipairs(CHAT_FRAMES) do hookFrame(_G[name]) end
  end
end

local events = CreateFrame("Frame")
events:RegisterEvent("PLAYER_LOGIN")
events:RegisterEvent("PLAYER_ENTERING_WORLD")
events:SetScript("OnEvent", function()
  setupFrames()
  if CLASS_COLORS then pcall(SetCVar, "chatClassColorOverride", "0") end
end)

-- Whisper windows and new chat tabs are created later.
if FCF_OpenTemporaryWindow then hooksecurefunc("FCF_OpenTemporaryWindow", setupFrames) end
if FCF_OpenNewWindow then hooksecurefunc("FCF_OpenNewWindow", setupFrames) end
setupFrames()
