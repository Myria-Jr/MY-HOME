-- KUROBARA · Build kit: shared helpers for the map modules
-- Scale: 1 stud ≈ 0.3 m. Nyx ≈ 5.3 studs (1.60 m). Storey = 13 studs (3.9 m).

local K = {}

K.World = {
	Spots = {},
	Seats = {},
	ClassFront = {},
	ItemSpawns = {},
	Interact = { Sinks = {}, Showers = {} },
	MapZones = {},
	Lawns = {},
}
K.Map = nil :: Folder?

K.FH = 13 -- storey height
function K.FLOOR(n: number): number
	return n * K.FH + 0.55 -- walkable floor of storey n (0 = ground)
end

K.COL = {
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
	Water = Color3.fromRGB(70, 150, 200),
}
local COL = K.COL

-- ── parts ────────────────────────────────────────────────────────────────────
function K.P(size: Vector3, cf: CFrame, color: Color3, mat: Enum.Material?, parent: Instance?, name: string?): Part
	local p = Instance.new("Part")
	p.Anchored = true
	p.Size = size
	p.CFrame = cf
	p.Color = color
	p.Material = mat or Enum.Material.SmoothPlastic
	p.TopSurface = Enum.SurfaceType.Smooth
	p.BottomSurface = Enum.SurfaceType.Smooth
	p.Name = name or "Part"
	p.Parent = parent or K.Map
	return p
end

