-- KUROBARA · Kurobara town (original): main road, shopping street, homes, park + shrine,
-- apartment block, train station with a parked train, and Nyx's house (with interior).

local K = require(script.Parent.BuildKit)
local COL = K.COL
local W = K.World
local box, deco, P, ball, cyl, glass, folder = K.box, K.deco, K.P, K.ball, K.cyl, K.glass, K.folder
local spot, zone = K.spot, K.zone

local Town = {}

local ROAD_Z1, ROAD_Z2 = 132, 156
local WALK = Color3.fromRGB(196, 192, 186)

-- ── roads ────────────────────────────────────────────────────────────────────
local function roads()
	local f = folder("Roads")
	zone("", -300, ROAD_Z1, 600, ROAD_Z2, "#3b3b42")
	box(-620, 0, ROAD_Z1, 920, 0.08, ROAD_Z2, COL.Asphalt, Enum.Material.Asphalt, f, "MainRoad")
	box(-620, 0, 124, 920, 0.3, ROAD_Z1, WALK, Enum.Material.Concrete, f, "Sidewalk")
	box(-620, 0, ROAD_Z2, 920, 0.3, 164, WALK, Enum.Material.Concrete, f, "Sidewalk")
	for x = -610, 910, 14 do
		deco(box(x, 0.08, 143.8, x + 7, 0.1, 144.2, COL.White, nil, f, "Dash"))
	end
	for _, z in { ROAD_Z1 + 0.6, ROAD_Z2 - 0.6 } do
		deco(box(-620, 0.08, z - 0.12, 920, 0.1, z + 0.12, COL.White, nil, f, "EdgeLine"))
	end
	-- crosswalk in front of the gate
	for i = 0, 7 do
		deco(box(-8 + i * 2.2, 0.08, ROAD_Z1 + 1, -7 + i * 2.2, 0.1, ROAD_Z2 - 1, COL.White, nil, f, "Crosswalk"))
	end
	-- avenues + cross street
	local streets = {
		{ -12, 164, 12, 560, "AvenueA" },
		{ 288, 164, 312, 560, "AvenueB" },
		{ -320, 340, 640, 364, "CrossStreet" },
	}
	for _, s in streets do
		box(s[1], 0, s[2], s[3], 0.08, s[4], COL.Asphalt, Enum.Material.Asphalt, f, s[5])
		zone("", s[1], s[2], s[3], s[4], "#3b3b42")
	end
	for _, x in { -18, 12, 282, 312 } do
		box(x, 0, 164, x + 6, 0.3, 340, WALK, Enum.Material.Concrete, f, "Sidewalk")
		box(x, 0, 364, x + 6, 0.3, 560, WALK, Enum.Material.Concrete, f, "Sidewalk")
	end
	box(-320, 0, 334, 640, 0.3, 340, WALK, Enum.Material.Concrete, f, "Sidewalk")
	box(-320, 0, 364, 640, 0.3, 370, WALK, Enum.Material.Concrete, f, "Sidewalk")
	for z = 170, 550, 14 do
		if z < 334 or z > 370 then
			deco(box(-0.2, 0.08, z, 0.2, 0.1, z + 7, COL.White, nil, f, "Dash"))
			deco(box(299.8, 0.08, z, 300.2, 0.1, z + 7, COL.White, nil, f, "Dash"))
		end
	end
	-- lamps, poles + wires, traffic lights
	for x = -300, 600, 40 do
		K.lamp(Vector3.new(x, 0.3, 127), f)
	end
	for z = 190, 540, 50 do
		K.lamp(Vector3.new(-15, 0.3, z), f)
		K.lamp(Vector3.new(315, 0.3, z), f)
	end
	local poles = {}
	for x = -300, 600, 34 do
		local base = Vector3.new(x, 0.3, 161)
		cyl(base + Vector3.new(0, 15, 0), 30, 0.9, Color3.fromRGB(110, 106, 100), Enum.Material.Concrete, f).Name = "UtilityPole"
		deco(box(x - 0.2, 27.5, 158, x + 0.2, 28, 164, Color3.fromRGB(90, 86, 80), nil, f, "CrossArm"))
		deco(box(x - 0.6, 22, 160.4, x + 0.6, 24, 161.6, Color3.fromRGB(120, 120, 126), Enum.Material.Metal, f, "Transformer"))
		table.insert(poles, base + Vector3.new(0, 27.8, 0))
	end
	for i = 1, #poles - 1 do
		for _, off in { -2.4, 0, 2.4 } do
			local a = poles[i] + Vector3.new(0, 0, off)
			local b = poles[i + 1] + Vector3.new(0, 0, off)
			local m = (a + b) / 2 - Vector3.new(0, 1.6, 0)
			for _, seg in { { a, m }, { m, b } } do
				deco(P(Vector3.new(0.07, 0.07, (seg[2] - seg[1]).Magnitude), CFrame.lookAt((seg[1] + seg[2]) / 2, seg[2]), COL.Black, nil, f, "Wire"))
			end
		end
	end
	for _, c in { { -16, 160, 1 }, { 16, 128, -1 }, { 284, 160, 1 }, { 316, 128, -1 } } do
		local x, z, dir = c[1], c[2], c[3]
		cyl(Vector3.new(x, 7, z), 14, 0.5, Color3.fromRGB(120, 120, 126), Enum.Material.Metal, f)
		local head = box(x - 0.8, 11.5, z - 0.6, x + 0.8, 15.5, z + 0.6, COL.Black, nil, f, "TrafficLight")
		deco(ball(Vector3.new(x, 14.6, z - 0.65 * dir), 0.8, Color3.fromRGB(255, 40, 40), Enum.Material.Neon, f))
		deco(ball(Vector3.new(x, 12.4, z - 0.65 * dir), 0.8, Color3.fromRGB(40, 220, 90), Enum.Material.Neon, f))
	end
	-- outside the gate: where people arrive / leave
	spot("Exit", Vector3.new(0, 0.3, 128), Vector3.new(0, 0.3, 160))
	spot("Exit", Vector3.new(-22, 0.3, 128), Vector3.new(-40, 0.3, 128))
	spot("Exit", Vector3.new(22, 0.3, 128), Vector3.new(40, 0.3, 128))
	K.vending(CFrame.lookAt(Vector3.new(24, 0.3, 125.6), Vector3.new(24, 0.3, 140)), Color3.fromRGB(30, 80, 160), f)
	K.vending(CFrame.lookAt(Vector3.new(28, 0.3, 125.6), Vector3.new(28, 0.3, 140)), COL.Crimson, f)
