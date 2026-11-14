-- CompactMinimap: lets the minimap and its zone bar go all the way into a screen corner. Edit
-- Mode keeps the whole minimap box (with its empty margin) on screen; this keeps only the parts you
-- can see on screen instead, so you can drag it right into the corner in Edit Mode.

local addonName = ...
local db

-- Options shown in Options > AddOns > Compact Minimap (and /fmap).
local OPTIONS = {
  { key = "toEdge", label = "Allow the minimap into the screen corner", default = true },
}

---------------------------------------------------------------------------------------------------

local cluster = _G.MinimapCluster

-- The parts of the minimap you can see, in the cluster's coordinates.
local function visibleRect()
  local parts = { cluster.MinimapContainer or _G.Minimap, cluster.ZoneTextButton, cluster.BorderTop }
  local scale = cluster:GetEffectiveScale()
  local left, bottom, right, top
  for _, part in ipairs(parts) do
    if part and part:IsShown() and part:GetLeft() then
      local l, b, w, h = part:GetRect()
      local s = part:GetEffectiveScale() / scale
      l, b, w, h = l * s, b * s, w * s, h * s
      left = left and math.min(left, l) or l
      bottom = bottom and math.min(bottom, b) or b
      right = right and math.max(right, l + w) or (l + w)
      top = top and math.max(top, b + h) or (b + h)
    end
  end
  return left, bottom, right, top
end

local function unclamp()
  if not db or not db.toEdge or not cluster or not cluster:GetLeft() then return end
  local left, bottom, right, top = visibleRect()
  if not left then return end
  cluster:SetClampRectInsets(left - cluster:GetLeft(), right - cluster:GetRight(),
    top - cluster:GetTop(), bottom - cluster:GetBottom())
end

local events = CreateFrame("Frame")
events:RegisterEvent("ADDON_LOADED")
events:RegisterEvent("PLAYER_ENTERING_WORLD")
events:SetScript("OnEvent", function(_, event, name)
  if event == "ADDON_LOADED" then
    if name ~= addonName then return end
    CompactMinimapDB = CompactMinimapDB or {}
    db = CompactMinimapDB
    ForeverTweaks.buildSettings("Compact Minimap", "/fmap", db, OPTIONS, unclamp)
  end
  C_Timer.After(0.5, unclamp)
end)

if cluster then
  -- Edit Mode recalculates the clamp from its selection box; put ours back right after.
  if cluster.UpdateClampOffsets then hooksecurefunc(cluster, "UpdateClampOffsets", unclamp) end
  cluster:HookScript("OnSizeChanged", function() C_Timer.After(0, unclamp) end)
end
if EditModeManagerFrame then
  EditModeManagerFrame:HookScript("OnShow", function() C_Timer.After(0, unclamp) end)
  EditModeManagerFrame:HookScript("OnHide", function() C_Timer.After(0.2, unclamp) end)
end
