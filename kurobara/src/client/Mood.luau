-- KUROBARA · Screen mood: heartbeat vignette, color grade, camera shake, kill flash, screen blood

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local TweenService = game:GetService("TweenService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage:WaitForChild("Shared"):WaitForChild("Config"))

local Mood = {}
local player = Players.LocalPlayer

local gui: ScreenGui
local vignette = {}
local bloodLayer: Frame
local flashFrame: Frame
local grade: ColorCorrectionEffect
local flashCC: ColorCorrectionEffect
local shake = { power = 0, untilT = 0 }
local seenPulse = 0

local function tween(i: Instance, t: number, props, style: Enum.EasingStyle?, dir: Enum.EasingDirection?)
	local tw = TweenService:Create(i, TweenInfo.new(t, style or Enum.EasingStyle.Quad, dir or Enum.EasingDirection.Out), props)
	tw:Play()
	return tw
end

local function edge(name: string, size: UDim2, pos: UDim2, rotation: number)
	local f = Instance.new("Frame")
	f.Name = name
	f.Size = size
	f.Position = pos
	f.BorderSizePixel = 0
	f.BackgroundColor3 = Color3.new(0, 0, 0)
	f.BackgroundTransparency = 0
	local g = Instance.new("UIGradient")
	g.Rotation = rotation
	g.Transparency = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0), NumberSequenceKeypoint.new(1, 1) })
	g.Parent = f
	f.Parent = gui
	table.insert(vignette, f)
	return f
end

function Mood.Init()
	gui = Instance.new("ScreenGui")
	gui.Name = "Mood"
	gui.IgnoreGuiInset = true
	gui.ResetOnSpawn = false
	gui.DisplayOrder = 1
	gui.Parent = player:WaitForChild("PlayerGui")

	edge("Top", UDim2.fromScale(1, 0.32), UDim2.fromScale(0, 0), 90)
	edge("Bottom", UDim2.fromScale(1, 0.32), UDim2.fromScale(0, 0.68), -90)
	edge("Left", UDim2.fromScale(0.24, 1), UDim2.fromScale(0, 0), 0)
	edge("Right", UDim2.fromScale(0.24, 1), UDim2.fromScale(0.76, 0), 180)

	bloodLayer = Instance.new("Frame")
	bloodLayer.Name = "ScreenBlood"
	bloodLayer.BackgroundTransparency = 1
	bloodLayer.Size = UDim2.fromScale(1, 1)
	bloodLayer.ClipsDescendants = true
	bloodLayer.Parent = gui

	flashFrame = Instance.new("Frame")
	flashFrame.BackgroundColor3 = Color3.new(1, 1, 1)
	flashFrame.BackgroundTransparency = 1
	flashFrame.Size = UDim2.fromScale(1, 1)
	flashFrame.Parent = gui

	local cam = workspace.CurrentCamera
	grade = Instance.new("ColorCorrectionEffect")
	grade.Name = "HeartbeatGrade"
	grade.Parent = cam
	flashCC = Instance.new("ColorCorrectionEffect")
	flashCC.Name = "KillGrade"
	flashCC.Parent = cam

	local t0 = os.clock()
	RunService.RenderStepped:Connect(function(dt)
		local bpm = player:GetAttribute("BPM") or Config.BPM.Base
		local intensity = math.clamp((bpm - Config.BPM.Elevated + 15) / (Config.BPM.Max - Config.BPM.Elevated), 0, 1)
		local phase = ((os.clock() - t0) * bpm / 60) % 1
		local beat = math.max(0, 1 - phase * 5) ^ 2
		seenPulse = math.max(0, seenPulse - dt * 1.4)
		local alpha = 0.18 + 0.55 * intensity + 0.18 * intensity * beat + 0.5 * seenPulse
		local col = Color3.new(0, 0, 0):Lerp(Color3.fromRGB(120, 0, 14), math.min(1, intensity * 1.3 + seenPulse))
		for _, f in vignette do
			f.BackgroundTransparency = 1 - math.min(alpha, 0.92)
			f.BackgroundColor3 = col
		end
		grade.Saturation = -0.5 * intensity
		grade.Contrast = 0.12 * intensity
		grade.TintColor = Color3.new(1, 1, 1):Lerp(Color3.fromRGB(255, 186, 190), intensity * 0.8)

		-- camera shake through the humanoid's camera offset
		local char = player.Character
		local hum = char and char:FindFirstChildOfClass("Humanoid")
		if hum then
			local now = os.clock()
			if now < shake.untilT then
				local p = shake.power * ((shake.untilT - now) / 0.5)
				hum.CameraOffset = Vector3.new((math.random() - 0.5) * p, (math.random() - 0.5) * p, (math.random() - 0.5) * p * 0.5)
			elseif hum.CameraOffset.Magnitude > 0 then
				hum.CameraOffset = Vector3.zero
			end
		end
	end)
