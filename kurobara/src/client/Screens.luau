-- KUROBARA · Full-screen moments: title, intro, day cards, night, game over, endings

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local Lighting = game:GetService("Lighting")
local UserInputService = game:GetService("UserInputService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Story = require(Shared.Story)
local Glass = require(script.Parent.Glass)

local Screens = {}
local player = Players.LocalPlayer
local UI = Config.UI

local gui: ScreenGui
local blur: BlurEffect
local onStart: () -> ()
local onRestart: () -> ()
local titleCamConn: RBXScriptConnection?

local function clear()
	for _, c in gui:GetChildren() do
		c:Destroy()
	end
end

local function backdrop(color: Color3?, alpha: number?): Frame
	local f = Instance.new("Frame")
	f.Size = UDim2.fromScale(1, 1)
	f.BackgroundColor3 = color or Color3.new(0, 0, 0)
	f.BackgroundTransparency = 1
	f.BorderSizePixel = 0
	f.Parent = gui
	Glass.tween(f, 0.6, { BackgroundTransparency = alpha or 0 })
	return f
end

local function centerLabel(parent: Instance, text: string, size: number, weight: Enum.FontWeight, color: Color3?, y: number): TextLabel
	local t = Glass.Label(parent, text, size, weight, color)
	t.AnchorPoint = Vector2.new(0.5, 0.5)
	t.Position = UDim2.new(0.5, 0, y, 0)
	t.Size = UDim2.new(0.9, 0, 0, size + 10)
	t.TextXAlignment = Enum.TextXAlignment.Center
	t.TextWrapped = true
	t.TextTransparency = 1
	return t
end

local function fadeIn(t: TextLabel, delay: number, dur: number?)
	task.delay(delay, function()
		if t.Parent then
			Glass.tween(t, dur or 0.8, { TextTransparency = 0 })
		end
	end)
end

local function spaced(s: string): string
	return (s:gsub(".", "%0 ")):sub(1, -2)
end

-- ── Title ────────────────────────────────────────────────────────────────────
local function titleCamera(on: boolean)
	local cam = workspace.CurrentCamera
	if titleCamConn then
		titleCamConn:Disconnect()
		titleCamConn = nil
	end
	if on then
		cam.CameraType = Enum.CameraType.Scriptable
		local t0 = os.clock()
		titleCamConn = RunService.RenderStepped:Connect(function()
			local t = (os.clock() - t0) * 0.04
			local pos = Vector3.new(math.sin(t) * 70, 26 + math.sin(t * 0.7) * 4, 70 + math.cos(t) * 30)
			cam.CFrame = CFrame.lookAt(pos, Vector3.new(0, 8, 10))
		end)
	else
		cam.CameraType = Enum.CameraType.Custom
		local char = player.Character
		local hum = char and char:FindFirstChildOfClass("Humanoid")
		if hum then
			cam.CameraSubject = hum
		end
	end
end

function Screens.Title()
	clear()
	titleCamera(true)
	Glass.tween(blur, 1, { Size = 6 })
	local bg = Instance.new("Frame")
	bg.Size = UDim2.fromScale(1, 1)
	bg.BackgroundColor3 = Color3.new(0, 0, 0)
	bg.BackgroundTransparency = 0.25
	bg.BorderSizePixel = 0
	bg.Parent = gui
	local g = Instance.new("UIGradient")
	g.Rotation = 90
	g.Color = ColorSequence.new(Color3.fromRGB(10, 6, 14), Color3.fromRGB(70, 6, 22))
	g.Transparency = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0.3), NumberSequenceKeypoint.new(1, 0) })
	g.Parent = bg

	local kanji = centerLabel(gui, "黒 薔 薇", 22, Enum.FontWeight.Medium, UI.Accent, 0.3)
	local title = centerLabel(gui, spaced(Config.Title), 72, Enum.FontWeight.Heavy, UI.Ink, 0.39)
	local tag = centerLabel(gui, Config.Tagline, 20, Enum.FontWeight.Medium, UI.SubInk, 0.48)
	fadeIn(kanji, 0.2)
	fadeIn(title, 0.4, 1.4)
	fadeIn(tag, 1.1)

	local begin = Glass.Button(gui, "Begin", { pos = UDim2.fromScale(0.5, 0.64), size = UDim2.fromOffset(240, 56) })
	begin.TextTransparency = 1
	begin.BackgroundTransparency = 1
	task.delay(1.4, function()
		Glass.tween(begin, 0.6, { TextTransparency = 0, BackgroundTransparency = 0.86 })
	end)

	local gorePill = Glass.Panel(gui, { size = UDim2.fromOffset(230, 30), pos = UDim2.new(0.5, 0, 0.73, 0), anchor = Vector2.new(0.5, 0.5), radius = 15, alpha = 0.5 })
	local gl = Glass.Label(gorePill, "Gore: " .. Config.GorePreset .. "  ·  Headphones on", 13, Enum.FontWeight.SemiBold, UI.SubInk)
	gl.Size = UDim2.fromScale(1, 1)
	gl.TextXAlignment = Enum.TextXAlignment.Center

	local warnText = centerLabel(gui, "Contains graphic violence and blood. Fictional characters and events.", 13, Enum.FontWeight.Medium, Color3.fromRGB(150, 150, 165), 0.94)
	fadeIn(warnText, 1.5)

	local started = false
	begin.Activated:Connect(function()
		if started then
			return
		end
		started = true
		Screens.Intro(function()
			onStart()
		end)
	end)
