-- KUROBARA · Kurobara Academy campus (original layout, realistic scale)
--   Main building: 3 storeys + roof, rooms facing the courtyard (south), corridor on the north,
--   two switch-back stair towers. Annex (cafeteria), sports hall + changing rooms, outdoor pool,
--   track & field, garden + greenhouse, incinerator, big courtyard with fountain, front gate.

local K = require(script.Parent.BuildKit)
local COL = K.COL
local W = K.World
local box, deco, P, ball, cyl, glass, folder = K.box, K.deco, K.P, K.ball, K.cyl, K.glass, K.folder
local spot, ring, zone = K.spot, K.ring, K.zone
local FLOOR, FH = K.FLOOR, K.FH

local School = {}

-- main building geometry
local X1, X2 = -140, 140
local Z_SOUTH, Z_CORR, Z_NORTH = 0, -26, -36
local ZA, ZB = -25.5, -0.5 -- room interior (ZA = corridor side)
local CZ = -13
local TOWERS = { -100, 86 } -- stair tower west edges (each 14 wide, north of the corridor)

-- rooms per storey (x1, x2). "open" rooms have no corridor wall.
local LEVELS = {
	[0] = {
		{ id = "Science", x1 = -140, x2 = -110, label = "SCIENCE LAB" },
		{ id = "Art", x1 = -110, x2 = -80, label = "ART ROOM" },
		{ id = "Nurse", x1 = -80, x2 = -50, label = "NURSE" },
		{ id = "Lounge", x1 = -50, x2 = -20, label = "TEACHERS' LOUNGE" },
		{ id = "Lobby", x1 = -20, x2 = 20, label = "ENTRANCE", open = true },
		{ id = "Janitor", x1 = 20, x2 = 50, label = "MAINTENANCE" },
		{ id = "Restroom", x1 = 50, x2 = 80, label = "RESTROOMS" },
		{ id = "Library", x1 = 80, x2 = 140, label = "LIBRARY" },
	},
	[1] = {
		{ id = "Class", class = "1A", x1 = -140, x2 = -110, label = "CLASS 1-A" },
		{ id = "Class", class = "1B", x1 = -110, x2 = -80, label = "CLASS 1-B" },
		{ id = "Music", x1 = -80, x2 = -50, label = "MUSIC ROOM" },
		{ id = "Restroom", x1 = -50, x2 = -20, label = "RESTROOMS" },
		{ id = "Atrium", x1 = -20, x2 = 20, label = "", open = true },
		{ id = "Class", class = "3A", x1 = 20, x2 = 50, label = "CLASS 3-A" },
		{ id = "Class", class = "3B", x1 = 50, x2 = 80, label = "CLASS 3-B" },
		{ id = "Study", x1 = 80, x2 = 110, label = "STUDY ROOM" },
		{ id = "Council", x1 = 110, x2 = 140, label = "STUDENT COUNCIL" },
	},
	[2] = {
		{ id = "Class", class = "2A", x1 = -140, x2 = -110, label = "CLASS 2-A" },
		{ id = "Class", class = "2B", x1 = -110, x2 = -80, label = "CLASS 2-B" },
		{ id = "Cooking", x1 = -80, x2 = -50, label = "COOKING CLUB" },
		{ id = "Restroom", x1 = -50, x2 = -20, label = "RESTROOMS" },
		{ id = "Atrium", x1 = -20, x2 = 20, label = "", open = true },
		{ id = "Occult", x1 = 20, x2 = 50, label = "OCCULT CLUB" },
		{ id = "Mystery", x1 = 50, x2 = 80, label = "MYSTERY CLUB" },
		{ id = "Storage", x1 = 80, x2 = 110, label = "STORAGE" },
		{ id = "AV", x1 = 110, x2 = 140, label = "AUDIO-VISUAL" },
	},
}

-- ── room furniture ───────────────────────────────────────────────────────────
local Furnish = {}

function Furnish.Class(r, f)
	local y, x1 = r.y, r.x1
	box(x1 + 0.5, y + 3, CZ - 7.5, x1 + 0.75, y + 7.6, CZ + 7.5, COL.Board, Enum.Material.Slate, f, "Blackboard")
	box(x1 + 0.5, y + 2.8, CZ - 7.8, x1 + 1.0, y + 3.0, CZ + 7.8, COL.Wood, Enum.Material.Wood, f, "ChalkLedge")
	box(x1 + 3.5, y, CZ - 2.5, x1 + 5.5, y + 2.6, CZ + 2.5, COL.DarkWood, Enum.Material.Wood, f, "TeacherDesk")
	box(x1 + 2.2, y, CZ - 4, x1 + 3, y + 0.4, CZ + 4, COL.DarkWood, Enum.Material.Wood, f, "Podium")
	local front = Vector3.new(x1 + 2.5, y, CZ + 5)
	W.ClassFront[r.class] = CFrame.lookAt(front, front + Vector3.new(1, 0, 0))
	for _, zo in { 4, 9, 14, 19 } do
		for _, xo in { 8, 14, 20, 26 } do
			K.deskAndChair(x1 + xo, y, ZA + zo, f, r.class)
		end
	end
	K.pinboard(CFrame.lookAt(Vector3.new(r.x2 - 0.6, y + 5.5, CZ), Vector3.new(r.x2 - 5, y + 5.5, CZ)), f)
	local clock = cyl(Vector3.new(r.x2 - 0.6, y + 9.2, CZ + 6), 0.2, 1.6, COL.White, nil, f)
	clock.CFrame = CFrame.new(r.x2 - 0.6, y + 9.2, CZ + 6)
	deco(clock)
	-- TV on a bracket in the corner
	deco(box(r.x2 - 1.2, y + 9.2, ZA + 0.6, r.x2 - 0.6, y + 11.6, ZA + 4.6, COL.Black, nil, f, "TV"))
end

function Furnish.Study(r, f)
	local y = r.y
	for _, zo in { 5, 12, 19 } do
		box(r.x1 + 5, y + 2.05, ZA + zo - 1.6, r.x2 - 5, y + 2.25, ZA + zo + 1.6, COL.LightWood, Enum.Material.Wood, f, "LongDesk")
		box(r.x1 + 6, y, ZA + zo - 0.3, r.x2 - 6, y + 2.05, ZA + zo + 0.3, COL.Metal, Enum.Material.Metal, f, "DeskLeg")
	end
	K.shelf(r.x2 - 2, ZA + 1, r.x2 - 0.6, ZB - 1, y, 7, f, true)
end

function Furnish.Art(r, f)
	local y = r.y
	for i, xo in { 8, 20 } do
		box(r.x1 + xo - 4, y + 2.05, CZ - 2.5, r.x1 + xo + 4, y + 2.3, CZ + 2.5, COL.LightWood, Enum.Material.Wood, f, "ArtTable")
		box(r.x1 + xo - 3.6, y, CZ - 2, r.x1 + xo - 3.2, y + 2.05, CZ + 2, COL.DarkWood, nil, f, "Leg")
		box(r.x1 + xo + 3.2, y, CZ - 2, r.x1 + xo + 3.6, y + 2.05, CZ + 2, COL.DarkWood, nil, f, "Leg")
		for _ = 1, 5 do
			local px, pz = r.x1 + xo - 3 + math.random() * 6, CZ - 2 + math.random() * 4
			deco(box(px, y + 2.3, pz, px + 0.5, y + 2.33, pz + 0.5, Color3.fromHSV(math.random(), 0.7, 0.9), nil, f, "Paint"))
		end
		if i == 1 then
			W.ItemSpawns.Art = CFrame.new(r.x1 + xo + 2, y + 2.5, CZ + 1.5) * CFrame.Angles(0, math.rad(30), math.rad(90))
		end
	end
	for _, zo in { 3, 12, 21 } do
		local x, z = r.x1 + 27, ZA + zo
		deco(box(x - 0.15, y, z - 0.15, x + 0.15, y + 6, z + 0.15, COL.Wood, Enum.Material.Wood, f, "Easel"))
		box(x - 0.3, y + 3, z - 1.6, x - 0.1, y + 5.5, z + 1.6, COL.White, Enum.Material.Fabric, f, "Canvas")
		deco(box(x - 0.35, y + 3.5, z - 1.2, x - 0.3, y + 5, z + 0.8, Color3.fromHSV(math.random(), 0.6, 0.8), nil, f, "PaintStroke"))
	end
	K.shelf(r.x1 + 1, ZB - 1.6, r.x1 + 12, ZB - 0.6, y, 4, f, false)
	for _, s in { { 8, CZ + 4.5 }, { 20, CZ - 4.5 }, { 24, ZA + 3 }, { 24, ZA + 21 } } do
		spot("Art", Vector3.new(r.x1 + s[1], y, s[2]), Vector3.new(r.x1 + s[1], y, CZ))
	end
end

function Furnish.Science(r, f)
	local y = r.y
	for _, xo in { 7, 15, 23 } do
		box(r.x1 + xo - 1.6, y, CZ - 6, r.x1 + xo + 1.6, y + 2.2, CZ + 6, COL.White, nil, f, "LabBench")
		box(r.x1 + xo - 1.7, y + 2.2, CZ - 6.1, r.x1 + xo + 1.7, y + 2.4, CZ + 6.1, COL.Black, Enum.Material.Slate, f, "LabTop")
		for k = 1, 4 do
			local p = cyl(Vector3.new(r.x1 + xo, y + 2.8, CZ - 5 + k * 2), 0.8, 0.45, Color3.fromHSV(math.random(), 0.8, 1), Enum.Material.Neon, f)
			p.Transparency = 0.3
			deco(p)
		end
		spot("Science", Vector3.new(r.x1 + xo + 2.8, y, CZ), Vector3.new(r.x1 + xo, y, CZ))
	end
	deco(box(r.x1 + 0.5, y + 3, CZ - 5, r.x1 + 0.7, y + 8, CZ + 5, COL.White, nil, f, "PeriodicTable"))
	K.shelf(r.x2 - 1.6, ZA + 2, r.x2 - 0.6, ZA + 10, y, 7, f, false)
