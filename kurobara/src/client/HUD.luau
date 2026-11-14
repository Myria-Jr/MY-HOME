-- KUROBARA · HUD: clock, heartbeat, stats, objective, police timer, toasts, phone

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local Lighting = game:GetService("Lighting")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Story = require(Shared.Story)
local Util = require(Shared.Util)
local Glass = require(script.Parent.Glass)

local HUD = {}
local player = Players.LocalPlayer
local UI = Config.UI

local gui: ScreenGui
local refs = {}
local toastHolder: Frame
local phone: Frame
local phoneOpen = false
local phoneBlur: BlurEffect

local function statusFor(bpm: number): (string, Color3)
	local B = Config.BPM
	if bpm >= B.Breakdown then
		return "Breakdown", UI.Warn
	elseif bpm >= B.Unstable then
		return "Unstable", UI.Accent
	elseif bpm >= B.Elevated then
		return "Elevated", Color3.fromRGB(255, 159, 10)
	end
	return "Calm", UI.Calm
end

-- ── Build ────────────────────────────────────────────────────────────────────
local function buildClock()
	local p = Glass.Panel(gui, { size = UDim2.fromOffset(232, 66), pos = UDim2.fromOffset(16, 10), radius = 22 })
	Glass.pad(p, 16, 10)
	local time = Glass.Label(p, "07:00", 30, Enum.FontWeight.SemiBold)
	time.Size = UDim2.new(0, 96, 1, 0)
	local right = Instance.new("Frame")
	right.BackgroundTransparency = 1
	right.Position = UDim2.fromOffset(100, 0)
	right.Size = UDim2.new(1, -100, 1, 0)
	right.Parent = p
	local day = Glass.Label(right, "MONDAY", 12, Enum.FontWeight.Bold, UI.SubInk)
	day.Position = UDim2.fromOffset(0, 4)
	local period = Glass.Label(right, "Before Class", 16, Enum.FontWeight.Medium)
	period.Position = UDim2.fromOffset(0, 22)
	refs.time, refs.day, refs.period = time, day, period

	local obj = Glass.Panel(gui, { size = UDim2.fromOffset(340, 34), pos = UDim2.fromOffset(16, 84), radius = 17, alpha = 0.42 })
	Glass.pad(obj, 14, 0)
	local dot = Glass.Label(obj, "◆", 12, Enum.FontWeight.Bold, UI.Accent)
	dot.Size = UDim2.new(0, 16, 1, 0)
	local text = Glass.Label(obj, "", 14, Enum.FontWeight.Medium)
	text.Position = UDim2.fromOffset(18, 0)
	text.Size = UDim2.new(1, -18, 1, 0)
	text.TextTruncate = Enum.TextTruncate.AtEnd
	refs.objective = text
	refs.objectivePanel = obj
end