end

function Screens.Intro(done: () -> ())
	clear()
	local bg = backdrop(Color3.fromRGB(4, 2, 6), 0)
	bg.BackgroundTransparency = 0
	local lines = Story.Intro
	local skip = false
	local conn = UserInputService.InputBegan:Connect(function(input)
		if input.UserInputType == Enum.UserInputType.MouseButton1 or input.UserInputType == Enum.UserInputType.Touch or input.KeyCode == Enum.KeyCode.Space then
			skip = true
		end
	end)
	local hint = centerLabel(gui, "click to skip", 12, Enum.FontWeight.Medium, Color3.fromRGB(110, 110, 120), 0.95)
	fadeIn(hint, 1)
	task.spawn(function()
		for i, line in lines do
			if skip then
				break
			end
			local last = i == #lines
			local t = centerLabel(gui, line, last and 30 or 24, last and Enum.FontWeight.Bold or Enum.FontWeight.Medium, last and UI.Accent or UI.Ink, 0.5)
			Glass.tween(t, 0.9, { TextTransparency = 0 })
			local wait = 1.6 + #line * 0.035
			local tt = 0
			while tt < wait and not skip do
				tt += task.wait(0.05)
			end
			Glass.tween(t, 0.5, { TextTransparency = 1 })
			task.wait(0.5)
			t:Destroy()
		end
		conn:Disconnect()
		titleCamera(false)
		Glass.tween(blur, 0.6, { Size = 0 })
		done()
	end)
end

-- ── Day / night ──────────────────────────────────────────────────────────────
function Screens.DayStart(d)
	clear()
	titleCamera(false)
	local bg = backdrop(Color3.new(0, 0, 0), 0)
	bg.BackgroundTransparency = 0
	local small = centerLabel(gui, "DAY " .. tostring(d.day), 18, Enum.FontWeight.Bold, UI.Accent, 0.42)
	local big = centerLabel(gui, string.upper(d.dayName or ""), 64, Enum.FontWeight.Heavy, UI.Ink, 0.5)
	local obj = centerLabel(gui, d.objective or "", 18, Enum.FontWeight.Medium, UI.SubInk, 0.6)
	fadeIn(small, 0.1)
	fadeIn(big, 0.3)
	fadeIn(obj, 0.8)
	task.delay(3.2, function()
		Glass.tween(bg, 1, { BackgroundTransparency = 1 })
		for _, t in { small, big, obj } do
			Glass.tween(t, 0.7, { TextTransparency = 1 })
		end
		task.delay(1.1, clear)
	end)
end

function Screens.Night(d)
	clear()
	local bg = backdrop(Color3.fromRGB(4, 2, 8), 0)
	local y = 0.36
	for i, line in d.lines or {} do
		local t = centerLabel(gui, line, i == 1 and 24 or 18, i == 1 and Enum.FontWeight.SemiBold or Enum.FontWeight.Medium, line:find("Missing") and UI.Accent or UI.Ink, y)
		fadeIn(t, 0.5 + i * 0.7)
		y += 0.06
	end
end

