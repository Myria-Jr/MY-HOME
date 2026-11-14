-- KUROBARA · Client animation player
-- Plays the procedural clips from Shared.Anim on any rig by offsetting Motor6D.C0,
-- layered (Base → Mood → Action → Hit) on top of Roblox's own walk/idle.

local RunService = game:GetService("RunService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Anim = require(ReplicatedStorage:WaitForChild("Shared"):WaitForChild("Anim"))

local Animator = {}
local active: { [Model]: any } = {}
local IDENT = CFrame.identity
local BLEND_IN, BLEND_OUT = 0.12, 0.22

local function setup(model: Model)
	local st = { motors = {}, base = {}, layers = {} }
	for key, motorName in Anim.JointNames do
		local m = model:FindFirstChild(motorName, true)
		if m and m:IsA("Motor6D") then
			st.motors[key] = m
			st.base[key] = m.C0
		end
	end
	active[model] = st
	return st
end

local function restore(model: Model, st)
	for key, m in st.motors do
		if m.Parent then
			m.C0 = st.base[key]
		end
	end
	active[model] = nil
end

function Animator.Play(model: Model?, clipName: any, layer: string?, speed: number?)
	if not model or not model.Parent then
		return
	end
	layer = layer or "Action"
	local st = active[model]
	if clipName == false then
		if not st then
			return
		end
		for name, l in st.layers do
			if layer == "*" or name == layer then
				l.fading = true
			end
		end
		return
	end
	local clip = Anim.Clips[clipName]
	if not clip then
		return
	end
	st = st or setup(model)
	local prev = st.layers[layer]
	st.layers[layer] = {
		clip = clip,
		t = 0,
		speed = speed or 1,
		weight = prev and prev.weight or 0,
		fading = false,
	}
end

-- Hitstop: hold the current pose for a split second on impact
function Animator.Freeze(models: { any }, duration: number)
	for _, m in models do
		if typeof(m) == "Instance" and m:IsA("Model") then
			local st = active[m]
			if st then
				st.frozenUntil = os.clock() + duration
			end
		end
	end
end

function Animator.Stop(model: Model)
	local st = active[model]
	if st then
		restore(model, st)
	end
end

local function step(dt: number)
	for model, st in active do
		if not model.Parent then
			active[model] = nil
			continue
		end
		local any = false
		local frozen = st.frozenUntil ~= nil and os.clock() < st.frozenUntil
		for name, l in st.layers do
			l.t += (frozen and 0 or dt) * l.speed
			if not l.clip.loop and not l.clip.hold and l.t >= l.clip.length then
				l.fading = true
			end
			if l.fading then
				l.weight -= dt / BLEND_OUT
				if l.weight <= 0 then
					st.layers[name] = nil
					continue
				end
			else
				l.weight = math.min(1, l.weight + dt / BLEND_IN)
			end
			if l.clip.hold then
				l.t = math.min(l.t, l.clip.length)
			end
			any = true
		end
		local looking = st.look ~= nil and (math.abs(st.look.yaw) > 0.005 or math.abs(st.look.pitch) > 0.005)
		if not any and not looking then
			restore(model, st)
			continue
		end
		local lookCF = looking and CFrame.fromEulerAnglesYXZ(st.look.pitch, st.look.yaw, 0) or IDENT
		local poses = {}
		for _, name in Anim.LayerOrder do
			local l = st.layers[name]
			if l then
				table.insert(poses, { Anim.Sample(l.clip, l.t), l.weight })
			end
		end
		for key, m in st.motors do
			if m.Enabled and m.Parent then
				local offset = IDENT
				for _, pw in poses do
					local cf = pw[1][key]
					if cf then
						offset *= IDENT:Lerp(cf, pw[2])
					end
				end
				if key == "Neck" then
					offset *= lookCF
				end
				m.C0 = st.base[key] * offset
			end
		end
	end
end

-- ── Head tracking: people glance at Nyx when she's close; Nyx watches Haru ────
local MAX_YAW, MAX_PITCH = math.rad(55), math.rad(25)

local function desiredLook(model: Model, target: Vector3, range: number): (number, number)
	local ut = model:FindFirstChild("UpperTorso") :: BasePart?
	local head = model:FindFirstChild("Head") :: BasePart?
	if not (ut and head) or model:GetAttribute("Expression") == "Dead" then
		return 0, 0
	end
	local to = target - head.Position
	if to.Magnitude > range or to.Magnitude < 0.5 then
		return 0, 0
	end
	local d = ut.CFrame:VectorToObjectSpace(to.Unit)
	local yaw = math.atan2(-d.X, -d.Z)
	if math.abs(yaw) > math.rad(80) then
		return 0, 0
	end
	return math.clamp(yaw, -MAX_YAW, MAX_YAW), math.clamp(math.asin(math.clamp(d.Y, -1, 1)), -MAX_PITCH, MAX_PITCH)
end

local function updateLooks(dt: number)
	local me = Players.LocalPlayer
	local myChar = me.Character
	local myHead = myChar and myChar:FindFirstChild("Head") :: BasePart?
	local npcs = workspace:FindFirstChild("NPCs")
	local wanted: { [Model]: { number } } = {}
	if npcs and myHead then
		for _, m in npcs:GetChildren() do
			if m:IsA("Model") then
				local yaw, pitch = desiredLook(m, myHead.Position + Vector3.new(0, 0.3, 0), 14)
				wanted[m] = { yaw, pitch }
			end
			if myChar and m:GetAttribute("NPCId") == "Haru" then
				local hh = m:FindFirstChild("Head") :: BasePart?
				if hh then
					local yaw, pitch = desiredLook(myChar, hh.Position, 24)
					wanted[myChar] = { yaw, pitch }
				end
			end
		end
	end
	local k = math.min(1, dt * 5)
	for m, w in wanted do
		local st = active[m]
		if not st and (math.abs(w[1]) > 0.01 or math.abs(w[2]) > 0.01) then
			st = setup(m)
		end
		if st then
			st.look = st.look or { yaw = 0, pitch = 0 }
			st.look.yaw += (w[1] - st.look.yaw) * k
			st.look.pitch += (w[2] - st.look.pitch) * k
		end
	end
	for m, st in active do
		if st.look and not wanted[m] then
			st.look.yaw -= st.look.yaw * k
			st.look.pitch -= st.look.pitch * k
		end
	end
end

function Animator.Init(remote: RemoteEvent)
	remote.OnClientEvent:Connect(Animator.Play)
	RunService.RenderStepped:Connect(function(dt)
		updateLooks(dt)
		step(dt)
	end)
end

return Animator
