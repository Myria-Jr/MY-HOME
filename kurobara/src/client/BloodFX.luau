-- KUROBARA · Client blood simulation
-- Every droplet is a tiny ellipsoid on a ballistic arc. On impact it leaves an organic splat:
-- a stretched body, overlapping lobes, thin spikes shooting in the travel direction and
-- satellite specks. Walls get runs that crawl downward, ceilings drip back down.

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local TweenService = game:GetService("TweenService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)

local BloodFX = {}
local Mood -- set in Init
local Animator -- set in Init

local GRAVITY = Vector3.new(0, -workspace.Gravity * 0.42, 0)
local MAX_DROPS = 520

local folder = Instance.new("Folder")
folder.Name = "LocalBlood"
folder.Parent = workspace

local params = RaycastParams.new()
params.FilterType = Enum.RaycastFilterType.Exclude
params.FilterDescendantsInstances = { folder }
params.CollisionGroup = "BloodRay"
params.IgnoreWater = true

local drops = {}
local freeParts: { BasePart } = {}
local splats: { BasePart } = {}
local splatCursor = 0
local splatTweens: { [BasePart]: Tween } = {}

local function splatTween(p: BasePart, info: TweenInfo, props)
	local old = splatTweens[p]
	if old then
		old:Cancel()
	end
	local tw = TweenService:Create(p, info, props)
	splatTweens[p] = tw
	tw:Play()
end

local function rand(a: number, b: number): number
	return a + math.random() * (b - a)
end

local function randomUnit(): Vector3
	local v = Vector3.new(rand(-1, 1), rand(-1, 1), rand(-1, 1))
	return v.Magnitude > 0.01 and v.Unit or Vector3.yAxis
end

local function bloodColor(): Color3
	return Config.Blood.Fresh:Lerp(Config.Blood.Dark, math.random() * 0.55)
end

local function makePart(): BasePart
	local p = Instance.new("Part")
	p.Anchored = true
	p.CanCollide = false
	p.CanQuery = false
	p.CanTouch = false
	p.CastShadow = false
	p.Material = Enum.Material.SmoothPlastic
	p.Reflectance = 0.14
	p.TopSurface = Enum.SurfaceType.Smooth
	p.BottomSurface = Enum.SurfaceType.Smooth
	local m = Instance.new("SpecialMesh")
	m.MeshType = Enum.MeshType.Sphere
	m.Parent = p
	p.Parent = folder
	return p
end

local function getPart(): BasePart
	local p = table.remove(freeParts)
	if p then
		p.Transparency = 0
		return p
	end
	return makePart()
end

local function release(p: BasePart)
	p.CFrame = CFrame.new(0, -1e4, 0)
	table.insert(freeParts, p)
end

-- ── Splats ───────────────────────────────────────────────────────────────────
local function perpendicular(n: Vector3, hint: Vector3): Vector3
	local t = hint - n * hint:Dot(n)
	if t.Magnitude < 0.05 then
		t = n:Cross(math.abs(n.Y) < 0.9 and Vector3.yAxis or Vector3.xAxis)
	end
	return t.Unit
end

local function nextSplatPart(): BasePart
	local gore = Config.Gore()
	splatCursor = splatCursor % gore.MaxSplats + 1
	local p = splats[splatCursor]
	if not p then
		p = makePart()
		splats[splatCursor] = p
	end
	return p
end

-- one flat ellipse on a surface (X = normal, Y = length direction)
local function disc(pos: Vector3, normal: Vector3, along: Vector3, length: number, width: number, color: Color3, lift: number)
	local p = nextSplatPart()
	local old = splatTweens[p]
	if old then
		old:Cancel()
		splatTweens[p] = nil
	end
	p:SetAttribute("Drying", nil)
	p.Transparency = 0
	p.Color = color
	p.Reflectance = 0.16
	p.Size = Vector3.new(0.03, math.max(length, 0.03), math.max(width, 0.03))
	p.CFrame = CFrame.fromMatrix(pos + normal * lift, normal, along)
	p:SetAttribute("Born", os.clock())
	return p
end

local spawnDrop -- forward

local function placeSplat(pos: Vector3, normal: Vector3, vel: Vector3, size: number)
	local gore = Config.Gore()
	local speed = vel.Magnitude
	local stretch = 1 + math.clamp(speed / 14, 0, 2.4)
	local tangent = perpendicular(normal, vel)
	local bitan = normal:Cross(tangent)
	local base = bloodColor()
	local lift = 0.014 + math.random() * 0.006

	-- body (pushed slightly ahead of the impact point)
	disc(pos + tangent * size * 0.18 * stretch, normal, tangent, size * stretch, size, base, lift)
	-- lobes = irregular liquid outline
	if size > 0.22 then
		for i = 1, math.random(2, 4) do
			local off = tangent * rand(-0.25, 0.55) * size * stretch + bitan * rand(-0.45, 0.45) * size
			local s = size * rand(0.32, 0.62)
			local along = (tangent + bitan * rand(-0.35, 0.35)).Unit
			disc(pos + off, normal, along, s * rand(1, 1.7), s, base:Lerp(Config.Blood.Dark, rand(0, 0.3)), lift + i * 0.0012)
		end
	end
	-- spikes: thin streaks flung forward on fast impacts
	if speed > 9 and size > 0.18 then
		for _ = 1, math.random(1, 4) do
			local sd = (tangent + bitan * rand(-0.6, 0.6)).Unit
			local len = size * rand(0.8, 2.2) * stretch
			disc(pos + sd * (size * 0.45 * stretch + len * 0.5), normal, sd, len, size * rand(0.07, 0.15), base, lift + 0.002)
			if math.random() < 0.6 then
				local tipD = size * rand(0.12, 0.22)
				disc(pos + sd * (size * 0.45 * stretch + len + tipD), normal, sd, tipD, tipD, base, lift + 0.003)
			end
		end
	end
	-- satellite specks
	if size > 0.3 and math.random() < 0.7 then
		for _ = 1, math.random(1, 4) do
			local d = size * rand(0.1, 0.3)
			local off = tangent * rand(size * 0.6, size * 1.9) * stretch * 0.6 + bitan * rand(-0.6, 0.6) * size
			disc(pos + off, normal, tangent, d, d, bloodColor(), lift + 0.001)
		end
	end
	-- walls: a run crawls downward
	if gore.WallDrips and math.abs(normal.Y) < 0.4 and size > 0.2 and math.random() < 0.4 then
		local down = perpendicular(normal, Vector3.new(0, -1, 0))
		local length = rand(0.8, 3.2)
		local drip = makePart()
		drip.Color = Config.Blood.Base
		drip.Size = Vector3.new(0.03, 0.05, size * 0.32)
		drip.CFrame = CFrame.fromMatrix(pos + normal * 0.02, normal, down)
		local endCF = CFrame.fromMatrix(pos + normal * 0.02 + down * (length / 2), normal, down)
		TweenService:Create(drip, TweenInfo.new(rand(2.5, 6), Enum.EasingStyle.Sine), { Size = Vector3.new(0.03, length, size * 0.24), CFrame = endCF }):Play()
		local bead = makePart()
		bead.Color = Config.Blood.Base
		bead.Size = Vector3.new(0.05, size * 0.4, size * 0.4)
		bead.CFrame = CFrame.fromMatrix(pos + normal * 0.025, normal, down)
		TweenService:Create(bead, TweenInfo.new(rand(2.5, 6), Enum.EasingStyle.Sine), { CFrame = CFrame.fromMatrix(pos + normal * 0.025 + down * length, normal, down) }):Play()
		task.delay(90, function()
			for _, x in { drip, bead } do
				TweenService:Create(x, TweenInfo.new(6), { Transparency = 1 }):Play()
			end
			task.delay(6, function()
				drip:Destroy()
				bead:Destroy()
			end)
		end)
	end
	-- ceilings: drip back down a few times
	if normal.Y < -0.6 and size > 0.2 and spawnDrop then
		task.spawn(function()
			for _ = 1, math.random(2, 5) do
				task.wait(rand(0.4, 2.2))
				spawnDrop(pos + normal * 0.05, Vector3.new(0, -1, 0), rand(0.08, 0.14))
			end
		end)
	end
end

-- ── Droplets ─────────────────────────────────────────────────────────────────
spawnDrop = function(pos: Vector3, vel: Vector3, size: number)
	if #drops >= MAX_DROPS then
		return
	end
	local p = getPart()
	p.Color = bloodColor()
	table.insert(drops, { part = p, pos = pos, vel = vel, size = size, life = 0 })
end
BloodFX.Drop = spawnDrop

local function mist(pos: Vector3, dir: Vector3, amount: number)
	local a = Instance.new("Attachment")
	a.Parent = workspace.Terrain
	a.CFrame = CFrame.lookAt(pos, pos + dir)
	local pe = Instance.new("ParticleEmitter")
	pe.Color = ColorSequence.new(Config.Blood.Fresh, Config.Blood.Dark)
	pe.Size = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0.25), NumberSequenceKeypoint.new(1, 1.5) })
	pe.Transparency = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0.2), NumberSequenceKeypoint.new(1, 1) })
	pe.Lifetime = NumberRange.new(0.25, 0.65)
	pe.Speed = NumberRange.new(6, 18)
	pe.SpreadAngle = Vector2.new(45, 45)
	pe.Drag = 7
	pe.Acceleration = Vector3.new(0, -25, 0)
	pe.LightInfluence = 1
	pe.Rate = 0
	pe.EmissionDirection = Enum.NormalId.Front
	pe.Parent = a
	pe:Emit(math.floor(amount))
	task.delay(1.5, function()
		a:Destroy()
	end)
