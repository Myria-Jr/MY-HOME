-- KUROBARA · Sound effects + music (client)
-- Every effect lives inside one uploaded "sheet" file; we play just its slice via PlaybackRegion.
-- Nothing here ever uses Roblox's built-in sounds.

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local SoundService = game:GetService("SoundService")
local TweenService = game:GetService("TweenService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Ids = require(Shared.AudioIds)
local Sheet = require(Shared.AudioSheet)

local Audio = {}
local player = Players.LocalPlayer

local function rand(a: number, b: number): number
	return a + math.random() * (b - a)
end

local function valid(id: any): boolean
	return type(id) == "string" and id ~= "" and id:find("%d") ~= nil
end

local function oneShotList(name: string): { string }?
	local list = Ids[name]
	if type(list) == "table" then
		local out = {}
		for _, id in list do
			if valid(id) then
				table.insert(out, id)
			end
		end
		if #out > 0 then
			return out
		end
	end
	return nil
end

function Audio.Has(name: string): boolean
	if oneShotList(name) then
		return true
	end
	return valid(Ids.Sheet) and Sheet[name] ~= nil
end

local function resolveParent(target: any): (Instance, Instance?)
	if typeof(target) == "Instance" then
		if target:IsA("Model") then
			local p = target:FindFirstChild("Head") or target.PrimaryPart or target:FindFirstChildWhichIsA("BasePart")
			if p then
				return p, nil
			end
		elseif target:IsA("BasePart") or target:IsA("Attachment") then
			return target, nil
		end
	elseif typeof(target) == "Vector3" then
		local a = Instance.new("Attachment")
		a.Parent = workspace.Terrain
		a.WorldPosition = target
		return a, a
	end
	return SoundService, nil
end

-- opts: volume, pitch, vary (random pitch spread), distance
function Audio.Play(name: string, target: any?, opts: { [string]: any }?): Sound?
	opts = opts or {}
	local s = Instance.new("Sound")
	local length
	local list = oneShotList(name)
	if list then
		s.SoundId = list[math.random(1, #list)]
		length = 8
	else
		local regions = Sheet[name]
		if not (regions and valid(Ids.Sheet)) then
			s:Destroy()
			return nil
		end
		local r = regions[math.random(1, #regions)]
		s.SoundId = Ids.Sheet
		s.PlaybackRegionsEnabled = true
		s.PlaybackRegion = NumberRange.new(r[1], r[1] + r[2])
		length = r[2]
	end
	local vary = opts.vary or 0.08
	s.PlaybackSpeed = (opts.pitch or 1) * (1 + rand(-vary, vary))
	s.Volume = (opts.volume or 0.6) * Config.Audio.SfxVolume
	s.RollOffMaxDistance = opts.distance or 90
	s.RollOffMinDistance = 6
	local parent, temp = resolveParent(target)
	s.Parent = parent
	s:Play()
	task.delay(length / math.max(s.PlaybackSpeed, 0.1) + 0.4, function()
		s:Destroy()
		if temp then
			temp:Destroy()
		end
	end)
	return s
end

-- ── Music: crossfading loops driven by the game state ────────────────────────
local tracks: { [string]: Sound } = {}
local current: string? = nil

local function track(name: string): Sound?
	if tracks[name] then
		return tracks[name]
	end
	local id = Ids[name]
	if not valid(id) then
		return nil
	end
	local s = Instance.new("Sound")
	s.Name = name
	s.SoundId = id
	s.Looped = true
	s.Volume = 0
	s.Parent = SoundService
	tracks[name] = s
	return s
end

function Audio.Music(name: string?)
	if name == current then
		return
	end
	current = name
	for n, s in tracks do
		if n ~= name then
			TweenService:Create(s, TweenInfo.new(1.6), { Volume = 0 }):Play()
			task.delay(1.7, function()
				if current ~= n then
					s:Pause()
				end
			end)
		end
	end
	if name then
		local s = track(name)
		if s then
			if not s.IsPlaying then
				s:Resume()
				if not s.IsPlaying then
					s:Play()
				end
			end
			TweenService:Create(s, TweenInfo.new(1.6), { Volume = Config.Audio.MusicVolume }):Play()
		end
	end
end

function Audio.RefreshMusic()
	local name = current
	if name and tracks[name] then
		TweenService:Create(tracks[name], TweenInfo.new(0.6), { Volume = Config.Audio.MusicVolume }):Play()
	end
end

-- ── Nyx's footsteps + heartbeat ──────────────────────────────────────────────
function Audio.Init(sfxRemote: RemoteEvent)
	sfxRemote.OnClientEvent:Connect(function(name: string, target, volume: number?, pitch: number?)
		Audio.Play(name, target, { volume = volume, pitch = pitch })
	end)

	local stepT, beatT, sirenT = 0, 0, 0
	RunService.Heartbeat:Connect(function(dt)
		local char = player.Character
		local hum = char and char:FindFirstChildOfClass("Humanoid")
		local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
		if hum and hrp and hum.FloorMaterial ~= Enum.Material.Air then
			local speed = Vector3.new(hrp.AssemblyLinearVelocity.X, 0, hrp.AssemblyLinearVelocity.Z).Magnitude
			if speed > 1.5 then
				stepT += dt
				local interval = math.clamp(4.6 / speed, 0.24, 0.62)
				if stepT >= interval then
					stepT = 0
					local vol = speed < 7 and 0.12 or (speed > 15 and 0.42 or 0.26)
					Audio.Play("Footstep", hrp, { volume = vol, vary = 0.12, distance = 50 })
				end
			else
				stepT = 0.2
			end
		end
		-- you can hear her heart once it's racing
		local bpm = player:GetAttribute("BPM") or Config.BPM.Base
		if bpm >= Config.BPM.Elevated and workspace:GetAttribute("Phase") == "Playing" then
			beatT += dt
			if beatT >= 60 / bpm then
				beatT = 0
				local k = math.clamp((bpm - Config.BPM.Elevated) / 70, 0, 1)
				Audio.Play("Heartbeat", nil, { volume = 0.25 + 0.55 * k, vary = 0.02 })
			end
		end
		-- sirens getting closer once the police are about to arrive
		local eta = workspace:GetAttribute("PoliceETA")
		if eta then
			local left = eta - workspace:GetServerTimeNow()
			if left < 30 then
				sirenT += dt
				if sirenT >= 4 then
					sirenT = 0
					Audio.Play("Siren", nil, { volume = math.clamp(0.9 - left / 40, 0.15, 0.85), vary = 0 })
				end
			end
		else
			sirenT = 4
		end
		-- music follows the mood
		local phase = workspace:GetAttribute("Phase")
		if phase == "Playing" or phase == "Title" or phase == nil then
			if workspace:GetAttribute("Chase") then
				Audio.Music("MusicChase")
			elseif bpm >= Config.BPM.Unstable or workspace:GetAttribute("PoliceETA") then
				Audio.Music("MusicTension")
			else
				Audio.Music("MusicCalm")
			end
		elseif phase == "Over" then
			Audio.Music("MusicTension")
		end
	end)
end

return Audio