end

function Furnish.Library(r, f)
	local y = r.y
	K.shelf(r.x1 + 1, ZB - 1.6, r.x2 - 1, ZB - 0.6, y, 9, f, true)
	for _, xo in { 6, 14, 22, 30 } do
		K.shelf(r.x1 + xo, CZ - 6, r.x1 + xo + 1.4, CZ + 4, y, 7, f, true)
	end
	for _, t in { { 42, CZ - 4 }, { 52, CZ - 4 }, { 47, CZ + 6 } } do
		box(r.x1 + t[1] - 3, y + 2, t[2] - 2, r.x1 + t[1] + 3, y + 2.2, t[2] + 2, COL.Wood, Enum.Material.Wood, f, "ReadingTable")
		box(r.x1 + t[1] - 0.5, y, t[2] - 0.5, r.x1 + t[1] + 0.5, y + 2, t[2] + 0.5, COL.DarkWood, nil, f, "TableLeg")
		spot("Library", Vector3.new(r.x1 + t[1], y, t[2] - 3.5), Vector3.new(r.x1 + t[1], y, t[2]))
	end
	box(r.x1 + 36, y, ZA + 1, r.x1 + 44, y + 3, ZA + 3, COL.DarkWood, Enum.Material.Wood, f, "LibraryDesk")
	spot("Library", Vector3.new(r.x1 + 10, y, CZ + 6), Vector3.new(r.x1 + 6, y, CZ + 6))
end

function Furnish.Music(r, f)
	local y = r.y
	box(r.x1 + 3, y, ZA + 2, r.x1 + 10, y + 3.2, ZA + 6, COL.Black, Enum.Material.SmoothPlastic, f, "Piano").Reflectance = 0.15
	deco(box(r.x1 + 3.2, y + 2.1, ZA + 6, r.x1 + 9.8, y + 2.3, ZA + 6.6, COL.White, nil, f, "Keys"))
	for i = 0, 7 do
		local x = r.x1 + 13 + (i % 4) * 4
		local z = CZ + (i < 4 and -3 or 4)
		deco(box(x - 0.6, y + 0.9, z - 0.6, x + 0.6, y + 1.05, z + 0.6, COL.Wood, nil, f, "Stool"))
		deco(box(x - 0.1, y + 3, z - 1.6, x + 0.1, y + 4.6, z - 1, COL.Black, Enum.Material.Metal, f, "MusicStand"))
	end
	spot("Music", Vector3.new(r.x1 + 6.5, y, ZA + 8), Vector3.new(r.x1 + 6.5, y, ZA + 3))
	spot("Music", Vector3.new(r.x1 + 13, y, CZ - 3), Vector3.new(r.x1 + 6, y, CZ))
	spot("Music", Vector3.new(r.x1 + 21, y, CZ + 4), Vector3.new(r.x1 + 6, y, CZ))
	spot("Music", Vector3.new(r.x1 + 25, y, CZ - 3), Vector3.new(r.x1 + 6, y, CZ))
end

function Furnish.Lounge(r, f)
	local y = r.y
	for i = 0, 2 do
		local x = r.x1 + 6 + i * 9
		box(x - 2.5, y + 2, CZ - 2, x + 2.5, y + 2.2, CZ + 2, COL.DarkWood, Enum.Material.Wood, f, "TeacherDesk")
		box(x - 2.3, y, CZ - 1.8, x + 2.3, y + 2, CZ + 1.8, COL.Metal, nil, f, "DeskBody")
		spot("Lounge", Vector3.new(x, y, CZ + 4), Vector3.new(x, y, CZ))
	end
	box(r.x2 - 10, y, ZB - 4, r.x2 - 1.5, y + 1.6, ZB - 1, Color3.fromRGB(110, 40, 50), Enum.Material.Fabric, f, "Sofa")
	box(r.x2 - 10, y + 1.6, ZB - 1.8, r.x2 - 1.5, y + 3.4, ZB - 1, Color3.fromRGB(110, 40, 50), Enum.Material.Fabric, f, "SofaBack")
	box(r.x1 + 1, y, ZA + 0.5, r.x1 + 3, y + 4, ZA + 2.5, COL.Black, nil, f, "CoffeeMachine")
	spot("Lounge", Vector3.new(r.x2 - 6, y, ZB - 6), Vector3.new(r.x2 - 6, y, ZB))
end

function Furnish.Nurse(r, f)
	local y = r.y
	for i = 0, 2 do
		local x = r.x1 + 6 + i * 8
		box(x - 2, y, ZB - 7, x + 2, y + 1.8, ZB - 1, COL.White, Enum.Material.Fabric, f, "Bed")
		box(x - 1.6, y + 1.8, ZB - 2.4, x + 1.6, y + 2.3, ZB - 1.2, COL.White, Enum.Material.Fabric, f, "Pillow")
		deco(box(x + 2.6, y, ZB - 8, x + 2.8, y + 7, ZB - 1, Color3.fromRGB(200, 230, 225), Enum.Material.Fabric, f, "Curtain")).Transparency = 0.2
	end
	box(r.x1 + 2, y + 2, ZA + 2, r.x1 + 8, y + 2.2, ZA + 6, COL.White, nil, f, "NurseDesk")
	K.sink(r.x2 - 1.3, y, ZA + 5, Vector3.new(-1, 0, 0), f)
end

function Furnish.Janitor(r, f)
	local y = r.y
	K.shelf(r.x1 + 1, ZB - 2, r.x1 + 12, ZB - 0.4, y, 7, f, false)
	for i = 0, 3 do
		deco(box(r.x1 + 2 + i * 2.5, y + 2.4, ZB - 1.8, r.x1 + 3.5 + i * 2.5, y + 3.6, ZB - 0.6, Color3.fromHSV(0.1 + i * 0.2, 0.5, 0.8), nil, f, "Supplies"))
	end
	W.ItemSpawns.Janitor = CFrame.new(r.x1 + 16, y + 2.6, ZB - 1.2) * CFrame.Angles(math.rad(-75), 0, 0)
	W.ItemSpawns.Janitor2 = CFrame.new(r.x1 + 6, y + 4.9, ZB - 1.2)
	cyl(Vector3.new(r.x1 + 19, y + 0.8, ZB - 2), 1.6, 1.8, Color3.fromRGB(255, 200, 40), Enum.Material.SmoothPlastic, f).Name = "Bucket"
	K.sink(r.x2 - 1.3, y, CZ + 3, Vector3.new(-1, 0, 0), f)
	box(r.x1 + 22, y, ZA + 1, r.x1 + 28, y + 5, ZA + 3, COL.Metal, Enum.Material.Metal, f, "Boiler")
end

function Furnish.Restroom(r, f)
	local y = r.y
	for i = 0, 3 do
		local x = r.x1 + 13 + i * 4
		deco(box(x - 0.1, y, ZB - 7, x + 0.1, y + 7.5, ZB - 0.5, Color3.fromRGB(200, 210, 230), nil, f, "StallWall"))
	end
	for i = 0, 2 do
		K.sink(r.x1 + 3 + i * 3.4, y, ZA + 1.3, Vector3.new(0, 0, 1), f)
	end
	local mirror = box(r.x1 + 1.5, y + 3.8, ZA + 0.5, r.x1 + 11, y + 7.5, ZA + 0.6, Color3.fromRGB(200, 210, 220), Enum.Material.Glass, f, "Mirror")
	mirror.Reflectance = 0.6
	deco(mirror)
end

function Furnish.Lobby(r, f)
	local y = r.y
	for i = 0, 1 do
		local x = r.x1 + 2 + i * 33.5
		box(x, y, ZA + 4, x + 2.5, y + 6, ZB - 7, Color3.fromRGB(150, 120, 90), Enum.Material.Wood, f, "ShoeLockers")
	end
	for i = 0, 2 do
		box(r.x1 + 9 + i * 8, y, CZ - 1, r.x1 + 11 + i * 8, y + 5.5, CZ + 4, Color3.fromRGB(150, 120, 90), Enum.Material.Wood, f, "ShoeLockers")
	end
	deco(box(-5, y, ZB - 3, 5, y + 0.05, ZB - 0.5, COL.Crimson, Enum.Material.Fabric, f, "Mat"))
	box(r.x2 - 8, y, ZA + 0.6, r.x2 - 2, y + 7, ZA + 2, COL.DarkWood, Enum.Material.Wood, f, "TrophyCase")
	glass(box(r.x2 - 7.8, y + 2, ZA + 1.99, r.x2 - 2.2, y + 6.6, ZA + 2.05, COL.Glass, nil, f, "TrophyGlass"))
	for _, s in { { -6, CZ - 6 }, { 6, CZ - 6 }, { -4, ZB - 5 }, { 4, ZB - 5 } } do
		spot("Lobby", Vector3.new(s[1], y, s[2]), Vector3.new(0, y, CZ))
	end
end