local function buildHeart()
	local p = Glass.Panel(gui, { size = UDim2.fromOffset(244, 92), pos = UDim2.new(1, -16, 0, 10), anchor = Vector2.new(1, 0), radius = 24 })
	Glass.pad(p, 16, 12)
	local heart = Glass.Label(p, "♥", 26, Enum.FontWeight.Bold, UI.Accent)
	heart.Size = UDim2.fromOffset(30, 30)
	heart.TextXAlignment = Enum.TextXAlignment.Center
	local hs = Instance.new("UIScale")
	hs.Parent = heart
	local bpm = Glass.Label(p, "72", 30, Enum.FontWeight.SemiBold)
	bpm.Position = UDim2.fromOffset(36, -2)
	bpm.Size = UDim2.fromOffset(60, 34)
	local unit = Glass.Label(p, "BPM", 12, Enum.FontWeight.Bold, UI.SubInk)
	unit.Position = UDim2.fromOffset(92, 8)
	unit.Size = UDim2.fromOffset(40, 16)
	local status = Glass.Label(p, "Calm", 14, Enum.FontWeight.SemiBold, UI.Calm)
	status.Position = UDim2.fromOffset(132, 6)
	status.Size = UDim2.fromOffset(80, 20)
	status.TextXAlignment = Enum.TextXAlignment.Right
	-- ECG strip
	local strip = Instance.new("Frame")
	strip.BackgroundTransparency = 1
	strip.Position = UDim2.fromOffset(0, 38)
	strip.Size = UDim2.new(1, 0, 0, 30)
	strip.ZIndex = 2
	strip.Parent = p
	local bars = {}
	local n = 42
	for i = 1, n do
		local b = Instance.new("Frame")
		b.AnchorPoint = Vector2.new(0.5, 0.5)
		b.Position = UDim2.new((i - 0.5) / n, 0, 0.5, 0)
		b.Size = UDim2.new(0, 3, 0, 2)
		b.BackgroundColor3 = UI.Accent
		b.BorderSizePixel = 0
		b.BackgroundTransparency = 1 - (i / n) ^ 0.7
		b.ZIndex = 3
		Glass.corner(b, 2)
		b.Parent = strip
		bars[i] = b
	end
	refs.heart, refs.heartScale, refs.bpm, refs.status, refs.bars = heart, hs, bpm, status, bars

	local s = Glass.Panel(gui, { size = UDim2.fromOffset(244, 108), pos = UDim2.new(1, -16, 0, 110), anchor = Vector2.new(1, 0), radius = 22, alpha = 0.4 })
	Glass.pad(s, 16, 10)
	local rows = {
		{ "Reputation", Color3.fromRGB(100, 210, 255), "rep" },
		{ "Blood on you", Config.Blood.Fresh, "blood" },
		{ "Haru ♥", UI.Love, "aff" },
		{ "Ryo 🔍", Color3.fromRGB(255, 214, 10), "susp" },
	}
	for i, r in rows do
		local y = (i - 1) * 22
		local l = Glass.Label(s, r[1], 12, Enum.FontWeight.SemiBold, UI.SubInk)
		l.Position = UDim2.fromOffset(0, y)
		l.Size = UDim2.fromOffset(90, 18)
		local track, fill = Glass.Bar(s, r[2], UDim2.new(1, -96, 0, 6), UDim2.fromOffset(96, y + 6))
		refs[r[3]] = fill
		refs[r[3] .. "Label"] = l
		refs[r[3] .. "Track"] = track
	end
end

local function buildPolice()
	local p = Glass.Panel(gui, { size = UDim2.fromOffset(300, 40), pos = UDim2.new(0.5, 0, 0, 10), anchor = Vector2.new(0.5, 0), radius = 20, tint = Color3.fromRGB(120, 10, 20), alpha = 0.25 })
	local t = Glass.Label(p, "🚨  POLICE ARRIVING · 02:30", 16, Enum.FontWeight.Bold)
	t.Size = UDim2.fromScale(1, 1)
	t.TextXAlignment = Enum.TextXAlignment.Center
	p.Visible = false
	refs.police, refs.policeText = p, t
end

local function buildSeen()
	local holder = Instance.new("Frame")
	holder.BackgroundTransparency = 1
	holder.AnchorPoint = Vector2.new(0.5, 0.5)
	holder.Position = UDim2.fromScale(0.5, 0.26)
	holder.Size = UDim2.fromOffset(400, 90)
	holder.Parent = gui
	local big = Glass.Label(holder, "SEEN", 54, Enum.FontWeight.Heavy, UI.Warn)
	big.Size = UDim2.new(1, 0, 0, 58)
	big.TextXAlignment = Enum.TextXAlignment.Center
	big.TextTransparency = 1
	local small = Glass.Label(holder, "", 16, Enum.FontWeight.SemiBold, UI.Ink)
	small.Position = UDim2.fromOffset(0, 58)
	small.TextXAlignment = Enum.TextXAlignment.Center
	small.TextTransparency = 1
	refs.seenBig, refs.seenSmall = big, small
end

local function buildToasts()
	toastHolder = Instance.new("Frame")
	toastHolder.BackgroundTransparency = 1
	toastHolder.AnchorPoint = Vector2.new(0.5, 0)
	toastHolder.Position = UDim2.new(0.5, 0, 0, 58)
	toastHolder.Size = UDim2.fromOffset(440, 300)
	toastHolder.Parent = gui
	local list = Instance.new("UIListLayout")
	list.HorizontalAlignment = Enum.HorizontalAlignment.Center
	list.Padding = UDim.new(0, 6)
	list.SortOrder = Enum.SortOrder.LayoutOrder
	list.Parent = toastHolder
