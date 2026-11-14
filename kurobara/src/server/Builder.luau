-- KUROBARA · World builder
-- Builds Kurobara Academy, the town street and Nyx's house out of parts at server start.
-- Tip: press Play once, copy Workspace.Map, stop, paste it → now you can edit the map by hand
-- (then set Builder.UseExistingMap = true so the script doesn't build a second copy).

local Lighting = game:GetService("Lighting")

local Builder = {}
Builder.UseExistingMap = false

local World = {
	Spots = {},
	Seats = {},
	ClassFront = {},
	ItemSpawns = {},
	Interact = { Sinks = {}, Showers = {} },
}
Builder.World = World

local Map: Folder

-- ── Palette ──────────────────────────────────────────────────────────────────
local COL = {
	Cream = Color3.fromRGB(236, 230, 218),
	Wall = Color3.fromRGB(244, 241, 234),
	Trim = Color3.fromRGB(64, 52, 64),
	Crimson = Color3.fromRGB(128, 18, 38),
	Concrete = Color3.fromRGB(178, 176, 172),
	RoofSlab = Color3.fromRGB(150, 150, 154),
	Floor = Color3.fromRGB(214, 210, 204),
	Wood = Color3.fromRGB(168, 124, 86),
	DarkWood = Color3.fromRGB(96, 66, 46),
	LightWood = Color3.fromRGB(208, 172, 128),
	Metal = Color3.fromRGB(120, 124, 132),
	Board = Color3.fromRGB(34, 62, 48),
	Glass = Color3.fromRGB(196, 222, 236),
	Grass = Color3.fromRGB(88, 122, 70),
	Soil = Color3.fromRGB(92, 64, 44),
	Asphalt = Color3.fromRGB(52, 52, 58),
	Sidewalk = Color3.fromRGB(190, 186, 180),
	Leaf = Color3.fromRGB(70, 118, 62),
	Blossom = Color3.fromRGB(255, 182, 206),
	Rose = Color3.fromRGB(70, 6, 18),
	Brick = Color3.fromRGB(130, 64, 50),
	White = Color3.fromRGB(250, 250, 250),
	Black = Color3.fromRGB(24, 24, 28),
}

local FLOOR_Y = 0.55 -- walkable floor height inside buildings
local WALL_TOP = 12
local ROOF_Y = 13

-- ── Part helpers ─────────────────────────────────────────────────────────────
local function P(size: Vector3, cf: CFrame, color: Color3, mat: Enum.Material?, parent: Instance?, name: string?): Part
	local p = Instance.new("Part")
	p.Anchored = true
	p.Size = size
	p.CFrame = cf
	p.Color = color
	p.Material = mat or Enum.Material.SmoothPlastic
	p.TopSurface = Enum.SurfaceType.Smooth
	p.BottomSurface = Enum.SurfaceType.Smooth
	p.Name = name or "Part"
	p.Parent = parent or Map
	return p
end

local function box(x1: number, y1: number, z1: number, x2: number, y2: number, z2: number, color: Color3, mat: Enum.Material?, parent: Instance?, name: string?): Part
	local sx, sy, sz = math.abs(x2 - x1), math.abs(y2 - y1), math.abs(z2 - z1)
	return P(
		Vector3.new(math.max(sx, 0.05), math.max(sy, 0.05), math.max(sz, 0.05)),
		CFrame.new((x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2),
		color, mat, parent, name
	)
end

local function deco(p: BasePart): BasePart
	p.CanCollide = false
	p.CanQuery = false
	p.CanTouch = false
	p.CastShadow = false
	return p
end

local function ball(pos: Vector3, d: number, color: Color3, mat: Enum.Material?, parent: Instance?): Part
	local p = P(Vector3.new(d, d, d), CFrame.new(pos), color, mat, parent, "Ball")
	p.Shape = Enum.PartType.Ball
	return p
end

-- vertical cylinder (Roblox cylinders run along X, so rotate)
local function cyl(pos: Vector3, height: number, d: number, color: Color3, mat: Enum.Material?, parent: Instance?): Part
	local p = P(Vector3.new(height, d, d), CFrame.new(pos) * CFrame.Angles(0, 0, math.rad(90)), color, mat, parent, "Cylinder")
	p.Shape = Enum.PartType.Cylinder
	return p
end

local function glass(p: BasePart): BasePart
	p.Material = Enum.Material.Glass
	p.Color = COL.Glass
	p.Transparency = 0.6
	p.CanQuery = false -- NPCs can see through windows
	p.CastShadow = false
	return p
end

local function folder(name: string, parent: Instance?): Folder
	local f = Instance.new("Folder")
	f.Name = name
	f.Parent = parent or Map
	return f
end

local function light(p: BasePart, range: number?, brightness: number?, color: Color3?)
	local l = Instance.new("SurfaceLight")
	l.Face = Enum.NormalId.Bottom
	l.Range = range or 18
	l.Brightness = brightness or 1.1
	l.Angle = 120
	l.Color = color or Color3.fromRGB(255, 246, 232)
	l.Shadows = true
	l.Parent = p
end

local function ceilingLight(x: number, z: number, parent: Instance)
	local p = box(x - 3, 11.7, z - 0.6, x + 3, 11.95, z + 0.6, Color3.fromRGB(255, 250, 240), Enum.Material.Neon, parent, "CeilingLight")
	deco(p)
	light(p)
end

local function sign(text: string, cf: CFrame, size: Vector2, parent: Instance, textColor: Color3?, bg: Color3?)
	local p = P(Vector3.new(size.X, size.Y, 0.2), cf, bg or COL.Trim, Enum.Material.SmoothPlastic, parent, "Sign")
	deco(p)
	local gui = Instance.new("SurfaceGui")
	gui.Face = Enum.NormalId.Front
	gui.SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud
	gui.PixelsPerStud = 60
	gui.LightInfluence = 0.3
	gui.Parent = p
	local t = Instance.new("TextLabel")
	t.Size = UDim2.fromScale(1, 1)
	t.BackgroundTransparency = 1
	t.Text = text
	t.TextScaled = true
	t.TextColor3 = textColor or COL.White
	t.FontFace = Font.new("rbxasset://fonts/families/GothamSSm.json", Enum.FontWeight.Bold)
	t.Parent = gui
	local pad = Instance.new("UIPadding")
	pad.PaddingTop = UDim.new(0.15, 0)
	pad.PaddingBottom = UDim.new(0.15, 0)
	pad.PaddingLeft = UDim.new(0.05, 0)
	pad.PaddingRight = UDim.new(0.05, 0)
	pad.Parent = t
	return p
end

-- Wall running along X at depth z. openings = { {a=, b=, bottom=, top=, glass=bool} }
local function wallX(x1: number, x2: number, z: number, y1: number, y2: number, t: number, openings: { any }, color: Color3, parent: Instance)
	table.sort(openings, function(o1, o2)
		return o1.a < o2.a
	end)
	local cursor = x1
	for _, o in openings do
		if o.a > cursor then
			box(cursor, y1, z - t / 2, o.a, y2, z + t / 2, color, nil, parent, "Wall")
		end
		if o.bottom > y1 then
			box(o.a, y1, z - t / 2, o.b, o.bottom, z + t / 2, color, nil, parent, "Wall")
		end
		if o.top < y2 then
			box(o.a, o.top, z - t / 2, o.b, y2, z + t / 2, color, nil, parent, "Wall")
		end
		if o.glass then
			glass(box(o.a, o.bottom, z - 0.08, o.b, o.top, z + 0.08, COL.Glass, nil, parent, "Window"))
			deco(box((o.a + o.b) / 2 - 0.1, o.bottom, z - 0.15, (o.a + o.b) / 2 + 0.1, o.top, z + 0.15, COL.Trim, nil, parent, "Mullion"))
			box(o.a, o.bottom - 0.2, z - t / 2 - 0.25, o.b, o.bottom, z + t / 2 + 0.25, COL.Trim, nil, parent, "Sill")
		end
		cursor = math.max(cursor, o.b)
	end
	if cursor < x2 then
		box(cursor, y1, z - t / 2, x2, y2, z + t / 2, color, nil, parent, "Wall")
	end
end

-- Wall running along Z at x.
local function wallZ(z1: number, z2: number, x: number, y1: number, y2: number, t: number, openings: { any }, color: Color3, parent: Instance)
	table.sort(openings, function(o1, o2)
		return o1.a < o2.a
	end)
	local cursor = z1
	for _, o in openings do
		if o.a > cursor then
			box(x - t / 2, y1, cursor, x + t / 2, y2, o.a, color, nil, parent, "Wall")
		end
		if o.bottom > y1 then
			box(x - t / 2, y1, o.a, x + t / 2, o.bottom, o.b, color, nil, parent, "Wall")
		end
		if o.top < y2 then
			box(x - t / 2, o.top, o.a, x + t / 2, y2, o.b, color, nil, parent, "Wall")
		end
		if o.glass then
			glass(box(x - 0.08, o.bottom, o.a, x + 0.08, o.top, o.b, COL.Glass, nil, parent, "Window"))
			deco(box(x - 0.15, o.bottom, (o.a + o.b) / 2 - 0.1, x + 0.15, o.top, (o.a + o.b) / 2 + 0.1, COL.Trim, nil, parent, "Mullion"))
			box(x - t / 2 - 0.25, o.bottom - 0.2, o.a, x + t / 2 + 0.25, o.bottom, o.b, COL.Trim, nil, parent, "Sill")
		end
		cursor = math.max(cursor, o.b)
	end
	if cursor < z2 then
		box(x - t / 2, y1, cursor, x + t / 2, y2, z2, color, nil, parent, "Wall")
	end
end

-- ── Spots ────────────────────────────────────────────────────────────────────
local function spot(group: string, pos: Vector3, look: Vector3?)
	World.Spots[group] = World.Spots[group] or {}
	local target = look or (pos + Vector3.new(0, 0, -1))
	target = Vector3.new(target.X, pos.Y, target.Z)
	if (target - pos).Magnitude < 0.01 then
		target = pos + Vector3.new(0, 0, -1)
	end
	table.insert(World.Spots[group], CFrame.lookAt(pos, target))
end

local function ring(group: string, center: Vector3, radius: number, count: number, faceIn: boolean)
	for i = 1, count do
		local a = (i / count) * math.pi * 2
		local pos = center + Vector3.new(math.cos(a) * radius, 0, math.sin(a) * radius)
		spot(group, pos, faceIn and center or (pos + (pos - center)))
	end
end

-- ── Furniture ────────────────────────────────────────────────────────────────
local function deskAndChair(x: number, z: number, parent: Instance, classId: string?)
	-- desk faces -X (toward the board on the room's west wall)
	box(x - 1, 2.6, z - 1.2, x + 0.9, 2.8, z + 1.2, COL.LightWood, Enum.Material.Wood, parent, "Desk")
	box(x - 0.9, FLOOR_Y, z - 1.15, x + 0.8, 2.6, z - 1.0, COL.Metal, Enum.Material.Metal, parent, "DeskSide")
	box(x - 0.9, FLOOR_Y, z + 1.0, x + 0.8, 2.6, z + 1.15, COL.Metal, Enum.Material.Metal, parent, "DeskSide")
	local seat = deco(box(x + 1.3, 1.45, z - 0.75, x + 2.3, 1.6, z + 0.75, COL.LightWood, Enum.Material.Wood, parent, "Chair"))
	deco(box(x + 2.3, 1.6, z - 0.75, x + 2.45, 3.4, z + 0.75, COL.LightWood, Enum.Material.Wood, parent, "ChairBack"))
	deco(box(x + 1.7, FLOOR_Y, z - 0.1, x + 1.9, 1.45, z + 0.1, COL.Metal, Enum.Material.Metal, parent, "ChairLeg"))
	if classId then
		World.Seats[classId] = World.Seats[classId] or {}
		local sitPos = Vector3.new(x + 1.8, 1.6 + 1.2, z)
		table.insert(World.Seats[classId], {
			sit = CFrame.lookAt(sitPos, sitPos - Vector3.new(1, 0, 0)),
			approach = Vector3.new(x + 2.7, FLOOR_Y, z),
			chair = seat,
		})
	end
end

local function bench(cf: CFrame, parent: Instance)
	local seat = P(Vector3.new(5, 0.3, 1.6), cf * CFrame.new(0, 1.45, 0), COL.Wood, Enum.Material.Wood, parent, "Bench")
	seat.CanCollide = false
	deco(P(Vector3.new(5, 1.2, 0.25), cf * CFrame.new(0, 2.2, 0.75), COL.Wood, Enum.Material.Wood, parent, "BenchBack"))
	for _, dx in { -2, 2 } do
		deco(P(Vector3.new(0.3, 1.3, 1.4), cf * CFrame.new(dx, 0.65, 0), COL.Black, Enum.Material.Metal, parent, "BenchLeg"))
	end
end

local function tree(x: number, z: number, kind: string, parent: Instance)
	local h = 9 + math.random() * 4
	local trunk = cyl(Vector3.new(x, h / 2, z), h, 1.4, COL.DarkWood, Enum.Material.Wood, parent)
	trunk.Name = "Trunk"
	local leaf = kind == "Blossom" and COL.Blossom or COL.Leaf
	for i = 1, 4 do
		local off = Vector3.new(math.random(-25, 25) / 10, math.random(-5, 15) / 10, math.random(-25, 25) / 10)
		local b = ball(Vector3.new(x, h + 1, z) + off, 6 + math.random() * 3, leaf, Enum.Material.Grass, parent)
		b.Name = "Canopy"
		b.CanCollide = false
		if kind == "Blossom" then
			b.Material = Enum.Material.SmoothPlastic
		end
	end
	if kind == "Blossom" then
		local pe = Instance.new("ParticleEmitter")
		pe.Color = ColorSequence.new(COL.Blossom)
		pe.Size = NumberSequence.new(0.18)
		pe.Lifetime = NumberRange.new(5, 8)
		pe.Rate = 3
		pe.Speed = NumberRange.new(0.5, 1.5)
		pe.SpreadAngle = Vector2.new(180, 180)
		pe.Acceleration = Vector3.new(0.6, -1.2, 0.3)
		pe.RotSpeed = NumberRange.new(-90, 90)
		pe.Rotation = NumberRange.new(0, 360)
		pe.LightInfluence = 1
		pe.Parent = trunk
	end
end

local function roseBed(x1: number, z1: number, x2: number, z2: number, parent: Instance)
	box(x1, 0, z1, x2, 1.2, z2, COL.Trim, Enum.Material.Slate, parent, "Planter")
	box(x1 + 0.4, 1.2, z1 + 0.4, x2 - 0.4, 1.3, z2 - 0.4, COL.Soil, Enum.Material.Ground, parent, "Soil")
	local cx, cz = (x1 + x2) / 2, (z1 + z2) / 2
	local w, d = math.abs(x2 - x1) - 1.5, math.abs(z2 - z1) - 1.5
	for _ = 1, 7 do
		local p = Vector3.new(cx + (math.random() - 0.5) * w, 2, cz + (math.random() - 0.5) * d)
		deco(ball(p, 1.8 + math.random(), COL.Leaf, Enum.Material.Grass, parent))
		for _ = 1, 2 do
			deco(ball(p + Vector3.new((math.random() - 0.5) * 1.6, 0.8 + math.random() * 0.4, (math.random() - 0.5) * 1.6), 0.55, COL.Rose, Enum.Material.SmoothPlastic, parent))
		end
	end
end

local function lamp(pos: Vector3, parent: Instance)
	cyl(pos + Vector3.new(0, 4.5, 0), 9, 0.4, COL.Black, Enum.Material.Metal, parent)
	local head = P(Vector3.new(1.4, 0.6, 1.4), CFrame.new(pos + Vector3.new(0, 9.2, 0)), Color3.fromRGB(255, 236, 200), Enum.Material.Neon, parent, "LampHead")
	deco(head)
	local l = Instance.new("PointLight")
	l.Range = 22
	l.Brightness = 1.2
	l.Color = Color3.fromRGB(255, 220, 170)
	l.Parent = head
	return head
end

local function shelf(x1: number, z1: number, x2: number, z2: number, h: number, parent: Instance, books: boolean)
	box(x1, FLOOR_Y, z1, x2, FLOOR_Y + h, z2, COL.DarkWood, Enum.Material.Wood, parent, "Shelf")
	if not books then
		return
	end
	local alongX = math.abs(x2 - x1) > math.abs(z2 - z1)
	local len = alongX and math.abs(x2 - x1) or math.abs(z2 - z1)
	for row = 0, 3 do
		local y = FLOOR_Y + 0.6 + row * (h - 0.8) / 4
		local cursor = 0.2
		while cursor < len - 0.4 do
			local bw = 0.25 + math.random() * 0.35
			local bh = 0.9 + math.random() * 0.5
			local c = Color3.fromHSV(math.random(), 0.45 + math.random() * 0.3, 0.35 + math.random() * 0.4)
			local px, pz
			if alongX then
				px, pz = math.min(x1, x2) + cursor + bw / 2, (z1 + z2) / 2
				deco(P(Vector3.new(bw, bh, math.abs(z2 - z1) + 0.15), CFrame.new(px, y + bh / 2, pz), c, nil, parent, "Book"))
			else
				px, pz = (x1 + x2) / 2, math.min(z1, z2) + cursor + bw / 2
				deco(P(Vector3.new(math.abs(x2 - x1) + 0.15, bh, bw), CFrame.new(px, y + bh / 2, pz), c, nil, parent, "Book"))
			end
			cursor += bw + 0.6 + math.random() * 0.8
		end
	end
end

local function sink(cf: CFrame, parent: Instance)
	local basin = P(Vector3.new(2.4, 0.5, 1.6), cf * CFrame.new(0, 3, 0), COL.White, Enum.Material.SmoothPlastic, parent, "Sink")
	deco(P(Vector3.new(2, 0.1, 1.2), cf * CFrame.new(0, 3.26, 0), Color3.fromRGB(150, 190, 210), Enum.Material.Glass, parent, "SinkWater")).Transparency = 0.4
	deco(P(Vector3.new(0.2, 0.8, 0.2), cf * CFrame.new(0, 3.6, 0.6), COL.Metal, Enum.Material.Metal, parent, "Tap"))
	deco(P(Vector3.new(0.6, 2.75, 0.6), cf * CFrame.new(0, 1.4, 0.2), COL.White, nil, parent, "SinkStand"))
	table.insert(World.Interact.Sinks, basin)
	return basin
end

local function shower(cf: CFrame, parent: Instance)
	-- stall opening faces the stall's -Z (LookVector)
	local f = folder("Shower", parent)
	deco(P(Vector3.new(0.2, 7, 4), cf * CFrame.new(-2, 3.5 + FLOOR_Y, 0), Color3.fromRGB(220, 230, 235), nil, f, "StallWall"))
	deco(P(Vector3.new(0.2, 7, 4), cf * CFrame.new(2, 3.5 + FLOOR_Y, 0), Color3.fromRGB(220, 230, 235), nil, f, "StallWall"))
	P(Vector3.new(4, 0.1, 4), cf * CFrame.new(0, FLOOR_Y + 0.05, 0), Color3.fromRGB(200, 214, 222), Enum.Material.Glass, f, "ShowerTray")
	local head = deco(P(Vector3.new(0.6, 0.2, 0.6), cf * CFrame.new(0, 8, 1.6), COL.Metal, Enum.Material.Metal, f, "ShowerHead"))
	local water = Instance.new("ParticleEmitter")
	water.Name = "Water"
	water.Enabled = false
	water.Color = ColorSequence.new(Color3.fromRGB(200, 230, 255))
	water.Transparency = NumberSequence.new(0.5, 1)
	water.Size = NumberSequence.new(0.08)
	water.Lifetime = NumberRange.new(0.4, 0.6)
	water.Rate = 120
	water.Speed = NumberRange.new(6, 9)
	water.EmissionDirection = Enum.NormalId.Bottom
	water.SpreadAngle = Vector2.new(12, 12)
	water.Acceleration = Vector3.new(0, -40, 0)
	water.Parent = head
	local trigger = P(Vector3.new(3, 4, 3), cf * CFrame.new(0, FLOOR_Y + 2, 0), COL.White, nil, f, "ShowerZone")
	trigger.Transparency = 1
	deco(trigger)
	table.insert(World.Interact.Showers, trigger)
	return trigger
end

-- ── Rooms ────────────────────────────────────────────────────────────────────
local ROOMS = {
	{ id = "Art", x1 = -100, x2 = -70, side = "S", label = "ART ROOM" },
	{ id = "Class2A", x1 = -70, x2 = -40, side = "S", label = "CLASS 2-A", class = "2A" },
	{ id = "Class3A", x1 = -40, x2 = -10, side = "S", label = "CLASS 3-A", class = "3A" },
	{ id = "Lobby", x1 = -10, x2 = 10, side = "S", label = "ENTRANCE", open = true },
	{ id = "Cafeteria", x1 = 10, x2 = 40, side = "S", label = "CAFETERIA" },
	{ id = "Lockers", x1 = 40, x2 = 70, side = "S", label = "LOCKER ROOM", highWindows = true },
	{ id = "Lounge", x1 = 70, x2 = 100, side = "S", label = "TEACHERS' LOUNGE" },
	{ id = "Science", x1 = -100, x2 = -70, side = "N", label = "SCIENCE LAB" },
	{ id = "Class1B", x1 = -70, x2 = -40, side = "N", label = "CLASS 1-B", class = "1B" },
	{ id = "Library", x1 = -40, x2 = -15, side = "N", label = "LIBRARY" },
	{ id = "Stairs", x1 = -15, x2 = 15, side = "N", label = "ROOFTOP ↑" },
	{ id = "Music", x1 = 15, x2 = 40, side = "N", label = "MUSIC ROOM" },
	{ id = "Janitor", x1 = 40, x2 = 70, side = "N", label = "MAINTENANCE" },
	{ id = "Restroom", x1 = 70, x2 = 100, side = "N", label = "RESTROOMS", highWindows = true },
}

local function roomZ(r)
	if r.side == "S" then
		return 4.5, 19.5, 12
	end
	return -19.5, -4.5, -12
end

local Furnish = {}

function Furnish.Classroom(r, f: Folder)
	local zA, zB, cz = roomZ(r)
	local x1 = r.x1
	box(x1 + 0.5, 3.6, cz - 6.5, x1 + 0.75, 8.2, cz + 6.5, COL.Board, Enum.Material.Slate, f, "Blackboard")
	box(x1 + 0.5, 3.35, cz - 6.8, x1 + 1.0, 3.6, cz + 6.8, COL.Wood, Enum.Material.Wood, f, "ChalkLedge")
	box(x1 + 3.5, FLOOR_Y, cz - 2.5, x1 + 5.5, 3.1, cz + 2.5, COL.DarkWood, Enum.Material.Wood, f, "TeacherDesk")
	local front = Vector3.new(x1 + 2.5, FLOOR_Y, cz + 4)
	World.ClassFront[r.class] = CFrame.lookAt(front, front + Vector3.new(1, 0, 0))
	for _, xo in { 10, 17, 24 } do
		for _, zo in { 3, 7.5, 12 } do
			deskAndChair(x1 + xo, zA + zo, f, r.class)
		end
	end
	-- wall clock
	local clock = cyl(Vector3.new(r.x2 - 0.6, 9, cz), 0.2, 1.6, COL.White, nil, f)
	clock.CFrame = CFrame.new(r.x2 - 0.6, 9, cz) * CFrame.Angles(0, 0, 0)
	deco(clock)
end

function Furnish.Art(r, f: Folder)
	local zA, _, cz = roomZ(r)
	for i, xo in { 8, 20 } do
		box(r.x1 + xo - 4, 2.6, cz - 2, r.x1 + xo + 4, 2.85, cz + 2, COL.LightWood, Enum.Material.Wood, f, "ArtTable")
		box(r.x1 + xo - 3.6, FLOOR_Y, cz - 1.6, r.x1 + xo - 3.2, 2.6, cz + 1.6, COL.DarkWood, nil, f, "Leg")
		box(r.x1 + xo + 3.2, FLOOR_Y, cz - 1.6, r.x1 + xo + 3.6, 2.6, cz + 1.6, COL.DarkWood, nil, f, "Leg")
		for _ = 1, 4 do
			deco(box(r.x1 + xo - 3 + math.random() * 6, 2.85, cz - 1.5 + math.random() * 3, r.x1 + xo - 2.6 + math.random() * 6, 2.88, cz - 1 + math.random() * 3, Color3.fromHSV(math.random(), 0.7, 0.9), nil, f, "Paint"))
		end
		if i == 1 then
			World.ItemSpawns.Art = CFrame.new(r.x1 + xo + 2, 3.05, cz + 1) * CFrame.Angles(0, math.rad(30), math.rad(90))
		end
	end
	for _, zo in { 2.5, 13 } do
		local x, z = r.x1 + 26, zA + zo
		deco(box(x - 0.15, FLOOR_Y, z - 0.15, x + 0.15, 6.5, z + 0.15, COL.Wood, Enum.Material.Wood, f, "Easel"))
		box(x - 0.3, 3.5, z - 1.6, x - 0.1, 6, z + 1.6, COL.White, Enum.Material.Fabric, f, "Canvas")
		deco(box(x - 0.35, 4, z - 1.2, x - 0.3, 5.5, z + 0.8, Color3.fromHSV(math.random(), 0.6, 0.8), nil, f, "PaintStroke"))
	end
	spot("Art", Vector3.new(r.x1 + 8, FLOOR_Y, cz + 3.5), Vector3.new(r.x1 + 8, FLOOR_Y, cz))
	spot("Art", Vector3.new(r.x1 + 20, FLOOR_Y, cz - 3.5), Vector3.new(r.x1 + 20, FLOOR_Y, cz))
	spot("Art", Vector3.new(r.x1 + 24, FLOOR_Y, zA + 2.5), Vector3.new(r.x1 + 27, FLOOR_Y, zA + 2.5))
	spot("Art", Vector3.new(r.x1 + 24, FLOOR_Y, zA + 13), Vector3.new(r.x1 + 27, FLOOR_Y, zA + 13))
end

function Furnish.Science(r, f: Folder)
	local _, _, cz = roomZ(r)
	for _, xo in { 7, 15, 23 } do
		box(r.x1 + xo - 1.5, FLOOR_Y, cz - 5, r.x1 + xo + 1.5, 2.7, cz + 5, COL.White, nil, f, "LabBench")
		box(r.x1 + xo - 1.6, 2.7, cz - 5.1, r.x1 + xo + 1.6, 2.9, cz + 5.1, COL.Black, Enum.Material.Slate, f, "LabTop")
		for k = 1, 3 do
			local p = cyl(Vector3.new(r.x1 + xo, 3.3, cz - 4 + k * 2), 0.8, 0.45, Color3.fromHSV(math.random(), 0.8, 1), Enum.Material.Neon, f)
			p.Transparency = 0.3
			deco(p)
		end
		spot("Science", Vector3.new(r.x1 + xo + 2.6, FLOOR_Y, cz), Vector3.new(r.x1 + xo, FLOOR_Y, cz))
	end
end

function Furnish.Library(r, f: Folder)
	local zA, zB, cz = roomZ(r)
	shelf(r.x1 + 1, zA + 0.2, r.x2 - 1, zA + 1.6, 8, f, true)
	for _, xo in { 6, 14 } do
		shelf(r.x1 + xo, cz - 1, r.x1 + xo + 1.4, cz + 5, 6, f, true)
	end
	box(r.x1 + 18, 2.5, cz - 2, r.x1 + 23.5, 2.75, cz + 3, COL.Wood, Enum.Material.Wood, f, "ReadingTable")
	box(r.x1 + 20.3, FLOOR_Y, cz - 0.5, r.x1 + 21.3, 2.5, cz + 1.5, COL.DarkWood, nil, f, "TableLeg")
	spot("Library", Vector3.new(r.x1 + 20.8, FLOOR_Y, cz - 4), Vector3.new(r.x1 + 20.8, FLOOR_Y, cz))
	spot("Library", Vector3.new(r.x1 + 20.8, FLOOR_Y, cz + 5), Vector3.new(r.x1 + 20.8, FLOOR_Y, cz))
	spot("Library", Vector3.new(r.x1 + 10, FLOOR_Y, cz + 2), Vector3.new(r.x1 + 6, FLOOR_Y, cz + 2))
	spot("Library", Vector3.new(r.x1 + 4, FLOOR_Y, zA + 3.5), Vector3.new(r.x1 + 4, FLOOR_Y, zA))
end

function Furnish.Stairs(r, f: Folder)
	-- invisible collision ramp + visual steps from floor (x=-8) up to roof level (x=8)
	local zA, _, _ = roomZ(r)
	local z1, z2 = zA + 1.5, zA + 7.5 -- -18 .. -12
	local rise = ROOF_Y - FLOOR_Y
	local run = 20
	local rampX = -10
	local ramp = Instance.new("WedgePart")
	ramp.Name = "StairRamp"
	ramp.Anchored = true
	ramp.Size = Vector3.new(z2 - z1, rise, run)
	ramp.CFrame = CFrame.new(rampX + run / 2, FLOOR_Y + rise / 2, (z1 + z2) / 2) * CFrame.Angles(0, math.rad(90), 0)
	ramp.Transparency = 1
	ramp.CastShadow = false
	ramp.Parent = f
	local steps = 24
	for i = 0, steps - 1 do
		local sx = rampX + i * (run / steps)
		local top = FLOOR_Y + (i + 1) * (rise / steps)
		local s = box(sx, FLOOR_Y, z1, sx + run / steps + 0.02, top, z2, i % 2 == 0 and COL.Concrete or Color3.fromRGB(190, 188, 184), Enum.Material.Concrete, f, "Step")
		s.CanCollide = false
		s.CanQuery = false
	end
	-- handrail
	for _, z in { z1 + 0.1, z2 - 0.1 } do
		local a = Vector3.new(rampX, FLOOR_Y + 3, z)
		local b = Vector3.new(rampX + run, ROOF_Y + 3, z)
		deco(P(Vector3.new(0.2, 0.2, (b - a).Magnitude), CFrame.lookAt((a + b) / 2, b), COL.Black, Enum.Material.Metal, f, "Handrail"))
	end
end

function Furnish.Music(r, f: Folder)
	local zA, _, cz = roomZ(r)
	box(r.x1 + 3, FLOOR_Y, zA + 2, r.x1 + 9, 3.4, zA + 5, COL.Black, Enum.Material.SmoothPlastic, f, "Piano").Reflectance = 0.15
	deco(box(r.x1 + 3.2, 2.6, zA + 5, r.x1 + 8.8, 2.8, zA + 5.6, COL.White, nil, f, "Keys"))
	for i = 0, 5 do
		local x = r.x1 + 14 + (i % 3) * 4
		local z = cz + (i < 3 and -2 or 3)
		box(x - 0.6, 1.45, z - 0.6, x + 0.6, 1.6, z + 0.6, COL.Wood, nil, f, "Stool").CanCollide = false
		deco(box(x - 0.1, FLOOR_Y, z - 0.1, x + 0.1, 1.45, z + 0.1, COL.Metal, nil, f, "StoolLeg"))
	end
	spot("Music", Vector3.new(r.x1 + 6, FLOOR_Y, zA + 7), Vector3.new(r.x1 + 6, FLOOR_Y, zA + 3))
	spot("Music", Vector3.new(r.x1 + 14, FLOOR_Y, cz - 2), Vector3.new(r.x1 + 6, FLOOR_Y, cz))
	spot("Music", Vector3.new(r.x1 + 18, FLOOR_Y, cz + 3), Vector3.new(r.x1 + 6, FLOOR_Y, cz))
	spot("Music", Vector3.new(r.x1 + 22, FLOOR_Y, cz - 2), Vector3.new(r.x1 + 6, FLOOR_Y, cz))
end

function Furnish.Janitor(r, f: Folder)
	local zA, _, cz = roomZ(r)
	shelf(r.x1 + 1, zA + 0.3, r.x1 + 12, zA + 2, 7, f, false)
	for i = 0, 3 do
		deco(box(r.x1 + 2 + i * 2.5, 3, zA + 0.6, r.x1 + 3.5 + i * 2.5, 4.2, zA + 1.8, Color3.fromHSV(0.1 + i * 0.2, 0.5, 0.8), nil, f, "Supplies"))
	end
	World.ItemSpawns.Janitor = CFrame.new(r.x1 + 16, FLOOR_Y + 3.2, zA + 1.2) * CFrame.Angles(math.rad(-75), 0, 0)
	World.ItemSpawns.Janitor2 = CFrame.new(r.x1 + 6, 5.4, zA + 1.2)
	cyl(Vector3.new(r.x1 + 19, FLOOR_Y + 0.8, zA + 2), 1.6, 1.8, Color3.fromRGB(255, 200, 40), Enum.Material.SmoothPlastic, f).Name = "Bucket"
	sink(CFrame.new(r.x2 - 3, FLOOR_Y - 0.55, cz + 3) * CFrame.Angles(0, math.rad(-90), 0), f)
end

function Furnish.Restroom(r, f: Folder)
	local zA, zB, cz = roomZ(r)
	for i = 0, 2 do
		local x = r.x1 + 15 + i * 5
		deco(box(x - 0.1, FLOOR_Y, zA, x + 0.1, 8, zA + 6, Color3.fromRGB(200, 210, 230), nil, f, "StallWall"))
	end
	for i = 0, 2 do
		sink(CFrame.new(r.x1 + 3 + i * 3.5, FLOOR_Y - 0.55, zB - 1.4) * CFrame.Angles(0, math.rad(180), 0), f)
	end
	local mirror = box(r.x1 + 1.5, 4.5, zB - 0.6, r.x1 + 11, 8, zB - 0.5, Color3.fromRGB(200, 210, 220), Enum.Material.Glass, f, "Mirror")
	mirror.Reflectance = 0.6
	deco(mirror)
end

function Furnish.Cafeteria(r, f: Folder)
	local zA, zB, cz = roomZ(r)
	for i = 0, 1 do
		local x = r.x1 + 7 + i * 11
		box(x - 4, 2.6, cz - 1.5, x + 4, 2.8, cz + 1.5, COL.LightWood, Enum.Material.Wood, f, "LunchTable")
		box(x - 0.4, FLOOR_Y, cz - 0.4, x + 0.4, 2.6, cz + 0.4, COL.Metal, nil, f, "TableLeg")
		for _, dz in { -2.6, 2.6 } do
			box(x - 4, 1.45, cz + dz - 0.5, x + 4, 1.6, cz + dz + 0.5, COL.Wood, nil, f, "LunchBench").CanCollide = false
			for k = -1, 1 do
				spot("Cafeteria", Vector3.new(x + k * 2.6, FLOOR_Y, cz + dz * 1.5), Vector3.new(x + k * 2.6, FLOOR_Y, cz))
			end
		end
	end
	-- kitchen counter along the outer wall
	box(r.x1 + 2, FLOOR_Y, zB - 2.2, r.x2 - 2, 2.9, zB - 0.5, COL.White, nil, f, "Counter")
	box(r.x1 + 2, 2.9, zB - 2.3, r.x2 - 2, 3.05, zB - 0.4, COL.Metal, Enum.Material.Metal, f, "CounterTop")
	deco(box(r.x2 - 8, 3.05, zB - 2, r.x2 - 4, 3.15, zB - 0.6, COL.Black, nil, f, "Stove"))
	World.ItemSpawns.Cafeteria = CFrame.new(r.x1 + 9, 3.15, zB - 1.4) * CFrame.Angles(0, math.rad(90), math.rad(90))
	-- vending machines
	for i = 0, 1 do
		local v = box(r.x2 - 2.5, FLOOR_Y, zA + 1 + i * 3, r.x2 - 0.6, 7, zA + 3.6 + i * 3, i == 0 and COL.Crimson or Color3.fromRGB(30, 80, 160), nil, f, "Vending")
		deco(box(r.x2 - 2.55, 3, zA + 1.4 + i * 3, r.x2 - 2.5, 6.2, zA + 3.2 + i * 3, Color3.fromRGB(255, 255, 230), Enum.Material.Neon, f, "VendingGlow"))
	end
	spot("Cafeteria", Vector3.new(r.x1 + 15, FLOOR_Y, zB - 4.5), Vector3.new(r.x1 + 15, FLOOR_Y, zB))
end

function Furnish.Lockers(r, f: Folder)
	local zA, zB, _ = roomZ(r)
	for i = 0, 7 do
		local x = r.x1 + 1.5 + i * 2.1
		local l = box(x, FLOOR_Y, zA + 0.2, x + 2, 8, zA + 2, Color3.fromRGB(70, 90, 120), Enum.Material.Metal, f, "Locker")
		deco(box(x + 1.5, 4, zA + 2, x + 1.7, 4.8, zA + 2.1, COL.Black, nil, f, "Handle"))
	end
	box(r.x1 + 3, 1.45, zA + 6, r.x1 + 15, 1.65, zA + 7.2, COL.Wood, Enum.Material.Wood, f, "LockerBench").CanCollide = false
	for i = 0, 2 do
		shower(CFrame.lookAt(Vector3.new(r.x1 + 19 + i * 4.3, 0, zB - 2.3), Vector3.new(r.x1 + 19 + i * 4.3, 0, zB - 10)), f)
	end
end

function Furnish.Lounge(r, f: Folder)
	local zA, zB, cz = roomZ(r)
	for i = 0, 2 do
		local x = r.x1 + 5 + i * 8
		box(x - 2.5, 2.6, cz - 1.5, x + 2.5, 2.8, cz + 1.5, COL.DarkWood, Enum.Material.Wood, f, "TeacherDesk")
		box(x - 2.3, FLOOR_Y, cz - 1.3, x + 2.3, 2.6, cz + 1.3, COL.Metal, nil, f, "DeskBody")
		spot("Lounge", Vector3.new(x, FLOOR_Y, cz + 3), Vector3.new(x, FLOOR_Y, cz))
	end
	box(r.x2 - 9, FLOOR_Y, zB - 4, r.x2 - 1.5, 2.2, zB - 1, Color3.fromRGB(110, 40, 50), Enum.Material.Fabric, f, "Sofa")
	box(r.x2 - 9, 2.2, zB - 1.8, r.x2 - 1.5, 4, zB - 1, Color3.fromRGB(110, 40, 50), Enum.Material.Fabric, f, "SofaBack")
	box(r.x1 + 1, FLOOR_Y, zA + 0.5, r.x1 + 3, 4.5, zA + 2.5, COL.Black, nil, f, "CoffeeMachine")
	spot("Lounge", Vector3.new(r.x2 - 5, FLOOR_Y, zB - 6), Vector3.new(r.x2 - 5, FLOOR_Y, zB))
end

function Furnish.Lobby(r, f: Folder)
	local zA, zB, _ = roomZ(r)
	for i = 0, 1 do
		local x = r.x1 + 2 + i * 13.5
		box(x, FLOOR_Y, zA + 2, x + 2.5, 6.5, zB - 6, Color3.fromRGB(150, 120, 90), Enum.Material.Wood, f, "ShoeLockers")
	end
	deco(box(-4, FLOOR_Y, zB - 3, 4, FLOOR_Y + 0.05, zB - 0.5, COL.Crimson, Enum.Material.Fabric, f, "Mat"))
	spot("Lobby", Vector3.new(-4, FLOOR_Y, 8), Vector3.new(-8, FLOOR_Y, 8))
	spot("Lobby", Vector3.new(4, FLOOR_Y, 8), Vector3.new(8, FLOOR_Y, 8))
	spot("Lobby", Vector3.new(-3, FLOOR_Y, 14), Vector3.new(0, FLOOR_Y, 14))
	spot("Lobby", Vector3.new(3, FLOOR_Y, 14), Vector3.new(0, FLOOR_Y, 14))
end

-- ── School building ──────────────────────────────────────────────────────────
local function buildSchool()
	local school = folder("School")
	local walls = folder("Walls", school)
	local rooms = folder("Rooms", school)

	box(-100.5, 0, -20.5, 100.5, 0.5, 20.5, COL.Concrete, Enum.Material.Concrete, school, "Foundation")
	box(-100, 0.5, -3.5, 100, FLOOR_Y, 3.5, COL.Floor, Enum.Material.SmoothPlastic, school, "CorridorFloor").Reflectance = 0.06
	-- crimson stripe down the corridor
	deco(box(-100, FLOOR_Y, -0.4, 100, FLOOR_Y + 0.01, 0.4, COL.Crimson, nil, school, "CorridorStripe"))

	-- Corridor walls with doors
	local southOpen, northOpen = {}, {}
	for _, r in ROOMS do
		local cx = (r.x1 + r.x2) / 2
		local list = r.side == "S" and southOpen or northOpen
		if r.open then
			table.insert(list, { a = r.x1 + 0.5, b = r.x2 - 0.5, bottom = 0.5, top = 10 })
		else
			table.insert(list, { a = cx - 2.5, b = cx + 2.5, bottom = 0.5, top = 8.5 })
		end
	end
	wallX(-100, 100, 4, 0.5, WALL_TOP, 1, southOpen, COL.Wall, walls)
	wallX(-100, 100, -4, 0.5, WALL_TOP, 1, northOpen, COL.Wall, walls)

	-- Exterior long walls with windows
	local southWin, northWin = {}, {}
	for _, r in ROOMS do
		local cx = (r.x1 + r.x2) / 2
		local list = r.side == "S" and southWin or northWin
		if r.id == "Lobby" then
			table.insert(list, { a = -5, b = 5, bottom = 0.5, top = 9 })
		elseif r.highWindows then
			table.insert(list, { a = cx - 9, b = cx - 3, bottom = 8.5, top = 10.5, glass = true })
			table.insert(list, { a = cx + 3, b = cx + 9, bottom = 8.5, top = 10.5, glass = true })
		elseif r.id == "Stairs" then
			table.insert(list, { a = cx - 3, b = cx + 3, bottom = 6, top = 10, glass = true })
		else
			table.insert(list, { a = cx - 10, b = cx - 4, bottom = 3.5, top = 9.5, glass = true })
			table.insert(list, { a = cx + 4, b = cx + 10, bottom = 3.5, top = 9.5, glass = true })
		end
	end
	wallX(-100.5, 100.5, 20, 0, WALL_TOP + 1, 1, southWin, COL.Cream, walls)
	wallX(-100.5, 100.5, -20, 0, WALL_TOP + 1, 1, northWin, COL.Cream, walls)

	-- End walls (corridor exits on both ends)
	local endOpen = {
		{ a = -15, b = -9, bottom = 3.5, top = 9.5, glass = true },
		{ a = -2.5, b = 2.5, bottom = 0.5, top = 8.5 },
		{ a = 9, b = 15, bottom = 3.5, top = 9.5, glass = true },
	}
	wallZ(-20.5, 20.5, -100, 0, WALL_TOP + 1, 1, table.clone(endOpen), COL.Cream, walls)
	wallZ(-20.5, 20.5, 100, 0, WALL_TOP + 1, 1, table.clone(endOpen), COL.Cream, walls)

	-- Walls between rooms
	for _, r in ROOMS do
		if r.x1 > -100 then
			local zA, zB = roomZ(r)
			wallZ(zA, zB, r.x1, 0.5, WALL_TOP, 1, {}, COL.Wall, walls)
		end
	end

	-- Trim band + crimson belt outside
	for _, z in { 20.6, -20.6 } do
		deco(box(-100.6, 12.3, z - 0.1, 100.6, 13, z + 0.1, COL.Crimson, nil, walls, "Belt"))
		deco(box(-100.6, 0, z - 0.1, 100.6, 1.2, z + 0.1, COL.Trim, nil, walls, "Base"))
	end

	-- Rooms: floors, lights, signs, furniture
	for _, r in ROOMS do
		local f = folder(r.id, rooms)
		local zA, zB, cz = roomZ(r)
		local floorColor = (r.class or r.id == "Library" or r.id == "Music" or r.id == "Art") and COL.Wood or COL.Floor
		local floorMat = floorColor == COL.Wood and Enum.Material.WoodPlanks or Enum.Material.SmoothPlastic
		box(r.x1 + 0.5, 0.5, zA, r.x2 - 0.5, FLOOR_Y, zB, floorColor, floorMat, f, "Floor")
		if r.id ~= "Stairs" then
			ceilingLight((r.x1 + r.x2) / 2 - 7, cz, f)
			ceilingLight((r.x1 + r.x2) / 2 + 7, cz, f)
		else
			ceilingLight(-9, -7, f)
		end
		local cx = (r.x1 + r.x2) / 2
		local signZ = r.side == "S" and 3.35 or -3.35
		local face = r.side == "S" and -1 or 1
		local pos = Vector3.new(cx, 9.6, signZ)
		sign(r.label, CFrame.lookAt(pos, pos + Vector3.new(0, 0, face)), Vector2.new(6.5, 1.2), f)
		if not r.open then
			-- slid-open door panel on the corridor side
			local dz = r.side == "S" and 3.35 or -3.35
			deco(box(cx + 2.5, 0.6, dz - 0.12, cx + 7.3, 8.4, dz + 0.12, COL.LightWood, Enum.Material.Wood, f, "DoorPanel"))
			glass(box(cx + 4, 5, dz - 0.14, cx + 6, 7.6, dz + 0.14, COL.Glass, nil, f, "DoorWindow"))
		end
		if r.class then
			Furnish.Classroom(r, f)
		elseif Furnish[r.id] then
			Furnish[r.id](r, f)
		end
	end
	for x = -90, 90, 20 do
		ceilingLight(x, 0, school)
	end

	-- hallway: fire-axe case outside maintenance (north wall, corridor side)
	local caseX = 58
	local case = box(caseX - 2, 4, -3.45, caseX + 2, 7.5, -3.0, Color3.fromRGB(180, 20, 30), Enum.Material.SmoothPlastic, school, "AxeCase")
	glass(box(caseX - 1.8, 4.2, -3.0, caseX + 1.8, 7.3, -2.92, COL.Glass, nil, school, "AxeCaseGlass")).Name = "AxeCaseGlass"
	World.ItemSpawns.AxeCase = CFrame.new(caseX, 5.8, -3.2) * CFrame.Angles(0, 0, math.rad(90)) * CFrame.Angles(math.rad(90), 0, 0)
	World.Interact.AxeCase = case

	-- hallway patrol points + lobby
	for _, x in { -85, -55, -25, 0, 25, 55, 85 } do
		spot("Hall", Vector3.new(x, FLOOR_Y, 0), Vector3.new(x + 10, FLOOR_Y, 0))
	end

	-- Roof
	local roof = folder("Roof", school)
	local hole = { x1 = -2, x2 = 10, z1 = -18, z2 = -12 }
	box(-100.5, WALL_TOP, -20.5, hole.x1, ROOF_Y, 20.5, COL.RoofSlab, Enum.Material.Concrete, roof, "RoofSlab")
	box(hole.x2, WALL_TOP, -20.5, 100.5, ROOF_Y, 20.5, COL.RoofSlab, Enum.Material.Concrete, roof, "RoofSlab")
	box(hole.x1, WALL_TOP, -20.5, hole.x2, ROOF_Y, hole.z1, COL.RoofSlab, Enum.Material.Concrete, roof, "RoofSlab")
	box(hole.x1, WALL_TOP, hole.z2, hole.x2, ROOF_Y, 20.5, COL.RoofSlab, Enum.Material.Concrete, roof, "RoofSlab")
	-- parapet + fence
	local fenceCol = Color3.fromRGB(70, 90, 80)
	for _, s in { { -100.5, 100.5, 20 }, { -100.5, 100.5, -20 } } do
		box(s[1], ROOF_Y, s[3] - 0.4, s[2], ROOF_Y + 1.2, s[3] + 0.4, COL.Cream, nil, roof, "Parapet")
		local fence = box(s[1], ROOF_Y + 1.2, s[3] - 0.08, s[2], ROOF_Y + 6, s[3] + 0.08, fenceCol, Enum.Material.DiamondPlate, roof, "Fence")
		fence.Transparency = 0.55
		fence.CastShadow = false
	end
	for _, x in { -100, 100 } do
		box(x - 0.4, ROOF_Y, -20.5, x + 0.4, ROOF_Y + 1.2, 20.5, COL.Cream, nil, roof, "Parapet")
		local fence = box(x - 0.08, ROOF_Y + 1.2, -20.5, x + 0.08, ROOF_Y + 6, 20.5, fenceCol, Enum.Material.DiamondPlate, roof, "Fence")
		fence.Transparency = 0.55
		fence.CastShadow = false
	end
	-- roof-access hut over the stair hole
	local hutTop = ROOF_Y + 8
	wallZ(-20, -10, -3, ROOF_Y, hutTop, 1, {}, COL.Cream, roof)
	wallZ(-20, -10, 13.5, ROOF_Y, hutTop, 1, {}, COL.Cream, roof)
	wallX(-3, 13.5, -10, ROOF_Y, hutTop, 1, { { a = 9, b = 13, bottom = ROOF_Y, top = ROOF_Y + 7.5 } }, COL.Cream, roof)
	box(-3.5, hutTop, -20.5, 14, hutTop + 0.6, -9.5, COL.Trim, nil, roof, "HutRoof")
	local hutLight = box(3, hutTop - 0.3, -16, 6, hutTop - 0.05, -14, Color3.fromRGB(255, 250, 240), Enum.Material.Neon, roof, "HutLight")
	deco(hutLight)
	light(hutLight, 14, 1)
	-- rooftop benches + spots
	for i, x in { -60, -35, 30, 55 } do
		bench(CFrame.new(x, ROOF_Y, 14) * CFrame.Angles(0, math.rad(180), 0), roof)
		spot("Roof", Vector3.new(x - 1.5, ROOF_Y, 16.4), Vector3.new(x - 1.5, ROOF_Y, 30))
		spot("Roof", Vector3.new(x + 1.5, ROOF_Y, 16.4), Vector3.new(x + 1.5, ROOF_Y, 30))
	end
	spot("RoofHaru", Vector3.new(40, ROOF_Y, 16), Vector3.new(40, ROOF_Y, 40))
	spot("RoofConfess", Vector3.new(40, ROOF_Y, 16), Vector3.new(40, ROOF_Y, 40))
	spot("RoofConfess", Vector3.new(40, ROOF_Y, 12.5), Vector3.new(40, ROOF_Y, 16))
end

-- ── Grounds ──────────────────────────────────────────────────────────────────
local function buildGrounds()
	local g = folder("Grounds")
	box(-500, -2, -500, 500, 0, 500, COL.Grass, Enum.Material.Grass, g, "Ground")
	box(-112, 0, -32, 112, 0.06, 32, COL.Sidewalk, Enum.Material.Concrete, g, "Apron")

	-- Courtyard path + fountain plaza
	box(-5, 0, 20, 5, 0.08, 82, Color3.fromRGB(205, 196, 186), Enum.Material.Pavement, g, "Path")
	local plaza = cyl(Vector3.new(0, 0.05, 50), 0.12, 34, Color3.fromRGB(200, 192, 182), Enum.Material.Pavement, g)
	plaza.Name = "Plaza"
	local basin = cyl(Vector3.new(0, 0.8, 50), 1.6, 15, Color3.fromRGB(170, 170, 176), Enum.Material.Marble, g)
	basin.Name = "FountainBasin"
	local water = cyl(Vector3.new(0, 1.62, 50), 0.1, 13.6, Color3.fromRGB(110, 170, 210), Enum.Material.Glass, g)
	water.Transparency = 0.25
	water.Name = "FountainWater"
	cyl(Vector3.new(0, 3, 50), 4.5, 1.6, Color3.fromRGB(180, 180, 186), Enum.Material.Marble, g)
	local top = cyl(Vector3.new(0, 5.3, 50), 0.6, 4.5, Color3.fromRGB(180, 180, 186), Enum.Material.Marble, g)
	local spray = Instance.new("ParticleEmitter")
	spray.Color = ColorSequence.new(Color3.fromRGB(210, 235, 255))
	spray.Transparency = NumberSequence.new(0.3, 1)
	spray.Size = NumberSequence.new(0.25, 0.05)
	spray.Lifetime = NumberRange.new(0.8, 1.1)
	spray.Rate = 90
	spray.Speed = NumberRange.new(7, 9)
	spray.SpreadAngle = Vector2.new(18, 18)
	spray.Acceleration = Vector3.new(0, -30, 0)
	spray.EmissionDirection = Enum.NormalId.Right -- cylinder X axis is world-up after rotation
	spray.Parent = top
	ring("Fountain", Vector3.new(0, 0.1, 50), 10.5, 6, true)

	-- benches round the plaza (Haru reads at the first one)
	for i, a in { 45, 135, 225, 315 } do
		local r = math.rad(a)
		local pos = Vector3.new(math.cos(r) * 15, 0.1, 50 + math.sin(r) * 15)
		bench(CFrame.lookAt(pos, Vector3.new(0, 0.1, 50)), g)
		if i == 1 then
			spot("FountainBench", pos + (Vector3.new(0, 0.1, 50) - pos).Unit * 1.8, Vector3.new(0, 0.1, 50))
		end
	end

	-- rose beds (Kurobara = black rose)
	for _, b in { { -40, 30, -18, 40 }, { 18, 30, 40, 40 }, { -40, 60, -18, 70 }, { 18, 60, 40, 70 } } do
		roseBed(b[1], b[2], b[3], b[4], g)
		local cx, cz = (b[1] + b[3]) / 2, (b[2] + b[4]) / 2
		spot("Courtyard", Vector3.new(cx - 6, 0.1, cz + 7.5), Vector3.new(cx - 6, 0.1, cz))
		spot("Courtyard", Vector3.new(cx + 6, 0.1, cz - 7.5), Vector3.new(cx + 6, 0.1, cz))
	end
	for _, p in { { -70, 35 }, { 70, 35 }, { -70, 68 }, { 70, 68 } } do
		tree(p[1], p[2], "Blossom", g)
	end
	for _, p in { { -100, 50 }, { 100, 50 }, { -120, -20 }, { 120, 0 }, { -50, -40 }, { 30, -40 } } do
		tree(p[1], p[2], "Green", g)
	end
	for _, x in { -55, 55 } do
		bench(CFrame.new(x, 0.1, 52) * CFrame.Angles(0, math.rad(x < 0 and -90 or 90), 0), g)
		spot("Courtyard", Vector3.new(x + (x < 0 and 2.5 or -2.5), 0.1, 50), Vector3.new(0, 0.1, 50))
		spot("Courtyard", Vector3.new(x + (x < 0 and 2.5 or -2.5), 0.1, 55), Vector3.new(0, 0.1, 55))
	end
	for _, p in { { -12, 25 }, { 12, 25 }, { -12, 76 }, { 12, 76 } } do
		lamp(Vector3.new(p[1], 0, p[2]), g)
	end

	-- Gate + perimeter wall
	local gate = folder("Gate", g)
	wallX(-130, -8, 80, 0, 5, 1, {}, COL.Cream, gate)
	wallX(8, 130, 80, 0, 5, 1, {}, COL.Cream, gate)
	for _, x in { -9, 9 } do
		box(x - 1.2, 0, 78.8, x + 1.2, 11, 81.2, COL.Trim, Enum.Material.Slate, gate, "GatePillar")
	end
	box(-10.2, 11, 79.4, 10.2, 13, 80.6, COL.Trim, Enum.Material.Slate, gate, "GateBeam")
	sign("KUROBARA ACADEMY", CFrame.lookAt(Vector3.new(0, 12, 80.75), Vector3.new(0, 12, 90)), Vector2.new(18, 1.6), gate, COL.White, COL.Crimson)
	sign("KUROBARA ACADEMY", CFrame.lookAt(Vector3.new(0, 12, 79.25), Vector3.new(0, 12, 70)), Vector2.new(18, 1.6), gate, COL.White, COL.Crimson)
	wallZ(-150, 80, -130, 0, 5, 1, {}, COL.Cream, gate)
	wallZ(-150, 80, 130, 0, 5, 1, {}, COL.Cream, gate)
	wallX(-130, 130, -150, 0, 5, 1, {}, COL.Cream, gate)
	for x = -6, 6, 4 do
		spot("Gate", Vector3.new(x, 0.1, 72), Vector3.new(x, 0.1, 60))
	end
	spot("Exit", Vector3.new(0, 0.1, 96), Vector3.new(0, 0.1, 120))
	spot("Exit", Vector3.new(-14, 0.1, 96), Vector3.new(-30, 0.1, 96))
	spot("Exit", Vector3.new(14, 0.1, 96), Vector3.new(30, 0.1, 96))
	local gateZone = box(-8, 0, 76, 8, 6, 82, COL.White, nil, gate, "GateZone")
	gateZone.Transparency = 1
	deco(gateZone)
	World.Interact.Gate = gateZone

	-- Bike shelter
	local bikes = folder("BikeShelter", g)
	box(-126, 7, 28, -110, 7.4, 62, COL.Metal, Enum.Material.Metal, bikes, "ShelterRoof")
	for _, z in { 29, 45, 61 } do
		for _, x in { -125, -111 } do
			box(x - 0.2, 0, z - 0.2, x + 0.2, 7, z + 0.2, COL.Metal, Enum.Material.Metal, bikes, "Post")
		end
	end
	for z = 32, 58, 3 do
		deco(box(-122, 0.8, z - 0.1, -116, 2, z + 0.1, Color3.fromHSV(math.random(), 0.5, 0.7), Enum.Material.Metal, bikes, "Bike"))
	end

	-- Sports field
	local field = folder("Field", g)
	box(-45, 0, -145, 25, 0.08, -60, Color3.fromRGB(176, 134, 92), Enum.Material.Ground, field, "Track")
	box(-38, 0.08, -138, 18, 0.1, -67, Color3.fromRGB(96, 140, 74), Enum.Material.Grass, field, "Pitch")
	for _, z in { -138, -67 } do
		deco(box(-38, 0.1, z - 0.15, 18, 0.12, z + 0.15, COL.White, nil, field, "Line"))
	end
	deco(box(-10.15, 0.1, -138, -9.85, 0.12, -67, COL.White, nil, field, "Line"))
	for _, z in { -136, -69 } do
		box(-14, 0, z - 0.2, -13.6, 7, z + 0.2, COL.White, nil, field, "GoalPost")
		box(-6.4, 0, z - 0.2, -6, 7, z + 0.2, COL.White, nil, field, "GoalPost")
		box(-14, 7, z - 0.2, -6, 7.4, z + 0.2, COL.White, nil, field, "GoalBar")
	end
	ring("Field", Vector3.new(-10, 0.1, -102), 14, 6, true)

	-- Gym
	local gym = folder("Gym", g)
	box(40, 0, -110, 100, 0.5, -55, COL.Concrete, Enum.Material.Concrete, gym, "GymFoundation")
	box(41, 0.5, -109, 99, FLOOR_Y, -56, Color3.fromRGB(196, 150, 96), Enum.Material.WoodPlanks, gym, "GymFloor")
	wallX(40, 100, -55, 0, 18, 1, { { a = 66, b = 74, bottom = 0.5, top = 10 }, { a = 45, b = 60, bottom = 11, top = 16, glass = true }, { a = 80, b = 95, bottom = 11, top = 16, glass = true } }, COL.Cream, gym)
	wallX(40, 100, -110, 0, 18, 1, { { a = 50, b = 90, bottom = 11, top = 16, glass = true } }, COL.Cream, gym)
	wallZ(-110, -55, 40, 0, 18, 1, { { a = -90, b = -75, bottom = 11, top = 16, glass = true } }, COL.Cream, gym)
	wallZ(-110, -55, 100, 0, 18, 1, { { a = -90, b = -75, bottom = 11, top = 16, glass = true } }, COL.Cream, gym)
	box(39.5, 18, -110.5, 100.5, 19, -54.5, COL.RoofSlab, Enum.Material.Concrete, gym, "GymRoof")
	deco(box(39.4, 16.5, -55.1, 100.6, 18, -54.9, COL.Crimson, nil, gym, "GymBelt"))
	for _, x in { 55, 70, 85 } do
		local gl = box(x - 3, 17.6, -83, x + 3, 17.9, -81, Color3.fromRGB(255, 250, 240), Enum.Material.Neon, gym, "GymLight")
		deco(gl)
		light(gl, 24, 1.2)
	end
	for _, z in { -107, -58 } do
		box(68, 8, z - 0.2, 72, 11, z + 0.2, COL.White, nil, gym, "Backboard")
		local rim = cyl(Vector3.new(70, 8.5, z + (z < -80 and 1 or -1)), 0.2, 1.8, Color3.fromRGB(240, 100, 30), Enum.Material.Metal, gym)
		deco(rim)
	end
	-- sports shed corner (bat rack)
	box(90, FLOOR_Y, -109, 99, 4, -104, COL.DarkWood, Enum.Material.Wood, gym, "Rack")
	World.ItemSpawns.Gym = CFrame.new(94, 4.4, -106) * CFrame.Angles(0, 0, math.rad(90))
	ring("Gym", Vector3.new(60, FLOOR_Y, -82), 9, 6, true)

	-- Garden + greenhouse
	local garden = folder("Garden", g)
	for i = 0, 3 do
		local z = -100 + i * 13
		box(-120, 0, z, -78, 0.6, z + 6, COL.Soil, Enum.Material.Ground, garden, "Bed")
		for k = 0, 8 do
			local flower = ball(Vector3.new(-117 + k * 4.5, 1.1, z + 3), 1, Color3.fromHSV(math.random(), 0.6, 1), Enum.Material.SmoothPlastic, garden)
			deco(flower)
			deco(cyl(Vector3.new(-117 + k * 4.5, 0.7, z + 3), 0.8, 0.15, COL.Leaf, nil, garden))
		end
		spot("Garden", Vector3.new(-104 + i * 6, 0.1, z + 8.5), Vector3.new(-104 + i * 6, 0.1, z + 3))
	end
	local dig = box(-72, 0, -54, -62, 0.25, -44, Color3.fromRGB(110, 76, 50), Enum.Material.Ground, garden, "DigPlot")
	World.Interact.DigPlot = dig
	sign("COMPOST · DO NOT DIG", CFrame.lookAt(Vector3.new(-67, 2, -43.5), Vector3.new(-67, 2, -30)), Vector2.new(5, 0.9), garden, COL.White, COL.DarkWood)

	local gh = folder("Greenhouse", garden)
	local gx1, gx2, gz1, gz2 = -128, -106, -138, -118
	box(gx1, 0, gz1, gx2, 0.4, gz2, COL.Concrete, Enum.Material.Concrete, gh, "GHFloor")
	local function ghWall(p: BasePart)
		glass(p)
		p.Transparency = 0.45
		p.CanQuery = true -- frosted: blocks sight → Mirei is alone in here
		p.Color = Color3.fromRGB(215, 235, 225)
	end
	ghWall(box(gx1, 0.4, gz1 - 0.1, gx2, 9, gz1 + 0.1, COL.Glass, nil, gh, "GHWall"))
	ghWall(box(gx1, 0.4, gz2 - 0.1, gx2, 9, gz2 + 0.1, COL.Glass, nil, gh, "GHWall"))
	ghWall(box(gx1 - 0.1, 0.4, gz1, gx1 + 0.1, 9, gz2, COL.Glass, nil, gh, "GHWall"))
	ghWall(box(gx2 - 0.1, 0.4, gz1, gx2 + 0.1, 9, -130, COL.Glass, nil, gh, "GHWall"))
	ghWall(box(gx2 - 0.1, 0.4, -125, gx2 + 0.1, 9, gz2, COL.Glass, nil, gh, "GHWall"))
	ghWall(box(gx2 - 0.1, 7.5, -130, gx2 + 0.1, 9, -125, COL.Glass, nil, gh, "GHWall"))
	ghWall(box(gx1, 9, gz1, gx2, 9.3, gz2, COL.Glass, nil, gh, "GHRoof"))
	for x = gx1, gx2, 5.5 do
		deco(box(x - 0.15, 0.4, gz1 - 0.15, x + 0.15, 9.3, gz1 + 0.15, COL.White, nil, gh, "Frame"))
		deco(box(x - 0.15, 0.4, gz2 - 0.15, x + 0.15, 9.3, gz2 + 0.15, COL.White, nil, gh, "Frame"))
	end
	box(gx1 + 1, 0.4, gz1 + 1, gx1 + 4, 3, gz2 - 1, COL.DarkWood, Enum.Material.Wood, gh, "PlantTable")
	for z = gz1 + 2, gz2 - 2, 2.5 do
		deco(ball(Vector3.new(gx1 + 2.5, 3.6, z), 1.4, Color3.fromRGB(240, 240, 255), Enum.Material.SmoothPlastic, gh)) -- lilies
		deco(cyl(Vector3.new(gx1 + 2.5, 3.2, z), 0.6, 1, Color3.fromRGB(150, 90, 60), Enum.Material.Slate, gh))
	end
	box(gx2 - 5, 0.4, gz1 + 1, gx2 - 1, 3, gz1 + 4, COL.DarkWood, Enum.Material.Wood, gh, "ToolTable")
	World.ItemSpawns.Greenhouse = CFrame.new(gx2 - 3, 3.25, gz1 + 2.5) * CFrame.Angles(0, math.rad(20), math.rad(90))
	World.ItemSpawns.Greenhouse2 = CFrame.new(gx2 - 1.2, 2.6, gz1 + 6) * CFrame.Angles(math.rad(-80), 0, 0)
	spot("Greenhouse", Vector3.new(gx1 + 6, 0.5, -128), Vector3.new(gx1 + 2, 0.5, -128))
	spot("Greenhouse", Vector3.new(gx1 + 6, 0.5, -123), Vector3.new(gx1 + 2, 0.5, -123))

	-- Incinerator + dumpster behind the east wing
	local inc = folder("Incinerator", g)
	box(108, 0, -46, 120, 7, -38, COL.Brick, Enum.Material.Brick, inc, "IncineratorBody")
	box(114, 7, -44, 117, 22, -41, COL.Brick, Enum.Material.Brick, inc, "Chimney")
	local mouth = box(110, 1.5, -38.2, 116, 5, -37.9, Color3.fromRGB(255, 120, 30), Enum.Material.Neon, inc, "IncineratorMouth")
	local fire = Instance.new("PointLight")
	fire.Color = Color3.fromRGB(255, 130, 50)
	fire.Range = 14
	fire.Brightness = 1.5
	fire.Parent = mouth
	local smoke = Instance.new("ParticleEmitter")
	smoke.Name = "Smoke"
	smoke.Color = ColorSequence.new(Color3.fromRGB(70, 70, 74))
	smoke.Transparency = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0.5), NumberSequenceKeypoint.new(1, 1) })
	smoke.Size = NumberSequence.new({ NumberSequenceKeypoint.new(0, 2), NumberSequenceKeypoint.new(1, 9) })
	smoke.Lifetime = NumberRange.new(5, 8)
	smoke.Rate = 4
	smoke.Speed = NumberRange.new(3, 5)
	smoke.EmissionDirection = Enum.NormalId.Top
	smoke.Acceleration = Vector3.new(1.5, 0.5, 0)
	smoke.Parent = inc:FindFirstChild("Chimney")
	World.Interact.Incinerator = mouth
	local dump = box(108, 0, -28, 118, 5, -22, Color3.fromRGB(40, 90, 60), Enum.Material.Metal, inc, "Dumpster")
	box(107.8, 5, -28.2, 118.2, 5.4, -21.8, Color3.fromRGB(30, 70, 46), Enum.Material.Metal, inc, "DumpsterLid")
	World.Interact.Dumpster = dump
