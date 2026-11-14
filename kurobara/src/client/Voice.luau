-- KUROBARA · Voices (Roblox text-to-speech, client side)
-- Every speech bubble is also spoken: positional from the speaker's head, with that
-- character's own voice / pitch / speed. Screams go through a distortion effect, or use real
-- recordings if you added any in AudioIds. Nyx's thoughts are a quiet whisper in your ear.

local SoundService = game:GetService("SoundService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)

local Voice = {}
local Audio -- set in Init
local active = 0
local deviceOut: Instance? = nil

local function sanitize(text: string): string
	text = text:gsub("%*[^%*]*%*", "") -- *stage directions*
	text = text:gsub("[^%w%s%.,!%?'%-]", "")
	text = text:gsub("%s+", " ")
	return (text:match("^%s*(.-)%s*$")) or ""
end

local function isScream(text: string): boolean
	local letters = text:gsub("[^%a]", "")
	if #letters < 3 then
		return false
	end
	local upper = letters:gsub("[^%u]", "")
	return #upper / #letters > 0.7 and text:find("!") ~= nil
end

local function output(): Instance?
	if deviceOut and deviceOut.Parent then
		return deviceOut
	end
	local ok, out = pcall(function()
		local o = Instance.new("AudioDeviceOutput")
		o.Name = "KurobaraVoiceOut"
		o.Parent = SoundService
		return o
	end)
	deviceOut = ok and out or nil
	return deviceOut
end

local function wire(from: Instance, to: Instance)
	local w = Instance.new("Wire")
	w.SourceInstance = from
	w.TargetInstance = to
	w.Parent = from
end

-- speak `text`; anchor = a BasePart for 3D, nil for 2D (in your head)
local function speak(text: string, anchor: BasePart?, voiceId: string, pitch: number, speed: number, volume: number, distort: number?)
	if active >= Config.Voice.MaxConcurrent then
		return
	end
	active += 1
	task.spawn(function()
		local made = {}
		local ok = pcall(function()
			local tts = Instance.new("AudioTextToSpeech")
			table.insert(made, tts)
			tts.Text = string.sub(text, 1, 280)
			tts.VoiceId = voiceId
			tts.Pitch = pitch
			tts.Speed = speed
			tts.Volume = volume
			local last: Instance = tts
			if distort and distort > 0 then
				local fx = Instance.new("AudioDistortion")
				table.insert(made, fx)
				fx.Level = distort
				fx.Parent = anchor or SoundService
				wire(last, fx)
				last = fx
			end
			if anchor then
				local emitter = Instance.new("AudioEmitter")
				table.insert(made, emitter)
				emitter.Parent = anchor
				wire(last, emitter)
				tts.Parent = anchor
			else
				local out = output()
				if not out then
					error("no output")
				end
				wire(last, out)
				tts.Parent = SoundService
			end
			local status = tts:LoadAsync()
			if status == Enum.AssetFetchStatus.Success then
				tts:Play()
				local t0 = os.clock()
				task.wait(0.2)
				while os.clock() - t0 < 2 + #text * 0.12 do
					local playing = tts.IsPlaying
					if not playing then
						break
					end
					task.wait(0.2)
				end
			end
		end)
		for _, x in made do
			x:Destroy()
		end
		active -= 1
		if not ok and Config.Debug then
			warn("[Kurobara] voice failed")
		end
	end)
end

local function profile(model: Model): (string, number, number)
	return tostring(model:GetAttribute("VoiceId") or "4"), model:GetAttribute("VoicePitch") or 0, model:GetAttribute("VoiceSpeed") or 1
end

function Voice.Say(model: Model?, text: string)
	if not Config.Voice.Enabled or not model or not model.Parent then
		return
	end
	local look = model:FindFirstChild("Look")
	local head = (look and look:FindFirstChild("FaceHead") or model:FindFirstChild("Head")) :: BasePart?
	if not head then
		return
	end
	local cam = workspace.CurrentCamera
	if cam and (cam.CFrame.Position - head.Position).Magnitude > Config.Voice.MaxDistance then
		return
	end
	local scream = isScream(text)
	local female = model:GetAttribute("Gender") == "F"
	if Audio and text:lower():find("haha") and model:GetAttribute("IsNyx") and Audio.Has("NyxLaugh") then
		Audio.Play("NyxLaugh", head, { volume = 0.9, vary = 0.05 })
		return
	end
	if scream and Audio then
		local list = female and "ScreamF" or "ScreamM"
		if Audio.Has(list) then
			Audio.Play(list, head, { volume = 1, vary = 0.1, distance = 140 })
			return
		end
	end
	local clean = sanitize(text)
	if clean == "" then
		return
	end
	local id, pitch, speed = profile(model)
	if scream then
		speak(clean, head, id, pitch + 6, speed * 1.3, 3.2, 0.45)
	else
		speak(clean, head, id, pitch, speed, 1.8, nil)
	end
end

-- Nyx's inner voice: quiet, slow, right in your ears
function Voice.Think(text: string)
	if not Config.Voice.Enabled or not Config.Voice.Thoughts then
		return
	end
	local clean = sanitize(text)
	if clean ~= "" then
		speak(clean, nil, Config.Voice.Nyx.id, Config.Voice.Nyx.pitch - 2, 0.88, 0.7, nil)
	end
end

-- School PA announcements
function Voice.Announce(text: string)
	if not Config.Voice.Enabled then
		return
	end
	local clean = sanitize(text)
	if clean ~= "" then
		speak(clean, nil, Config.Voice.PA.id, Config.Voice.PA.pitch, Config.Voice.PA.speed, 1.1, 0.08)
	end
end

function Voice.Init(remote: RemoteEvent, audio)
	Audio = audio
	remote.OnClientEvent:Connect(function(model: Model?, text: string)
		Voice.Say(model, text)
	end)
end

return Voice