function Furnish.Atrium(r, f)
	local y = r.y
	for _, x in { -12, 12 } do
		K.bench(CFrame.new(x, y, CZ + 4) * CFrame.Angles(0, math.rad(180), 0), f)
		K.pottedPlant(Vector3.new(x * 1.4, y, ZB - 2), f)
	end
	spot("Atrium", Vector3.new(-12, y, CZ + 6.5), Vector3.new(-12, y, 0))
	spot("Atrium", Vector3.new(12, y, CZ + 6.5), Vector3.new(12, y, 0))
	spot("Atrium", Vector3.new(0, y, CZ - 4), Vector3.new(0, y, 0))
	deco(box(-18, y + 3, ZA + 0.55, 18, y + 9, ZA + 0.7, Color3.fromRGB(30, 30, 36), nil, f, "NoticeWall"))
	for i = 0, 6 do
		deco(box(-16 + i * 5, y + 4 + (i % 2), ZA + 0.75, -13 + i * 5, y + 7 + (i % 2), ZA + 0.8, Color3.fromHSV(i / 7, 0.35, 1), nil, f, "Poster"))
	end
end

function Furnish.Council(r, f)
	local y = r.y
	box(r.x1 + 6, y + 2, CZ - 3, r.x2 - 6, y + 2.2, CZ + 3, COL.DarkWood, Enum.Material.Wood, f, "MeetingTable")
	box(r.x1 + 8, y, CZ - 1, r.x2 - 8, y + 2, CZ + 1, COL.DarkWood, nil, f, "TableBase")
	deco(box(r.x2 - 0.7, y + 3, CZ - 6, r.x2 - 0.5, y + 8, CZ + 6, COL.Crimson, Enum.Material.Fabric, f, "Banner"))
end

function Furnish.Cooking(r, f)
	local y = r.y
	for _, zo in { 6, 14 } do
		box(r.x1 + 4, y, ZA + zo - 2, r.x2 - 4, y + 2.6, ZA + zo + 2, COL.White, nil, f, "Counter")
		box(r.x1 + 3.9, y + 2.6, ZA + zo - 2.1, r.x2 - 3.9, y + 2.75, ZA + zo + 2.1, COL.Metal, Enum.Material.Metal, f, "CounterTop")
		for k = 0, 2 do
			deco(box(r.x1 + 7 + k * 7, y + 2.75, ZA + zo - 1, r.x1 + 10 + k * 7, y + 2.85, ZA + zo + 1, COL.Black, nil, f, "Stove"))
		end
	end
	K.sink(r.x2 - 1.3, y, ZB - 4, Vector3.new(-1, 0, 0), f)
end

function Furnish.Occult(r, f)
	local y = r.y
	box(r.x1 + 10, y + 2, CZ - 3, r.x1 + 20, y + 2.2, CZ + 3, Color3.fromRGB(30, 20, 34), Enum.Material.Wood, f, "RoundTable")
	local orb = ball(Vector3.new(r.x1 + 15, y + 3, CZ), 1.2, Color3.fromRGB(150, 110, 255), Enum.Material.Neon, f)
	orb.Transparency = 0.3
	deco(orb)
	local glow = Instance.new("PointLight")
	glow.Color = Color3.fromRGB(150, 110, 255)
	glow.Range = 12
	glow.Brightness = 1.2
	glow.Parent = orb
	for i = 1, 6 do
		local a = i / 6 * math.pi * 2
		local candlePos = Vector3.new(r.x1 + 15 + math.cos(a) * 4, y + 2.6, CZ + math.sin(a) * 2.4)
		deco(cyl(candlePos, 0.8, 0.3, Color3.fromRGB(240, 230, 210), nil, f))
		deco(ball(candlePos + Vector3.new(0, 0.55, 0), 0.25, Color3.fromRGB(255, 180, 80), Enum.Material.Neon, f))
	end
	deco(box(r.x1 + 1, y + 1, ZB - 0.7, r.x2 - 1, y + 11, ZB - 0.6, Color3.fromRGB(30, 10, 30), Enum.Material.Fabric, f, "BlackoutCurtain"))
end

function Furnish.Mystery(r, f)
	local y = r.y
	local board = box(r.x1 + 4, y + 3, ZB - 0.75, r.x2 - 4, y + 9, ZB - 0.6, Color3.fromRGB(176, 136, 92), Enum.Material.Wood, f, "CaseBoard")
	deco(board)
	local pins = {}
	for i = 1, 9 do
		local p = Vector3.new(r.x1 + 6 + math.random() * 18, y + 4 + math.random() * 4, ZB - 0.85)
		deco(box(p.X - 0.5, p.Y - 0.6, p.Z - 0.02, p.X + 0.5, p.Y + 0.6, p.Z + 0.02, Color3.fromHSV(0.1, 0.1, 0.95), nil, f, "Photo"))
		table.insert(pins, p)
		if i > 1 then
			local a, b = pins[i - 1], p
			deco(P(Vector3.new(0.05, 0.05, (b - a).Magnitude), CFrame.lookAt((a + b) / 2 - Vector3.new(0, 0, 0.05), b - Vector3.new(0, 0, 0.05)), Color3.fromRGB(220, 20, 30), nil, f, "RedString"))
		end
	end
	box(r.x1 + 8, y + 2, CZ - 3, r.x1 + 22, y + 2.2, CZ + 2, COL.Wood, Enum.Material.Wood, f, "Desk")
end

function Furnish.Storage(r, f)
	local y = r.y
	for _ = 1, 14 do
		local x, z = r.x1 + 2 + math.random() * 24, ZA + 2 + math.random() * 20
		local s = 1.5 + math.random() * 2
		box(x, y, z, x + s, y + s, z + s, Color3.fromRGB(170, 130, 90), Enum.Material.Cardboard, f, "Box")
	end
end

function Furnish.AV(r, f)
	local y = r.y
	deco(box(r.x1 + 0.6, y + 3, CZ - 8, r.x1 + 0.8, y + 10, CZ + 8, COL.White, nil, f, "Screen"))
	for row = 0, 3 do
		for k = 0, 5 do
			deco(box(r.x1 + 8 + row * 4, y + 0.9, CZ - 9 + k * 3.4, r.x1 + 9.4 + row * 4, y + 1.05, CZ - 7.6 + k * 3.4, Color3.fromRGB(110, 30, 40), Enum.Material.Fabric, f, "Seat"))
		end
	end
end

-- ── stair towers (switch-back, two 6.5-stud flights per storey) ─────────────
local function stairTower(s: number, f: Folder)
	local top = 3 * FH + 9
	K.wallZ(-54, -36, s, 0, top, 1, K.windowRow(-52, -38, 4, 9, 3, 6), COL.Cream, f)
	K.wallZ(-54, -36, s + 14, 0, top, 1, {}, COL.Cream, f)
	K.wallX(s, s + 14, -54, 0, top, 1, {}, COL.Cream, f)
	box(s - 0.5, top, -54.5, s + 14.5, top + 0.6, -35.5, COL.Trim, nil, f, "TowerCap")
	box(s + 0.5, 0, -53.5, s + 13.5, 0.55, -36, COL.Concrete, Enum.Material.Concrete, f, "TowerFloor")
	-- central dividing wall between the two lanes
	box(s + 6.8, 0, -50, s + 7.2, top, -38, COL.Wall, nil, f, "StairCore")
	for n = 0, 2 do
		local base = FLOOR(n)
		local half = FH / 2
		-- flight A: from the corridor (z -38) up to the landing (z -50)
		local a = K.wedge(Vector3.new(6, half, 12), CFrame.new(s + 3.9, base + half / 2, -44) * CFrame.Angles(0, math.rad(180), 0), COL.Concrete, Enum.Material.Concrete, f, "Ramp")
		a.Transparency = 1
		-- flight B: from the landing back to the next storey
		local b = K.wedge(Vector3.new(6, half, 12), CFrame.new(s + 10.1, base + half + half / 2, -44), COL.Concrete, Enum.Material.Concrete, f, "Ramp")
		b.Transparency = 1
		box(s + 0.5, base + half - 0.5, -53.5, s + 13.5, base + half, -50, COL.Concrete, Enum.Material.Concrete, f, "Landing")
		-- visible steps
		for i = 0, 12 do
			local hA = base + (i + 1) * half / 13
			local stepA = box(s + 0.9, base, -38 - i * (12 / 13) - 12 / 13, s + 6.8, hA, -38 - i * (12 / 13), i % 2 == 0 and COL.Concrete or Color3.fromRGB(190, 188, 184), Enum.Material.Concrete, f, "Step")
			stepA.CanCollide = false
			stepA.CanQuery = false
			local hB = base + half + (i + 1) * half / 13
			local stepB = box(s + 7.2, base + half, -50 + i * (12 / 13), s + 13.1, hB, -50 + (i + 1) * (12 / 13), i % 2 == 0 and COL.Concrete or Color3.fromRGB(190, 188, 184), Enum.Material.Concrete, f, "Step")
			stepB.CanCollide = false
			stepB.CanQuery = false
		end
		-- entry strip at the storey above
		box(s + 0.5, FLOOR(n + 1) - 0.5, -38, s + 13.5, FLOOR(n + 1), -36, COL.Concrete, Enum.Material.Concrete, f, "StairStrip")
		K.ceilingLight(s + 7, base + half + 7, -52, f)
	end
	-- roof exit
	K.wallX(s, s + 14, -36, 3 * FH, top, 1, { { a = s + 4, b = s + 10, bottom = FLOOR(3), top = FLOOR(3) + 7.5 } }, COL.Cream, f)
	K.ceilingLight(s + 7, top - 0.2, -40, f)
end