end

-- Dark clots that tumble, bounce and settle (Extreme only)
local function clots(pos: Vector3, dir: Vector3, count: number)
	for _ = 1, count do
		local p = Instance.new("Part")
		p.Shape = Enum.PartType.Ball
		local d = rand(0.18, 0.38)
		p.Size = Vector3.new(d, d, d)
		p.Color = Config.Blood.Dark:Lerp(Config.Blood.Base, math.random() * 0.4)
		p.Material = Enum.Material.SmoothPlastic
		p.Reflectance = 0.2
		p.CanQuery = false
		p.CanTouch = false
		p.CastShadow = false
		p.CollisionGroup = "BloodRay"
		p.CFrame = CFrame.new(pos + randomUnit() * 0.2)
		p.AssemblyLinearVelocity = dir * rand(5, 12) + Vector3.new(rand(-3, 3), rand(4, 9), rand(-3, 3))
		p.Parent = folder
		task.delay(1.4, function()
			if not p.Parent then
				return
			end
			p.Anchored = true
			local hit = workspace:Raycast(p.Position + Vector3.new(0, 0.5, 0), Vector3.new(0, -2, 0), params)
			if hit then
				placeSplat(hit.Position, hit.Normal, Vector3.new(0, -3, 0), d * 1.8)
			end
			task.delay(30, function()
				TweenService:Create(p, TweenInfo.new(5), { Transparency = 1 }):Play()
				task.delay(5, function()
					p:Destroy()
				end)
			end)
		end)
	end
