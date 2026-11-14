-- KUROBARA · Client animation player
-- Plays the procedural clips from Shared.Anim on any rig by offsetting Motor6D.C0,
-- layered (Base → Mood → Action → Hit) on top of Roblox's own walk/idle.

local RunService = game:GetService("RunService")
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
		for name, l in st.layers do
			l.t += dt * l.speed
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
		if not any then
			restore(model, st)
			continue
		end
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
				m.C0 = st.base[key] * offset
			end
		end
	end
end

function Animator.Init(remote: RemoteEvent)
	remote.OnClientEvent:Connect(Animator.Play)
	RunService.RenderStepped:Connect(step)
end

return Animator