-- ── main building ────────────────────────────────────────────────────────────
local function mainBuilding()
	local b = folder("MainBuilding")
	local walls = folder("Walls", b)
	zone("Main Building", X1, Z_NORTH - 18, X2, Z_SOUTH, "#8a7f86")

	box(X1 - 0.5, 0, Z_NORTH - 0.5, X2 + 0.5, 0.5, Z_SOUTH + 0.5, COL.Concrete, Enum.Material.Concrete, b, "Foundation")
	for n = 1, 3 do
		box(X1 - 0.5, n * FH, Z_NORTH - 0.5, X2 + 0.5, n * FH + 0.5, Z_SOUTH + 0.5, COL.Concrete, Enum.Material.Concrete, b, n == 3 and "RoofSlab" or "Slab")
	end
	box(X1, 3 * FH + 0.5, Z_NORTH, X2, FLOOR(3), Z_SOUTH, COL.RoofSlab, Enum.Material.Concrete, b, "RoofFinish")

	for n = 0, 2 do
		local y = FLOOR(n)
		local yb, yt = n * FH, (n + 1) * FH
		local rooms = LEVELS[n]
		local lvl = folder("Floor" .. (n + 1), b)
		-- corridor floor + crimson stripe + lights
		local cf = box(X1, yb + 0.5, -35.5, X2, y, -26.5, COL.Floor, Enum.Material.SmoothPlastic, lvl, "CorridorFloor")
		cf.Reflectance = 0.06
		deco(box(X1, y, -31.4, X2, y + 0.01, -30.6, COL.Crimson, nil, lvl, "Stripe"))
		for x = X1 + 10, X2 - 10, 20 do
			K.ceilingLight(x, yt, -31, lvl)
		end
		for x = X1 + 20, X2 - 20, 40 do
			spot("Hall", Vector3.new(x, y, -31), Vector3.new(x + 10, y, -31))
		end
		-- corridor wall (rooms ↔ corridor)
		local corrOpen = {}
		local southOpen = {}
		for _, r in rooms do
			local cx = (r.x1 + r.x2) / 2
			if r.open then
				table.insert(corrOpen, { a = r.x1 + 0.5, b = r.x2 - 0.5, bottom = yb, top = y + 10 })
			else
				table.insert(corrOpen, { a = cx - 2.5, b = cx + 2.5, bottom = yb, top = y + 8 })
			end
			if r.id == "Lobby" then
				table.insert(southOpen, { a = -7, b = 7, bottom = yb, top = y + 9 })
				table.insert(southOpen, { a = -17, b = -10, bottom = y + 2.5, top = y + 9.5, glass = true })
				table.insert(southOpen, { a = 10, b = 17, bottom = y + 2.5, top = y + 9.5, glass = true })
			elseif r.id == "Atrium" then
				table.insert(southOpen, { a = -18, b = 18, bottom = y + 1, top = y + 11, glass = true })
			elseif r.id == "Restroom" then
				table.insert(southOpen, { a = cx - 9, b = cx - 3, bottom = y + 7, top = y + 10, glass = true })
				table.insert(southOpen, { a = cx + 3, b = cx + 9, bottom = y + 7, top = y + 10, glass = true })
			else
				for _, o in K.windowRow(r.x1 + 1, r.x2 - 1, y + 2.9, y + 9.6, 8, 3) do
					table.insert(southOpen, o)
				end
			end
		end
		K.wallX(X1, X2, Z_CORR, yb + 0.5, yt, 1, corrOpen, COL.Wall, walls)
		K.wallX(X1 - 0.5, X2 + 0.5, Z_SOUTH, yb, yt, 1, southOpen, COL.Cream, walls)
		-- north façade: corridor windows + doorways into the stair towers
		local northOpen = {}
		for _, o in K.windowRow(X1 + 1, X2 - 1, y + 3.5, y + 9.2, 6, 5) do
			local clash = false
			for _, s in TOWERS do
				if o.b > s - 1 and o.a < s + 15 then
					clash = true
				end
			end
			if not clash then
				table.insert(northOpen, o)
			end
		end
		for _, s in TOWERS do
			table.insert(northOpen, { a = s + 1, b = s + 13, bottom = yb, top = y + 9 })
		end
		K.wallX(X1 - 0.5, X2 + 0.5, Z_NORTH, yb, yt, 1, northOpen, COL.Cream, walls)
		-- end walls (ground floor: exits at both corridor ends)
		for _, x in { X1, X2 } do
			local ends = {
				{ a = -20, b = -6, bottom = y + 3, top = y + 9.5, glass = true },
				n == 0 and { a = -33.5, b = -28.5, bottom = yb, top = y + 8 } or { a = -34, b = -28, bottom = y + 3.5, top = y + 9.2, glass = true },
			}
			K.wallZ(Z_NORTH - 0.5, Z_SOUTH + 0.5, x, yb, yt, 1, ends, COL.Cream, walls)
		end
		-- room dividers, floors, lights, signs, furniture
		for _, r in rooms do
			r.y = y
			r.n = n
			local rf = folder((r.class and ("Class" .. r.class)) or r.id, lvl)
			if r.x1 > X1 then
				K.wallZ(ZA, ZB, r.x1, yb + 0.5, yt, 1, {}, COL.Wall, walls)
			end
			local wood = r.id == "Class" or r.id == "Library" or r.id == "Music" or r.id == "Art" or r.id == "Study" or r.id == "Council" or r.id == "AV"
			box(r.x1 + 0.5, yb + 0.5, ZA, r.x2 - 0.5, y, ZB, wood and COL.Wood or COL.Floor, wood and Enum.Material.WoodPlanks or Enum.Material.SmoothPlastic, rf, "Floor")
			local cx = (r.x1 + r.x2) / 2
			local span = r.x2 - r.x1
			for k = 1, math.max(2, math.floor(span / 15)) do
				K.ceilingLight(r.x1 + (k - 0.5) * span / math.max(2, math.floor(span / 15)), yt, CZ, rf)
			end
			if r.label ~= "" then
				local pos = Vector3.new(cx, y + 9.4, Z_CORR - 0.65)
				K.sign(r.label, CFrame.lookAt(pos, pos + Vector3.new(0, 0, -1)), Vector2.new(6.5, 1.2), rf)
			end
			if not r.open then
				deco(box(cx + 2.5, y, Z_CORR - 0.77, cx + 7.3, y + 7.9, Z_CORR - 0.53, COL.LightWood, Enum.Material.Wood, rf, "DoorPanel"))
				glass(box(cx + 4, y + 4.6, Z_CORR - 0.79, cx + 6, y + 7.2, Z_CORR - 0.51, COL.Glass, nil, rf, "DoorWindow"))
				-- curtains at the courtyard windows
				if r.id ~= "Restroom" then
					for _, o in K.windowRow(r.x1 + 1, r.x2 - 1, y + 2.9, y + 9.6, 8, 3) do
						deco(box(o.a, y + 2.6, ZB - 0.15, o.a + 1.2, y + 10.2, ZB - 0.05, Color3.fromRGB(236, 228, 210), Enum.Material.Fabric, rf, "Curtain"))
						deco(box(o.b - 1.2, y + 2.6, ZB - 0.15, o.b, y + 10.2, ZB - 0.05, Color3.fromRGB(236, 228, 210), Enum.Material.Fabric, rf, "Curtain"))
					end
				end
			end
			local fn = Furnish[r.id]
			if fn then
				fn(r, rf)
			end
		end
		-- corridor: baseboards, plants, a pinboard per room
		deco(box(X1, y, Z_CORR - 0.56, X2, y + 0.45, Z_CORR - 0.5, COL.Trim, nil, lvl, "Baseboard"))
		for _, x in { -128, -60, 60, 128 } do
			K.pottedPlant(Vector3.new(x, y, -34.2), lvl)
		end
	end

	-- fire-axe case on the ground floor corridor, next to maintenance
	local case = box(40, FLOOR(0) + 3.5, Z_CORR - 0.95, 44, FLOOR(0) + 7, Z_CORR - 0.5, Color3.fromRGB(180, 20, 30), Enum.Material.SmoothPlastic, b, "AxeCase")
	glass(box(40.2, FLOOR(0) + 3.7, Z_CORR - 1.02, 43.8, FLOOR(0) + 6.8, Z_CORR - 0.94, COL.Glass, nil, b, "AxeCaseGlass")).Name = "AxeCaseGlass"
	W.ItemSpawns.AxeCase = CFrame.new(42, FLOOR(0) + 5.3, Z_CORR - 0.72) * CFrame.Angles(0, 0, math.rad(90)) * CFrame.Angles(math.rad(90), 0, 0)
	W.Interact.AxeCase = case

	for _, s in TOWERS do
		stairTower(s, folder("StairTower", b))
	end

	-- façade dressing
	for x = X1 + 5, X2 - 5, 10 do
		if math.abs(x) > 20 then
			deco(box(x - 0.6, 0, Z_SOUTH + 0.4, x + 0.6, 3 * FH, Z_SOUTH + 1.0, Color3.fromRGB(222, 214, 200), nil, b, "Pilaster"))
		end
	end
	for n = 1, 3 do
		deco(box(X1 - 0.6, n * FH - 0.3, Z_SOUTH + 0.4, X2 + 0.6, n * FH + 0.5, Z_SOUTH + 1.1, n == 3 and COL.Crimson or COL.Trim, nil, b, "Belt"))
	end
	box(-12, 9.8, Z_SOUTH + 0.5, 12, 10.5, 8, COL.Trim, Enum.Material.Slate, b, "Canopy")
	for _, x in { -11, 11 } do
		cyl(Vector3.new(x, 4.9, 7), 9.8, 0.9, COL.Cream, Enum.Material.Marble, b).Name = "CanopyPillar"
	end
	local cl = box(-6, 9.45, 2, 6, 9.75, 7, Color3.fromRGB(255, 244, 225), Enum.Material.Neon, b, "CanopyLight")
	deco(cl)
	K.light(cl, 18, 0.9)
	box(-9, 0, Z_SOUTH + 0.5, 9, 0.3, 5, Color3.fromRGB(200, 196, 190), Enum.Material.Concrete, b, "Step")

	-- roof: parapet + fence, benches, the clock, confession spot
	local roof = folder("Roof", b)
	local ry = FLOOR(3)
	for _, z in { Z_SOUTH, Z_NORTH } do
		local gaps = {}
		if z == Z_NORTH then
			for _, s in TOWERS do
				table.insert(gaps, { a = s, b = s + 14, bottom = 3 * FH, top = ry + 7 })
			end
		end
		K.wallX(X1, X2, z, 3 * FH, ry + 1.4, 0.8, gaps, COL.Cream, roof)
		local fence = box(X1, ry + 1.4, z - 0.08, X2, ry + 6, z + 0.08, Color3.fromRGB(70, 90, 80), Enum.Material.DiamondPlate, roof, "Fence")
		fence.Transparency = 0.55
		fence.CastShadow = false
		if z == Z_NORTH then
			fence:Destroy()
		end
	end
	for _, x in { X1, X2 } do
		box(x - 0.4, 3 * FH, Z_NORTH, x + 0.4, ry + 1.4, Z_SOUTH, COL.Cream, nil, roof, "Parapet")
		local fence = box(x - 0.08, ry + 1.4, Z_NORTH, x + 0.08, ry + 6, Z_SOUTH, Color3.fromRGB(70, 90, 80), Enum.Material.DiamondPlate, roof, "Fence")
		fence.Transparency = 0.55
		fence.CastShadow = false
	end
	for _, x in { -110, -60, -30, 30, 100, 120 } do
		K.bench(CFrame.new(x, ry, -4) * CFrame.Angles(0, math.rad(180), 0), roof)
		spot("Roof", Vector3.new(x - 1.5, ry, -1.6), Vector3.new(x - 1.5, ry, 20))
		spot("Roof", Vector3.new(x + 1.5, ry, -1.6), Vector3.new(x + 1.5, ry, 20))
	end
	spot("RoofHaru", Vector3.new(60, ry, -3), Vector3.new(60, ry, 30))
	spot("RoofConfess", Vector3.new(60, ry, -3), Vector3.new(60, ry, 30))
	spot("RoofConfess", Vector3.new(60, ry, -6.5), Vector3.new(60, ry, -3))
	for _, x in { -80, 0, 80 } do
		box(x - 4, ry, -30, x + 4, ry + 3, -24, COL.Metal, Enum.Material.Metal, roof, "ACUnit")
	end
	-- clock block above the entrance
	local tower = folder("ClockTower", roof)
	box(-8, ry, -8, 8, ry + 11, 0.4, COL.Cream, nil, tower, "Tower")
	box(-8.6, ry + 11, -8.6, 8.6, ry + 11.8, 1, COL.Trim, Enum.Material.Slate, tower, "TowerCap")
	local face = cyl(Vector3.new(0, ry + 5.5, 0.5), 0.3, 7, COL.White, nil, tower)
	face.CFrame = CFrame.new(0, ry + 5.5, 0.55) * CFrame.Angles(0, math.rad(90), 0)
	deco(face)
	local hourHand = deco(P(Vector3.new(0.3, 1.9, 0.1), CFrame.new(0, ry + 6.2, 0.75), COL.Black, nil, tower, "HourHand"))
	local minuteHand = deco(P(Vector3.new(0.2, 2.8, 0.1), CFrame.new(0, ry + 6.6, 0.8), COL.Black, nil, tower, "MinuteHand"))
	W.ClockHands = { hour = hourHand, minute = minuteHand, center = Vector3.new(0, ry + 5.5, 0.75) }
	K.sign("KUROBARA ACADEMY", CFrame.lookAt(Vector3.new(0, ry + 1.4, 0.55), Vector3.new(0, ry + 1.4, 40)), Vector2.new(14, 1.6), tower, COL.White, COL.Crimson)