function Screens.Fade(d)
	local f = Instance.new("Frame")
	f.Size = UDim2.fromScale(1, 1)
	f.BackgroundColor3 = Color3.new(0, 0, 0)
	f.BackgroundTransparency = 1
	f.BorderSizePixel = 0
	f.ZIndex = 20
	f.Parent = gui
	local time = d and d.time or 1
	Glass.tween(f, time * 0.4, { BackgroundTransparency = 0 })
	if d and d.text then
		local t = centerLabel(f, d.text, 22, Enum.FontWeight.Medium, UI.SubInk, 0.5)
		t.ZIndex = 21
		fadeIn(t, 0.2, 0.3)
	end
	task.delay(time * 0.6, function()
		Glass.tween(f, time * 0.4, { BackgroundTransparency = 1 })
		for _, c in f:GetChildren() do
			if c:IsA("TextLabel") then
				Glass.tween(c, time * 0.3, { TextTransparency = 1 })
			end
		end
		task.delay(time * 0.45, function()
			f:Destroy()
		end)
	end)
end

-- ── Endings ──────────────────────────────────────────────────────────────────
function Screens.GameOver(d)
	clear()
	Glass.tween(blur, 0.8, { Size = 14 })
	local bg = backdrop(Color3.fromRGB(40, 0, 6), 0.25)
	local grad = Instance.new("UIGradient")
	grad.Rotation = 90
	grad.Transparency = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0.4), NumberSequenceKeypoint.new(1, 0) })
	grad.Parent = bg
	local title = centerLabel(gui, spaced(d.title or "GAME OVER"), 60, Enum.FontWeight.Heavy, UI.Warn, 0.36)
	local text = centerLabel(gui, d.text or "", 20, Enum.FontWeight.Medium, UI.Ink, 0.46)
	local extra = centerLabel(gui, d.extra or "", 16, Enum.FontWeight.Medium, UI.SubInk, 0.52)
	fadeIn(title, 0.3, 1.2)
	fadeIn(text, 1.0)
	fadeIn(extra, 1.5)
	local again = Glass.Button(gui, "Try again", { pos = UDim2.fromScale(0.5, 0.66) })
	again.Activated:Connect(function()
		again.Active = false
		Glass.tween(blur, 0.6, { Size = 0 })
		onRestart()
	end)
end

function Screens.Ending(d)
	clear()
	Glass.tween(blur, 1.5, { Size = 10 })
	local tint = d.kind == "Bad" and Color3.fromRGB(6, 6, 10) or Color3.fromRGB(30, 4, 14)
	local bg = backdrop(tint, 0.1)
	local y = 0.32
	local delay = 0.6
	for _, line in d.lines or {} do
		local t = centerLabel(gui, line, 22, Enum.FontWeight.Medium, UI.Ink, y)
		fadeIn(t, delay, 1.2)
		delay += 1.6
		y += 0.065
	end
	local title = centerLabel(gui, spaced(d.title or ""), 56, Enum.FontWeight.Heavy, d.kind == "Bad" and UI.SubInk or UI.Accent, y + 0.08)
	fadeIn(title, delay + 0.4, 1.6)
	local tag = centerLabel(gui, d.kind == "Good" and "ending · ♥" or (d.kind == "Neutral" and "ending · ◆" or "ending · ✕"), 14, Enum.FontWeight.SemiBold, UI.SubInk, y + 0.15)
	fadeIn(tag, delay + 1.2)
	local again = Glass.Button(gui, "Play again", { pos = UDim2.fromScale(0.5, math.min(y + 0.25, 0.9)) })
	again.TextTransparency = 1
	again.BackgroundTransparency = 1
	task.delay(delay + 1.8, function()
		Glass.tween(again, 0.6, { TextTransparency = 0, BackgroundTransparency = 0.86 })
	end)
	again.Activated:Connect(function()
		again.Active = false
		Glass.tween(blur, 0.6, { Size = 0 })
		onRestart()
	end)
end

function Screens.Clear()
	clear()
end

function Screens.Init(startFn: () -> (), restartFn: () -> ())
	onStart = startFn
	onRestart = restartFn
	gui = Instance.new("ScreenGui")
	gui.Name = "Screens"
	gui.ResetOnSpawn = false
	gui.IgnoreGuiInset = true
	gui.DisplayOrder = 20
	gui.Parent = player:WaitForChild("PlayerGui")
	blur = Instance.new("BlurEffect")
	blur.Name = "ScreenBlur"
	blur.Size = 0
	blur.Parent = Lighting
end

return Screens
