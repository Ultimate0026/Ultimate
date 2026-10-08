--[[
	CliffDresser: turns plain wall blocks into themed cliffs.

	Before you start: run PropSetup.lua on the imported Cliff_* models, and run ZoneDresser.lua for the
	zone first (it tags the zone floor, which tells this script which way the valley is).

	1. Set ZONE below to Meadow, Swamp, Lava, Crystal or Void.
	2. Select that zone's wall blocks in the Explorer.
	3. Paste this whole file into the Command Bar and press Enter.

	Each block gets cliff pieces stretched to its exact size, rocky face turned toward the valley. The
	block itself stays where it is as an invisible collision wall, so gameplay doesn't change.
	Cliffs go into Workspace > MapProps > <ZONE> > Cliffs. Running it again on the same blocks replaces
	their cliffs; to undo, delete the cliffs and set the blocks' Transparency back to 0.
]]

local ZONE = "Meadow"
local PIECE_WIDTH = 24  -- roughly how wide each cliff piece is before stretching

local template
for _, root in ipairs({ game:GetService("ServerStorage"), game:GetService("ReplicatedStorage"), workspace }) do
	for _, d in ipairs(root:GetDescendants()) do
		if not template and d:IsA("Model") and d:GetAttribute("PropZone") == ZONE
			and d:GetAttribute("PropKind") == "Cliff" and not d:FindFirstAncestor("MapProps") then
			template = d
		end
	end
end
if not template then
	error(("CliffDresser: no Cliff_%s found. Import it and run PropSetup.lua first."):format(ZONE))
end

local blocks = {}
for _, s in ipairs(game:GetService("Selection"):Get()) do
	if s:IsA("BasePart") then
		table.insert(blocks, s)
	end
end
if #blocks == 0 then
	error("CliffDresser: select the zone's wall blocks in the Explorer, then run this again.")
end

-- which way is the valley: toward the zone floor tagged by ZoneDresser, or else the middle of the walls
local floors = {}
for _, d in ipairs(workspace:GetDescendants()) do
	if d:IsA("BasePart") and d:GetAttribute("Zone") == ZONE and not d:FindFirstAncestor("MapProps") then
		table.insert(floors, d)
	end
end
local middle = Vector3.zero
for _, b in ipairs(blocks) do
	middle += b.Position / #blocks
end
if #floors == 0 then
	warn("CliffDresser: no floor tagged with Zone = " .. ZONE .. "; facing cliffs toward the middle of the selection")
end
local function valleyPoint(pos)
	local best, bestDist = middle, math.huge
	for _, f in ipairs(floors) do
		local local_ = f.CFrame:PointToObjectSpace(pos)
		local half = f.Size / 2
		local clamped = Vector3.new(math.clamp(local_.X, -half.X, half.X), 0, math.clamp(local_.Z, -half.Z, half.Z))
		local p = f.CFrame:PointToWorldSpace(clamped)
		local dist = (Vector3.new(p.X, 0, p.Z) - Vector3.new(pos.X, 0, pos.Z)).Magnitude
		if dist < bestDist then
			best, bestDist = p, dist
		end
	end
	return best
end

local root = workspace:FindFirstChild("MapProps") or Instance.new("Folder")
root.Name = "MapProps"
root.Parent = workspace
local zoneFolder = root:FindFirstChild(ZONE) or Instance.new("Folder")
zoneFolder.Name = ZONE
zoneFolder:SetAttribute("Zone", ZONE)
zoneFolder.Parent = root
local cliffs = zoneFolder:FindFirstChild("Cliffs") or Instance.new("Folder")
cliffs.Name = "Cliffs"
cliffs.Parent = zoneFolder

local selected = {}
for _, b in ipairs(blocks) do
	selected[b] = true
end
for _, c in ipairs(cliffs:GetChildren()) do
	local source = c:FindFirstChild("SourceBlock")
	if source and selected[source.Value] then
		c:Destroy()
	end
end

-- template size measured once, in its own upright frame
local _, baseSize = template:GetBoundingBox()

local function stretch(model, scale)
	-- scale every part about the pivot, per axis (cliff parts are axis-aligned with the model)
	local p = model:GetPivot()
	for _, part in ipairs(model:GetDescendants()) do
		if part:IsA("BasePart") then
			local rel = p:ToObjectSpace(part.CFrame)
			part.Size = Vector3.new(part.Size.X * scale.X, part.Size.Y * scale.Y, part.Size.Z * scale.Z)
			part.CFrame = p * CFrame.new(rel.Position * scale) * rel.Rotation
		end
	end
end

local made = 0
for _, block in ipairs(blocks) do
	local size = block.Size
	local alongX = size.X >= size.Z
	local length = alongX and size.X or size.Z
	local depth = alongX and size.Z or size.X
	-- face toward the valley along the block's short axis
	local toValley = valleyPoint(block.Position) - block.Position
	local shortAxis = alongX and block.CFrame.LookVector or block.CFrame.RightVector
	shortAxis = Vector3.new(shortAxis.X, 0, shortAxis.Z).Unit
	local facing = (toValley:Dot(shortAxis) >= 0) and shortAxis or -shortAxis
	local lengthAxis = Vector3.new(0, 1, 0):Cross(facing).Unit
	local count = math.max(1, math.round(length / PIECE_WIDTH))
	local width = length / count
	local bottom = block.Position - Vector3.new(0, size.Y / 2, 0)
	for i = 1, count do
		local clone = template:Clone()
		local scale = Vector3.new(width * 1.03 / baseSize.X, size.Y / baseSize.Y, depth / baseSize.Z)
		stretch(clone, scale)
		local offset = (i - 0.5) * width - length / 2
		local pos = bottom + lengthAxis * offset
		clone:PivotTo(CFrame.lookAt(pos, pos + facing))
		local tag = Instance.new("ObjectValue")
		tag.Name = "SourceBlock"
		tag.Value = block
		tag.Parent = clone
		clone.Parent = cliffs
		made += 1
	end
	block.Transparency = 1
	block.CastShadow = false
	block:SetAttribute("CliffDressed", true)
end
print(("CliffDresser: placed %d %s cliff piece(s) on %d block(s)"):format(made, ZONE, #blocks))