end

-- ── annex: cafeteria ─────────────────────────────────────────────────────────
local function annex()
	local f = folder("Annex")
	local x1, x2, z1, z2, h = 160, 240, -46, 0, 15
	zone("Cafeteria", x1, z1, x2, z2, "#9c7b5e")
	box(x1 - 0.5, 0, z1 - 0.5, x2 + 0.5, 0.5, z2 + 0.5, COL.Concrete, Enum.Material.Concrete, f, "Foundation")
	box(x1, 0.5, z1, x2, 0.55, z2, Color3.fromRGB(226, 220, 210), Enum.Material.SmoothPlastic, f, "Floor")
	local south = K.windowRow(x1 + 2, x2 - 2, 3, 11, 8, 3)
	for i = #south, 1, -1 do
		if south[i].b > 194 and south[i].a < 206 then
			table.remove(south, i)
		end
	end
	table.insert(south, { a = 196, b = 204, bottom = 0.5, top = 9.5 })
	K.wallX(x1 - 0.5, x2 + 0.5, z2, 0, h, 1, south, COL.Cream, f)
	K.wallX(x1 - 0.5, x2 + 0.5, z1, 0, h, 1, K.windowRow(x1 + 2, x2 - 2, 8, 12, 6, 6), COL.Cream, f)
	K.wallZ(z1, z2, x1, 0, h, 1, { { a = -26, b = -20, bottom = 0.5, top = 9 } }, COL.Cream, f)
	K.wallZ(z1, z2, x2, 0, h, 1, K.windowRow(z1 + 2, z2 - 2, 3, 11, 8, 4), COL.Cream, f)
	box(x1 - 1, h, z1 - 1, x2 + 1, h + 1, z2 + 1, COL.RoofSlab, Enum.Material.Concrete, f, "Roof")
	deco(box(x1 - 1, h - 1, z2 + 0.4, x2 + 1, h, z2 + 1.1, COL.Crimson, nil, f, "Belt"))
	K.sign("CAFETERIA", CFrame.lookAt(Vector3.new(200, 11.5, z2 + 0.65), Vector3.new(200, 11.5, 30)), Vector2.new(10, 1.6), f, COL.White, COL.Crimson)
	for i = 0, 3 do
		local z = -36 + i * 9
		box(172, 2.05, z - 1.5, 226, 2.25, z + 1.5, COL.LightWood, Enum.Material.Wood, f, "LunchTable")
		for _, lx in { 176, 199, 222 } do
			box(lx - 0.4, 0.55, z - 0.4, lx + 0.4, 2.05, z + 0.4, COL.Metal, nil, f, "TableLeg")
		end
		for _, dz in { -2.6, 2.6 } do
			deco(box(172, 0.95, z + dz - 0.5, 226, 1.1, z + dz + 0.5, COL.Wood, nil, f, "LunchBench"))
			for k = 0, 3 do
				spot("Cafeteria", Vector3.new(180 + k * 13, 0.55, z + dz * 1.5), Vector3.new(180 + k * 13, 0.55, z))
			end
		end
	end
	box(x1 + 4, 0.55, z1 + 0.5, x2 - 30, 2.9, z1 + 3, COL.White, nil, f, "Counter")
	box(x1 + 3.9, 2.9, z1 + 0.4, x2 - 29.9, 3.05, z1 + 3.1, COL.Metal, Enum.Material.Metal, f, "CounterTop")
	deco(box(x1 + 20, 3.05, z1 + 0.8, x1 + 26, 3.15, z1 + 2.6, COL.Black, nil, f, "Grill"))
	W.ItemSpawns.Cafeteria = CFrame.new(x1 + 12, 3.15, z1 + 1.8) * CFrame.Angles(0, math.rad(90), math.rad(90))
	K.sink(x1 + 35, 0.55, z1 + 1.3, Vector3.new(0, 0, 1), f)
	K.sign("TODAY · CURRY · MELON BREAD · UDON", CFrame.lookAt(Vector3.new(x1 + 22, 8, z1 + 0.6), Vector3.new(x1 + 22, 8, 0)), Vector2.new(16, 1.4), f, COL.White, COL.Black)
	for i = 0, 2 do
		K.vending(CFrame.lookAt(Vector3.new(x2 - 2, 0.55, -40 + i * 4), Vector3.new(x2 - 10, 0.55, -40 + i * 4)), i == 1 and COL.Crimson or Color3.fromRGB(30, 80, 160), f)
	end
	for x = x1 + 10, x2 - 10, 20 do
		for _, z in { -36, -14 } do
			K.ceilingLight(x, h, z, f)
		end
	end
end

