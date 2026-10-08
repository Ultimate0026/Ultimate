--[[
	PropScatter: dresses a zone floor with that zone's props.

	Before you start: import the prop .glb files and run PropSetup.lua once. Keep the set-up props
	somewhere out of the way (ServerStorage is a good place); they are the templates.

	1. Set ZONE below to Meadow, Swamp, Lava, Crystal or Void.
	2. Select the floor part(s) of that zone in the Explorer.
	3. Paste this whole file into the Command Bar and press Enter.

	Props land on the top of each selected floor, away from its edges, with random turns and sizes.
	A lane down the middle is kept clear so players and guardians have room to run: the lane runs
	front to back across the floor's shorter side, the way your zone banners are lined up.
	Clones go into Workspace > MapProps > <ZONE>. Running it again for the same zone replaces them.
]]

local ZONE = "Meadow"
local COUNT = 26          -- props per selected floor
local KEEP_CLEAR = 0.3    -- share of the floor's long side kept free down the middle
local EDGE = 4            -- studs kept free along the floor's edges
local GAP = 2             -- extra studs between props
local SCALE_MIN, SCALE_MAX = 0.85, 1.2
local SEED = 1            -- change for a different layout

local rng = Random.new(SEED)

local templates = {}
local function collect(root)
	for _, d in ipairs(root:GetDescendants()) do
		if d:IsA("Model") and d:GetAttribute("PropZone") == ZONE and not d:FindFirstAncestor("MapProps") then
			table.insert(templates, d)
		end
	end
end
collect(game:GetService("ServerStorage"))
collect(game:GetService("ReplicatedStorage"))
collect(workspace)
if #templates == 0 then
	error(("PropScatter: no %s props found. Import them and run PropSetup.lua first."):format(ZONE))
end

local floors = {}
for _, s in ipairs(game:GetService("Selection"):Get()) do
	if s:IsA("BasePart") then
		table.insert(floors, s)
	end
end
if #floors == 0 then
	error("PropScatter: select the zone's floor part(s) in the Explorer, then run this again.")
end

local root = workspace:FindFirstChild("MapProps")
if not root then
	root = Instance.new("Folder")
	root.Name = "MapProps"
	root.Parent = workspace
end
local old = root:FindFirstChild(ZONE)
if old then
	old:Destroy()
end
local folder = Instance.new("Folder")
folder.Name = ZONE
folder.Parent = root

local placed = 0
for _, floor in ipairs(floors) do
	local size = floor.Size
	local longIsX = size.X >= size.Z
	local taken = {}
	local tries = 0
	local here = 0
	while here < COUNT and tries < COUNT * 40 do
		tries += 1
		local x = rng:NextNumber(-size.X / 2 + EDGE, size.X / 2 - EDGE)
		local z = rng:NextNumber(-size.Z / 2 + EDGE, size.Z / 2 - EDGE)
		local along = longIsX and x or z
		local long = longIsX and size.X or size.Z
		if math.abs(along) > KEEP_CLEAR * long / 2 then
			local template = templates[rng:NextInteger(1, #templates)]
			local scale = rng:NextNumber(SCALE_MIN, SCALE_MAX)
			local _, box = template:GetBoundingBox()
			local radius = math.max(box.X, box.Z) / 2 * scale
			local free = true
			for _, t in ipairs(taken) do
				if (Vector2.new(x, z) - t.pos).Magnitude < radius + t.radius + GAP then
					free = false
					break
				end
			end
			if free then
				local clone = template:Clone()
				clone:ScaleTo(template:GetScale() * scale)
				local top = floor.CFrame * CFrame.new(x, size.Y / 2, z)
				clone:PivotTo(top * CFrame.Angles(0, rng:NextNumber(0, 2 * math.pi), 0))
				clone.Parent = folder
				table.insert(taken, { pos = Vector2.new(x, z), radius = radius })
				here += 1
			end
		end
	end
	placed += here
end
print(("PropScatter: placed %d %s prop(s) on %d floor(s)"):format(placed, ZONE, #floors))
