--[[
	MapLighting: bright, soft lighting for the whole map, plus a mood for each zone.

	Paste this whole file into the Studio Command Bar and press Enter. It:
	  - sets up Lighting: a warm afternoon sun, soft shadows, a light haze (Atmosphere), a gentle glow
	    on neon (Bloom), sun rays and a little extra colour (ColorCorrection named "SlimeMood")
	  - installs a LocalScript called ZoneMood in StarterPlayerScripts. When a player walks onto a floor
	    tagged by ZoneDresser.lua, ZoneMood fades the haze and tint to that zone's mood: warm and clear in
	    the Meadow, misty green in the Swamp, hot orange haze in the Lava zone, cool and bright in the
	    Crystal zone, and dark purple in the Void. Back on untagged ground it fades to the normal look.

	Running it again is safe; it replaces what it made before.
]]

local Lighting = game:GetService("Lighting")

Lighting.ClockTime = 14.5
Lighting.Brightness = 2.6
Lighting.Ambient = Color3.fromRGB(110, 110, 120)
Lighting.OutdoorAmbient = Color3.fromRGB(150, 150, 160)
Lighting.EnvironmentDiffuseScale = 0.6
Lighting.EnvironmentSpecularScale = 0.6
Lighting.GlobalShadows = true
Lighting.ShadowSoftness = 0.35
pcall(function()
	Lighting.Technology = Enum.Technology.ShadowMap
end)

local function fresh(className, name)
	local old = Lighting:FindFirstChild(name)
	if old then
		old:Destroy()
	end
	local obj = Instance.new(className)
	obj.Name = name
	obj.Parent = Lighting
	return obj
end

for _, c in ipairs(Lighting:GetChildren()) do
	if c:IsA("Atmosphere") then
		c:Destroy()
	end
end
local atmosphere = fresh("Atmosphere", "Atmosphere")
atmosphere.Density = 0.25
atmosphere.Offset = 0.2
atmosphere.Color = Color3.fromHex("#C8D8E8")
atmosphere.Decay = Color3.fromHex("#92A9C2")
atmosphere.Glare = 0.2
atmosphere.Haze = 0.6

local bloom = fresh("BloomEffect", "SlimeBloom")
bloom.Intensity = 0.6
bloom.Size = 28
bloom.Threshold = 1.4

local rays = fresh("SunRaysEffect", "SlimeSunRays")
rays.Intensity = 0.06
rays.Spread = 0.6

local mood = fresh("ColorCorrectionEffect", "SlimeMood")
mood.Saturation = 0.12
mood.Contrast = 0.05
mood.Brightness = 0.02
mood.TintColor = Color3.new(1, 1, 1)

local ZONE_MOOD = [==[
-- ZoneMood (installed by MapLighting.lua): fades the haze and colour to the mood of the zone you stand in.
local Players = game:GetService("Players")
local Lighting = game:GetService("Lighting")
local TweenService = game:GetService("TweenService")

local MOODS = {
	Default = { density = 0.25, color = "#C8D8E8", decay = "#92A9C2", haze = 0.6, glare = 0.2, tint = "#FFFFFF", saturation = 0.12, brightness = 0.02 },
	Meadow = { density = 0.22, color = "#E8F2D0", decay = "#A8C890", haze = 0.5, glare = 0.3, tint = "#FFF8EC", saturation = 0.18, brightness = 0.03 },
	Swamp = { density = 0.42, color = "#9DB89A", decay = "#4F6B55", haze = 2.2, glare = 0, tint = "#E8F5E2", saturation = 0.0, brightness = -0.02 },
	Lava = { density = 0.38, color = "#FFB27A", decay = "#8A2D10", haze = 1.6, glare = 0.6, tint = "#FFE8DA", saturation = 0.18, brightness = 0.0 },
	Crystal = { density = 0.3, color = "#DCE6FF", decay = "#9AA6E8", haze = 1.0, glare = 0.5, tint = "#EEF0FF", saturation = 0.08, brightness = 0.04 },
	Void = { density = 0.5, color = "#5A3C7A", decay = "#1A0E2A", haze = 2.5, glare = 0, tint = "#E6D8FF", saturation = 0.08, brightness = -0.06 },
}
local FADE = TweenInfo.new(1.6, Enum.EasingStyle.Sine, Enum.EasingDirection.InOut)

local player = Players.LocalPlayer
local params = RaycastParams.new()
params.FilterType = Enum.RaycastFilterType.Exclude

local function zoneUnder(rootPart)
	params.FilterDescendantsInstances = { player.Character }
	local hit = workspace:Raycast(rootPart.Position, Vector3.new(0, -80, 0), params)
	local inst = hit and hit.Instance
	while inst and inst ~= workspace do
		local zone = inst:GetAttribute("Zone")
		if zone and MOODS[zone] then
			return zone
		end
		inst = inst.Parent
	end
	return "Default"
end

local current
local function apply(zone)
	local m = MOODS[zone]
	local atmosphere = Lighting:FindFirstChildOfClass("Atmosphere")
	if atmosphere then
		TweenService:Create(atmosphere, FADE, {
			Density = m.density, Color = Color3.fromHex(m.color), Decay = Color3.fromHex(m.decay), Haze = m.haze, Glare = m.glare,
		}):Play()
	end
	local cc = Lighting:FindFirstChild("SlimeMood")
	if cc then
		TweenService:Create(cc, FADE, {
			TintColor = Color3.fromHex(m.tint), Saturation = m.saturation, Brightness = m.brightness,
		}):Play()
	end
end

while true do
	local character = player.Character
	local rootPart = character and character:FindFirstChild("HumanoidRootPart")
	if rootPart then
		local zone = zoneUnder(rootPart)
		if zone ~= current then
			current = zone
			apply(zone)
		end
	end
	task.wait(0.4)
end
]==]

local scripts = game:GetService("StarterPlayer"):FindFirstChildOfClass("StarterPlayerScripts")
local old = scripts:FindFirstChild("ZoneMood")
if old then
	old:Destroy()
end
local zoneMood = Instance.new("LocalScript")
zoneMood.Name = "ZoneMood"
zoneMood.Source = ZONE_MOOD
zoneMood.Parent = scripts

print("MapLighting: lighting set and ZoneMood installed in StarterPlayerScripts")