-- ── sports hall + changing rooms ─────────────────────────────────────────────
local function gym()
	local f = folder("Gym")
	local x1, x2, z1, z2, h = 30, 150, -200, -130, 36
	zone("Sports Hall", x1, z1, x2, z2, "#7d8fa3")
	box(x1 - 0.5, 0, z1 - 0.5, x2 + 0.5, 0.5, z2 + 0.5, COL.Concrete, Enum.Material.Concrete, f, "Foundation")
	box(x1, 0.5, z1, x2, 0.55, z2, Color3.fromRGB(196, 150, 96), Enum.Material.WoodPlanks, f, "Court")
	local hi = K.windowRow(x1 + 4, x2 - 4, 22, 31, 10, 6)
	local south = table.clone(hi)
	table.insert(south, { a = 85, b = 95, bottom = 0.5, top = 12 })
	K.wallX(x1 - 0.5, x2 + 0.5, z2, 0, h, 1, south, COL.Cream, f)
	K.wallX(x1 - 0.5, x2 + 0.5, z1, 0, h, 1, table.clone(hi), COL.Cream, f)
	K.wallZ(z1, z2, x1, 0, h, 1, K.windowRow(z1 + 4, z2 - 4, 22, 31, 10, 6), COL.Cream, f)
	K.wallZ(z1, z2, x2, 0, h, 1, K.windowRow(z1 + 4, z2 - 4, 22, 31, 10, 6), COL.Cream, f)
	box(x1 - 1, h, z1 - 1, x2 + 1, h + 1.2, z2 + 1, COL.RoofSlab, Enum.Material.Concrete, f, "Roof")
	deco(box(x1 - 1, h - 2, z2 + 0.4, x2 + 1, h, z2 + 1.1, COL.Crimson, nil, f, "Belt"))
	K.sign("SPORTS HALL", CFrame.lookAt(Vector3.new(90, 16, z2 + 0.65), Vector3.new(90, 16, 0)), Vector2.new(12, 1.8), f, COL.White, COL.Crimson)
	-- court lines + hoops
	for _, z in { z1 + 6, z2 - 6 } do
		deco(box(x1 + 6, 0.55, z - 0.15, x2 - 6, 0.57, z + 0.15, COL.White, nil, f, "Line"))
	end
	for _, x in { x1 + 6, (x1 + x2) / 2, x2 - 6 } do
		deco(box(x - 0.15, 0.55, z1 + 6, x + 0.15, 0.57, z2 - 6, COL.White, nil, f, "Line"))
	end
	for _, x in { x1 + 3, x2 - 3 } do
		local dir = x < 90 and 1 or -1
		box(x - 0.2, 0.55, -167, x + 0.2, 14, -163, COL.Metal, Enum.Material.Metal, f, "HoopPost")
		box(x + dir * 1.2 - 0.15, 11, -168, x + dir * 1.2 + 0.15, 14.5, -162, COL.White, nil, f, "Backboard")
		local rim = cyl(Vector3.new(x + dir * 2.6, 10.6, -165), 0.15, 2.4, Color3.fromRGB(240, 100, 30), Enum.Material.Metal, f)
		deco(rim)
	end
	-- stage + equipment room
	box(x1 + 30, 0.55, z1 + 0.5, x2 - 30, 4, z1 + 12, COL.DarkWood, Enum.Material.WoodPlanks, f, "Stage")
	deco(box(x1 + 30, 4, z1 + 0.6, x2 - 30, 24, z1 + 0.9, COL.Crimson, Enum.Material.Fabric, f, "StageCurtain"))
	K.wallX(x2 - 16, x2, z1 + 16, 0.5, 12, 0.6, { { a = x2 - 12, b = x2 - 7, bottom = 0.5, top = 8 } }, COL.Wall, f)
	K.wallZ(z1, z1 + 16, x2 - 16, 0.5, 12, 0.6, {}, COL.Wall, f)
	box(x2 - 10, 0.55, z1 + 1, x2 - 1, 4, z1 + 5, COL.DarkWood, Enum.Material.Wood, f, "BatRack")
	W.ItemSpawns.Gym = CFrame.new(x2 - 5.5, 4.4, z1 + 3) * CFrame.Angles(0, 0, math.rad(90))
	for _ = 1, 8 do
		deco(ball(Vector3.new(x2 - 14 + math.random() * 10, 1.2, z1 + 8 + math.random() * 6), 1.2, Color3.fromRGB(230, 110, 40), nil, f))
	end
	-- bleachers along the south wall
	for i = 0, 3 do
		box(x1 + 8, 0.55, z2 - 4 - i * 2.5, x1 + 70, 1.6 + i * 1.6, z2 - 1.5 - i * 2.5, Color3.fromRGB(160, 120, 80), Enum.Material.Wood, f, "Bleacher")
	end
	for x = x1 + 15, x2 - 15, 30 do
		for _, z in { -185, -145 } do
			local gl = box(x - 4, h - 0.6, z - 1.5, x + 4, h - 0.3, z + 1.5, Color3.fromRGB(255, 250, 240), Enum.Material.Neon, f, "GymLight")
			deco(gl)
			K.light(gl, 30, 1.2)
		end
	end
	ring("Gym", Vector3.new(80, 0.55, -165), 12, 6, true)

	-- changing rooms (lockers + showers) south-west of the hall
	local c = folder("ChangingRooms", f)
	local cx1, cx2, cz1, cz2 = 30, 80, -128, -104
	zone("Changing", cx1, cz1, cx2, cz2, "#6f8aa0")
	box(cx1 - 0.5, 0, cz1 - 0.5, cx2 + 0.5, 0.5, cz2 + 0.5, COL.Concrete, Enum.Material.Concrete, c, "Foundation")
	box(cx1, 0.5, cz1, cx2, 0.55, cz2, Color3.fromRGB(200, 214, 222), Enum.Material.SmoothPlastic, c, "Floor")
	K.wallX(cx1 - 0.5, cx2 + 0.5, cz2, 0, 13, 1, { { a = 52, b = 58, bottom = 0.5, top = 8.5 }, { a = 34, b = 46, bottom = 9, top = 11.5, glass = true }, { a = 64, b = 76, bottom = 9, top = 11.5, glass = true } }, COL.Cream, c)
	K.wallX(cx1 - 0.5, cx2 + 0.5, cz1, 0, 13, 1, {}, COL.Cream, c)
	K.wallZ(cz1, cz2, cx1, 0, 13, 1, {}, COL.Cream, c)
	K.wallZ(cz1, cz2, cx2, 0, 13, 1, {}, COL.Cream, c)
	box(cx1 - 1, 13, cz1 - 1, cx2 + 1, 13.8, cz2 + 1, COL.RoofSlab, Enum.Material.Concrete, c, "Roof")
	K.sign("CHANGING ROOMS", CFrame.lookAt(Vector3.new(55, 10.2, cz2 + 0.65), Vector3.new(55, 10.2, 0)), Vector2.new(9, 1.2), c)
	for i = 0, 9 do
		box(cx1 + 14 + i * 2.2, 0.55, cz1 + 0.6, cx1 + 16 + i * 2.2, 8, cz1 + 2.4, Color3.fromRGB(70, 90, 120), Enum.Material.Metal, c, "Locker")
	end
	deco(box(cx1 + 16, 0.95, cz1 + 7, cx1 + 34, 1.15, cz1 + 8.2, COL.Wood, Enum.Material.Wood, c, "LockerBench"))
	for i = 0, 3 do
		K.shower(CFrame.lookAt(Vector3.new(cx1 + 2.4, 0.55, cz1 + 3 + i * 4.4), Vector3.new(cx1 + 10, 0.55, cz1 + 3 + i * 4.4)), c)
	end
	K.ceilingLight(45, 13, -116, c)
	K.ceilingLight(65, 13, -116, c)
end

-- ── outdoor pool ─────────────────────────────────────────────────────────────
local function pool()
	local f = folder("Pool")
	local x1, x2, z1, z2 = 170, 260, -300, -215
	local wx1, wx2, wz1, wz2 = 182, 248, -284, -232
	zone("Pool", x1, z1, x2, z2, "#4aa3d6")
	-- deck around the basin
	box(x1, 0, z1, x2, 0.6, wz1, COL.Sidewalk, Enum.Material.Concrete, f, "Deck")
	box(x1, 0, wz2, x2, 0.6, z2, COL.Sidewalk, Enum.Material.Concrete, f, "Deck")
	box(x1, 0, wz1, wx1, 0.6, wz2, COL.Sidewalk, Enum.Material.Concrete, f, "Deck")
	box(wx2, 0, wz1, x2, 0.6, wz2, COL.Sidewalk, Enum.Material.Concrete, f, "Deck")
	-- basin
	local tile = Color3.fromRGB(120, 190, 220)
	box(wx1, -7, wz1, wx2, -6.5, wz2, tile, Enum.Material.SmoothPlastic, f, "PoolFloor")
	box(wx1 - 0.5, -7, wz1 - 0.5, wx2 + 0.5, 0.6, wz1, tile, nil, f, "PoolWall")
	box(wx1 - 0.5, -7, wz2, wx2 + 0.5, 0.6, wz2 + 0.5, tile, nil, f, "PoolWall")
	box(wx1 - 0.5, -7, wz1, wx1, 0.6, wz2, tile, nil, f, "PoolWall")
	box(wx2, -7, wz1, wx2 + 0.5, 0.6, wz2, tile, nil, f, "PoolWall")
	local filled = pcall(function()
		workspace.Terrain:FillBlock(CFrame.new((wx1 + wx2) / 2, -3.3, (wz1 + wz2) / 2), Vector3.new(wx2 - wx1, 6.4, wz2 - wz1), Enum.Material.Water)
	end)
	if not filled then
		local water = box(wx1, -6.5, wz1, wx2, -0.2, wz2, COL.Water, Enum.Material.Glass, f, "Water")
		water.Transparency = 0.35
		water.CanCollide = false
		water.CanQuery = false
	end
	for i = 1, 5 do
		local z = wz1 + i * (wz2 - wz1) / 6
		deco(box(wx1, -0.1, z - 0.12, wx2, 0.1, z + 0.12, i % 2 == 0 and Color3.fromRGB(240, 60, 60) or COL.White, nil, f, "LaneRope"))
		box(wx1 - 2.5, 0.6, z - 1, wx1 - 0.6, 2.2, z + 1, COL.White, nil, f, "StartingBlock")
	end
	local fenceCol = Color3.fromRGB(70, 90, 80)
	for _, s in { { x1, z1, x2, z1 }, { x1, z2, x2, z2 } } do
		local fz = s[2]
		local fence = box(s[1], 0.6, fz - 0.08, s[3], 8, fz + 0.08, fenceCol, Enum.Material.DiamondPlate, f, "Fence")
		fence.Transparency = 0.55
	end
	for _, x in { x1, x2 } do
		local fence = box(x - 0.08, 0.6, z1, x + 0.08, 8, z2 - 10, fenceCol, Enum.Material.DiamondPlate, f, "Fence")
		fence.Transparency = 0.55
	end
	box(x1 + 2, 0.6, z2 - 10, x1 + 20, 10, z2 - 2, COL.Cream, nil, f, "PoolHut")
	K.sign("POOL", CFrame.lookAt(Vector3.new(x1 + 11, 8, z2 - 1.9), Vector3.new(x1 + 11, 8, 0)), Vector2.new(5, 1.2), f)
	spot("Pool", Vector3.new(215, 0.6, z2 - 6), Vector3.new(215, 0.6, wz2))
	spot("Pool", Vector3.new(x2 - 6, 0.6, -258), Vector3.new(wx2, 0.6, -258))
