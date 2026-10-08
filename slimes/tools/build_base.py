#!/usr/bin/env python3
"""Lays out the player base and writes it as a Roblox Studio builder script.

    python3 slimes/tools/build_base.py

Writes slimes/base/BaseBuilder.lua (paste into the Command Bar to build the base out of Parts) and
slimes/base/base.json (the same parts, used by the preview page). Units are studs. The base's pivot
is the bottom centre of its footprint and the entrance faces -Z.
"""
import json
import math
import os

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# theme: change these and rebuild, or recolour in Studio afterwards
FRAME = "#FF5C7A"     # coral: pillars, rails, low walls, sign frame, drips
TRIM = "#1E2440"      # navy: plinth, back wall, sign board, pad bases
GLOW = "#6CFFC9"      # mint neon: lamps, pad rims, tier edge
GLASS = "#CDEBFF"
TILE_A, TILE_B = "#FFF1F5", "#FFE0EA"       # lower floor checker
TIER_A, TIER_B = "#F1ECFF", "#E3DAFF"       # back tier checker
PAD_TOP = "#FFFFFF"
DOOR = "#FF3355"

W, D = 52.0, 40.0          # footprint
FLOOR = 1.2                # lower floor top
TIER = 2.2                 # back tier top
WALL_TOP = 9.0             # top of the glass
SLOT_X = (-20, -10, 0, 10, 20)
FRONT_Z, BACK_Z = -8.0, 9.0