end

-- ── Public effects ───────────────────────────────────────────────────────────
function BloodFX.Hit(d)
	local gore = Config.Gore()
	local pos: Vector3 = d.pos
	local dir: Vector3 = (d.dir and d.dir.Magnitude > 0.01) and d.dir.Unit or Vector3.zAxis
	local power: number = d.power or 1
	local count = math.floor((18 + 38 * power) * gore.Droplets * (d.final and 1.7 or 1))
	for _ = 1, count do
		local v
		if math.random() < 0.22 then
			-- back-spatter onto the attacker
			v = -dir * rand(3, 10) + randomUnit() * rand(1, 4) + Vector3.new(0, rand(2, 7), 0)
		else
			v = dir * rand(5, 19) + randomUnit() * rand(2, 9) + Vector3.new(0, rand(1, 10), 0)
		end
		spawnDrop(pos + randomUnit() * 0.25, v * (0.75 + power * 0.25), rand(0.07, 0.25) * (d.blunt and 1.3 or 1))
	end
	-- a high-pressure jet: fast, narrow, it reaches the walls
	if gore.Jets then
		local jet = math.floor((d.final and 26 or 8) * power * gore.Droplets)
		local aim = (dir + Vector3.new(0, rand(0.05, 0.3), 0)).Unit
		for _ = 1, jet do
			spawnDrop(pos, (aim + randomUnit() * 0.1).Unit * rand(20, 36), rand(0.06, 0.15))
		end
	end
	if gore.Clots and d.final then
		clots(pos, dir, math.random(2, 5))
	end
	mist(pos, dir, 20 * power * gore.Droplets * (d.final and 2 or 1))

	if gore.Hitstop and Animator then
		Animator.Freeze({ d.attacker, d.victim }, d.final and 0.13 or 0.06)
	end
	local cam = workspace.CurrentCamera
	local me = Players.LocalPlayer
	local close = cam and (cam.CFrame.Position - pos).Magnitude < 10
	if gore.ScreenBlood and Mood and (close or d.killer == me.UserId) then
		Mood.ScreenBlood(math.clamp(power * (d.final and 1.4 or 0.8), 0.3, 2))
	end