function K.box(x1: number, y1: number, z1: number, x2: number, y2: number, z2: number, color: Color3, mat: Enum.Material?, parent: Instance?, name: string?): Part
	local sx, sy, sz = math.abs(x2 - x1), math.abs(y2 - y1), math.abs(z2 - z1)
	return K.P(
		Vector3.new(math.max(sx, 0.05), math.max(sy, 0.05), math.max(sz, 0.05)),
		CFrame.new((x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2),
		color, mat, parent, name
	)
end

function K.deco(p: BasePart): BasePart
	p.CanCollide = false
	p.CanQuery = false
	p.CanTouch = false
	p.CastShadow = false
	return p
end

function K.ball(pos: Vector3, d: number, color: Color3, mat: Enum.Material?, parent: Instance?): Part
	local p = K.P(Vector3.new(d, d, d), CFrame.new(pos), color, mat, parent, "Ball")
	p.Shape = Enum.PartType.Ball
	return p
end

-- vertical cylinder (Roblox cylinders run along X, so rotate)
function K.cyl(pos: Vector3, height: number, d: number, color: Color3, mat: Enum.Material?, parent: Instance?): Part
	local p = K.P(Vector3.new(height, d, d), CFrame.new(pos) * CFrame.Angles(0, 0, math.rad(90)), color, mat, parent, "Cylinder")
	p.Shape = Enum.PartType.Cylinder
	return p
end

function K.wedge(size: Vector3, cf: CFrame, color: Color3, mat: Enum.Material?, parent: Instance?, name: string?): WedgePart
	local w = Instance.new("WedgePart")
	w.Anchored = true
	w.Size = size
	w.CFrame = cf
	w.Color = color
	w.Material = mat or Enum.Material.SmoothPlastic
	w.Name = name or "Wedge"
	w.Parent = parent or K.Map
	return w
end

function K.glass(p: BasePart): BasePart
	p.Material = Enum.Material.Glass
	p.Color = COL.Glass
	p.Transparency = 0.6
	p.CanQuery = false -- NPCs can see through windows
	p.CastShadow = false
	return p
end

function K.folder(name: string, parent: Instance?): Folder
	local f = Instance.new("Folder")
	f.Name = name
	f.Parent = parent or K.Map
	return f
end

function K.light(p: BasePart, range: number?, brightness: number?, color: Color3?, shadows: boolean?)
	local l = Instance.new("SurfaceLight")
	l.Face = Enum.NormalId.Bottom
	l.Range = range or 18
	l.Brightness = brightness or 1.1
	l.Angle = 120
	l.Color = color or Color3.fromRGB(255, 246, 232)
	l.Shadows = shadows == true
	l.Parent = p
end

function K.ceilingLight(x: number, y: number, z: number, parent: Instance, shadows: boolean?)
	local p = K.box(x - 3, y - 0.35, z - 0.7, x + 3, y - 0.1, z + 0.7, Color3.fromRGB(255, 250, 240), Enum.Material.Neon, parent, "CeilingLight")
	K.deco(p)
	K.light(p, 18, 1.05, nil, shadows)
end

function K.sign(text: string, cf: CFrame, size: Vector2, parent: Instance, textColor: Color3?, bg: Color3?)
	local p = K.P(Vector3.new(size.X, size.Y, 0.2), cf, bg or COL.Trim, Enum.Material.SmoothPlastic, parent, "Sign")
	K.deco(p)
	local gui = Instance.new("SurfaceGui")
	gui.Face = Enum.NormalId.Front
	gui.SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud
	gui.PixelsPerStud = 50
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

-- ── walls with openings (doors / windows with frames) ────────────────────────
local function frameWindowX(o, z: number, t: number, parent: Instance)
	K.glass(K.box(o.a, o.bottom, z - 0.08, o.b, o.top, z + 0.08, COL.Glass, nil, parent, "Window"))
	K.deco(K.box((o.a + o.b) / 2 - 0.1, o.bottom, z - 0.15, (o.a + o.b) / 2 + 0.1, o.top, z + 0.15, COL.Trim, nil, parent, "Mullion"))
	K.box(o.a, o.bottom - 0.2, z - t / 2 - 0.25, o.b, o.bottom, z + t / 2 + 0.25, COL.Trim, nil, parent, "Sill")
	K.deco(K.box(o.a - 0.2, o.top, z - t / 2 - 0.12, o.b + 0.2, o.top + 0.3, z + t / 2 + 0.12, COL.Trim, nil, parent, "Lintel"))
	for _, x in { o.a, o.b } do
		K.deco(K.box(x - 0.12, o.bottom, z - t / 2 - 0.08, x + 0.12, o.top, z + t / 2 + 0.08, COL.Trim, nil, parent, "Jamb"))
	end
end

local function frameWindowZ(o, x: number, t: number, parent: Instance)
	K.glass(K.box(x - 0.08, o.bottom, o.a, x + 0.08, o.top, o.b, COL.Glass, nil, parent, "Window"))
	K.deco(K.box(x - 0.15, o.bottom, (o.a + o.b) / 2 - 0.1, x + 0.15, o.top, (o.a + o.b) / 2 + 0.1, COL.Trim, nil, parent, "Mullion"))
	K.box(x - t / 2 - 0.25, o.bottom - 0.2, o.a, x + t / 2 + 0.25, o.bottom, o.b, COL.Trim, nil, parent, "Sill")
	K.deco(K.box(x - t / 2 - 0.12, o.top, o.a - 0.2, x + t / 2 + 0.12, o.top + 0.3, o.b + 0.2, COL.Trim, nil, parent, "Lintel"))
	for _, zz in { o.a, o.b } do
		K.deco(K.box(x - t / 2 - 0.08, o.bottom, zz - 0.12, x + t / 2 + 0.08, o.top, zz + 0.12, COL.Trim, nil, parent, "Jamb"))
	end
end

function K.wallX(x1: number, x2: number, z: number, y1: number, y2: number, t: number, openings: { any }, color: Color3, parent: Instance, mat: Enum.Material?)
	table.sort(openings, function(a, b)
		return a.a < b.a
	end)
	local cursor = x1
	for _, o in openings do
		if o.a > cursor then
			K.box(cursor, y1, z - t / 2, o.a, y2, z + t / 2, color, mat, parent, "Wall")
		end
		if o.bottom > y1 then
			K.box(o.a, y1, z - t / 2, o.b, o.bottom, z + t / 2, color, mat, parent, "Wall")
		end
		if o.top < y2 then
			K.box(o.a, o.top, z - t / 2, o.b, y2, z + t / 2, color, mat, parent, "Wall")
		end
		if o.glass then
			frameWindowX(o, z, t, parent)
		end
		cursor = math.max(cursor, o.b)
	end
	if cursor < x2 then
		K.box(cursor, y1, z - t / 2, x2, y2, z + t / 2, color, mat, parent, "Wall")
	end
end

function K.wallZ(z1: number, z2: number, x: number, y1: number, y2: number, t: number, openings: { any }, color: Color3, parent: Instance, mat: Enum.Material?)
	table.sort(openings, function(a, b)
		return a.a < b.a
	end)
	local cursor = z1
	for _, o in openings do
		if o.a > cursor then
			K.box(x - t / 2, y1, cursor, x + t / 2, y2, o.a, color, mat, parent, "Wall")
		end
		if o.bottom > y1 then
			K.box(x - t / 2, y1, o.a, x + t / 2, o.bottom, o.b, color, mat, parent, "Wall")
		end
		if o.top < y2 then
			K.box(x - t / 2, o.top, o.a, x + t / 2, y2, o.b, color, mat, parent, "Wall")
		end
		if o.glass then
			frameWindowZ(o, x, t, parent)
		end
		cursor = math.max(cursor, o.b)
	end
	if cursor < z2 then
		K.box(x - t / 2, y1, cursor, x + t / 2, y2, z2, color, mat, parent, "Wall")
	end
end

-- evenly spaced windows between a and b (for long façades)
function K.windowRow(a: number, b: number, bottom: number, top: number, width: number, gap: number): { any }
	local list = {}
	local span = width + gap
	local n = math.floor((b - a - gap) / span)
	local start = a + ((b - a) - (n * span - gap)) / 2
	for i = 0, n - 1 do
		local wa = start + i * span
		table.insert(list, { a = wa, b = wa + width, bottom = bottom, top = top, glass = true })
	end
	return list
end

-- ── spots (where NPCs go) & minimap zones ────────────────────────────────────
function K.spot(group: string, pos: Vector3, look: Vector3?)
	local W = K.World
	W.Spots[group] = W.Spots[group] or {}
	local target = look or (pos + Vector3.new(0, 0, -1))
	target = Vector3.new(target.X, pos.Y, target.Z)
	if (target - pos).Magnitude < 0.01 then
		target = pos + Vector3.new(0, 0, -1)
	end
	table.insert(W.Spots[group], CFrame.lookAt(pos, target))
end

function K.ring(group: string, center: Vector3, radius: number, count: number, faceIn: boolean)
	for i = 1, count do
		local a = (i / count) * math.pi * 2
		local pos = center + Vector3.new(math.cos(a) * radius, 0, math.sin(a) * radius)
		K.spot(group, pos, faceIn and center or (pos + (pos - center)))
	end
end

function K.zone(name: string, x1: number, z1: number, x2: number, z2: number, hex: string)
	table.insert(K.World.MapZones, { name, math.min(x1, x2), math.min(z1, z2), math.max(x1, x2), math.max(z1, z2), hex })
end

-- ── furniture & props ────────────────────────────────────────────────────────
-- desk faces -X (toward a board on the room's west wall); y = floor top
function K.deskAndChair(x: number, y: number, z: number, parent: Instance, classId: string?)
	local box, deco = K.box, K.deco
	box(x - 1, y + 2.05, z - 1.2, x + 0.9, y + 2.25, z + 1.2, COL.LightWood, Enum.Material.Wood, parent, "Desk")
	box(x - 0.9, y, z - 1.15, x + 0.8, y + 2.05, z - 1.0, COL.Metal, Enum.Material.Metal, parent, "DeskSide")
	box(x - 0.9, y, z + 1.0, x + 0.8, y + 2.05, z + 1.15, COL.Metal, Enum.Material.Metal, parent, "DeskSide")
	local seat = deco(box(x + 1.3, y + 0.9, z - 0.75, x + 2.3, y + 1.05, z + 0.75, COL.LightWood, Enum.Material.Wood, parent, "Chair"))
	deco(box(x + 2.3, y + 1.05, z - 0.75, x + 2.45, y + 2.85, z + 0.75, COL.LightWood, Enum.Material.Wood, parent, "ChairBack"))
	deco(box(x + 1.7, y, z - 0.1, x + 1.9, y + 0.9, z + 0.1, COL.Metal, Enum.Material.Metal, parent, "ChairLeg"))
	if classId then
		local W = K.World
		W.Seats[classId] = W.Seats[classId] or {}
		local sitPos = Vector3.new(x + 1.8, y + 1.05 + 1.2, z)
		table.insert(W.Seats[classId], {
			sit = CFrame.lookAt(sitPos, sitPos - Vector3.new(1, 0, 0)),
			approach = Vector3.new(x + 2.7, y, z),
			chair = seat,
		})
	end
end

function K.bench(cf: CFrame, parent: Instance)
	local seat = K.P(Vector3.new(5, 0.3, 1.6), cf * CFrame.new(0, 1.45, 0), COL.Wood, Enum.Material.Wood, parent, "Bench")
	seat.CanCollide = false
	K.deco(K.P(Vector3.new(5, 1.2, 0.25), cf * CFrame.new(0, 2.2, 0.75), COL.Wood, Enum.Material.Wood, parent, "BenchBack"))
	for _, dx in { -2, 2 } do
		K.deco(K.P(Vector3.new(0.3, 1.3, 1.4), cf * CFrame.new(dx, 0.65, 0), COL.Black, Enum.Material.Metal, parent, "BenchLeg"))
	end
end

-- full-size trees (6–9 m)
function K.tree(x: number, z: number, kind: string, parent: Instance, y: number?)
	local base = y or 0
	local h = 16 + math.random() * 8
	local trunk = K.cyl(Vector3.new(x, base + h / 2, z), h, 1.8 + math.random() * 0.6, COL.DarkWood, Enum.Material.Wood, parent)
	trunk.Name = "Trunk"
	local leaf = kind == "Blossom" and COL.Blossom or COL.Leaf:Lerp(Color3.new(0, 0, 0), math.random() * 0.15)
	for _ = 1, 5 do
		local off = Vector3.new(math.random(-40, 40) / 10, math.random(-10, 25) / 10, math.random(-40, 40) / 10)
		local b = K.ball(Vector3.new(x, base + h + 1, z) + off, 9 + math.random() * 5, leaf, kind == "Blossom" and Enum.Material.SmoothPlastic or Enum.Material.Grass, parent)
		b.Name = "Canopy"
		b.CanCollide = false
	end
	if kind == "Blossom" then
		local pe = Instance.new("ParticleEmitter")
		pe.Color = ColorSequence.new(COL.Blossom)
		pe.Size = NumberSequence.new(0.2)
		pe.Lifetime = NumberRange.new(6, 9)
		pe.Rate = 4
		pe.Speed = NumberRange.new(0.5, 1.5)
		pe.SpreadAngle = Vector2.new(180, 180)
		pe.Acceleration = Vector3.new(0.6, -1.2, 0.3)
		pe.RotSpeed = NumberRange.new(-90, 90)
		pe.Rotation = NumberRange.new(0, 360)
		pe.Parent = trunk
	end
end

function K.bush(pos: Vector3, parent: Instance)
	K.deco(K.ball(pos + Vector3.new(0, 1.4, 0), 3.2 + math.random(), COL.Leaf, Enum.Material.Grass, parent))
end

function K.roseBed(x1: number, z1: number, x2: number, z2: number, parent: Instance)
	K.box(x1, 0, z1, x2, 1.2, z2, COL.Trim, Enum.Material.Slate, parent, "Planter")
	K.box(x1 + 0.4, 1.2, z1 + 0.4, x2 - 0.4, 1.3, z2 - 0.4, COL.Soil, Enum.Material.Ground, parent, "Soil")
	local cx, cz = (x1 + x2) / 2, (z1 + z2) / 2
	local w, d = math.abs(x2 - x1) - 1.5, math.abs(z2 - z1) - 1.5
	for _ = 1, math.floor(w * d / 14) + 4 do
		local p = Vector3.new(cx + (math.random() - 0.5) * w, 2, cz + (math.random() - 0.5) * d)
		K.deco(K.ball(p, 1.8 + math.random(), COL.Leaf, Enum.Material.Grass, parent))
		for _ = 1, 2 do
			K.deco(K.ball(p + Vector3.new((math.random() - 0.5) * 1.6, 0.8 + math.random() * 0.4, (math.random() - 0.5) * 1.6), 0.55, COL.Rose, Enum.Material.SmoothPlastic, parent))
		end
	end
end

-- 5 m street lamp
function K.lamp(pos: Vector3, parent: Instance)
	K.cyl(pos + Vector3.new(0, 8, 0), 16, 0.5, COL.Black, Enum.Material.Metal, parent)
	K.deco(K.P(Vector3.new(0.3, 0.3, 2.4), CFrame.new(pos + Vector3.new(0, 15.8, 1)), COL.Black, Enum.Material.Metal, parent, "LampArm"))
	local head = K.P(Vector3.new(1.4, 0.5, 2), CFrame.new(pos + Vector3.new(0, 15.5, 2)), Color3.fromRGB(255, 236, 200), Enum.Material.Neon, parent, "LampHead")
	K.deco(head)
	local l = Instance.new("SpotLight")
	l.Face = Enum.NormalId.Bottom
	l.Range = 34
	l.Angle = 100
	l.Brightness = 1.4
	l.Color = Color3.fromRGB(255, 220, 170)
	l.Parent = head
	return head
end

function K.shelf(x1: number, z1: number, x2: number, z2: number, y: number, h: number, parent: Instance, books: boolean)
	K.box(x1, y, z1, x2, y + h, z2, COL.DarkWood, Enum.Material.Wood, parent, "Shelf")
	if not books then
		return
	end
	local alongX = math.abs(x2 - x1) > math.abs(z2 - z1)
	local len = alongX and math.abs(x2 - x1) or math.abs(z2 - z1)
	for row = 0, 3 do
		local yy = y + 0.6 + row * (h - 0.8) / 4
		local cursor = 0.2
		while cursor < len - 0.4 do
			local bw = 0.25 + math.random() * 0.35
			local bh = 0.9 + math.random() * 0.5
			local c = Color3.fromHSV(math.random(), 0.45 + math.random() * 0.3, 0.35 + math.random() * 0.4)
			if alongX then
				K.deco(K.P(Vector3.new(bw, bh, math.abs(z2 - z1) + 0.15), CFrame.new(math.min(x1, x2) + cursor + bw / 2, yy + bh / 2, (z1 + z2) / 2), c, nil, parent, "Book"))
			else
				K.deco(K.P(Vector3.new(math.abs(x2 - x1) + 0.15, bh, bw), CFrame.new((x1 + x2) / 2, yy + bh / 2, math.min(z1, z2) + cursor + bw / 2), c, nil, parent, "Book"))
			end
			cursor += bw + 0.6 + math.random() * 0.8
		end
	end
end

-- sink against a wall; facing = direction the person stands (from the basin)
function K.sink(x: number, y: number, z: number, facing: Vector3, parent: Instance)
	local cf = CFrame.lookAt(Vector3.new(x, y, z), Vector3.new(x, y, z) - facing)
	local basin = K.P(Vector3.new(2.4, 0.5, 1.6), cf * CFrame.new(0, 2.45, 0), COL.White, Enum.Material.SmoothPlastic, parent, "Sink")
	local water = K.deco(K.P(Vector3.new(2, 0.1, 1.2), cf * CFrame.new(0, 2.71, 0), Color3.fromRGB(150, 190, 210), Enum.Material.Glass, parent, "SinkWater"))
	water.Transparency = 0.4
	K.deco(K.P(Vector3.new(0.2, 0.8, 0.2), cf * CFrame.new(0, 3.05, -0.6), COL.Metal, Enum.Material.Metal, parent, "Tap"))
	K.deco(K.P(Vector3.new(0.6, 2.2, 0.6), cf * CFrame.new(0, 1.1, -0.2), COL.White, nil, parent, "SinkStand"))
	table.insert(K.World.Interact.Sinks, basin)
	return basin
end

-- shower stall; cf at floor level, opening toward cf.LookVector
function K.shower(cf: CFrame, parent: Instance)
	local f = K.folder("Shower", parent)
	K.deco(K.P(Vector3.new(0.2, 7.5, 4), cf * CFrame.new(-2, 3.75, 0), Color3.fromRGB(220, 230, 235), nil, f, "StallWall"))
	K.deco(K.P(Vector3.new(0.2, 7.5, 4), cf * CFrame.new(2, 3.75, 0), Color3.fromRGB(220, 230, 235), nil, f, "StallWall"))
	K.P(Vector3.new(4, 0.1, 4), cf * CFrame.new(0, 0.05, 0), Color3.fromRGB(200, 214, 222), Enum.Material.Glass, f, "ShowerTray")
	local head = K.deco(K.P(Vector3.new(0.6, 0.2, 0.6), cf * CFrame.new(0, 7.4, 1.6), COL.Metal, Enum.Material.Metal, f, "ShowerHead"))
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
	local trigger = K.P(Vector3.new(3, 4, 3), cf * CFrame.new(0, 2, 0), COL.White, nil, f, "ShowerZone")
	trigger.Transparency = 1
	K.deco(trigger)
	table.insert(K.World.Interact.Showers, trigger)
	return trigger
end

function K.pottedPlant(pos: Vector3, parent: Instance)
	K.cyl(pos + Vector3.new(0, 0.9, 0), 1.8, 1.6, Color3.fromRGB(180, 110, 80), Enum.Material.Slate, parent).Name = "Pot"
	K.deco(K.ball(pos + Vector3.new(0, 2.6, 0), 2.4, COL.Leaf, Enum.Material.Grass, parent))
	K.deco(K.ball(pos + Vector3.new(0.4, 3.4, 0.2), 1.6, COL.Leaf:Lerp(Color3.new(1, 1, 1), 0.08), Enum.Material.Grass, parent))
end

function K.pinboard(cf: CFrame, parent: Instance)
	K.deco(K.P(Vector3.new(5, 3, 0.15), cf, Color3.fromRGB(176, 136, 92), Enum.Material.Wood, parent, "Pinboard"))
	for _ = 1, 5 do
		local off = Vector3.new(math.random(-18, 18) / 10, math.random(-10, 10) / 10, -0.1)
		K.deco(K.P(Vector3.new(0.8 + math.random() * 0.5, 1 + math.random() * 0.4, 0.04), cf * CFrame.new(off) * CFrame.Angles(0, 0, math.rad(math.random(-8, 8))), Color3.fromHSV(math.random(), 0.25, 1), Enum.Material.SmoothPlastic, parent, "Poster"))
	end
end

-- a real-size car: 4.5 m long, 1.8 m wide, 1.5 m tall (Nyx is 1.6 m)
function K.car(cf: CFrame, color: Color3, parent: Instance)
	local f = K.folder("Car", parent)
	local body = K.P(Vector3.new(6.2, 2.4, 15), cf * CFrame.new(0, 1.9, 0), color, Enum.Material.SmoothPlastic, f, "Body")
	body.Reflectance = 0.15
	local cabin = K.P(Vector3.new(5.6, 2.2, 8), cf * CFrame.new(0, 4.1, 0.6), color:Lerp(Color3.new(0, 0, 0), 0.1), Enum.Material.SmoothPlastic, f, "Cabin")
	cabin.Reflectance = 0.1
	K.deco(K.P(Vector3.new(5.7, 1.6, 7.2), cf * CFrame.new(0, 4.15, 0.6), Color3.fromRGB(40, 50, 60), Enum.Material.Glass, f, "Windows")).Transparency = 0.15
	for _, wx in { -2.8, 2.8 } do
		for _, wz in { -4.8, 4.8 } do
			local w = K.P(Vector3.new(0.9, 2.3, 2.3), cf * CFrame.new(wx, 1.15, wz), COL.Black, Enum.Material.SmoothPlastic, f, "Wheel")
			w.Shape = Enum.PartType.Cylinder
		end
	end
	for _, hx in { -2.1, 2.1 } do
		K.deco(K.P(Vector3.new(1.2, 0.5, 0.1), cf * CFrame.new(hx, 2.4, -7.52), Color3.fromRGB(255, 250, 230), Enum.Material.Neon, f, "Headlight"))
		K.deco(K.P(Vector3.new(1.2, 0.45, 0.1), cf * CFrame.new(hx, 2.5, 7.52), Color3.fromRGB(200, 20, 30), Enum.Material.Neon, f, "Taillight"))
	end
	return f
end

function K.vending(cf: CFrame, color: Color3, parent: Instance)
	K.P(Vector3.new(3.4, 6, 2.6), cf * CFrame.new(0, 3, 0), color, Enum.Material.SmoothPlastic, parent, "Vending")
	K.deco(K.P(Vector3.new(2.8, 3.4, 0.05), cf * CFrame.new(0, 3.8, -1.32), Color3.fromRGB(255, 255, 235), Enum.Material.Neon, parent, "VendingGlow"))
end

return K