end

function Mood.Shake(power: number, duration: number)
	shake.power = math.max(power, shake.power * 0.5)
	shake.untilT = math.max(shake.untilT, os.clock() + duration)
end

function Mood.KillFlash(power: number)
	power = power or 1
	Mood.Shake(0.8 * power, 0.5)
	flashCC.Saturation = -1
	flashCC.Contrast = 0.55
	flashCC.Brightness = 0.06
	flashCC.TintColor = Color3.fromRGB(255, 150, 155)
	tween(flashCC, 0.9, { Saturation = 0, Contrast = 0, Brightness = 0, TintColor = Color3.new(1, 1, 1) }, Enum.EasingStyle.Quint)
	local cam = workspace.CurrentCamera
	local fov = 70
	cam.FieldOfView = fov - 12
	tween(cam, 0.6, { FieldOfView = fov }, Enum.EasingStyle.Back)
end

function Mood.HitShake(power: number)
	Mood.Shake(0.35 * (power or 1), 0.25)
end

function Mood.Flash()
	flashFrame.BackgroundTransparency = 0.05
	tween(flashFrame, 0.6, { BackgroundTransparency = 1 })
end

function Mood.Seen()
	seenPulse = 1
end

-- Blood on the lens: a big splat with satellites that slowly runs down and fades
function Mood.ScreenBlood(intensity: number)
	if not Config.Gore().ScreenBlood then
		return
	end
	local vp = workspace.CurrentCamera.ViewportSize
	local cx = math.random(math.floor(vp.X * 0.15), math.floor(vp.X * 0.85))
	local cy = math.random(math.floor(vp.Y * 0.1), math.floor(vp.Y * 0.6))
	local blobs = math.floor(4 + 7 * intensity)
	for i = 1, blobs do
		local big = i == 1
		local d = big and math.random(110, 210) * intensity or math.random(14, 70) * intensity
		local f = Instance.new("Frame")
		f.AnchorPoint = Vector2.new(0.5, 0.5)
		local ox = big and 0 or math.random(-180, 180) * intensity
		local oy = big and 0 or math.random(-120, 120) * intensity
		f.Position = UDim2.fromOffset(cx + ox, cy + oy)
		f.Size = UDim2.fromOffset(d * (0.8 + math.random() * 0.5), d)
		f.Rotation = math.random(0, 360)
		f.BackgroundColor3 = Config.Blood.Fresh:Lerp(Config.Blood.Dark, math.random() * 0.4)
		f.BackgroundTransparency = 0.08
		f.BorderSizePixel = 0
		local c = Instance.new("UICorner")
		c.CornerRadius = UDim.new(0.5, 0)
		c.Parent = f
		local g = Instance.new("UIGradient")
		g.Rotation = math.random(0, 360)
		g.Color = ColorSequence.new(Color3.new(1, 1, 1), Color3.fromRGB(150, 150, 150))
		g.Parent = f
		f.Parent = bloodLayer
		-- drip
		if math.random() < 0.6 then
			local drip = Instance.new("Frame")
			drip.AnchorPoint = Vector2.new(0.5, 0)
			drip.Position = UDim2.new(0.5, math.random(-8, 8), 0.5, 0)
			drip.Size = UDim2.fromOffset(math.max(4, d * 0.12), 0)
			drip.BackgroundColor3 = f.BackgroundColor3
			drip.BorderSizePixel = 0
			local dc = Instance.new("UICorner")
			dc.CornerRadius = UDim.new(0.5, 0)
			dc.Parent = drip
			drip.Parent = f
			tween(drip, math.random(25, 45) / 10, { Size = UDim2.fromOffset(math.max(4, d * 0.12), d * (0.8 + math.random() * 1.8)) }, Enum.EasingStyle.Sine)
			tween(drip, 4.5, { BackgroundTransparency = 1 }, Enum.EasingStyle.Sine, Enum.EasingDirection.In)
		end
		local delayT = 1.2 + math.random() * 1.2
		task.delay(delayT, function()
			tween(f, 3.2, { BackgroundTransparency = 1, Position = f.Position + UDim2.fromOffset(0, 40) }, Enum.EasingStyle.Sine, Enum.EasingDirection.In)
			task.delay(3.4, function()
				f:Destroy()
			end)
		end)
	end
end

return Mood