end

local function buildHint()
	local p = Glass.Panel(gui, { size = UDim2.fromOffset(420, 34), pos = UDim2.new(0, 16, 1, -16), anchor = Vector2.new(0, 1), radius = 17, alpha = 0.45 })
	local t = Glass.Label(p, "Shift run  ·  C sneak  ·  E interact  ·  Click attack  ·  Tab phone", 13, Enum.FontWeight.Medium, UI.SubInk)
	t.Size = UDim2.fromScale(1, 1)
	t.TextXAlignment = Enum.TextXAlignment.Center
	refs.hint = p
end

local function buildPhone()
	phone = Glass.Panel(gui, { size = UDim2.new(0, 360, 1, -150), pos = UDim2.new(1, 380, 0, 210), anchor = Vector2.new(1, 0), radius = 34, alpha = 0.18, tint = Color3.fromRGB(16, 14, 22) })
	phone.Visible = false
	Glass.pad(phone, 22, 20)
	local title = Glass.Label(phone, "Notes", 30, Enum.FontWeight.Bold)
	title.Size = UDim2.new(1, 0, 0, 36)
	local sub = Glass.Label(phone, "Nyx's phone · private", 13, Enum.FontWeight.Medium, UI.SubInk)
	sub.Position = UDim2.fromOffset(0, 36)
	local scroll = Instance.new("ScrollingFrame")
	scroll.BackgroundTransparency = 1
	scroll.BorderSizePixel = 0
	scroll.Position = UDim2.fromOffset(0, 64)
	scroll.Size = UDim2.new(1, 0, 1, -64)
	scroll.CanvasSize = UDim2.new()
	scroll.AutomaticCanvasSize = Enum.AutomaticSize.Y
	scroll.ScrollBarThickness = 3
	scroll.ScrollBarImageColor3 = Color3.new(1, 1, 1)
	scroll.ZIndex = 2
	scroll.Parent = phone
	local list = Instance.new("UIListLayout")
	list.Padding = UDim.new(0, 12)
	list.SortOrder = Enum.SortOrder.LayoutOrder
	list.Parent = scroll
	for i, note in Story.Notes do
		local card = Glass.Panel(scroll, { size = UDim2.new(1, -6, 0, 0), radius = 18, alpha = 0.5, tint = Color3.fromRGB(40, 36, 52) })
		card.AutomaticSize = Enum.AutomaticSize.Y
		card.LayoutOrder = i
		Glass.pad(card, 14, 12)
		local inner = Instance.new("UIListLayout")
		inner.Padding = UDim.new(0, 6)
		inner.SortOrder = Enum.SortOrder.LayoutOrder
		inner.Parent = card
		local h = Glass.Label(card, note.title, 16, Enum.FontWeight.Bold, i == 1 and UI.Accent or UI.Ink)
		h.LayoutOrder = 1
		local b = Glass.Label(card, note.body, 14, Enum.FontWeight.Regular, UI.SubInk)
		b.LayoutOrder = 2
		b.TextWrapped = true
		b.TextYAlignment = Enum.TextYAlignment.Top
		b.Size = UDim2.new(1, 0, 0, 0)
		b.AutomaticSize = Enum.AutomaticSize.Y
		b.LineHeight = 1.15
	end
	phoneBlur = Instance.new("BlurEffect")
	phoneBlur.Size = 0
	phoneBlur.Name = "PhoneBlur"
	phoneBlur.Parent = Lighting
end

