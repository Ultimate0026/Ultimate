--[[
	ZoneDresser: dresses one zone of the valley.

	Before you start: import the prop .glb files and run PropSetup.lua once. Keep the set-up props
	somewhere out of the way (ServerStorage is a good place); they are the templates.

	1. Set ZONE below to Meadow, Swamp, Lava, Crystal or Void.
	2. Select the floor part(s) of that zone in the Explorer.
	3. Paste this whole file into the Command Bar and press Enter.

	It adds, to each selected floor:
	  - the zone's props, scattered with random turns and sizes, away from the edges
	  - a themed path down the middle (stepping stones, a boardwalk, basalt over glowing lava, ice tiles,
	    glowing void tiles) that shows players where to run; the area around it is kept free of props
	  - ambient particles over the whole floor (pollen, swamp bubbles and mist, embers, sparkles, void motes)
	  - a Zone attribute on the floor, which ZoneMood (installed by MapLighting.lua) uses to change the
	    mood when a player walks in, and which CliffDresser.lua uses to face the cliffs into the valley

	The path runs front to back across the floor's shorter side, the way your zone banners are lined up.
	Everything goes into Workspace > MapProps > <ZONE>. Running it again for a zone replaces its props,
	path and particles (cliffs from CliffDresser.lua are kept).
]]

local ZONE = "Meadow"
local COUNT = 26          -- props per selected floor
local PATH_WIDTH = 10     -- studs
local KEEP_CLEAR = 0.3    -- share of the floor's long side kept free of props around the path
local EDGE = 4            -- studs kept free along the floor's edges
local GAP = 2             -- extra studs between props
local SCALE_MIN, SCALE_MAX = 0.85, 1.2
local SEED = 1            -- change for a different layout

local rng = Random.new(SEED)

local PATHS = {
	Meadow = { style = "stones", color = "#C9BFAE", material = Enum.Material.Slate },
	Swamp = { style = "planks", color = "#8A6A48", material = Enum.Material.Wood },
	Lava = { style = "tiles", color = "#3A302C", material = Enum.Material.Basalt, glow = "#FF6A00" },
	Crystal = { style = "tiles", color = "#CFE9FF", material = Enum.Material.Ice, glow = "#B9A7FF" },
	Void = { style = "tiles", color = "#241634", material = Enum.Material.Slate, glow = "#B026FF" },
}

local SPARKLE = "rbxasset://textures/particles/sparkles_main.dds"
local SMOKE = "rbxasset://textures/particles/smoke_main.dds"
local AMBIENCE = {
	Meadow = {
		{ texture = SPARKLE, color = "#FFF3B0", size = 0.25, rate = 8, life = { 6, 10 }, speed = { 0.3, 1 }, lift = 0.15, glow = 0.6 },
	},
	Swamp = {
		{ texture = SPARKLE, color = "#A8E07A", size = 0.35, rate = 8, life = { 4, 7 }, speed = { 0.2, 0.6 }, lift = 0.8, glow = 0.3 },
		{ texture = SMOKE, color = "#B9D1B0", size = 9, rate = 2, life = { 8, 12 }, speed = { 0.2, 0.5 }, lift = 0, glow = 0, transparency = 0.88, low = true },
	},
	Lava = {
		{ texture = SPARKLE, color = "#FF9A3D", size = 0.3, rate = 16, life = { 2, 4 }, speed = { 2, 5 }, lift = 2, glow = 1 },
	},
	Crystal = {
		{ texture = SPARKLE, color = "#E7F4FF", size = 0.35, rate = 10, life = { 3, 6 }, speed = { 0, 0.3 }, lift = 0, glow = 1 },
	},
	Void = {
		{ texture = SPARKLE, color = "#C77DFF", size = 0.3, rate = 12, life = { 4, 8 }, speed = { 0.2, 0.8 }, lift = 0.5, glow = 1 },
	},
}

assert(PATHS[ZONE], "ZoneDresser: ZONE must be Meadow, Swamp, Lava, Crystal or Void")

local templates = {}
local function collect(root)
	for _, d in ipairs(root:GetDescendants()) do
		if d:IsA("Model") and d:GetAttribute("PropZone") == ZONE and d:GetAttribute("PropKind") == "Scatter"
			and not d:FindFirstAncestor("MapProps") then
			table.insert(templates, d)
		end
	end
end
collect(game:GetService("ServerStorage"))
collect(game:GetService("ReplicatedStorage"))
collect(workspace)
if #templates == 0 then
	error(("ZoneDresser: no %s props found. Import them and run PropSetup.lua first."):format(ZONE))
end

local floors = {}
for _, s in ipairs(game:GetService("Selection"):Get()) do
	if s:IsA("BasePart") then
		table.insert(floors, s)
	end
end
if #floors == 0 then
	error("ZoneDresser: select the zone's floor part(s) in the Explorer, then run this again.")
end

local root = workspace:FindFirstChild("MapProps") or Instance.new("Folder")
root.Name = "MapProps"
root.Parent = workspace
local zoneFolder = root:FindFirstChild(ZONE) or Instance.new("Folder")
zoneFolder.Name = ZONE
zoneFolder:SetAttribute("Zone", ZONE)
zoneFolder.Parent = root
for _, name in ipairs({ "Props", "Path", "Ambience" }) do
	local old = zoneFolder:FindFirstChild(name)
	if old then
		old:Destroy()
	end
	local f = Instance.new("Folder")
	f.Name = name
	f.Parent = zoneFolder
end

local function makePart(props)
	local p = Instance.new("Part")
	p.Anchored = true
	p.CanCollide = false
	p.CanTouch = false
	p.CanQuery = true
	p.TopSurface = Enum.SurfaceType.Smooth
	p.BottomSurface = Enum.SurfaceType.Smooth
	if props.Shape then
		p.Shape = props.Shape
	end
	for k, v in pairs(props) do
		if k ~= "Shape" then
			p[k] = v
		end
	end
	return p
