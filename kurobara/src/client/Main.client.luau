-- KUROBARA · Client entry point

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Remotes = ReplicatedStorage:WaitForChild("Remotes")
local here = script.Parent

local Animator = require(here.Animator)
local BloodFX = require(here.BloodFX)
local Mood = require(here.Mood)
local HUD = require(here.HUD)
local Screens = require(here.Screens)
local Prompts = require(here.Prompts)
local Controls = require(here.Controls)

local player = Players.LocalPlayer

Mood.Init()
Animator.Init(Remotes.PlayAnim)
BloodFX.Init(Remotes.BloodFX, Mood)
HUD.Init()
Prompts.Init()
Controls.Init(Remotes.ClientAction, HUD)

Screens.Init(function()
	Remotes.ClientAction:FireServer("Start")
end, function()
	Remotes.ClientAction:FireServer("Restart")
end)

local function enterPlay()
	HUD.Show(true)
	Controls.Enable(true)
end

local function leavePlay()
	HUD.Show(false)
	Controls.Enable(false)
end

Remotes.Notify.OnClientEvent:Connect(function(text: string, kind: string)
	HUD.Toast(text, kind)
end)

Remotes.ScreenFX.OnClientEvent:Connect(function(kind: string, d)
	d = d or {}
	if kind == "KillFlash" then
		Mood.KillFlash(d.power or 1)
	elseif kind == "HitShake" then
		Mood.HitShake(d.power or 1)
	elseif kind == "Flash" then
		Mood.Flash()
	elseif kind == "Seen" then
		Mood.Seen()
		HUD.Seen(d.name or "someone", d.kind or "")
	elseif kind == "DayStart" then
		Screens.DayStart(d)
		enterPlay()
		HUD.FlashHint()
	elseif kind == "Night" then
		leavePlay()
		Screens.Night(d)
	elseif kind == "Fade" then
		Screens.Fade(d)
	elseif kind == "GameOver" then
		leavePlay()
		Screens.GameOver(d)
	elseif kind == "Ending" then
		leavePlay()
		Screens.Ending(d)
	elseif kind == "Bell" then
		-- a soft two-tone chime
		local s = Instance.new("Sound")
		s.SoundId = "rbxasset://sounds/electronicpingshort.wav"
		s.Volume = 0.5
		s.PlaybackSpeed = 0.6
		s.Parent = workspace.CurrentCamera
		s:Play()
		task.delay(0.35, function()
			s.PlaybackSpeed = 0.5
			s:Play()
			task.delay(2, function()
				s:Destroy()
			end)
		end)
	end
end)

-- Blink: eyes close for a moment every few seconds (purely cosmetic, local)
task.spawn(function()
	while true do
		task.wait(0.15)
		for _, folder in { workspace:FindFirstChild("NPCs"), workspace } do
			if folder then
				for _, m in folder:GetChildren() do
					if m:IsA("Model") and m:GetAttribute("Expression") and m:GetAttribute("Expression") ~= "Dead" and math.random() < 0.035 then
						local look = m:FindFirstChild("Look")
						if look then
							local parts = {}
							for _, s in { "L", "R" } do
								for _, n in { "_Sclera", "_Iris", "_Pupil", "_Shine" } do
									local p = look:FindFirstChild("Eye" .. s .. n)
									if p then
										table.insert(parts, { p, p.Transparency })
									end
								end
								local lid = look:FindFirstChild("Eye" .. s .. "_Lid")
								if lid then
									table.insert(parts, { lid, lid.Transparency })
								end
							end
							for _, pt in parts do
								pt[1].LocalTransparencyModifier = pt[1].Name:find("_Lid") and 0 or 1
								if pt[1].Name:find("_Lid") then
									pt[1].Transparency = 0
								end
							end
							task.delay(0.12, function()
								for _, pt in parts do
									pt[1].LocalTransparencyModifier = 0
									if pt[1].Name:find("_Lid") then
										pt[1].Transparency = pt[2]
									end
								end
							end)
						end
					end
				end
			end
		end
	end
end)

-- Wait for the world, then show the title
local t0 = os.clock()
while not workspace:GetAttribute("Ready") and os.clock() - t0 < 30 do
	RunService.Heartbeat:Wait()
end
if workspace:GetAttribute("Phase") == "Title" or workspace:GetAttribute("Phase") == nil then
	Screens.Title()
else
	enterPlay()
end