end

-- ── Street & Nyx's house ─────────────────────────────────────────────────────
local function house(x1: number, z1: number, x2: number, z2: number, color: Color3, parent: Instance)
	local f = folder("House", parent)
	box(x1, 0, z1, x2, 9, z2, color, Enum.Material.Plaster, f, "HouseBody")
	local cx = (x1 + x2) / 2
	local w, d = x2 - x1 + 1, z2 - z1 + 1
	local roofCol = Color3.fromRGB(70, 60, 70)
	local w1 = Instance.new("WedgePart")
	w1.Anchored = true
	w1.Size = Vector3.new(w, 5, d / 2)
	w1.CFrame = CFrame.new(cx, 11.5, z1 + d / 4 - 0.5)
	w1.Color = roofCol
	w1.Material = Enum.Material.Slate
	w1.Parent = f
	local w2 = w1:Clone()
	w2.CFrame = CFrame.new(cx, 11.5, z2 - d / 4 + 0.5) * CFrame.Angles(0, math.rad(180), 0)
	w2.Parent = f
	for _, xo in { 0.3, 0.7 } do
		local wx = x1 + (x2 - x1) * xo
		deco(box(wx - 1.5, 3.5, z1 - 0.1, wx + 1.5, 6.5, z1, Color3.fromRGB(255, 230, 170), Enum.Material.Neon, f, "LitWindow")).Transparency = 0.3
	end