end

local function dressPath(floor)
	local style = PATHS[ZONE]
	local size = floor.Size
	local longIsX = size.X >= size.Z
	local runLength = longIsX and size.Z or size.X
	-- local frame where the path runs along +Z, centred on the floor top
	local top = floor.CFrame * CFrame.new(0, size.Y / 2, 0)
	if not longIsX then
		top = top * CFrame.Angles(0, math.pi / 2, 0)
	end
	local folder = zoneFolder.Path
	local function add(cf, partSize, color, material, shape)
		local p = makePart({
			Name = "PathTile",
			Size = partSize,
			CFrame = top * cf,
			Color = Color3.fromHex(color),
			Material = material,
			Shape = shape or Enum.PartType.Block,
		})
		p.Parent = folder
		return p
	end
	if style.glow then
		local glow = add(CFrame.new(0, 0.03, 0), Vector3.new(PATH_WIDTH - 1, 0.1, runLength - 2 * EDGE), style.glow, Enum.Material.Neon)
		glow.Name = "PathGlow"
	end
	local z = -runLength / 2 + EDGE + 2
	while z < runLength / 2 - EDGE - 2 do
		if style.style == "stones" then
			local d = rng:NextNumber(3.2, 4.4)
			local x = rng:NextNumber(-1.8, 1.8)
			add(CFrame.new(x, 0.1, z) * CFrame.Angles(0, 0, math.pi / 2), Vector3.new(0.3, d, d), style.color, style.material, Enum.PartType.Cylinder)
			z += d + rng:NextNumber(0.6, 1.4)
		elseif style.style == "planks" then
			add(CFrame.new(rng:NextNumber(-0.3, 0.3), 0.2, z) * CFrame.Angles(0, math.rad(rng:NextNumber(-3, 3)), 0),
				Vector3.new(PATH_WIDTH - 1, 0.4, 1.5), style.color, style.material)
			z += 1.85
		else
			local tile = (PATH_WIDTH - 1.5) / 2
			for _, x in ipairs({ -tile / 2 - 0.25, tile / 2 + 0.25 }) do
				add(CFrame.new(x, 0.12, z + tile / 2) * CFrame.Angles(0, math.rad(rng:NextNumber(-4, 4)), 0),
					Vector3.new(tile, 0.24, tile), style.color, style.material)
			end
			z += tile + 0.5
		end
	end
end

local function dressAmbience(floor)
	local size = floor.Size
	for i, a in ipairs(AMBIENCE[ZONE]) do
		local height = a.low and 4 or 16
		local holder = makePart({
			Name = "Ambience" .. i,
			Size = Vector3.new(size.X, height, size.Z),
			CFrame = floor.CFrame * CFrame.new(0, size.Y / 2 + height / 2, 0),
			Transparency = 1,
			CanQuery = false,
			CastShadow = false,
		})
		local e = Instance.new("ParticleEmitter")
		e.Texture = a.texture
		e.Color = ColorSequence.new(Color3.fromHex(a.color))
		e.Size = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0), NumberSequenceKeypoint.new(0.2, a.size),
			NumberSequenceKeypoint.new(0.8, a.size), NumberSequenceKeypoint.new(1, 0) })
		local t = a.transparency or 0.15
		e.Transparency = NumberSequence.new({ NumberSequenceKeypoint.new(0, 1), NumberSequenceKeypoint.new(0.2, t),
			NumberSequenceKeypoint.new(0.8, t), NumberSequenceKeypoint.new(1, 1) })
		e.Rate = a.rate * math.max(1, size.X * size.Z / 6000)
		e.Lifetime = NumberRange.new(a.life[1], a.life[2])
		e.Speed = NumberRange.new(a.speed[1], a.speed[2])
		e.SpreadAngle = Vector2.new(180, 180)
		e.Acceleration = Vector3.new(0, a.lift, 0)
		e.LightEmission = a.glow
		e.Rotation = NumberRange.new(0, 360)
		e.RotSpeed = NumberRange.new(-30, 30)
		e.Shape = Enum.ParticleEmitterShape.Box
		e.ShapeStyle = Enum.ParticleEmitterShapeStyle.Volume
		e.Parent = holder
		holder.Parent = zoneFolder.Ambience
	end
end

local placed = 0
for _, floor in ipairs(floors) do
	floor:SetAttribute("Zone", ZONE)
	dressPath(floor)
	dressAmbience(floor)
	local size = floor.Size
	local longIsX = size.X >= size.Z
	local long = longIsX and size.X or size.Z
	local clear = math.max(KEEP_CLEAR * long / 2, PATH_WIDTH / 2 + 3)
	local taken = {}
	local tries, here = 0, 0
	while here < COUNT and tries < COUNT * 40 do
		tries += 1
		local x = rng:NextNumber(-size.X / 2 + EDGE, size.X / 2 - EDGE)
		local z = rng:NextNumber(-size.Z / 2 + EDGE, size.Z / 2 - EDGE)
		local along = longIsX and x or z
		if math.abs(along) > clear then
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
				clone:PivotTo(floor.CFrame * CFrame.new(x, size.Y / 2, z) * CFrame.Angles(0, rng:NextNumber(0, 2 * math.pi), 0))
				clone.Parent = zoneFolder.Props
				table.insert(taken, { pos = Vector2.new(x, z), radius = radius })
				here += 1
			end
		end
	end
	placed += here
end
print(("ZoneDresser: dressed %d %s floor(s) with %d props, a path and particles"):format(#floors, ZONE, placed))