end

function BloodFX.Burst(d)
	local gore = Config.Gore()
	local power = d.power or 1
	for _ = 1, math.floor(46 * power * gore.Droplets) do
		spawnDrop(d.pos + randomUnit() * 0.2, randomUnit() * rand(4, 15) + Vector3.new(0, rand(3, 11), 0), rand(0.08, 0.27))
	end
	if gore.Clots then
		clots(d.pos, Vector3.yAxis, math.random(1, 3))
	end
	mist(d.pos, Vector3.yAxis, 30 * power * gore.Droplets)
end

-- A pumping arterial jet that follows a moving part (pulses like a dying heartbeat)
function BloodFX.Arterial(d)
	local gore = Config.Gore()
	if not gore.Arterial then
		return
	end
	local part: BasePart? = d.part
	if not part then
		return
	end
	local offset: CFrame = d.offset or CFrame.identity
	local localDir: Vector3 = (d.dir or Vector3.new(0, 0.3, -1)).Unit
	local duration: number = d.duration or 3
	local power: number = d.power or 1
	task.spawn(function()
		local t, acc = 0, 0
		while t < duration and part.Parent do
			local dt = RunService.Heartbeat:Wait()
			t += dt
			local life = 1 - t / duration
			local pulse = ((math.sin(t * math.pi * 2 * 1.35) + 1) / 2) ^ 5
			local rate = (6 + 120 * pulse) * life * power * gore.Droplets
			acc += rate * dt
			local origin = part.CFrame * offset
			local worldDir = origin:VectorToWorldSpace(localDir)
			while acc >= 1 do
				acc -= 1
				local v = worldDir * (6 + 16 * pulse) * (0.4 + 0.6 * life) + randomUnit() * 1.2 + Vector3.new(0, 1.5, 0)
				spawnDrop(origin.Position, v, rand(0.06, 0.16))
			end
		end
	end)
end

function BloodFX.Init(remote: RemoteEvent, mood, animator)
	Mood = mood
	Animator = animator
	remote.OnClientEvent:Connect(function(kind: string, data)
		local fn = BloodFX[kind]
		if fn and data then
			fn(data)
		end
	end)

	local moveParts: { BasePart } = {}
	local moveCFs: { CFrame } = {}
	RunService.RenderStepped:Connect(function(dt)
		dt = math.min(dt, 1 / 20)
		table.clear(moveParts)
		table.clear(moveCFs)
		local i = 1
		while i <= #drops do
			local d = drops[i]
			local newVel = d.vel + GRAVITY * dt
			local step = (d.vel + newVel) * 0.5 * dt
			local hit = workspace:Raycast(d.pos, step, params)
			d.life += dt
			if hit or d.life > 4 then
				if hit then
					local impact = d.vel.Magnitude
					placeSplat(hit.Position, hit.Normal, d.vel, d.size * (1.6 + math.min(impact / 10, 2.6)))
				end
				release(d.part)
				drops[i] = drops[#drops]
				drops[#drops] = nil
			else
				d.pos += step
				d.vel = newVel
				local speed = newVel.Magnitude
				local len = d.size * (1 + math.min(speed * 0.05, 2.5))
				d.part.Size = Vector3.new(d.size, d.size, len)
				table.insert(moveParts, d.part)
				table.insert(moveCFs, speed > 0.1 and CFrame.lookAt(d.pos, d.pos + newVel) or CFrame.new(d.pos))
				i += 1
			end
		end
		if #moveParts > 0 then
			workspace:BulkMoveTo(moveParts, moveCFs, Enum.BulkMoveMode.FireCFrameChanged)
		end
	end)

	-- old splats slowly darken, then fade
	task.spawn(function()
		while true do
			task.wait(5)
			local now = os.clock()
			for _, s in splats do
				local born = s:GetAttribute("Born")
				if born then
					local age = now - born
					if age > 150 and s.Transparency < 1 then
						s:SetAttribute("Born", nil)
						splatTween(s, TweenInfo.new(8), { Transparency = 1 })
					elseif age > 20 and not s:GetAttribute("Drying") then
						s:SetAttribute("Drying", true)
						splatTween(s, TweenInfo.new(120, Enum.EasingStyle.Linear), { Color = Config.Blood.Dried, Reflectance = 0.02 })
					end
				end
			end
		end
	end)
end

return BloodFX