end

local function buildStreet()
	local s = folder("Street")
	box(-500, 0, 90, 500, 0.08, 110, COL.Asphalt, Enum.Material.Asphalt, s, "Road")
	box(-500, 0, 84, 500, 0.3, 90, COL.Sidewalk, Enum.Material.Concrete, s, "Sidewalk")
	box(-500, 0, 110, 500, 0.3, 116, COL.Sidewalk, Enum.Material.Concrete, s, "Sidewalk")
	for x = -490, 490, 12 do
		deco(box(x, 0.08, 99.8, x + 6, 0.1, 100.2, COL.White, nil, s, "Dash"))
	end
	for i = 0, 6 do
		deco(box(-6 + i * 2, 0.08, 91, -5 + i * 2, 0.1, 109, COL.White, nil, s, "Crosswalk"))
	end
	for x = -200, 200, 40 do
		lamp(Vector3.new(x, 0.3, 87), s)
		lamp(Vector3.new(x + 20, 0.3, 113), s)
	end
	for _, h in { { -150, 124, -120, 150 }, { -110, 124, -80, 148 }, { -10, 124, 20, 152 }, { 30, 124, 60, 146 }, { 75, 124, 105, 150 }, { 120, 124, 150, 148 } } do
		house(h[1], h[2], h[3], h[4], Color3.fromHSV(math.random(), 0.15, 0.85), s)
	end
