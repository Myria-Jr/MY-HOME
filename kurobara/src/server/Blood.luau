-- KUROBARA · Server blood: persistent evidence
--   · organic pools (several overlapping lobes + a wet sheen) that spread and slowly dry brown
--   · bloody footprints when Nyx steps through fresh blood, drips when she's soaked
--   · drag smears, stained clothes, bloody weapons (with red swing trails)
-- The flashy, short-lived stuff (droplets, jets, splats, screen blood) is simulated on each
-- client from Remotes.BloodFX so it stays smooth.

local CollectionService = game:GetService("CollectionService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local Players = game:GetService("Players")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Util = require(Shared.Util)
local Remotes = ReplicatedStorage:WaitForChild("Remotes")

local Blood = {}
local S -- services, set in Init

local poolFolder: Folder
local prints: { BasePart } = {}

function Blood.Init(services)
	S = services
	poolFolder = workspace.Evidence:FindFirstChild("Blood") :: Folder
	if not poolFolder then
		poolFolder = Instance.new("Folder")
		poolFolder.Name = "Blood"
		poolFolder.Parent = workspace.Evidence
	end
end

-- ── Client FX broadcast ──────────────────────────────────────────────────────
function Blood.FX(kind: string, data: { [string]: any })
	Remotes.BloodFX:FireAllClients(kind, data)
end

-- ── Helpers ──────────────────────────────────────────────────────────────────
local groundParams = RaycastParams.new()
groundParams.FilterType = Enum.RaycastFilterType.Exclude
groundParams.CollisionGroup = "BloodRay"

local function groundAt(pos: Vector3): (Vector3?, Vector3?)
	groundParams.FilterDescendantsInstances = { poolFolder }
	local hit = workspace:Raycast(pos + Vector3.new(0, 2, 0), Vector3.new(0, -14, 0), groundParams)
	if hit then
		return hit.Position, hit.Normal
	end
	return nil, nil
end

local function rand(a: number, b: number): number
	return a + math.random() * (b - a)
end

-- a flat ellipse lying on a surface. X = surface normal, Y = length direction.
local function flatPart(name: string, cf: CFrame, length: number, width: number, color: Color3, sphere: boolean): Part
	local p = Instance.new("Part")
	p.Name = name
	p.Anchored = true
	p.CanCollide = false
	p.CanTouch = false
	p.CanQuery = true
	p.CastShadow = false
	p.Material = Enum.Material.SmoothPlastic
	p.Reflectance = 0.14
	p.Color = color
	p.TopSurface = Enum.SurfaceType.Smooth
	p.BottomSurface = Enum.SurfaceType.Smooth
	if sphere then
		Util.mesh(p, Enum.MeshType.Sphere)
		p.Size = Vector3.new(0.04, length, width)
	else
		p.Shape = Enum.PartType.Cylinder
		p.Size = Vector3.new(0.04, length, width)
	end
	p.CFrame = cf
	p.CollisionGroup = "BloodRay"
	return p
end

local function surfaceCF(ground: Vector3, normal: Vector3, along: Vector3?, lift: number): CFrame
	local hint = along or Vector3.new(0.3, 0.1, 0.9)
	local tangent = hint - normal * hint:Dot(normal)
	if tangent.Magnitude < 0.05 then
		tangent = normal:Cross(Vector3.xAxis)
	end
	return CFrame.fromMatrix(ground + normal * lift, normal, tangent.Unit)
end

-- ── Pools ────────────────────────────────────────────────────────────────────
-- Layout of a pool = core disc + irregular lobes + glossy sheen, all scaled by `size`.
local function layout(core: BasePart, size: number, time: number)
	local aspect = core:GetAttribute("Aspect") or 0.9
	Util.tween(core, time, { Size = Vector3.new(0.04, size, size * aspect) }, Enum.EasingStyle.Sine)
	for _, lobe in core:GetChildren() do
		if lobe:IsA("BasePart") then
			local oy, oz, s = lobe:GetAttribute("OY") or 0, lobe:GetAttribute("OZ") or 0, lobe:GetAttribute("S") or 0.5
			local lift = lobe:GetAttribute("Lift") or 0.003
			local target = core.CFrame * CFrame.new(lift, oy * size, oz * size)
			Util.tween(lobe, time, { Size = Vector3.new(0.04, s * size, s * size * (lobe:GetAttribute("A") or 1)), CFrame = target }, Enum.EasingStyle.Sine)
		end
	end
end

local function dry(core: BasePart)
	local fresh = Config.Blood.Base:Lerp(Config.Blood.Dark, math.random() * 0.25)
	core.Color = fresh
	Util.tween(core, 240, { Color = Config.Blood.Dried, Reflectance = 0.03 }, Enum.EasingStyle.Linear)
	for _, lobe in core:GetChildren() do
		if lobe:IsA("BasePart") then
			if lobe.Name == "Sheen" then
				lobe.Transparency = 0.55
				Util.tween(lobe, 200, { Transparency = 1 }, Enum.EasingStyle.Linear)
			else
				lobe.Color = fresh:Lerp(Config.Blood.Fresh, math.random() * 0.4)
				Util.tween(lobe, 240 + math.random() * 60, { Color = Config.Blood.Dried, Reflectance = 0.03 }, Enum.EasingStyle.Linear)
			end
		end
	end
	core:SetAttribute("Born", workspace:GetServerTimeNow())
end

-- Creates (or grows) a pool. size = final diameter in studs.
function Blood.Pool(pos: Vector3, size: number, growTime: number?, opts: { [string]: any }?): Part?
	local gore = Config.Gore()
	size *= gore.PoolScale
	local ground, normal = groundAt(pos)
	if not ground then
		return nil
	end
	normal = normal or Vector3.yAxis
	growTime = growTime or 2.5
	-- merge into a nearby pool instead of stacking
	for _, p in poolFolder:GetChildren() do
		if p.Name == "BloodPool" and p:IsA("BasePart") and (p.Position - ground).Magnitude < math.max(p.Size.Y, 1) * 0.4 then
			local cur = p:GetAttribute("Size") or p.Size.Y
			local target = math.min(math.max(cur, size) + size * 0.25, 16)
			p:SetAttribute("Size", target)
			layout(p, target, growTime)
			dry(p)
			return p
		end
	end
	local base = surfaceCF(ground, normal, nil, 0.02 + math.random() * 0.008) * CFrame.Angles(math.random() * math.pi * 2, 0, 0)
	local core = flatPart("BloodPool", base, 0.3, 0.3, Config.Blood.Base, false)
	core:SetAttribute("Aspect", rand(0.78, 1))
	core:SetAttribute("Size", size)
	if opts and opts.victim then
		core:SetAttribute("Victim", opts.victim)
	end
	-- lobes give the edge an irregular, liquid outline
	local lobes = math.random(4, 7)
	for i = 1, lobes do
		local a = (i / lobes) * math.pi * 2 + rand(-0.4, 0.4)
		local r = rand(0.22, 0.42)
		local lobe = flatPart("Lobe", base, 0.2, 0.2, Config.Blood.Base, false)
		lobe:SetAttribute("OY", math.cos(a) * r)
		lobe:SetAttribute("OZ", math.sin(a) * r)
		lobe:SetAttribute("S", rand(0.32, 0.62))
		lobe:SetAttribute("A", rand(0.7, 1.2))
		lobe:SetAttribute("Lift", 0.002 + i * 0.0015)
		lobe.CanQuery = false
		lobe.Parent = core
	end
	-- tiny satellite drops around the rim
	for _ = 1, math.random(2, 5) do
		local a = rand(0, math.pi * 2)
		local r = rand(0.55, 0.8)
		local drop = flatPart("Lobe", base, 0.1, 0.1, Config.Blood.Base, false)
		drop:SetAttribute("OY", math.cos(a) * r)
		drop:SetAttribute("OZ", math.sin(a) * r)
		drop:SetAttribute("S", rand(0.05, 0.12))
		drop:SetAttribute("Lift", 0.004)
		drop.CanQuery = false
		drop.Parent = core
	end
	local sheen = flatPart("Sheen", base, 0.2, 0.2, Config.Blood.Fresh, false)
	sheen.Material = Enum.Material.Glass
	sheen.Reflectance = 0.35
	sheen.Transparency = 0.55
	sheen.CanQuery = false
	sheen:SetAttribute("OY", rand(-0.12, 0.12))
	sheen:SetAttribute("OZ", rand(-0.12, 0.12))
	sheen:SetAttribute("S", 0.55)
	sheen:SetAttribute("Lift", 0.014)
	sheen.Parent = core
	CollectionService:AddTag(core, "BloodPool")
	core.Parent = poolFolder
	layout(core, size, growTime)
	dry(core)
	return core
end

-- Drag marks: an elongated smear with two streaks either side.
function Blood.Smear(a: Vector3, b: Vector3, width: number)
	local mid = (a + b) / 2
	local ground, normal = groundAt(mid)
	if not ground then
		return
	end
	normal = normal or Vector3.yAxis
	local dir = Util.flat(b - a)
	if dir.Magnitude < 0.2 then
		return
	end
	local len = dir.Magnitude + width * 0.6
	local cf = surfaceCF(ground, normal, dir, 0.018)
	local smear = flatPart("Smear", cf, len, width, Config.Blood.Base:Lerp(Config.Blood.Dark, math.random() * 0.3), true)
	smear.Transparency = rand(0.05, 0.25)
	smear:SetAttribute("Size", len * 0.8)
	CollectionService:AddTag(smear, "BloodPool")
	smear.Parent = poolFolder
	for _, side in { -1, 1 } do
		local streak = flatPart("Streak", cf * CFrame.new(0.002, rand(-0.2, 0.2), side * width * rand(0.42, 0.6)), len * rand(0.6, 1), width * 0.12, Config.Blood.Dark, true)
		streak.CanQuery = false
		streak.Parent = smear
	end
	Util.tween(smear, 260, { Color = Config.Blood.Dried }, Enum.EasingStyle.Linear)
end

-- One bloody shoe print. side = -1 left / 1 right
function Blood.Footprint(pos: Vector3, dir: Vector3, side: number, strength: number)
	local ground, normal = groundAt(pos)
	if not ground or dir.Magnitude < 0.05 then
		return
	end
	normal = normal or Vector3.yAxis
	dir = dir.Unit
	local right = Vector3.new(-dir.Z, 0, dir.X)
	local at = ground + right * side * 0.38
	local cf = surfaceCF(at, normal, dir, 0.016)
	local color = Config.Blood.Base:Lerp(Config.Blood.Dark, 0.2)
	local sole = flatPart("Print", cf * CFrame.new(0, 0.2, 0), 0.72, 0.4, color, true)
	sole.Transparency = 1 - strength * 0.85
	sole:SetAttribute("Size", 0.5)
	local heel = flatPart("Heel", cf * CFrame.new(0.001, -0.42, 0), 0.38, 0.34, color, true)
	heel.Transparency = sole.Transparency
	heel.CanQuery = false
	heel.Parent = sole
	sole.Parent = poolFolder
	table.insert(prints, sole)
	if #prints > 260 then
		local old = table.remove(prints, 1)
		if old and old.Parent then
			old:Destroy()
		end
	end
end

function Blood.Drip(pos: Vector3, size: number?)
	local ground, normal = groundAt(pos)
	if not ground then
		return
	end
	local d = size or rand(0.18, 0.42)
	local p = flatPart("Drip", surfaceCF(ground, normal or Vector3.yAxis, nil, 0.017), d, d * rand(0.8, 1.1), Config.Blood.Base, false)
	p:SetAttribute("Size", d)
	p.Parent = poolFolder
end

-- Mop / clean. Returns true if anything was cleaned.
function Blood.CleanNear(pos: Vector3, radius: number, amount: number): boolean
	local cleaned = false
	for _, p in poolFolder:GetChildren() do
		if p:IsA("BasePart") then
			local d = (Util.flat(p.Position) - Util.flat(pos)).Magnitude
			local size = p:GetAttribute("Size") or p.Size.Y
			if d <= radius + size / 2 then
				cleaned = true
				if p.Name == "BloodPool" then
					local newSize = size - amount
					if newSize <= 0.7 then
						p:Destroy()
					else
						p:SetAttribute("Size", newSize)
						layout(p, newSize, 0.4)
						for _, x in { p, table.unpack(p:GetChildren()) } do
							if x:IsA("BasePart") then
								x.Transparency = math.min(x.Transparency + 0.1, 0.8)
							end
						end
					end
				else
					p:Destroy()
				end
			end
		end
	end
	return cleaned
end

function Blood.ClearAll()
	poolFolder:ClearAllChildren()
	table.clear(prints)
end

-- ── Footprints & drips that follow Nyx ───────────────────────────────────────
function Blood.StartTracking()
	local track = {}
	local acc = 0
	RunService.Heartbeat:Connect(function(dt)
		acc += dt
		if acc < 0.08 then
			return
		end
		acc = 0
		local gore = Config.Gore()
		for _, player in Players:GetPlayers() do
			local st = S.GameState.Get(player)
			local char = player.Character
			local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
			if st and hrp then
				local t = track[player]
				if not t or t.char ~= char then
					t = { char = char, last = hrp.Position, steps = 0, soaked = 0 }
					track[player] = t
				end
				-- stepping through fresh blood soaks the soles
				if gore.Footprints then
					local now = workspace:GetServerTimeNow()
					for _, core in CollectionService:GetTagged("BloodPool") do
						if core.Name == "BloodPool" and core.Parent and now - (core:GetAttribute("Born") or 0) < 150 then
							local r = (core:GetAttribute("Size") or 0) / 2 + 0.4
							if (Util.flat(core.Position) - Util.flat(hrp.Position)).Magnitude < r and math.abs(core.Position.Y - (hrp.Position.Y - 3)) < 2.5 then
								t.soaked = 14
								break
							end
						end
					end
				end
				local moved = Util.flat(hrp.Position - t.last)
				if moved.Magnitude >= 2.3 then
					t.last = hrp.Position
					t.steps += 1
					if t.soaked > 0 then
						t.soaked -= 1
						Blood.Footprint(hrp.Position, moved, t.steps % 2 == 0 and 1 or -1, math.clamp(t.soaked / 14 + 0.15, 0.15, 1))
					end
					if st.Bloodiness >= 35 and math.random() < 0.45 then
						Blood.Drip(hrp.Position + Vector3.new(rand(-0.5, 0.5), 0, rand(-0.5, 0.5)))
					end
				end
			end
		end
	end)
end

-- ── Wounds (persistent server-side emitters on bodies / stumps) ──────────────
function Blood.Wound(part: BasePart, localCF: CFrame, strength: number, duration: number)
	local a = Instance.new("Attachment")
	a.Name = "Wound"
	a.CFrame = localCF
	a.Parent = part
	local pe = Instance.new("ParticleEmitter")
	pe.Name = "Bleed"
	pe.Color = ColorSequence.new(Config.Blood.Fresh, Config.Blood.Dark)
	pe.LightEmission = 0
	pe.LightInfluence = 1
	pe.Size = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0.14 * strength), NumberSequenceKeypoint.new(1, 0.05) })
	pe.Squash = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0), NumberSequenceKeypoint.new(1, -0.6) })
	pe.Transparency = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0), NumberSequenceKeypoint.new(0.8, 0.1), NumberSequenceKeypoint.new(1, 1) })
	pe.Lifetime = NumberRange.new(0.5, 0.9)
	pe.Speed = NumberRange.new(2 * strength, 5 * strength)
	pe.SpreadAngle = Vector2.new(18, 18)
	pe.Acceleration = Vector3.new(0, -50, 0)
	pe.Drag = 0.5
	pe.Rate = 70 * strength * Config.Gore().Droplets
	pe.EmissionDirection = Enum.NormalId.Front
	pe.Parent = a
	-- pulse with a dying heartbeat, then fade to a drip
	task.spawn(function()
		local t0 = os.clock()
		while a.Parent and os.clock() - t0 < duration do
			local life = 1 - (os.clock() - t0) / duration
			local beat = (math.sin((os.clock() - t0) * 9) + 1) / 2
			pe.Rate = (10 + 90 * beat ^ 6) * strength * life * Config.Gore().Droplets
			task.wait(0.05)
		end
		if pe.Parent then
			pe.Rate = 3
			task.wait(20)
			if pe.Parent then
				pe.Enabled = false
			end
		end
	end)
	return a
