-- KUROBARA · Client blood simulation
-- Every droplet is a tiny ellipsoid flying on a ballistic arc. When it hits something it leaves
-- an oriented splat (stretched along its impact direction); on walls it may start to run.

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local TweenService = game:GetService("TweenService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)

local BloodFX = {}
local Mood -- set in Init

local GRAVITY = Vector3.new(0, -workspace.Gravity * 0.42, 0)
local MAX_DROPS = 420

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
	p.Reflectance = 0.12
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

local function placeSplat(pos: Vector3, normal: Vector3, vel: Vector3, size: number)
	local gore = Config.Gore()
	splatCursor = splatCursor % gore.MaxSplats + 1
	local p = splats[splatCursor]
	if not p then
		p = makePart()
		splats[splatCursor] = p
	end
	local speed = vel.Magnitude
	local stretch = 1 + math.clamp(speed / 14, 0, 2.2)
	local tangent = perpendicular(normal, vel)
	p.Transparency = 0
	p.Color = bloodColor()
	p.Size = Vector3.new(0.035, size * stretch, size)
	p.CFrame = CFrame.fromMatrix(pos + normal * (0.015 + math.random() * 0.01), normal, tangent)
	p:SetAttribute("Born", os.clock())
	-- satellite specks
	if size > 0.35 and math.random() < 0.6 then
		for _ = 1, math.random(1, 3) do
			splatCursor = splatCursor % gore.MaxSplats + 1
			local s = splats[splatCursor] or makePart()
			splats[splatCursor] = s
			local d = size * rand(0.15, 0.35)
			local off = tangent * rand(size * 0.6, size * 1.6) + normal:Cross(tangent) * rand(-0.4, 0.4) * size
			s.Transparency = 0
			s.Color = bloodColor()
			s.Size = Vector3.new(0.03, d, d)
			s.CFrame = CFrame.fromMatrix(pos + off + normal * 0.016, normal, tangent)
			s:SetAttribute("Born", os.clock())
		end
	end
	-- runs down walls
	if gore.WallDrips and math.abs(normal.Y) < 0.4 and size > 0.22 and math.random() < 0.35 then
		local down = perpendicular(normal, Vector3.new(0, -1, 0))
		local length = rand(0.8, 2.6)
		local drip = makePart()
		drip.Color = Config.Blood.Base
		drip.Size = Vector3.new(0.03, 0.05, size * 0.35)
		drip.CFrame = CFrame.fromMatrix(pos + normal * 0.02, normal, down)
		local endCF = CFrame.fromMatrix(pos + normal * 0.02 + down * (length / 2), normal, down)
		TweenService:Create(drip, TweenInfo.new(rand(2.5, 5), Enum.EasingStyle.Sine), { Size = Vector3.new(0.03, length, size * 0.28), CFrame = endCF }):Play()
		task.delay(80, function()
			TweenService:Create(drip, TweenInfo.new(6), { Transparency = 1 }):Play()
			task.delay(6, function()
				drip:Destroy()
			end)
		end)
	end
end

-- ── Droplets ─────────────────────────────────────────────────────────────────
local function spawnDrop(pos: Vector3, vel: Vector3, size: number)
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
	pe.Size = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0.25), NumberSequenceKeypoint.new(1, 1.4) })
	pe.Transparency = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0.25), NumberSequenceKeypoint.new(1, 1) })
	pe.Lifetime = NumberRange.new(0.25, 0.6)
	pe.Speed = NumberRange.new(6, 16)
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

-- ── Public effects ───────────────────────────────────────────────────────────
function BloodFX.Hit(d)
	local gore = Config.Gore()
	local pos: Vector3 = d.pos
	local dir: Vector3 = (d.dir and d.dir.Magnitude > 0.01) and d.dir.Unit or Vector3.zAxis
	local power: number = d.power or 1
	local count = math.floor((16 + 34 * power) * gore.Droplets * (d.final and 1.7 or 1))
	for _ = 1, count do
		local v
		if math.random() < 0.22 then
			-- back-spatter onto the attacker
			v = -dir * rand(3, 10) + randomUnit() * rand(1, 4) + Vector3.new(0, rand(2, 7), 0)
		else
			v = dir * rand(5, 19) + randomUnit() * rand(2, 9) + Vector3.new(0, rand(1, 10), 0)
		end
		spawnDrop(pos + randomUnit() * 0.25, v * (0.75 + power * 0.25), rand(0.07, 0.24) * (d.blunt and 1.25 or 1))
	end
	mist(pos, dir, 18 * power * gore.Droplets * (d.final and 2 or 1))
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
	for _ = 1, math.floor(40 * power * gore.Droplets) do
		spawnDrop(d.pos + randomUnit() * 0.2, randomUnit() * rand(4, 14) + Vector3.new(0, rand(3, 10), 0), rand(0.08, 0.26))
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
			local rate = (6 + 110 * pulse) * life * power * gore.Droplets
			acc += rate * dt
			local origin = part.CFrame * offset
			local worldDir = origin:VectorToWorldSpace(localDir)
			while acc >= 1 do
				acc -= 1
				local v = worldDir * (6 + 14 * pulse) * (0.4 + 0.6 * life) + randomUnit() * 1.2 + Vector3.new(0, 1.5, 0)
				spawnDrop(origin.Position, v, rand(0.06, 0.15))
			end
		end
	end)
end

function BloodFX.Init(remote: RemoteEvent, mood)
	Mood = mood
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
					placeSplat(hit.Position, hit.Normal, d.vel, d.size * (1.6 + math.min(impact / 10, 2.4)))
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

	-- slowly fade old splats
	task.spawn(function()
		while true do
			task.wait(5)
			local now = os.clock()
			for _, s in splats do
				local born = s:GetAttribute("Born")
				if born and now - born > 120 and s.Transparency < 1 then
					s:SetAttribute("Born", nil)
					TweenService:Create(s, TweenInfo.new(8), { Transparency = 1 }):Play()
				end
			end
		end
	end)
end

return BloodFX