end

-- ── buildings ────────────────────────────────────────────────────────────────
-- two-storey house; door on the north side (z1)
local function house(x1: number, z1: number, w: number, d: number, color: Color3, parent: Instance)
	local f = folder("House", parent)
	local x2, z2 = x1 + w, z1 + d
	zone("", x1, z1, x2, z2, "#c9b7a6")
	box(x1, 0, z1, x2, 23, z2, color, Enum.Material.Plaster, f, "HouseBody")
	local roofCol = Color3.fromHSV(math.random(), 0.25, 0.32)
	local cx = (x1 + x2) / 2
	K.wedge(Vector3.new(w + 2, 7, d / 2 + 1), CFrame.new(cx, 26.5, z1 + d / 4 - 0.5), roofCol, Enum.Material.Slate, f, "Roof")
	K.wedge(Vector3.new(w + 2, 7, d / 2 + 1), CFrame.new(cx, 26.5, z2 - d / 4 + 0.5) * CFrame.Angles(0, math.rad(180), 0), roofCol, Enum.Material.Slate, f, "Roof")
	for _, xo in { 0.22, 0.78 } do
		local wx = x1 + w * xo
		for _, y in { 4, 15.5 } do
			local pane = glass(box(wx - 2.4, y, z1 - 0.12, wx + 2.4, y + 4.4, z1 - 0.02, COL.Glass, nil, f, "Window"))
			pane.Transparency = 0.25
			deco(box(wx - 2.7, y - 0.3, z1 - 0.2, wx + 2.7, y, z1, COL.White, nil, f, "Sill"))
		end
	end
	box(cx - 2, 0, z1 - 0.15, cx + 2, 7.5, z1, COL.DarkWood, Enum.Material.Wood, f, "Door")
	deco(box(cx - 3.5, 8, z1 - 2.5, cx + 3.5, 8.4, z1, COL.Trim, nil, f, "DoorCanopy"))
	box(cx - 4, 12.5, z1 - 4, cx + 4, 13, z1, COL.Trim, nil, f, "Balcony")
	deco(box(cx - 4, 13, z1 - 4.1, cx + 4, 15.5, z1 - 3.9, COL.White, nil, f, "Railing"))
	-- low garden wall with a gap at the path
	K.wallX(x1 - 3, x2 + 3, z1 - 9, 0, 2.2, 0.8, { { a = cx - 2.5, b = cx + 2.5, bottom = 0, top = 2.2 } }, Color3.fromRGB(200, 196, 188), f, Enum.Material.Concrete)
	box(cx - 2, 0, z1 - 9, cx + 2, 0.32, z1, WALK, Enum.Material.Pavement, f, "Path")
	K.bush(Vector3.new(x1 + 2, 0, z1 - 4), f)
	K.bush(Vector3.new(x2 - 2, 0, z1 - 4), f)
	box(x2 - 3, 9, z1 - 1.4, x2 - 1, 11, z1, COL.White, nil, f, "ACUnit")