end

-- ── Stained clothes ──────────────────────────────────────────────────────────
local STAIN_PARTS = { "UpperTorso", "LowerTorso", "RightUpperArm", "RightLowerArm", "RightHand", "LeftLowerArm", "LeftHand", "RightUpperLeg", "LeftUpperLeg", "RightLowerLeg" }

function Blood.Stain(character: Model, count: number)
	local folder = character:FindFirstChild("Stains")
	if not folder then
		folder = Instance.new("Folder")
		folder.Name = "Stains"
		folder.Parent = character
	end
	if #folder:GetChildren() > 70 then
		return
	end
	for _ = 1, count do
		local name = STAIN_PARTS[math.random(1, #STAIN_PARTS)]
		local bone = character:FindFirstChild(name) :: BasePart?
		if bone then
			local s = bone.Size
			local front = math.random() < 0.8
			local offset = Vector3.new((math.random() - 0.5) * s.X * 0.85, (math.random() - 0.5) * s.Y * 0.85, (front and -1 or 1) * (s.Z / 2 + 0.01))
			local d = 0.16 + math.random() * 0.44
			local cf = bone.CFrame * CFrame.new(offset) * CFrame.Angles(0, 0, math.random() * math.pi)
			local color = Config.Blood.Base:Lerp(Config.Blood.Dark, math.random() * 0.5)
			local p = Util.deco(Vector3.new(d * (0.7 + math.random() * 0.6), d, 0.03), cf, color, folder, "Stain")
			Util.mesh(p, Enum.MeshType.Sphere)
			Util.weld(bone, p)
			-- a run of blood dripping down from bigger stains
			if d > 0.38 and math.random() < 0.5 then
				local run = Util.deco(Vector3.new(d * 0.22, d * (1.2 + math.random()), 0.025), bone.CFrame * CFrame.new(offset + Vector3.new(0, -d * 0.7, 0)), color, folder, "Stain")
				Util.mesh(run, Enum.MeshType.Sphere)
				Util.weld(bone, run)
			end
		end
	end
end

function Blood.ClearStains(character: Model)
	local folder = character:FindFirstChild("Stains")
	if folder then
		folder:ClearAllChildren()
	end
end

-- ── Weapons ──────────────────────────────────────────────────────────────────
function Blood.SetWeaponBloody(tool: Tool, bloody: boolean)
	tool:SetAttribute("Bloody", bloody)
	for _, p in tool:GetDescendants() do
		if p:IsA("BasePart") and p:GetAttribute("Blade") then
			local orig = p:GetAttribute("OrigColor")
			if orig == nil then
				p:SetAttribute("OrigColor", p.Color)
				orig = p.Color
			end
			p.Color = bloody and (orig :: Color3):Lerp(Config.Blood.Base, 0.78) or orig
			p.Reflectance = bloody and 0.12 or p.Reflectance
			local drip = p:FindFirstChild("Drip")
			if bloody and not drip then
				local pe = Instance.new("ParticleEmitter")
				pe.Name = "Drip"
				pe.Color = ColorSequence.new(Config.Blood.Base)
				pe.Size = NumberSequence.new(0.08)
				pe.Squash = NumberSequence.new(-0.5)
				pe.Lifetime = NumberRange.new(0.5, 0.8)
				pe.Rate = 4
				pe.Speed = NumberRange.new(0, 0.2)
				pe.Acceleration = Vector3.new(0, -40, 0)
				pe.Parent = p
			elseif not bloody and drip then
				drip:Destroy()
			end
		elseif p:IsA("Trail") then
			p.Color = bloody and ColorSequence.new(Config.Blood.Fresh, Config.Blood.Dark) or ColorSequence.new(Color3.new(1, 1, 1))
			p.Transparency = bloody and NumberSequence.new(0.2, 1) or NumberSequence.new(0.6, 1)
		end
	end
end

return Blood
