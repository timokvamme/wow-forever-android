-- Shared by the launcher's addons (added to the top of each): their settings pages in
-- Options > AddOns, a slash command for each, the /forever menu and the minimap button.
if not ForeverTweaks then
  ForeverTweaks = { pages = {} }
  local hub = ForeverTweaks

  function hub.open(page)
    if page.category and Settings and Settings.OpenToCategory then
      Settings.OpenToCategory(page.category:GetID())
    end
  end

  local button

  local function showMenu(owner)
    if MenuUtil and MenuUtil.CreateContextMenu then
      MenuUtil.CreateContextMenu(owner or button, function(_, root)
        root:CreateTitle("Forever addons")
        for _, page in ipairs(hub.pages) do
          root:CreateButton(page.title .. "   " .. page.slash, function() hub.open(page) end)
        end
      end)
    elseif hub.pages[1] then
      hub.open(hub.pages[1])
    end
  end

  -- A round button on the edge of the minimap that lists the addons' settings pages.
  button = CreateFrame("Button", "ForeverTweaksMinimapButton", Minimap)
  button:SetSize(31, 31)
  button:SetFrameStrata("MEDIUM")
  button:SetFrameLevel(8)
  button:SetPoint("CENTER", Minimap, "BOTTOMLEFT", 14, 14)
  button:RegisterForClicks("AnyUp")
  button:SetHighlightTexture("Interface\\Minimap\\UI-Minimap-ZoomButton-Highlight")
  local icon = button:CreateTexture(nil, "BACKGROUND")
  icon:SetTexture("Interface\\Icons\\INV_Misc_Gear_01")
  icon:SetSize(20, 20)
  icon:SetPoint("CENTER", 0, 1)
  local border = button:CreateTexture(nil, "OVERLAY")
  border:SetTexture("Interface\\Minimap\\MiniMap-TrackingBorder")
  border:SetSize(53, 53)
  border:SetPoint("TOPLEFT")
  button:SetScript("OnClick", function(self) showMenu(self) end)
  button:SetScript("OnEnter", function(self)
    GameTooltip:SetOwner(self, "ANCHOR_LEFT")
    GameTooltip:AddLine("Forever addons")
    GameTooltip:AddLine("Click to change their settings. Same as /forever.", 1, 1, 1, true)
    GameTooltip:Show()
  end)
  button:SetScript("OnLeave", function() GameTooltip:Hide() end)

  SLASH_FOREVERTWEAKS1 = "/forever"
  SlashCmdList.FOREVERTWEAKS = function() showMenu(button) end

  -- defs: { key, label, default, [min, max, step], [tooltip] }; booleans become checkboxes and
  -- numbers sliders. Values are saved in db (the addon's SavedVariables table).
  function hub.buildSettings(title, slash, db, defs, onChange)
    for _, def in ipairs(defs) do
      if db[def.key] == nil then db[def.key] = def.default end
    end
    local page = { title = title, slash = slash }
    table.insert(hub.pages, page)
    local key = "FOREVERTWEAKS_" .. title:upper():gsub("%W", "")
    _G["SLASH_" .. key .. "1"] = slash
    SlashCmdList[key] = function() hub.open(page) end

    if not (Settings and Settings.RegisterVerticalLayoutCategory) then return end
    local category = Settings.RegisterVerticalLayoutCategory(title)
    page.category = category
    for _, def in ipairs(defs) do
      local isBool = type(def.default) == "boolean"
      local setting = Settings.RegisterAddOnSetting(category, key .. "_" .. def.key, def.key, db,
        isBool and Settings.VarType.Boolean or Settings.VarType.Number, def.label, def.default)
      setting:SetValueChangedCallback(function() if onChange then onChange(def.key) end end)
      if isBool then
        Settings.CreateCheckbox(category, setting, def.tooltip)
      else
        local options = Settings.CreateSliderOptions(def.min, def.max, def.step or 1)
        if MinimalSliderWithSteppersMixin then
          options:SetLabelFormatter(MinimalSliderWithSteppersMixin.Label.Right)
        end
        Settings.CreateSlider(category, setting, options, def.tooltip)
      end
    end
    Settings.RegisterAddOnCategory(category)
  end
end