end

local function shop(x1: number, x2: number, name: string, accent: Color3, kind: string, parent: Instance)
	local f = folder("Shop_" .. name, parent)
	local z1, z2, h = 170, 200, 16
	zone(name, x1, z1, x2, z2, "#d08c60")
	box(x1, 0, z1, x2, 0.4, z2, COL.Floor, Enum.Material.SmoothPlastic, f, "Floor")
	K.wallX(x1, x2, z1, 0, h, 1, { { a = x1 + 2, b = x2 - 2, bottom = 0.4, top = 10, glass = true } }, Color3.fromRGB(230, 226, 220), f)
	K.wallX(x1, x2, z2, 0, h, 1, {}, Color3.fromRGB(230, 226, 220), f)
	K.wallZ(z1, z2, x1, 0, h, 1, {}, Color3.fromRGB(230, 226, 220), f)
	K.wallZ(z1, z2, x2, 0, h, 1, {}, Color3.fromRGB(230, 226, 220), f)
	box(x1 - 0.5, h, z1 - 0.5, x2 + 0.5, h + 0.8, z2 + 0.5, COL.RoofSlab, Enum.Material.Concrete, f, "Roof")
	K.wedge(Vector3.new(x2 - x1, 2, 4), CFrame.new((x1 + x2) / 2, 11, z1 - 2) * CFrame.Angles(0, math.rad(180), 0), accent, Enum.Material.Fabric, f, "Awning")
	K.sign(name, CFrame.lookAt(Vector3.new((x1 + x2) / 2, 13.6, z1 - 0.65), Vector3.new((x1 + x2) / 2, 13.6, 100)), Vector2.new(math.min(x2 - x1 - 4, 20), 2.2), f, COL.White, accent)
	local l = box((x1 + x2) / 2 - 4, h - 0.4, 183, (x1 + x2) / 2 + 4, h - 0.1, 187, Color3.fromRGB(255, 250, 240), Enum.Material.Neon, f, "ShopLight")
	deco(l)
	K.light(l, 22, 1)
	if kind == "mart" then
		for i = 0, 3 do
			K.shelf(x1 + 6 + i * 8, 178, x1 + 8 + i * 8, 196, 0.4, 6, f, true)
		end
		box(x2 - 10, 0.4, 172, x2 - 3, 3.4, 176, COL.White, nil, f, "Register")
	elseif kind == "cafe" then
		for i = 0, 2 do
			local x = x1 + 7 + i * 8
			cyl(Vector3.new(x, 1.5, 186), 0.2, 3.5, COL.DarkWood, Enum.Material.Wood, f)
			deco(cyl(Vector3.new(x, 0.95, 186), 1.1, 0.5, COL.Black, Enum.Material.Metal, f))
		end
		box(x1 + 2, 0.4, 196, x2 - 2, 3.4, 199, COL.DarkWood, Enum.Material.Wood, f, "CafeCounter")
	elseif kind == "books" then
		for i = 0, 2 do
			K.shelf(x1 + 4 + i * 9, 180, x1 + 6 + i * 9, 198, 0.4, 8, f, true)
		end
	else
		box(x1 + 2, 0.4, 192, x2 - 2, 3.2, 195, COL.Wood, Enum.Material.Wood, f, "RamenCounter")
		for i = 0, 3 do
			deco(cyl(Vector3.new(x1 + 5 + i * 6, 1.6, 189), 1.8, 1.4, COL.Crimson, nil, f))
		end
	end
end