end

-- ── track & field ────────────────────────────────────────────────────────────
local function field()
	local f = folder("Field")
	local x1, x2, z1, z2 = -250, 10, -320, -130
	zone("Track & Field", x1, z1, x2, z2, "#6e9a5a")
	local trackCol = Color3.fromRGB(176, 104, 80)
	box(x1, 0, z1, x2, 0.1, z1 + 14, trackCol, Enum.Material.Ground, f, "Track")
	box(x1, 0, z2 - 14, x2, 0.1, z2, trackCol, Enum.Material.Ground, f, "Track")
	box(x1, 0, z1 + 14, x1 + 14, 0.1, z2 - 14, trackCol, Enum.Material.Ground, f, "Track")
	box(x2 - 14, 0, z1 + 14, x2, 0.1, z2 - 14, trackCol, Enum.Material.Ground, f, "Track")
	for k = 1, 3 do
		deco(box(x1 + k * 3.5, 0.1, z1 + 0.5, x1 + k * 3.5 + 0.2, 0.12, z2 - 0.5, COL.White, nil, f, "LaneLine"))
		deco(box(x1 + 0.5, 0.1, z2 - k * 3.5, x2 - 0.5, 0.12, z2 - k * 3.5 + 0.2, COL.White, nil, f, "LaneLine"))
	end
	table.insert(W.Lawns, { x1 + 14, z1 + 14, x2 - 14, z2 - 14 })
	local cx = (x1 + x2) / 2
	for _, z in { z1 + 20, z2 - 20 } do
		local dir = z < -225 and 1 or -1
		box(cx - 12, 0, z - 0.25, cx - 11.5, 8, z + 0.25, COL.White, nil, f, "GoalPost")
		box(cx + 11.5, 0, z - 0.25, cx + 12, 8, z + 0.25, COL.White, nil, f, "GoalPost")
		box(cx - 12, 8, z - 0.25, cx + 12, 8.5, z + 0.25, COL.White, nil, f, "GoalBar")
		local net = box(cx - 11.5, 0, z + dir * 0.3, cx + 11.5, 8, z + dir * 6, COL.White, Enum.Material.DiamondPlate, f, "GoalNet")
		net.Transparency = 0.75
		net.CanCollide = false
	end
	for i = 0, 3 do
		box(12, 0, -300 + i * 0, 14 + i * 3, 1.5 + i * 1.5, -160, Color3.fromRGB(150, 150, 160), Enum.Material.Concrete, f, "Bleacher")
	end
	ring("Field", Vector3.new(cx, 0.1, -225), 26, 8, true)
end

-- ── garden + greenhouse ──────────────────────────────────────────────────────
local function garden()
	local f = folder("Garden")
	zone("Garden", -255, -120, -160, -40, "#5f8a4e")
	for i = 0, 4 do
		local z = -112 + i * 14
		box(-210, 0, z, -165, 0.7, z + 7, COL.Soil, Enum.Material.Ground, f, "Bed")
		for k = 0, 9 do
			deco(cyl(Vector3.new(-207 + k * 4.5, 0.9, z + 3.5), 0.8, 0.15, COL.Leaf, nil, f))
			deco(ball(Vector3.new(-207 + k * 4.5, 1.4, z + 3.5), 1.1, Color3.fromHSV(math.random(), 0.6, 1), Enum.Material.SmoothPlastic, f))
		end
		spot("Garden", Vector3.new(-200 + i * 7, 0.1, z + 9.5), Vector3.new(-200 + i * 7, 0.1, z + 3.5))
	end
	local dig = box(-200, 0, -60, -186, 0.3, -46, Color3.fromRGB(110, 76, 50), Enum.Material.Ground, f, "DigPlot")
	W.Interact.DigPlot = dig
	K.sign("COMPOST · DO NOT DIG", CFrame.lookAt(Vector3.new(-193, 2.5, -45.5), Vector3.new(-193, 2.5, -30)), Vector2.new(5, 0.9), f, COL.White, COL.DarkWood)
	-- greenhouse
	local gh = folder("Greenhouse", f)
	local gx1, gx2, gz1, gz2 = -255, -222, -112, -86
	zone("Greenhouse", gx1, gz1, gx2, gz2, "#a7d6b0")
	box(gx1, 0, gz1, gx2, 0.4, gz2, COL.Concrete, Enum.Material.Concrete, gh, "GHFloor")
	local function pane(p: BasePart)
		glass(p)
		p.Transparency = 0.45
		p.CanQuery = true -- frosted: nobody sees Mirei in here
		p.Color = Color3.fromRGB(215, 235, 225)
	end
	pane(box(gx1, 0.4, gz1 - 0.1, gx2, 11, gz1 + 0.1, COL.Glass, nil, gh, "GHWall"))
	pane(box(gx1, 0.4, gz2 - 0.1, gx2, 11, gz2 + 0.1, COL.Glass, nil, gh, "GHWall"))
	pane(box(gx1 - 0.1, 0.4, gz1, gx1 + 0.1, 11, gz2, COL.Glass, nil, gh, "GHWall"))
	pane(box(gx2 - 0.1, 0.4, gz1, gx2 + 0.1, 11, -102, COL.Glass, nil, gh, "GHWall"))
	pane(box(gx2 - 0.1, 0.4, -96, gx2 + 0.1, 11, gz2, COL.Glass, nil, gh, "GHWall"))
	pane(box(gx2 - 0.1, 8.5, -102, gx2 + 0.1, 11, -96, COL.Glass, nil, gh, "GHWall"))
	pane(box(gx1, 11, gz1, gx2, 11.3, gz2, COL.Glass, nil, gh, "GHRoof"))
	for x = gx1, gx2, 5.5 do
		deco(box(x - 0.15, 0.4, gz1 - 0.15, x + 0.15, 11.3, gz1 + 0.15, COL.White, nil, gh, "Frame"))
		deco(box(x - 0.15, 0.4, gz2 - 0.15, x + 0.15, 11.3, gz2 + 0.15, COL.White, nil, gh, "Frame"))
	end
	box(gx1 + 1, 0.4, gz1 + 1, gx1 + 4, 2.9, gz2 - 1, COL.DarkWood, Enum.Material.Wood, gh, "PlantTable")
	for z = gz1 + 2, gz2 - 2, 2.5 do
		deco(ball(Vector3.new(gx1 + 2.5, 3.5, z), 1.4, Color3.fromRGB(240, 240, 255), Enum.Material.SmoothPlastic, gh))
		deco(cyl(Vector3.new(gx1 + 2.5, 3.1, z), 0.6, 1, Color3.fromRGB(150, 90, 60), Enum.Material.Slate, gh))
	end
	box(gx2 - 6, 0.4, gz1 + 1, gx2 - 1, 2.9, gz1 + 4, COL.DarkWood, Enum.Material.Wood, gh, "ToolTable")
	W.ItemSpawns.Greenhouse = CFrame.new(gx2 - 3.5, 3.15, gz1 + 2.5) * CFrame.Angles(0, math.rad(20), math.rad(90))
	W.ItemSpawns.Greenhouse2 = CFrame.new(gx2 - 1.2, 2.6, gz1 + 7) * CFrame.Angles(math.rad(-80), 0, 0)
	spot("Greenhouse", Vector3.new(gx1 + 7, 0.4, -100), Vector3.new(gx1 + 2, 0.4, -100))
	spot("Greenhouse", Vector3.new(gx1 + 7, 0.4, -94), Vector3.new(gx1 + 2, 0.4, -94))
end

-- ── incinerator + dumpster ───────────────────────────────────────────────────
local function backyard()
	local f = folder("Backyard")
	zone("Incinerator", 168, -100, 205, -70, "#7a4a3a")
	box(170, 0, -98, 186, 9, -86, COL.Brick, Enum.Material.Brick, f, "IncineratorBody")
	local chimney = box(176, 9, -95, 180, 30, -91, COL.Brick, Enum.Material.Brick, f, "Chimney")
	local mouth = box(173, 1.5, -86.2, 183, 6, -85.9, Color3.fromRGB(255, 120, 30), Enum.Material.Neon, f, "IncineratorMouth")
	local fire = Instance.new("PointLight")
	fire.Color = Color3.fromRGB(255, 130, 50)
	fire.Range = 16
	fire.Brightness = 1.5
	fire.Parent = mouth
	local smoke = Instance.new("ParticleEmitter")
	smoke.Name = "Smoke"
	smoke.Color = ColorSequence.new(Color3.fromRGB(70, 70, 74))
	smoke.Transparency = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0.5), NumberSequenceKeypoint.new(1, 1) })
	smoke.Size = NumberSequence.new({ NumberSequenceKeypoint.new(0, 2.5), NumberSequenceKeypoint.new(1, 11) })
	smoke.Lifetime = NumberRange.new(5, 8)
	smoke.Rate = 4
	smoke.Speed = NumberRange.new(3, 5)
	smoke.EmissionDirection = Enum.NormalId.Top
	smoke.Acceleration = Vector3.new(1.5, 0.5, 0)
	smoke.Parent = chimney
	W.Interact.Incinerator = mouth
	local dump = box(190, 0, -96, 204, 6, -88, Color3.fromRGB(40, 90, 60), Enum.Material.Metal, f, "Dumpster")
	box(189.8, 6, -96.2, 204.2, 6.4, -87.8, Color3.fromRGB(30, 70, 46), Enum.Material.Metal, f, "DumpsterLid")
	W.Interact.Dumpster = dump
	box(165, 0, -105, 210, 0.08, -66, COL.Concrete, Enum.Material.Concrete, f, "Yard")
