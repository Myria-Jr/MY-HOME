-- KUROBARA · World builder
-- Builds the campus (BuildSchool) and the town (BuildTown) at server start, plus lighting,
-- time-of-day grading, terrain grass and the phone's minimap data.
-- Tip: press Play once, copy Workspace.Map, stop, paste it → edit the map by hand
-- (then set Builder.UseExistingMap = true so the script doesn't build a second copy).

local Lighting = game:GetService("Lighting")
local HttpService = game:GetService("HttpService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local K = require(script.Parent.BuildKit)
local School = require(script.Parent.BuildSchool)
local Town = require(script.Parent.BuildTown)

local Builder = {}
Builder.UseExistingMap = false
Builder.World = K.World
local World = K.World

-- world square shown on the phone map: x from -300, z from -340, 900 studs wide
local MAP_BOUNDS = Vector3.new(-300, -340, 900)

-- ── ground + real grass ──────────────────────────────────────────────────────
local function ground()
	local g = K.folder("Ground")
	K.box(-900, -2.05, -900, 1200, -0.05, 1200, K.COL.Grass, Enum.Material.Grass, g, "Ground")
end

local function terrainLawns()
	local terrain = workspace.Terrain
	for _, l in World.Lawns do
		local x1, z1, x2, z2 = l[1], l[2], l[3], l[4]
		local ok = pcall(function()
			terrain:FillBlock(CFrame.new((x1 + x2) / 2, -2, (z1 + z2) / 2), Vector3.new(x2 - x1, 4, z2 - z1), Enum.Material.Grass)
		end)
		if not ok then
			K.box(x1, -0.05, z1, x2, 0, z2, K.COL.Grass, Enum.Material.Grass, K.Map, "Lawn")
		end
	end
	pcall(function()
		terrain:SetMaterialColor(Enum.Material.Grass, Color3.fromRGB(96, 132, 72))
		terrain.Decoration = true
	end)
end

-- ── time of day: light, haze and grade follow the clock ──────────────────────
local TOD = {
	{ t = 7, amb = Color3.fromRGB(72, 62, 82), out = Color3.fromRGB(150, 128, 140), bright = 1.8, atm = Color3.fromRGB(226, 192, 204), decay = Color3.fromRGB(146, 104, 124), haze = 2.1, tint = Color3.fromRGB(255, 236, 230), exp = 0.15, rays = 0.1 },
	{ t = 9.5, amb = Color3.fromRGB(62, 60, 72), out = Color3.fromRGB(128, 124, 138), bright = 2.3, atm = Color3.fromRGB(205, 200, 216), decay = Color3.fromRGB(112, 96, 126), haze = 1.3, tint = Color3.fromRGB(252, 250, 255), exp = 0.05, rays = 0.05 },
	{ t = 14.5, amb = Color3.fromRGB(64, 60, 70), out = Color3.fromRGB(134, 126, 134), bright = 2.4, atm = Color3.fromRGB(210, 200, 210), decay = Color3.fromRGB(120, 96, 116), haze = 1.3, tint = Color3.fromRGB(255, 250, 248), exp = 0.05, rays = 0.05 },
	{ t = 16.6, amb = Color3.fromRGB(82, 62, 70), out = Color3.fromRGB(172, 122, 112), bright = 2.2, atm = Color3.fromRGB(244, 182, 150), decay = Color3.fromRGB(186, 92, 90), haze = 2.2, tint = Color3.fromRGB(255, 226, 206), exp = 0.12, rays = 0.14 },
	{ t = 17.6, amb = Color3.fromRGB(66, 48, 74), out = Color3.fromRGB(140, 92, 124), bright = 1.7, atm = Color3.fromRGB(206, 140, 176), decay = Color3.fromRGB(126, 60, 104), haze = 2.6, tint = Color3.fromRGB(255, 212, 226), exp = 0.06, rays = 0.12 },
	{ t = 18.5, amb = Color3.fromRGB(40, 36, 60), out = Color3.fromRGB(80, 74, 110), bright = 1.0, atm = Color3.fromRGB(120, 110, 160), decay = Color3.fromRGB(60, 50, 90), haze = 2.0, tint = Color3.fromRGB(220, 220, 255), exp = 0, rays = 0.02 },
}

function Builder.TimeOfDay(minutes: number)
	local h = minutes / 60
	local a, b = TOD[1], TOD[#TOD]
	for i = 1, #TOD - 1 do
		if h >= TOD[i].t and h <= TOD[i + 1].t then
			a, b = TOD[i], TOD[i + 1]
			break
		end
	end
	local k = 0
	if b.t > a.t then
		k = math.clamp((h - a.t) / (b.t - a.t), 0, 1)
	elseif h > a.t then
		k = 1
	end
	local function lerp(x: number, y: number)
		return x + (y - x) * k
	end
	Lighting.Ambient = a.amb:Lerp(b.amb, k)
	Lighting.OutdoorAmbient = a.out:Lerp(b.out, k)
	Lighting.Brightness = lerp(a.bright, b.bright)
	Lighting.ExposureCompensation = lerp(a.exp, b.exp)
	local atm = Lighting:FindFirstChild("Atmosphere") :: Atmosphere?
	if atm then
		atm.Color = a.atm:Lerp(b.atm, k)
		atm.Decay = a.decay:Lerp(b.decay, k)
		atm.Haze = lerp(a.haze, b.haze)
	end
	local cc = Lighting:FindFirstChild("Grade") :: ColorCorrectionEffect?
	if cc then
		cc.TintColor = a.tint:Lerp(b.tint, k)
	end
	local rays = Lighting:FindFirstChild("SunRays") :: SunRaysEffect?
	if rays then
		rays.Intensity = lerp(a.rays, b.rays)
	end
	-- the clock above the entrance keeps game time
	local hands = World.ClockHands
	if hands and hands.hour.Parent then
		local function place(part: BasePart, frac: number, len: number, zoff: number)
			local ang = frac * math.pi * 2
			local dir = Vector3.new(math.sin(ang), math.cos(ang), 0)
			part.CFrame = CFrame.new(hands.center + dir * len / 2 + Vector3.new(0, 0, zoff)) * CFrame.Angles(0, 0, -ang)
		end
		place(hands.hour, (h % 12) / 12, 1.9, 0)
		place(hands.minute, (minutes % 60) / 60, 2.8, 0.05)
	end
end

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
	atm.Density = 0.28
	atm.Offset = 0.15
	atm.Glare = 0.25
	local bloom = ensure("BloomEffect", "Bloom") :: BloomEffect
	bloom.Intensity = 0.55
	bloom.Size = 26
	bloom.Threshold = 1.5
	local rays = ensure("SunRaysEffect", "SunRays") :: SunRaysEffect
	rays.Spread = 0.6
	local cc = ensure("ColorCorrectionEffect", "Grade") :: ColorCorrectionEffect
	cc.Contrast = 0.08
	cc.Saturation = 0.05
	local sky = ensure("Sky", "Sky") :: Sky
	sky.SunAngularSize = 14
	sky.MoonAngularSize = 9
	local dof = ensure("DepthOfFieldEffect", "DepthOfField") :: DepthOfFieldEffect
	dof.FarIntensity = 0.1
	dof.FocusDistance = 90
	dof.InFocusRadius = 90
	dof.NearIntensity = 0
	local clouds = workspace.Terrain:FindFirstChildOfClass("Clouds")
	if not clouds then
		clouds = Instance.new("Clouds")
		clouds.Parent = workspace.Terrain
	end
	clouds.Cover = 0.55
	clouds.Density = 0.55
	clouds.Color = Color3.fromRGB(255, 246, 250)
	Builder.TimeOfDay(7.5 * 60)
end

function Builder.Build()
	local existing = workspace:FindFirstChild("Map")
	if existing and Builder.UseExistingMap then
		K.Map = existing :: Folder
	else
		if existing then
			existing:Destroy()
		end
		local map = Instance.new("Folder")
		map.Name = "Map"
		K.Map = map
		ground()
		School.Build()
		Town.Build()
		map.Parent = workspace
		terrainLawns()
	end
	Builder.SetupLighting()
	for _, name in { "NPCs", "Evidence", "Items" } do
		if not workspace:FindFirstChild(name) then
			local f = Instance.new("Folder")
			f.Name = name
			f.Parent = workspace
		end
	end
	-- minimap data for the phone
	pcall(function()
		ReplicatedStorage:SetAttribute("MapZones", HttpService:JSONEncode(World.MapZones))
		ReplicatedStorage:SetAttribute("MapBounds", MAP_BOUNDS)
	end)
	return World
end

return Builder