local function apartments(parent: Instance)
	local f = folder("Apartments", parent)
	local x1, x2, z1, z2 = 40, 140, 390, 432
	zone("Apartments", x1, z1, x2, z2, "#b0a090")
	local floors = 5
	box(x1, 0, z1, x2, floors * 11 + 1, z2, Color3.fromRGB(214, 206, 196), Enum.Material.Concrete, f, "Block")
	for fl = 0, floors - 1 do
		local y = fl * 11
		for x = x1 + 4, x2 - 10, 12 do
			glass(box(x, y + 3, z1 - 0.12, x + 7, y + 8.5, z1 - 0.02, COL.Glass, nil, f, "Window")).Transparency = 0.25
			if fl > 0 then
				box(x - 1, y + 0.5, z1 - 4, x + 8, y + 1, z1, Color3.fromRGB(190, 184, 176), Enum.Material.Concrete, f, "Balcony")
				deco(box(x - 1, y + 1, z1 - 4.1, x + 8, y + 3.8, z1 - 3.9, Color3.fromRGB(200, 200, 205), Enum.Material.Metal, f, "Railing"))
			end
		end
	end
	box(x1 + 44, 0, z1 - 6, x1 + 56, 9, z1, COL.Trim, nil, f, "Entrance")
	K.sign("KUROBARA HEIGHTS", CFrame.lookAt(Vector3.new(x1 + 50, 7.6, z1 - 6.15), Vector3.new(x1 + 50, 7.6, 0)), Vector2.new(10, 1.4), f, COL.White, COL.Trim)
end

local function park(parent: Instance)
	local f = folder("Park", parent)
	local x1, x2, z1, z2 = -300, -30, 376, 550
	zone("Park", x1, z1, x2, z2, "#5f8a4e")
	box(x1, 0, z1, x2, 0.15, z2, Color3.fromRGB(94, 132, 72), Enum.Material.Grass, f, "Lawn")
	box(x1 + 10, 0.15, 452, x2 - 10, 0.22, 460, Color3.fromRGB(200, 186, 160), Enum.Material.Ground, f, "Path")
	box(-170, 0.15, z1 + 6, -162, 0.22, z2 - 6, Color3.fromRGB(200, 186, 160), Enum.Material.Ground, f, "Path")
	-- pond
	cyl(Vector3.new(-230, 0.4, 410), 0.8, 52, Color3.fromRGB(150, 146, 140), Enum.Material.Slate, f).Name = "PondRim"
	local water = cyl(Vector3.new(-230, 0.55, 410), 0.3, 48, COL.Water, Enum.Material.Glass, f)
	water.Transparency = 0.2
	water.Name = "Pond"
	for _ = 1, 5 do
		deco(cyl(Vector3.new(-230 + math.random(-18, 18), 0.75, 410 + math.random(-18, 18)), 0.08, 2.4, Color3.fromRGB(60, 120, 60), nil, f)).Name = "LilyPad"
	end
	-- playground
	K.wedge(Vector3.new(4, 7, 12), CFrame.new(-90, 3.65, 410), Color3.fromRGB(240, 70, 70), Enum.Material.SmoothPlastic, f, "Slide")
	box(-92, 0.15, 416, -88, 7.2, 420, Color3.fromRGB(70, 140, 230), nil, f, "SlideTower")
	box(-75, 0.15, 400, -74.5, 9, 400.5, COL.Metal, Enum.Material.Metal, f, "SwingPost")
	box(-63, 0.15, 400, -62.5, 9, 400.5, COL.Metal, Enum.Material.Metal, f, "SwingPost")
	box(-75, 9, 400, -62.5, 9.5, 400.5, COL.Metal, Enum.Material.Metal, f, "SwingBar")
	for _, x in { -71, -66.5 } do
		deco(box(x - 0.05, 3, 400.2, x + 0.05, 9, 400.3, COL.Black, nil, f, "Chain"))
		box(x - 1, 2.6, 399.6, x + 1, 3, 400.9, Color3.fromRGB(240, 200, 40), nil, f, "SwingSeat")
	end
	box(-110, 0.15, 395, -100, 0.6, 405, Color3.fromRGB(230, 210, 160), Enum.Material.Sand, f, "Sandbox")
	-- shrine with a red torii
	local red = Color3.fromRGB(200, 40, 30)
	for _, x in { -246, -234 } do
		cyl(Vector3.new(x, 9, 505), 18, 1.4, red, Enum.Material.SmoothPlastic, f).Name = "ToriiPillar"
	end
	box(-250, 16.5, 504, -230, 17.5, 506, red, nil, f, "ToriiBeam")
	box(-252, 18.2, 503.4, -228, 19.4, 506.6, COL.Black, nil, f, "ToriiTop")
	box(-262, 0.15, 520, -218, 1.5, 546, Color3.fromRGB(170, 166, 160), Enum.Material.Slate, f, "ShrineBase")
	box(-258, 1.5, 524, -222, 12, 542, Color3.fromRGB(150, 90, 50), Enum.Material.Wood, f, "Shrine")
	K.wedge(Vector3.new(42, 6, 11), CFrame.new(-240, 15, 528.5), Color3.fromRGB(60, 60, 66), Enum.Material.Slate, f, "ShrineRoof")
	K.wedge(Vector3.new(42, 6, 11), CFrame.new(-240, 15, 537.5) * CFrame.Angles(0, math.rad(180), 0), Color3.fromRGB(60, 60, 66), Enum.Material.Slate, f, "ShrineRoof")
	for _, x in { -252, -228 } do
		box(x - 1, 0.15, 512, x + 1, 5, 514, Color3.fromRGB(160, 156, 150), Enum.Material.Slate, f, "StoneLantern")
		deco(box(x - 0.6, 3.6, 512.4, x + 0.6, 4.4, 513.6, Color3.fromRGB(255, 200, 120), Enum.Material.Neon, f))
	end
	box(-244, 0.15, 470, -236, 0.25, 520, Color3.fromRGB(200, 186, 160), Enum.Material.Ground, f, "ShrinePath")
	for _, p in { { -280, 390 }, { -140, 390 }, { -60, 470 }, { -280, 480 }, { -120, 530 }, { -190, 500 } } do
		K.tree(p[1], p[2], math.random() < 0.4 and "Blossom" or "Green", f, 0.15)
	end
	for _, x in { -200, -130, -100 } do
		K.bench(CFrame.new(x, 0.22, 450) * CFrame.Angles(0, math.rad(180), 0), f)
	end