-- ── Public ───────────────────────────────────────────────────────────────────
function HUD.Toast(text: string, kind: string?)
	if not toastHolder then
		return
	end
	local color = kind == "warn" and UI.Warn or (kind == "love" and UI.Love or UI.Ink)
	local tint = kind == "warn" and Color3.fromRGB(70, 10, 18) or (kind == "love" and Color3.fromRGB(70, 20, 50) or Color3.fromRGB(22, 20, 30))
	local p = Glass.Panel(toastHolder, { size = UDim2.fromOffset(0, 38), radius = 19, tint = tint, alpha = 0.25 })
	p.AutomaticSize = Enum.AutomaticSize.X
	p.LayoutOrder = math.floor(os.clock() * 100)
	Glass.pad(p, 18, 0)
	local t = Glass.Label(p, text, 15, Enum.FontWeight.SemiBold, color)
	t.Size = UDim2.fromOffset(0, 38)
	t.AutomaticSize = Enum.AutomaticSize.X
	t.TextTransparency = 1
	local sc = Instance.new("UIScale")
	sc.Scale = 0.85
	sc.Parent = p
	Glass.tween(sc, 0.45, { Scale = 1 }, Enum.EasingStyle.Back)
	Glass.tween(t, 0.3, { TextTransparency = 0 })
	local kids = toastHolder:GetChildren()
	local count = 0
	for _, k in kids do
		if k:IsA("Frame") then
			count += 1
		end
	end
	if count > 4 then
		for _, k in kids do
			if k:IsA("Frame") and k ~= p then
				k:Destroy()
				break
			end
		end
	end
	task.delay(kind == "warn" and 4.5 or 3.4, function()
		if not p.Parent then
			return
		end
		Glass.tween(t, 0.4, { TextTransparency = 1 })
		Glass.tween(p, 0.4, { BackgroundTransparency = 1 })
		Glass.tween(sc, 0.4, { Scale = 0.9 })
		task.delay(0.42, function()
			p:Destroy()
		end)
	end)
end