end

local function buildHome()
	local h = folder("Home")
	local x1, z1, x2, z2 = -64, 124, -28, 152
	local wallCol = Color3.fromRGB(232, 220, 226)
	box(x1, 0, z1, x2, 0.5, z2, COL.Concrete, Enum.Material.Concrete, h, "HomeFoundation")
	box(x1 + 0.5, 0.5, z1 + 0.5, x2 - 0.5, FLOOR_Y, z2 - 0.5, Color3.fromRGB(150, 110, 80), Enum.Material.WoodPlanks, h, "HomeFloor")
	wallX(x1, x2, z1, 0, 10, 1, { { a = -56, b = -52, bottom = 0.5, top = 8 }, { a = -40, b = -34, bottom = 3.5, top = 7.5, glass = true } }, wallCol, h)
	wallX(x1, x2, z2, 0, 10, 1, { { a = -60, b = -54, bottom = 3.5, top = 7.5, glass = true }, { a = -38, b = -32, bottom = 3.5, top = 7.5, glass = true } }, wallCol, h)
	wallZ(z1, z2, x1, 0, 10, 1, {}, wallCol, h)
	wallZ(z1, z2, x2, 0, 10, 1, {}, wallCol, h)
	-- interior partitions
	wallZ(z1 + 0.5, z2 - 0.5, -44, 0.5, 10, 0.6, { { a = 128, b = 132, bottom = 0.5, top = 8 }, { a = 142, b = 146, bottom = 0.5, top = 8 } }, Color3.fromRGB(245, 238, 240), h)
	wallX(-44, x2 - 0.5, 136, 0.5, 10, 0.6, {}, Color3.fromRGB(245, 238, 240), h)
	box(x1 - 0.5, 10, z1 - 0.5, x2 + 0.5, 10.6, z2 + 0.5, Color3.fromRGB(70, 60, 70), Enum.Material.Slate, h, "HomeRoof")
	for _, p in { { -54, 138 }, { -36, 144 }, { -36, 130 } } do
		local l = box(p[1] - 1.5, 9.6, p[2] - 1.5, p[1] + 1.5, 9.95, p[2] + 1.5, Color3.fromRGB(255, 240, 225), Enum.Material.Neon, h, "HomeLight")
		deco(l)
		light(l, 16, 0.9, Color3.fromRGB(255, 225, 200))
	end
	-- kitchen (west wall)
	box(x1 + 0.5, FLOOR_Y, 128, x1 + 3, 2.9, 146, COL.White, nil, h, "KitchenCounter")
	box(x1 + 0.4, 2.9, 127.9, x1 + 3.1, 3.05, 146.1, Color3.fromRGB(60, 60, 66), Enum.Material.Granite, h, "KitchenTop")
	sink(CFrame.new(x1 + 1.8, FLOOR_Y - 0.55 + 0.05, 140) * CFrame.Angles(0, math.rad(90), 0), h)
	World.ItemSpawns.HomeKitchen = CFrame.new(x1 + 1.8, 3.15, 132) * CFrame.Angles(0, 0, math.rad(90))
	box(-56, 2.5, 136, -50, 2.7, 142, COL.Wood, Enum.Material.Wood, h, "DiningTable")
	box(-53.4, FLOOR_Y, 138.6, -52.6, 2.5, 139.4, COL.DarkWood, nil, h, "TableLeg")
	-- bathroom (north-east)
	shower(CFrame.lookAt(Vector3.new(-31, 0, 128), Vector3.new(-40, 0, 128)), h)
	-- bedroom (south-east)
	box(-34, FLOOR_Y, 144, -29, 2, 151.5, Color3.fromRGB(40, 30, 40), Enum.Material.Fabric, h, "Bed")
	box(-34, 2, 144, -29, 2.4, 151.5, Color3.fromRGB(120, 20, 40), Enum.Material.Fabric, h, "Blanket")
	box(-30.5, 2.4, 149.5, -29.3, 2.9, 151.3, COL.White, Enum.Material.Fabric, h, "Pillow")
	box(-43.4, FLOOR_Y, 147, -40, 2.8, 151.5, COL.DarkWood, Enum.Material.Wood, h, "Desk")
	local diary = box(-42.2, 2.8, 149, -41.2, 3, 150.2, COL.Crimson, Enum.Material.Fabric, h, "Diary")
	World.Interact.Diary = diary
	local wardrobe = box(-43.4, FLOOR_Y, 137, -41, 8, 141, COL.DarkWood, Enum.Material.Wood, h, "Wardrobe")
	World.Interact.Wardrobe = wardrobe
	local door = box(-56, 0.5, 124.4, -52, 8, 124.8, COL.White, nil, h, "FrontDoorZone")
	door.Transparency = 1
	deco(door)
	World.Interact.HomeDoor = door
	World.HomeSpawn = CFrame.lookAt(Vector3.new(-36, FLOOR_Y + 3.2, 146), Vector3.new(-44, FLOOR_Y + 3.2, 146))
	World.SchoolArrive = CFrame.lookAt(Vector3.new(0, 3.3, 84), Vector3.new(0, 3.3, 60))
	-- garden path to the street
	box(-56, 0, 116, -52, 0.32, 124, COL.Sidewalk, Enum.Material.Pavement, h, "HomePath")
	sign("AMANE", CFrame.lookAt(Vector3.new(-50, 7, 123.4), Vector3.new(-50, 7, 110)), Vector2.new(3, 0.8), h, COL.White, COL.Trim)