end

local function station(parent: Instance)
	local f = folder("Station", parent)
	zone("Station", 320, 164, 452, 334, "#9aa3b0")
	-- tracks
	box(452, 0, -420, 478, 0.6, ROAD_Z1, Color3.fromRGB(110, 104, 98), Enum.Material.Slate, f, "Ballast")
	box(452, 0, ROAD_Z2, 478, 0.6, 900, Color3.fromRGB(110, 104, 98), Enum.Material.Slate, f, "Ballast")
	zone("", 452, -340, 478, 560, "#6b6560")
	for _, tx in { 460, 470 } do
		for _, off in { -2.4, 2.4 } do
			box(tx + off - 0.2, 0.6, -420, tx + off + 0.2, 1.0, 900, COL.Metal, Enum.Material.Metal, f, "Rail")
		end
		for z = -400, 890, 6 do
			if z < ROAD_Z1 - 2 or z > ROAD_Z2 + 2 then
				deco(box(tx - 3.5, 0.6, z, tx + 3.5, 0.75, z + 1, COL.DarkWood, Enum.Material.Wood, f, "Sleeper"))
			end
		end
	end
	-- level crossing barriers
	for _, c in { { 448, ROAD_Z1 - 2 }, { 482, ROAD_Z2 + 2 } } do
		box(c[1] - 0.6, 0.3, c[2] - 0.6, c[1] + 0.6, 6, c[2] + 0.6, COL.Black, nil, f, "CrossingPost")
		deco(box(c[1] - 0.4, 4.5, c[2] - 0.4, c[1] + 0.4, 5.5, c[2] + 0.4, Color3.fromRGB(255, 40, 40), Enum.Material.Neon, f))
	end
	-- platform + canopy
	box(420, 0, 170, 452, 4.6, 330, Color3.fromRGB(190, 186, 182), Enum.Material.Concrete, f, "Platform")
	deco(box(450.5, 4.6, 170, 451.5, 4.62, 330, Color3.fromRGB(255, 214, 10), nil, f, "SafetyLine"))
	K.wedge(Vector3.new(30, 4.6, 12), CFrame.new(436, 2.3, 158) * CFrame.Angles(0, math.rad(0), 0), Color3.fromRGB(190, 186, 182), Enum.Material.Concrete, f, "PlatformRamp")
	for z = 180, 320, 20 do
		cyl(Vector3.new(428, 10, z), 11, 0.6, COL.Metal, Enum.Material.Metal, f)
	end
	box(422, 15.5, 172, 448, 16.3, 328, Color3.fromRGB(80, 84, 92), Enum.Material.Metal, f, "Canopy")
	for z = 190, 310, 40 do
		K.bench(CFrame.new(426, 4.6, z) * CFrame.Angles(0, math.rad(-90), 0), f)
	end
	-- station building
	box(330, 0, 176, 410, 0.4, 226, COL.Floor, Enum.Material.SmoothPlastic, f, "Hall")
	K.wallX(330, 410, 176, 0, 26, 1, { { a = 350, b = 390, bottom = 0.4, top = 11, glass = true } }, Color3.fromRGB(226, 222, 216), f)
	K.wallX(330, 410, 226, 0, 26, 1, {}, Color3.fromRGB(226, 222, 216), f)
	K.wallZ(176, 226, 330, 0, 26, 1, {}, Color3.fromRGB(226, 222, 216), f)
	K.wallZ(176, 226, 410, 0, 26, 1, { { a = 190, b = 212, bottom = 0.4, top = 11 } }, Color3.fromRGB(226, 222, 216), f)
	box(329, 26, 175, 411, 27, 227, COL.RoofSlab, Enum.Material.Concrete, f, "Roof")
	K.sign("KUROBARA STATION", CFrame.lookAt(Vector3.new(370, 19, 175.35), Vector3.new(370, 19, 100)), Vector2.new(30, 3.4), f, COL.White, Color3.fromRGB(30, 60, 120))
	for i = 0, 3 do
		box(372 + i * 6, 0.4, 200, 373 + i * 6, 4, 210, COL.Metal, Enum.Material.Metal, f, "TicketGate")
	end
	box(410, 0.4, 190, 420, 4.6, 212, Color3.fromRGB(190, 186, 182), Enum.Material.Concrete, f, "Bridge")
	local l = box(360, 25.4, 196, 380, 25.7, 206, Color3.fromRGB(255, 250, 240), Enum.Material.Neon, f, "HallLight")
	deco(l)
	K.light(l, 30, 1)
	-- the train
	for car = 0, 1 do
		local z1 = 186 + car * 62
		local bodyCol = Color3.fromRGB(226, 228, 232)
		box(455.2, 1.4, z1, 464.8, 13.4, z1 + 58, bodyCol, Enum.Material.Metal, f, "TrainCar").Reflectance = 0.2
		deco(box(455.1, 4.2, z1, 455.15, 5.2, z1 + 58, COL.Crimson, nil, f, "Stripe"))
		deco(box(464.85, 4.2, z1, 464.9, 5.2, z1 + 58, COL.Crimson, nil, f, "Stripe"))
		for wz = z1 + 4, z1 + 52, 8 do
			glass(box(455.05, 6.5, wz, 455.15, 11, wz + 5, COL.Glass, nil, f, "TrainWindow")).Transparency = 0.3
			glass(box(464.85, 6.5, wz, 464.95, 11, wz + 5, COL.Glass, nil, f, "TrainWindow")).Transparency = 0.3
		end
		for _, dz in { 18, 38 } do
			deco(box(455.0, 1.6, z1 + dz, 455.1, 11.5, z1 + dz + 4, Color3.fromRGB(170, 174, 180), Enum.Material.Metal, f, "TrainDoor"))
		end
		box(456, 0.9, z1 + 4, 464, 1.4, z1 + 54, COL.Black, Enum.Material.Metal, f, "Bogie")
	end
	spot("Station", Vector3.new(436, 4.6, 260), Vector3.new(460, 4.6, 260))