end

-- ── courtyard, gate, perimeter ───────────────────────────────────────────────
local function courtyard()
	local g = folder("Courtyard")
	zone("Courtyard", -255, 2, 255, 118, "#7aa45f")
	-- paths
	box(-7, 0, 1, 7, 0.1, 121, Color3.fromRGB(205, 196, 186), Enum.Material.Pavement, g, "MainPath")
	box(-170, 0, 56, 170, 0.1, 68, Color3.fromRGB(205, 196, 186), Enum.Material.Pavement, g, "CrossPath")
	box(158, 0, 0, 170, 0.1, 68, Color3.fromRGB(205, 196, 186), Enum.Material.Pavement, g, "AnnexPath")
	box(-170, 0, -40, -158, 0.1, 68, Color3.fromRGB(205, 196, 186), Enum.Material.Pavement, g, "GardenPath")
	box(-150, 0, -40, 160, 0.06, -37, COL.Sidewalk, Enum.Material.Concrete, g, "BackWalk")
	box(-262, 0, -130, 262, 0.06, -100, COL.Sidewalk, Enum.Material.Concrete, g, "BackLot")
	-- plaza + fountain
	local plaza = cyl(Vector3.new(0, 0.06, 62), 0.12, 46, Color3.fromRGB(200, 192, 182), Enum.Material.Pavement, g)
	plaza.Name = "Plaza"
	cyl(Vector3.new(0, 1, 62), 2, 20, Color3.fromRGB(170, 170, 176), Enum.Material.Marble, g).Name = "FountainBasin"
	local water = cyl(Vector3.new(0, 2.02, 62), 0.1, 18.4, COL.Water, Enum.Material.Glass, g)
	water.Transparency = 0.25
	water.Name = "FountainWater"
	cyl(Vector3.new(0, 4, 62), 6, 2, Color3.fromRGB(180, 180, 186), Enum.Material.Marble, g)
	local top = cyl(Vector3.new(0, 7.2, 62), 0.7, 6, Color3.fromRGB(180, 180, 186), Enum.Material.Marble, g)
	local spray = Instance.new("ParticleEmitter")
	spray.Color = ColorSequence.new(Color3.fromRGB(210, 235, 255))
	spray.Transparency = NumberSequence.new(0.3, 1)
	spray.Size = NumberSequence.new(0.3, 0.05)
	spray.Lifetime = NumberRange.new(0.9, 1.2)
	spray.Rate = 110
	spray.Speed = NumberRange.new(8, 10)
	spray.SpreadAngle = Vector2.new(18, 18)
	spray.Acceleration = Vector3.new(0, -30, 0)
	spray.EmissionDirection = Enum.NormalId.Right
	spray.Parent = top
	ring("Fountain", Vector3.new(0, 0.1, 62), 13.5, 8, true)
	for i, a in { 45, 135, 225, 315 } do
		local r = math.rad(a)
		local pos = Vector3.new(math.cos(r) * 19, 0.1, 62 + math.sin(r) * 19)
		K.bench(CFrame.lookAt(pos, Vector3.new(0, 0.1, 62)), g)
		if i == 1 then
			spot("FountainBench", pos + (Vector3.new(0, 0.1, 62) - pos).Unit * 1.8, Vector3.new(0, 0.1, 62))
		end
	end
	-- rose beds, hedges, trees, lamps, benches
	for _, b in { { -70, 22, -30, 36 }, { 30, 22, 70, 36 }, { -70, 84, -30, 100 }, { 30, 84, 70, 100 }, { -140, 26, -100, 40 }, { 100, 26, 140, 40 } } do
		K.roseBed(b[1], b[2], b[3], b[4], g)
		local cx, cz = (b[1] + b[3]) / 2, (b[2] + b[4]) / 2
		spot("Courtyard", Vector3.new(cx - 8, 0.1, b[4] + 3), Vector3.new(cx - 8, 0.1, cz))
		spot("Courtyard", Vector3.new(cx + 8, 0.1, b[2] - 3), Vector3.new(cx + 8, 0.1, cz))
	end
	for _, side in { -1, 1 } do
		box(side < 0 and -138 or 22, 0, 2, side < 0 and -22 or 138, 2.6, 4.5, Color3.fromRGB(62, 104, 58), Enum.Material.Grass, g, "Hedge")
	end
	for _, p in { { -95, 50 }, { 95, 50 }, { -95, 80 }, { 95, 80 }, { -200, 30 }, { 200, 30 }, { -200, 95 }, { 200, 95 }, { -40, 112 }, { 40, 112 } } do
		K.tree(p[1], p[2], "Blossom", g)
	end
	for _, p in { { -235, 10 }, { 235, 10 }, { -120, -60 }, { 120, -60 }, { -240, -150 }, { 240, -120 }, { 20, -60 } } do
		K.tree(p[1], p[2], "Green", g)
	end
	for _, x in { -150, -120, 120, 150 } do
		K.bench(CFrame.new(x, 0.1, 74) * CFrame.Angles(0, math.rad(180), 0), g)
		spot("Courtyard", Vector3.new(x, 0.1, 76.5), Vector3.new(x, 0.1, 90))
	end
	for _, p in { { -12, 20 }, { 12, 20 }, { -12, 100 }, { 12, 100 }, { -60, 62 }, { 60, 62 }, { -130, 62 }, { 130, 62 } } do
		K.lamp(Vector3.new(p[1], 0.1, p[2]), g)
	end
	for z = 16, 108, 12 do
		for _, x in { -9, 9 } do
			box(x - 0.9, 0, z - 2.5, x + 0.9, 1, z + 2.5, COL.Trim, Enum.Material.Slate, g, "PathPlanter")
			for k = -1, 1 do
				deco(ball(Vector3.new(x, 1.3, z + k * 1.4), 0.9, Color3.fromHSV(math.random(), 0.55, 1), nil, g))
			end
		end
	end
	table.insert(W.Lawns, { -255, 6, -24, 54 })
	table.insert(W.Lawns, { -255, 70, -24, 116 })
	table.insert(W.Lawns, { 24, 6, 155, 54 })
	table.insert(W.Lawns, { 24, 70, 255, 116 })
	-- bike parking
	local bikes = folder("BikeParking", g)
	box(-250, 9, 80, -205, 9.5, 112, COL.Metal, Enum.Material.Metal, bikes, "ShelterRoof")
	for _, x in { -249, -206 } do
		for _, z in { 81, 96, 111 } do
			box(x - 0.25, 0, z - 0.25, x + 0.25, 9, z + 0.25, COL.Metal, Enum.Material.Metal, bikes, "Post")
		end
	end
	for x = -246, -210, 3 do
		deco(box(x - 0.1, 0.9, 88, x + 0.1, 3, 94, Color3.fromHSV(math.random(), 0.5, 0.7), Enum.Material.Metal, bikes, "Bike"))
	end

	-- gate + walls
	local gate = folder("Gate", g)
	K.wallX(-262, -14, 120, 0, 7, 1, {}, COL.Cream, gate)
	K.wallX(14, 262, 120, 0, 7, 1, {}, COL.Cream, gate)
	K.wallZ(-330, 120, -262, 0, 7, 1, {}, COL.Cream, gate)
	K.wallZ(-330, 120, 262, 0, 7, 1, {}, COL.Cream, gate)
	K.wallX(-262, 262, -330, 0, 7, 1, {}, COL.Cream, gate)
	for _, x in { -15.5, 15.5 } do
		box(x - 1.5, 0, 118.5, x + 1.5, 13, 121.5, COL.Trim, Enum.Material.Slate, gate, "GatePillar")
	end
	box(-17, 13, 119.3, 17, 15.4, 120.7, COL.Trim, Enum.Material.Slate, gate, "GateBeam")
	K.sign("KUROBARA ACADEMY", CFrame.lookAt(Vector3.new(0, 14.2, 120.8), Vector3.new(0, 14.2, 140)), Vector2.new(22, 2), gate, COL.White, COL.Crimson)
	K.sign("KUROBARA ACADEMY", CFrame.lookAt(Vector3.new(0, 14.2, 119.2), Vector3.new(0, 14.2, 100)), Vector2.new(22, 2), gate, COL.White, COL.Crimson)
	for x = -9, 9, 4.5 do
		spot("Gate", Vector3.new(x, 0.1, 112), Vector3.new(x, 0.1, 90))
	end
	local gateZone = box(-13, 0, 115, 13, 7, 123, COL.White, nil, gate, "GateZone")
	gateZone.Transparency = 1
	deco(gateZone)
	W.Interact.Gate = gateZone
end

function School.Build()
	mainBuilding()
	annex()
	gym()
	pool()
	field()
	garden()
	backyard()
	courtyard()
end

return School