-- Nyx's inner voice: soft italic line low on the screen
function HUD.Thought(text: string)
	local old = gui:FindFirstChild("Thought")
	if old then
		old:Destroy()
	end
	local t = Instance.new("TextLabel")
	t.Name = "Thought"
	t.BackgroundTransparency = 1
	t.AnchorPoint = Vector2.new(0.5, 1)
	t.Position = UDim2.new(0.5, 0, 1, -96)
	t.Size = UDim2.new(0.7, 0, 0, 30)
	t.Text = "“" .. text .. "”"
	t.TextSize = 21
	t.TextWrapped = true
	t.FontFace = Font.new(Config.UI.Font, Enum.FontWeight.Medium, Enum.FontStyle.Italic)
	t.TextColor3 = Color3.fromRGB(255, 196, 214)
	t.TextStrokeColor3 = Color3.fromRGB(40, 0, 14)
	t.TextStrokeTransparency = 0.55
	t.TextTransparency = 1
	t.Parent = gui
	Glass.tween(t, 0.8, { TextTransparency = 0, TextStrokeTransparency = 0.55 })
	task.delay(math.max(3.5, #text * 0.07), function()
		if t.Parent then
			Glass.tween(t, 1, { TextTransparency = 1, TextStrokeTransparency = 1 })
			task.delay(1.05, function()
				t:Destroy()
			end)
		end
	end)
end

function HUD.Seen(name: string, kind: string)
	refs.seenBig.Text = kind == "Murder" and "WITNESSED" or "SEEN"
	refs.seenSmall.Text = "by " .. name
	refs.seenBig.TextTransparency = 0
	refs.seenSmall.TextTransparency = 0
	local sc = refs.seenBig:FindFirstChildOfClass("UIScale") or Instance.new("UIScale", refs.seenBig)
	sc.Scale = 1.4
	Glass.tween(sc, 0.35, { Scale = 1 }, Enum.EasingStyle.Back)
	task.delay(1.6, function()
		Glass.tween(refs.seenBig, 0.6, { TextTransparency = 1 })
		Glass.tween(refs.seenSmall, 0.6, { TextTransparency = 1 })
	end)
end

function HUD.TogglePhone(force: boolean?)
	if not phone then
		return
	end
	phoneOpen = if force ~= nil then force else not phoneOpen
	if phoneOpen then
		phone.Visible = true
		Glass.tween(phone, 0.5, { Position = UDim2.new(1, -16, 0, 210) })
		Glass.tween(phoneBlur, 0.4, { Size = 10 })
	else
		Glass.tween(phone, 0.4, { Position = UDim2.new(1, 380, 0, 210) })
		Glass.tween(phoneBlur, 0.4, { Size = 0 })
	end
end

function HUD.Show(on: boolean)
	gui.Enabled = on
	if not on then
		HUD.TogglePhone(false)
	end
end

function HUD.FlashHint()
	if refs.hint then
		refs.hint.Visible = true
		task.delay(18, function()
			refs.hint.Visible = false
		end)
	end
end

function HUD.Init()
	gui = Instance.new("ScreenGui")
	gui.Name = "HUD"
	gui.ResetOnSpawn = false
	gui.IgnoreGuiInset = false
	gui.DisplayOrder = 5
	gui.Enabled = false
	gui.Parent = player:WaitForChild("PlayerGui")
	buildClock()
	buildHeart()
	buildPolice()
	buildSeen()
	buildToasts()
	buildHint()
	buildPhone()

	local samples = table.create(#refs.bars, 0)
	local t0 = os.clock()
	local lastBeatIdx = -1
	local acc = 0
	RunService.RenderStepped:Connect(function(dt)
		if not gui.Enabled then
			return
		end
		-- clock / day / period
		local minutes = workspace:GetAttribute("Clock") or Config.Time.DayStart
		refs.time.Text = Util.fmtClock(minutes)
		refs.day.Text = string.upper(workspace:GetAttribute("DayName") or "Monday") .. " · DAY " .. tostring(workspace:GetAttribute("Day") or 1)
		refs.period.Text = workspace:GetAttribute("Period") or ""
		refs.objective.Text = workspace:GetAttribute("Objective") or ""

		-- heartbeat
		local bpm = player:GetAttribute("BPM") or Config.BPM.Base
		refs.bpm.Text = tostring(bpm)
		local label, col = statusFor(bpm)
		refs.status.Text = label
		refs.status.TextColor3 = col
		refs.heart.TextColor3 = col
		local beats = (os.clock() - t0) * bpm / 60
		local idx = math.floor(beats)
		if idx ~= lastBeatIdx then
			lastBeatIdx = idx
			refs.heartScale.Scale = 1.28
			Glass.tween(refs.heartScale, 0.35, { Scale = 1 }, Enum.EasingStyle.Quad)
		end
		acc += dt
		if acc >= 1 / 30 then
			acc = 0
			local ph = beats % 1
			local v = 0
			if ph < 0.03 then
				v = 0.25
			elseif ph < 0.06 then
				v = 1
			elseif ph < 0.09 then
				v = -0.55
			elseif ph > 0.25 and ph < 0.36 then
				v = 0.22
			end
			v += (math.random() - 0.5) * 0.06
			table.remove(samples, 1)
			table.insert(samples, v)
			for i, b in refs.bars do
				local s = samples[i] or 0
				b.Size = UDim2.new(0, 3, 0, 2 + math.abs(s) * 26)
				b.Position = UDim2.new((i - 0.5) / #refs.bars, 0, 0.5, -s * 6)
				b.BackgroundColor3 = col
			end
		end

		-- stats
		local rep = player:GetAttribute("Reputation") or 0
		refs.rep.Size = UDim2.fromScale(math.clamp((rep + 100) / 200, 0.02, 1), 1)
		refs.rep.BackgroundColor3 = rep < -40 and UI.Warn or Color3.fromRGB(100, 210, 255)
		refs.blood.Size = UDim2.fromScale(math.clamp((player:GetAttribute("Bloodiness") or 0) / 100, 0, 1), 1)
		refs.aff.Size = UDim2.fromScale(math.clamp((player:GetAttribute("Affection") or 0) / 40, 0, 1), 1)
		local susp = workspace:GetAttribute("Suspicion") or 0
		local investigating = workspace:GetAttribute("Investigating") == true
		refs.susp.Size = UDim2.fromScale(math.clamp(susp / 100, 0, 1), 1)
		refs.susp.BackgroundColor3 = susp >= 70 and UI.Warn or Color3.fromRGB(255, 214, 10)
		refs.suspLabel.TextColor3 = investigating and (susp >= 70 and UI.Warn or UI.Ink) or Color3.fromRGB(110, 110, 125)

		-- police
		local eta = workspace:GetAttribute("PoliceETA")
		if eta then
			local left = math.max(0, eta - workspace:GetServerTimeNow())
			refs.police.Visible = true
			refs.policeText.Text = string.format("🚨  POLICE ARRIVING · %02d:%02d", math.floor(left / 60), math.floor(left % 60))
			refs.police.BackgroundTransparency = 0.25 + 0.2 * (math.sin(os.clock() * 6) + 1) / 2
		else
			refs.police.Visible = false
		end
	end)
end

return HUD