end

-- ── Nyx's house (ground floor has a real interior) ───────────────────────────
local function home(parent: Instance)
	local h = folder("Home", parent)
	local x1, z1, x2, z2 = -100, 176, -60, 212
	zone("Home", x1, z1, x2, z2, "#e05a7a")
	local wallCol = Color3.fromRGB(232, 220, 226)
	local inner = Color3.fromRGB(245, 238, 240)
	local y = 0.55
	box(x1, 0, z1, x2, 0.5, z2, COL.Concrete, Enum.Material.Concrete, h, "Foundation")
	box(x1 + 0.5, 0.5, z1 + 0.5, x2 - 0.5, y, z2 - 0.5, Color3.fromRGB(150, 110, 80), Enum.Material.WoodPlanks, h, "Floor")
	K.wallX(x1, x2, z1, 0, 12, 1, { { a = -94, b = -89, bottom = 0.5, top = 8 }, { a = -76, b = -66, bottom = 3.5, top = 8, glass = true } }, wallCol, h)
	K.wallX(x1, x2, z2, 0, 12, 1, { { a = -96, b = -86, bottom = 3.5, top = 8, glass = true }, { a = -74, b = -66, bottom = 3.5, top = 8, glass = true } }, wallCol, h)
	K.wallZ(z1, z2, x1, 0, 12, 1, { { a = 182, b = 190, bottom = 3.5, top = 8, glass = true } }, wallCol, h)
	K.wallZ(z1, z2, x2, 0, 12, 1, { { a = 200, b = 208, bottom = 3.5, top = 8, glass = true } }, wallCol, h)
	K.wallZ(z1 + 0.5, z2 - 0.5, -80, 0.5, 12, 0.6, { { a = 182, b = 186, bottom = 0.5, top = 8 }, { a = 199, b = 203, bottom = 0.5, top = 8 } }, inner, h)
	K.wallX(-80, x2 - 0.5, 192, 0.5, 12, 0.6, {}, inner, h)
	box(x1 - 0.5, 12, z1 - 0.5, x2 + 0.5, 12.5, z2 + 0.5, COL.Concrete, Enum.Material.Concrete, h, "Ceiling")
	-- upper storey (shell) + roof
	K.wallX(x1, x2, z1, 12.5, 23, 1, { { a = -96, b = -88, bottom = 15.5, top = 20, glass = true }, { a = -74, b = -66, bottom = 15.5, top = 20, glass = true } }, wallCol, h)
	K.wallX(x1, x2, z2, 12.5, 23, 1, {}, wallCol, h)
	K.wallZ(z1, z2, x1, 12.5, 23, 1, {}, wallCol, h)
	K.wallZ(z1, z2, x2, 12.5, 23, 1, {}, wallCol, h)
	box(x1 - 0.5, 23, z1 - 0.5, x2 + 0.5, 23.4, z2 + 0.5, wallCol, nil, h, "Attic")
	local roofCol = Color3.fromRGB(70, 60, 70)
	K.wedge(Vector3.new(42, 7, 19), CFrame.new(-80, 26.9, 184.5), roofCol, Enum.Material.Slate, h, "Roof")
	K.wedge(Vector3.new(42, 7, 19), CFrame.new(-80, 26.9, 203.5) * CFrame.Angles(0, math.rad(180), 0), roofCol, Enum.Material.Slate, h, "Roof")
	for _, p in { { -90, 192 }, { -70, 184 }, { -70, 202 } } do
		local l = box(p[1] - 1.5, 11.6, p[2] - 1.5, p[1] + 1.5, 11.95, p[2] + 1.5, Color3.fromRGB(255, 240, 225), Enum.Material.Neon, h, "HomeLight")
		deco(l)
		K.light(l, 16, 0.9, Color3.fromRGB(255, 225, 200))
	end
	-- kitchen + living
	box(x1 + 0.5, y, 186, x1 + 3, y + 2.4, 208, COL.White, nil, h, "KitchenCounter")
	box(x1 + 0.4, y + 2.4, 185.9, x1 + 3.1, y + 2.55, 208.1, Color3.fromRGB(60, 60, 66), Enum.Material.Granite, h, "KitchenTop")
	K.sink(x1 + 1.8, y, 200, Vector3.new(1, 0, 0), h)
	W.ItemSpawns.HomeKitchen = CFrame.new(x1 + 1.8, y + 2.65, 190) * CFrame.Angles(0, 0, math.rad(90))
	box(-92, y + 2, 196, -86, y + 2.2, 202, COL.Wood, Enum.Material.Wood, h, "DiningTable")
	box(-89.4, y, 198.6, -88.6, y + 2, 199.4, COL.DarkWood, nil, h, "TableLeg")
	box(-97, y, 177, -85, y + 1.6, 180, Color3.fromRGB(90, 70, 90), Enum.Material.Fabric, h, "Sofa")
	box(-97, y + 1.6, 176.6, -85, y + 3.4, 177.4, Color3.fromRGB(90, 70, 90), Enum.Material.Fabric, h, "SofaBack")
	-- bathroom
	K.shower(CFrame.lookAt(Vector3.new(-63, y, 182), Vector3.new(-70, y, 182)), h)
	-- bedroom
	box(-66, y, 201, -60.5, y + 1.6, 211.5, Color3.fromRGB(40, 30, 40), Enum.Material.Fabric, h, "Bed")
	box(-66, y + 1.6, 201, -60.5, y + 2, 211.5, Color3.fromRGB(120, 20, 40), Enum.Material.Fabric, h, "Blanket")
	box(-62, y + 2, 209.5, -60.8, y + 2.5, 211.3, COL.White, Enum.Material.Fabric, h, "Pillow")
	box(-79.4, y, 206.5, -75, y + 2.3, 211.5, COL.DarkWood, Enum.Material.Wood, h, "Desk")
	local diary = box(-78.2, y + 2.3, 208.5, -77.2, y + 2.5, 209.7, COL.Crimson, Enum.Material.Fabric, h, "Diary")
	W.Interact.Diary = diary
	local wardrobe = box(-79.4, y, 193, -77, y + 7.5, 197, COL.DarkWood, Enum.Material.Wood, h, "Wardrobe")
	W.Interact.Wardrobe = wardrobe
	local door = box(-94, 0.5, z1 - 0.4, -89, 8, z1 + 0.4, COL.White, nil, h, "FrontDoorZone")
	door.Transparency = 1
	deco(door)
	W.Interact.HomeDoor = door
	W.HomeSpawn = CFrame.lookAt(Vector3.new(-70, y + 3.2, 200), Vector3.new(-78, y + 3.2, 200))
	W.SchoolArrive = CFrame.lookAt(Vector3.new(0, 3.6, 128), Vector3.new(0, 3.6, 100))
	box(-94, 0, 164, -89, 0.32, 176, WALK, Enum.Material.Pavement, h, "HomePath")
	K.sign("AMANE", CFrame.lookAt(Vector3.new(-86.5, 6.5, z1 - 0.65), Vector3.new(-86.5, 6.5, 100)), Vector2.new(3, 0.8), h, COL.White, COL.Trim)
	K.wallX(x1 - 3, x2 + 3, 167, 0, 2.2, 0.8, { { a = -94, b = -89, bottom = 0, top = 2.2 } }, Color3.fromRGB(200, 196, 188), h, Enum.Material.Concrete)