end

-- ── Lighting ─────────────────────────────────────────────────────────────────
function Builder.SetupLighting()
	Lighting.Ambient = Color3.fromRGB(60, 56, 70)
	Lighting.OutdoorAmbient = Color3.fromRGB(118, 112, 130)
	Lighting.Brightness = 2.2
	Lighting.EnvironmentDiffuseScale = 0.6
	Lighting.EnvironmentSpecularScale = 0.8
	Lighting.ShadowSoftness = 0.25
	Lighting.GeographicLatitude = 35
	Lighting.ExposureCompensation = 0.1
	local function ensure(className: string, name: string)
		local e = Lighting:FindFirstChild(name)
		if not e then
			e = Instance.new(className)
			e.Name = name
			e.Parent = Lighting
		end
		return e
	end
	local atm = ensure("Atmosphere", "Atmosphere") :: Atmosphere
	atm.Density = 0.3
	atm.Offset = 0.15
	atm.Color = Color3.fromRGB(205, 194, 214)
	atm.Decay = Color3.fromRGB(110, 86, 120)
	atm.Glare = 0.25
	atm.Haze = 1.4
	local bloom = ensure("BloomEffect", "Bloom") :: BloomEffect
	bloom.Intensity = 0.55
	bloom.Size = 26
	bloom.Threshold = 1.5
	local rays = ensure("SunRaysEffect", "SunRays") :: SunRaysEffect
	rays.Intensity = 0.05
	rays.Spread = 0.6
	local cc = ensure("ColorCorrectionEffect", "Grade") :: ColorCorrectionEffect
	cc.Contrast = 0.08
	cc.Saturation = 0.04
	cc.TintColor = Color3.fromRGB(255, 246, 250)
	ensure("Sky", "Sky")
end

function Builder.Build()
	local existing = workspace:FindFirstChild("Map")
	if existing and Builder.UseExistingMap then
		Map = existing :: Folder
	else
		if existing then
			existing:Destroy()
		end
		Map = Instance.new("Folder")
		Map.Name = "Map"
		buildSchool()
		buildGrounds()
		buildStreet()
		buildHome()
		Map.Parent = workspace
	end
	Builder.SetupLighting()
	for _, name in { "NPCs", "Evidence", "Items" } do
		if not workspace:FindFirstChild(name) then
			local f = Instance.new("Folder")
			f.Name = name
			f.Parent = workspace
		end
	end
	return World
end

return Builder