def rx(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


RZ90 = [[0, -1, 0], [1, 0, 0], [0, 0, 1]]   # turns a Roblox cylinder (axis X) upright


class Base:
    def __init__(self):
        self.parts = []

    def part(self, path, name, shape, size, pos, color, material="SmoothPlastic", transparency=0.0,
             collide=True, rot=None):
        self.parts.append({"path": path, "name": name, "shape": shape, "size": [round(v, 3) for v in size],
                           "pos": [round(v, 3) for v in pos], "rot": rot, "color": color,
                           "material": material, "transparency": transparency, "collide": collide})

    def block(self, path, name, size, pos, color, **kw):
        self.part(path, name, "Block", size, pos, color, **kw)

    def column(self, path, name, d, y0, y1, x, z, color, **kw):
        """Upright cylinder from y0 to y1."""
        self.part(path, name, "Cylinder", (y1 - y0, d, d), (x, (y0 + y1) / 2, z), color, rot=RZ90, **kw)

    def rod(self, path, name, d, x0, x1, y, z, color, **kw):
        """Cylinder lying along X."""
        self.part(path, name, "Cylinder", (x1 - x0, d, d), ((x0 + x1) / 2, y, z), color, **kw)

    def ball(self, path, name, d, pos, color, **kw):
        self.part(path, name, "Ball", (d, d, d), pos, color, **kw)


def build():
    b = Base()
    hw, hd = W / 2, D / 2

    # ---- ground: plinth, checker floor, raised back tier, entrance steps
    b.block("Floor", "Plinth", (W, 1.0, D), (0, 0.5, 0), TRIM)
    for i in range(13):
        for k in range(5):
            x = -hw + 2 + 4 * i
            z = -hd + 2 + 4 * k
            b.block("Floor/Tiles", "Tile", (4, FLOOR - 1.0, 4), (x, (1.0 + FLOOR) / 2, z),
                    TILE_A if (i + k) % 2 == 0 else TILE_B)
    b.block("Floor", "Tier", (W, TIER - 0.2 - 1.0, hd), (0, (1.0 + TIER - 0.2) / 2, hd / 2), TRIM)
    for i in range(13):
        for k in range(5):
            x = -hw + 2 + 4 * i
            z = 2 + 4 * k
            b.block("Floor/Tiles", "TierTile", (4, 0.2, 4), (x, TIER - 0.1, z),
                    TIER_A if (i + k) % 2 == 0 else TIER_B)
    b.block("Floor", "TierEdge", (W - 2, 0.3, 0.3), (0, (FLOOR + TIER) / 2 + 0.1, -0.12), GLOW, material="Neon")
    b.block("Floor", "StepUpper", (16, 0.8, 1.0), (0, 0.4, -hd - 0.5), FRAME)
    b.block("Floor", "StepLower", (16, 0.4, 1.0), (0, 0.2, -hd - 1.5), TRIM)

    # ---- walls: coral kick walls, glass above, rails on top, solid back wall
    kick = 2.8
    for side in (-1, 1):
        x = side * (hw - 0.5)
        b.block("Walls", "SideWall", (1, kick - 1.0, D), (x, (1.0 + kick) / 2, 0), FRAME)
        b.block("Walls", "SideGlass", (0.4, WALL_TOP - kick, D - 2), (x, (kick + WALL_TOP) / 2, 0), GLASS,
                material="Glass", transparency=0.6)
        b.block("Walls", "SideRail", (1.2, 1.0, D), (x, WALL_TOP + 0.5, 0), FRAME)
        for z in (-hd / 3, hd / 3):
            b.block("Walls", "Post", (1.3, WALL_TOP - kick, 1.3), (x, (kick + WALL_TOP) / 2, z), FRAME)
        # front walls either side of the door
        x0, x1 = side * 10.0, side * hw
        cx, wdt = (x0 + x1) / 2, abs(x1 - x0)
        b.block("Walls", "FrontWall", (wdt, kick - 1.0, 1), (cx, (1.0 + kick) / 2, -hd + 0.5), FRAME)
        b.block("Walls", "FrontGlass", (wdt - 2, WALL_TOP - kick, 0.4), (cx, (kick + WALL_TOP) / 2, -hd + 0.5),
                GLASS, material="Glass", transparency=0.6)
        b.block("Walls", "FrontRail", (wdt, 1.0, 1.2), (cx, WALL_TOP + 0.5, -hd + 0.5), FRAME)
        b.block("Walls", "Post", (1.3, WALL_TOP - kick, 1.3), (side * 17.5, (kick + WALL_TOP) / 2, -hd + 0.5), FRAME)
    b.block("Walls", "BackWall", (W, WALL_TOP + 1.0 - TIER, 1), (0, (TIER + WALL_TOP + 1.0) / 2, hd - 0.5), TRIM)
    b.block("Walls", "BackRail", (W, 1.0, 1.2), (0, WALL_TOP + 1.5, hd - 0.5), FRAME)
    b.block("Walls", "BackStripe", (W - 4, 0.4, 0.2), (0, WALL_TOP - 1.5, hd - 1.05), GLOW, material="Neon")

    # ---- corner pillars with lamp balls
    for sx in (-1, 1):
        for sz in (-1, 1):
            x, z = sx * (hw - 0.5), sz * (hd - 0.5)
            b.column("Pillars", "Pillar", 3.0, 0, WALL_TOP + 1.6, x, z, FRAME)
            for y in (kick, WALL_TOP + 0.4):
                b.column("Pillars", "Band", 3.3, y - 0.3, y + 0.3, x, z, TRIM)
            b.ball("Pillars", "Lamp", 2.8, (x, WALL_TOP + 2.9, z), GLOW, material="Neon", collide=False)

    # ---- entrance arch and sign
    sz = -hd + 0.5
    for sx in (-1, 1):
        b.column("Entrance", "ArchPillar", 3.0, 0, 18.4, sx * 8.5, sz, FRAME)
        for y in (kick, WALL_TOP + 0.4):
            b.column("Entrance", "Band", 3.3, y - 0.3, y + 0.3, sx * 8.5, sz, TRIM)
        b.ball("Entrance", "Lamp", 2.6, (sx * 8.5, 19.6, sz), GLOW, material="Neon", collide=False)
    b.block("Entrance", "DoorBeam", (17, 1.0, 1.2), (0, WALL_TOP + 0.5, sz), FRAME)
    sign_z, sign_y0, sign_y1, sign_w = -hd - 0.9, 12.4, 17.2, 27.0
    b.block("Entrance", "Sign", (sign_w, sign_y1 - sign_y0, 0.8), (0, (sign_y0 + sign_y1) / 2, sign_z), TRIM)
    fz = sign_z - 0.25
    b.block("Entrance", "SignFrame", (sign_w + 1.2, 0.6, 0.9), (0, sign_y1 + 0.3, fz), FRAME)
    b.block("Entrance", "SignFrame", (sign_w + 1.2, 0.6, 0.9), (0, sign_y0 - 0.3, fz), FRAME)
    for sx in (-1, 1):
        b.block("Entrance", "SignFrame", (0.6, sign_y1 - sign_y0 + 1.2, 0.9), (sx * (sign_w / 2 + 0.3),
                (sign_y0 + sign_y1) / 2, fz), FRAME)
    # goo dripping off the bottom of the sign
    drips = [(-12.6, 1.2, 0.9), (-10.9, 0.9, 0.0), (-9.4, 1.4, 1.5), (-7.6, 1.0, 0.3), (-6.0, 1.2, 0.0),
             (-4.3, 1.5, 1.1), (-2.5, 0.9, 0.0), (-0.9, 1.3, 1.7), (0.8, 1.0, 0.4), (2.4, 1.4, 0.0),
             (4.1, 1.1, 1.2), (5.8, 0.9, 0.0), (7.4, 1.5, 0.6), (9.1, 1.1, 0.0), (10.8, 1.4, 1.3),
             (12.6, 1.0, 0.3)]
    for x, d, hang in drips:
        top = sign_y0 - 0.45
        if hang > 0:
            b.column("Entrance/Drips", "Drip", d * 0.7, top - hang, top, x, fz, FRAME, collide=False)
        b.ball("Entrance/Drips", "Drop", d, (x, top - hang, fz), FRAME, collide=False)
    # little slime mascot sitting on top of the sign
    b.ball("Entrance", "Mascot", 3.4, (-sign_w / 2 + 2.5, sign_y1 + 1.9, sign_z), GLOW, collide=False)
    b.block("Entrance", "MascotBase", (3.2, 0.6, 2.2), (-sign_w / 2 + 2.5, sign_y1 + 0.6, sign_z), GLOW,
            collide=False)
    for ex in (-0.55, 0.55):
        b.ball("Entrance", "MascotEye", 0.55, (-sign_w / 2 + 2.5 + ex, sign_y1 + 2.3, sign_z - 1.5), TRIM,
               collide=False)

    # ---- laser door
    b.block("Door", "Field", (14, WALL_TOP - 1.0, 0.3), (0, (1.0 + WALL_TOP) / 2, sz), DOOR, material="ForceField",
            transparency=0.0)
    for y in (2.6, 4.4, 6.2, 8.0):
        b.rod("Door", "Beam", 0.35, -7, 7, y, sz, DOOR, material="Neon", collide=False)

    # ---- lock button just inside the door
    lx, lz = 13.0, -16.5
    b.column("LockButton", "Pedestal", 3.4, FLOOR, FLOOR + 1.0, lx, lz, TRIM)
    b.column("LockButton", "Button", 2.6, FLOOR + 1.0, FLOOR + 1.5, lx, lz, DOOR, material="Neon")

    # ---- ten slots: front row on the floor, back row on the tier
    n = 0
    for z, y in ((FRONT_Z, FLOOR), (BACK_Z, TIER)):
        for x in SLOT_X:
            n += 1
            path = f"Slots/Slot{n}"
            b.column(path, "Rim", 8.4, y, y + 0.25, x, z, GLOW, material="Neon")
            b.column(path, "Base", 8.0, y, y + 0.7, x, z, TRIM)
            b.column(path, "Top", 7.0, y + 0.7, y + 0.95, x, z, PAD_TOP)
            b.block(path, "PlaqueStand", (0.6, 0.8, 0.6), (x, y + 0.4, z - 5.0), TRIM)
            b.block(path, "Plaque", (4.6, 1.6, 0.3), (x, y + 1.3, z - 5.0), TRIM, rot=rx(25))
    return b


# ---------------------------------------------------------------- outputs

LUA = r'''--[[
	BaseBuilder (generated by slimes/tools/build_base.py; edit that, not this file)

	Paste this whole file into the Studio Command Bar and press Enter. It builds a model called
	"SlimeBase" at PLACE_AT below. The pivot is the bottom centre of the base and the entrance faces
	the pivot's LookVector, so you can move or clone it with base:PivotTo(cframe).

	What your game scripts can use:
	  Slots/Slot1 .. Slot10      Model per pet slot (1-5 front row, 6-10 back row)
	      Top.PetSpot            Attachment where the pet stands (its WorldCFrame faces the street)
	      Rim                    neon ring; recolour it to the pet's rarity
	      Plaque.PlaqueGui.Label TextLabel for the earnings text
	  Entrance/Sign.SignGui      Avatar (ImageLabel) and OwnerName (TextLabel)
	  Door/Field, Door/Beam      the laser door; see setDoorLocked below for how to lock/unlock it
	  Door/Field.LockTimer.Label BillboardGui text such as "LOCKED 0:30"
	  LockButton/Button          step on it to lock the base
]]

local PLACE_AT = CFrame.new(0, 0, 0)
local OWNER_TEXT = nil -- nil: uses your Studio account, e.g. "Ultrevo's Base"

local PARTS = {
--@PARTS@
}

local model = Instance.new("Model")
model.Name = "SlimeBase"

local function container(path)
	local parent = model
	if path == "" then
		return parent
	end
	for name in path:gmatch("[^/]+") do
		local child = parent:FindFirstChild(name)
		if not child then
			child = Instance.new("Model")
			child.Name = name
			child.Parent = parent
		end
		parent = child
	end
	return parent
end

for _, p in ipairs(PARTS) do
	local path, name, shape, size, pos, rot, color, material, transparency, collide = table.unpack(p, 1, 10)
	local part = Instance.new("Part")
	part.Name = name
	part.Shape = Enum.PartType[shape]
	part.Size = Vector3.new(size[1], size[2], size[3])
	if rot then
		part.CFrame = CFrame.new(pos[1], pos[2], pos[3], rot[1], rot[2], rot[3], rot[4], rot[5], rot[6], rot[7], rot[8], rot[9])
	else
		part.CFrame = CFrame.new(pos[1], pos[2], pos[3])
	end
	part.Color = Color3.fromHex(color)
	part.Material = Enum.Material[material]
	part.Transparency = transparency
	part.CanCollide = collide
	part.CanTouch = collide or name == "Button"
	part.Anchored = true
	part.TopSurface = Enum.SurfaceType.Smooth
	part.BottomSurface = Enum.SurfaceType.Smooth
	part.CastShadow = material ~= "Neon" and material ~= "Glass"
	part.Parent = container(path)
end

local function find(path)
	local node = model
	for name in path:gmatch("[^/]+") do
		node = node:FindFirstChild(name)
	end
	return node
end

local FONT = Enum.Font.FredokaOne

local function label(parent, text, color, strokeColor)
	local l = Instance.new("TextLabel")
	l.Name = "Label"
	l.BackgroundTransparency = 1
	l.Size = UDim2.fromScale(1, 1)
	l.Font = FONT
	l.TextScaled = true
	l.Text = text
	l.TextColor3 = color
	local stroke = Instance.new("UIStroke")
	stroke.Color = strokeColor
	stroke.Thickness = 3
	stroke.Parent = l
	l.Parent = parent
	return l
end

-- sign: round avatar on the left, owner name on the right
local sign = find("Entrance/Sign")
local gui = Instance.new("SurfaceGui")
gui.Name = "SignGui"
gui.Face = Enum.NormalId.Front
gui.SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud
gui.PixelsPerStud = 40
gui.LightInfluence = 0
gui.Parent = sign
local avatar = Instance.new("ImageLabel")
avatar.Name = "Avatar"
avatar.AnchorPoint = Vector2.new(0, 0.5)
avatar.Position = UDim2.new(0, 40, 0.5, 0)
avatar.Size = UDim2.fromOffset(150, 150)
avatar.BackgroundColor3 = Color3.fromHex("#2C3560")
avatar.Parent = gui
Instance.new("UICorner", avatar).CornerRadius = UDim.new(0.5, 0)
local ring = Instance.new("UIStroke")
ring.Color = Color3.fromHex("--@FRAME@")
ring.Thickness = 8
ring.Parent = avatar
local nameLabel = label(gui, "", Color3.fromHex("#FFB3C2"), Color3.fromHex("#0E1226"))
nameLabel.Name = "OwnerName"
nameLabel.AnchorPoint = Vector2.new(1, 0.5)
nameLabel.Position = UDim2.new(1, -40, 0.5, 0)
nameLabel.Size = UDim2.new(1, -250, 0.62, 0)

local ownerText, userId = OWNER_TEXT, nil
if not ownerText then
	local ok, id = pcall(function()
		return game:GetService("StudioService"):GetUserId()
	end)
	if ok and id and id > 0 then
		userId = id
		local okName, name = pcall(function()
			return game:GetService("Players"):GetNameFromUserIdAsync(id)
		end)
		if okName then
			ownerText = name .. "'s Base"
		end
	end
end
nameLabel.Text = ownerText or "My Base"
if userId then
	avatar.Image = ("rbxthumb://type=AvatarHeadShot&id=%d&w=150&h=150"):format(userId)
end

-- earnings plaques and pet spots
for i = 1, 10 do
	local slot = find("Slots/Slot" .. i)
	slot:SetAttribute("SlotIndex", i)
	local top = slot.Top
	local spot = Instance.new("Attachment")
	spot.Name = "PetSpot"
	-- the cylinder lies on its side, so turn the attachment upright and facing the street (-Z)
	spot.CFrame = top.CFrame:ToObjectSpace(CFrame.new(top.Position + Vector3.new(0, top.Size.X / 2, 0)))
	spot.Parent = top
	local plaqueGui = Instance.new("SurfaceGui")
	plaqueGui.Name = "PlaqueGui"
	plaqueGui.Face = Enum.NormalId.Front
	plaqueGui.SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud
	plaqueGui.PixelsPerStud = 50
	plaqueGui.Parent = slot.Plaque
	label(plaqueGui, "EMPTY", Color3.fromHex("#FFFFFF"), Color3.fromHex("#0E1226"))
end

-- lamps glow onto the base
for _, d in ipairs(model:GetDescendants()) do
	if d:IsA("BasePart") and d.Name == "Lamp" then
		local light = Instance.new("PointLight")
		light.Color = d.Color
		light.Range = 14
		light.Brightness = 1.2
		light.Parent = d
	end
end

-- lock button label and door timer
local button = find("LockButton/Button")
local tag = Instance.new("BillboardGui")
tag.Name = "LockTag"
tag.Size = UDim2.fromScale(5, 1.4)
tag.StudsOffset = Vector3.new(0, 2.2, 0)
tag.AlwaysOnTop = false
tag.Parent = button
label(tag, "LOCK BASE", Color3.fromHex("#FFFFFF"), Color3.fromHex("#8A0F25"))

local field = find("Door/Field")
local timer = Instance.new("BillboardGui")
timer.Name = "LockTimer"
timer.Size = UDim2.fromScale(10, 2.2)
timer.StudsOffset = Vector3.new(0, 1, -1)
timer.Parent = field
label(timer, "LOCKED 0:30", Color3.fromHex("#7CFFB0"), Color3.fromHex("#0E1226"))

--[[ To lock or unlock from your game scripts:
local function setDoorLocked(base, locked)
	for _, p in ipairs(base.Door:GetChildren()) do
		p.Transparency = locked and 0 or 1
	end
	base.Door.Field.CanCollide = locked
	base.Door.Field.LockTimer.Enabled = locked
end
]]

model.PrimaryPart = find("Floor/Plinth")
model.WorldPivot = CFrame.new(0, 0, 0)
model:PivotTo(PLACE_AT)
model.Parent = workspace
print("BaseBuilder: built SlimeBase with " .. #PARTS .. " parts")
'''


def lua_value(v):
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, float)):
        return f"{v:g}"
    if isinstance(v, str):
        return f'"{v}"'
    if v is None:
        return "false"
    return "{" + ", ".join(lua_value(x) for x in v) + "}"


def main():
    b = build()
    out = os.path.join(ROOT, "base")
    os.makedirs(out, exist_ok=True)
    rows = []
    for p in b.parts:
        rot = None if p["rot"] is None else [round(x, 6) for row in p["rot"] for x in row]
        rows.append("\t{" + ", ".join(lua_value(v) for v in (p["path"], p["name"], p["shape"], p["size"], p["pos"],
                                                             rot, p["color"], p["material"], p["transparency"],
                                                             p["collide"])) + "},")
    with open(os.path.join(out, "BaseBuilder.lua"), "w") as f:
        f.write(LUA.replace("--@PARTS@", "\n".join(rows)).replace("--@FRAME@", FRAME))
    slots = []
    for p in b.parts:
        if p["name"] == "Top":
            slots.append([p["pos"][0], p["pos"][1] + p["size"][0] / 2, p["pos"][2]])
    sign = next(p for p in b.parts if p["name"] == "Sign")
    with open(os.path.join(out, "base.json"), "w") as f:
        json.dump({"parts": b.parts, "slots": slots, "sign": sign}, f)
    print(f"{len(b.parts)} parts, {len(slots)} slots")


if __name__ == "__main__":
    main()