end

local function scenery(parent: Instance)
	local f = folder("Scenery", parent)
	-- tree lines around the edge of town and behind the school
	for x = -440, 640, 26 do
		K.tree(x + math.random(-6, 6), -360 + math.random(-10, 10), "Green", f)
	end
	for z = -320, 560, 30 do
		K.tree(-330 + math.random(-6, 6), z, "Green", f)
		K.tree(620 + math.random(-6, 6), z, "Green", f)
	end
	for _, p in { { -40, 230 }, { 40, 230 }, { 270, 220 }, { 160, 360 }, { -150, 360 }, { 200, 470 } } do
		K.tree(p[1], p[2], "Green", f)
	end
	-- parked cars
	local cars = {
		{ CFrame.new(214, 0.08, 186), Color3.fromRGB(200, 205, 210) },
		{ CFrame.new(232, 0.08, 186), Color3.fromRGB(150, 24, 36) },
		{ CFrame.new(250, 0.08, 188), Color3.fromRGB(30, 34, 44) },
		{ CFrame.new(268, 0.08, 186), Color3.fromRGB(240, 240, 236) },
		{ CFrame.new(-7, 0.08, 250), Color3.fromRGB(60, 90, 150) },
		{ CFrame.new(7, 0.08, 300) * CFrame.Angles(0, math.rad(180), 0), Color3.fromRGB(230, 200, 80) },
		{ CFrame.new(-120, 0.08, 144) * CFrame.Angles(0, math.rad(90), 0), Color3.fromRGB(240, 240, 236) },
		{ CFrame.new(140, 0.08, 150) * CFrame.Angles(0, math.rad(-90), 0), Color3.fromRGB(40, 40, 46) },
	}
	for _, c in cars do
		K.car(c[1], c[2], f)
	end
	box(200, 0, 166, 280, 0.1, 210, COL.Asphalt, Enum.Material.Asphalt, f, "ParkingLot")
	for x = 205, 275, 18 do
		deco(box(x, 0.1, 176, x + 0.3, 0.12, 196, COL.White, nil, f, "ParkingLine"))
	end
end

function Town.Build()
	local t = folder("Town")
	roads()
	home(t)
	for i, x in { -160, -220, -290 } do
		house(x, 176, 36, 34, Color3.fromHSV((i * 0.17) % 1, 0.12, 0.88), t)
	end
	for i, x in { -100, -160, -220, -290 } do
		house(x, 262, 36, 34, Color3.fromHSV((i * 0.23 + 0.4) % 1, 0.12, 0.86), t)
	end
	shop(26, 70, "HOSHI MART", Color3.fromRGB(30, 140, 90), "mart", t)
	shop(80, 112, "KURO CAFÉ", Color3.fromRGB(110, 60, 40), "cafe", t)
	shop(122, 152, "PAGE & PETAL", Color3.fromRGB(150, 90, 160), "books", t)
	shop(162, 194, "YUME RAMEN", Color3.fromRGB(200, 40, 40), "ramen", t)
	for i, x in { 30, 90, 150, 210 } do
		house(x, 256, 36, 34, Color3.fromHSV((i * 0.31) % 1, 0.1, 0.88), t)
	end
	for i, x in { 170, 230 } do
		house(x, 396, 36, 34, Color3.fromHSV((i * 0.13 + 0.2) % 1, 0.12, 0.87), t)
		house(x, 476, 36, 34, Color3.fromHSV((i * 0.29 + 0.6) % 1, 0.12, 0.87), t)
	end
	for i, z in { 190, 260, 400, 470 } do
		house(500, z, 36, 34, Color3.fromHSV((i * 0.21) % 1, 0.1, 0.86), t)
	end
	apartments(t)
	park(t)
	station(t)
	scenery(t)
end

return Town
