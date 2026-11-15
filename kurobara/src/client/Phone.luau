-- KUROBARA · Nyx's phone
-- A full-screen-height smartphone: home screen with a clock widget, apps (Messages, Notes, Map,
-- Profile, Camera, Settings), chat bubbles, a live campus map and real toggles.
-- Close it with ✕, by tapping next to it, with Tab, or the home bar (from the home screen).

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local HttpService = game:GetService("HttpService")
local UserInputService = game:GetService("UserInputService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Story = require(Shared.Story)
local Util = require(Shared.Util)
local Glass = require(script.Parent.Glass)

local Phone = {}
local player = Players.LocalPlayer
local UI = Config.UI

local gui: ScreenGui
local device: Frame
local pages: Frame
local clockLabel: TextLabel
local badge: TextLabel
local open = false
local currentPage: Frame? = nil
local CameraMode, HUD, Audio
local threads = {}
local unread = 0
local mapDots = {}
local mapCanvas: Frame? = nil

-- ── small builders ───────────────────────────────────────────────────────────
local function frame(parent: Instance, size: UDim2, pos: UDim2?, color: Color3?, alpha: number?): Frame
	local f = Instance.new("Frame")
	f.Size = size
	f.Position = pos or UDim2.new()
	f.BackgroundColor3 = color or Color3.new(0, 0, 0)
	f.BackgroundTransparency = alpha or 1
	f.BorderSizePixel = 0
	f.Parent = parent
	return f
end

local function label(parent: Instance, text: string, size: number, weight: Enum.FontWeight?, color: Color3?): TextLabel
	local t = Glass.Label(parent, text, size, weight, color)
	t.ZIndex = 5
	return t
end

local function tap(parent: Instance, size: UDim2, pos: UDim2?): TextButton
	local b = Instance.new("TextButton")
	b.Text = ""
	b.AutoButtonColor = false
	b.BackgroundTransparency = 1
	b.Size = size
	b.Position = pos or UDim2.new()
	b.ZIndex = 6
	b.Parent = parent
	return b
end

local function scroller(parent: Instance): ScrollingFrame
	local s = Instance.new("ScrollingFrame")
	s.BackgroundTransparency = 1
	s.BorderSizePixel = 0
	s.Size = UDim2.new(1, 0, 1, -64)
	s.Position = UDim2.fromOffset(0, 64)
	s.CanvasSize = UDim2.new()
	s.AutomaticCanvasSize = Enum.AutomaticSize.Y
	s.ScrollBarThickness = 2
	s.ScrollBarImageColor3 = Color3.new(1, 1, 1)
	s.ZIndex = 4
	s.Parent = parent
	Glass.pad(s, 14, 6)
	local l = Instance.new("UIListLayout")
	l.Padding = UDim.new(0, 8)
	l.SortOrder = Enum.SortOrder.LayoutOrder
	l.Parent = s
	return s
end

local function card(parent: Instance, order: number): Frame
	local c = frame(parent, UDim2.new(1, 0, 0, 0), nil, Color3.fromRGB(44, 40, 54), 0.35)
	c.AutomaticSize = Enum.AutomaticSize.Y
	c.LayoutOrder = order
	Glass.corner(c, 16)
	Glass.pad(c, 12, 10)
	local l = Instance.new("UIListLayout")
	l.Padding = UDim.new(0, 4)
	l.SortOrder = Enum.SortOrder.LayoutOrder
	l.Parent = c
	return c
end

local function wrapLabel(parent: Instance, text: string, size: number, weight: Enum.FontWeight?, color: Color3?, order: number?): TextLabel
	local t = label(parent, text, size, weight, color)
	t.TextWrapped = true
	t.TextYAlignment = Enum.TextYAlignment.Top
	t.Size = UDim2.new(1, 0, 0, 0)
	t.AutomaticSize = Enum.AutomaticSize.Y
	t.LayoutOrder = order or 0
	return t
end

-- ── navigation ───────────────────────────────────────────────────────────────
local function showPage(p: Frame)
	if currentPage and currentPage ~= p then
		currentPage.Visible = false
	end
	p.Visible = true
	p.Position = UDim2.fromScale(0.08, 0)
	Glass.tween(p, 0.25, { Position = UDim2.fromScale(0, 0) })
	currentPage = p
end

local home: Frame
local app: { [string]: () -> Frame } = {}
local built: { [string]: Frame } = {}

local function goHome()
	showPage(home)
	mapCanvas = nil
end

local function appPage(title: string): Frame
	local p = frame(pages, UDim2.fromScale(1, 1))
	p.Visible = false
	p.ZIndex = 3
	local back = tap(p, UDim2.fromOffset(90, 30), UDim2.fromOffset(8, 2))
	local bl = label(back, "‹ Home", 15, Enum.FontWeight.SemiBold, UI.Accent)
	bl.Size = UDim2.fromScale(1, 1)
	back.Activated:Connect(goHome)
	local t = label(p, title, 28, Enum.FontWeight.Bold)
	t.Position = UDim2.fromOffset(16, 28)
	t.Size = UDim2.new(1, -32, 0, 32)
	return p
end

local function openApp(name: string)
	if not built[name] then
		built[name] = app[name]()
	end
	showPage(built[name])
	if name == "Messages" then
		unread = 0
		badge.Visible = false
	end
end

-- ── apps ─────────────────────────────────────────────────────────────────────
local function threadFor(id: string)
	for _, t in threads do
		if t.id == id then
			return t
		end
	end
	return nil
end

local function chatPage(t): Frame
	local p = appPage(t.name)
	local s = scroller(p)
	for i, m in t.messages do
		local mine = m[1] == "Nyx"
		local row = frame(s, UDim2.new(1, 0, 0, 0))
		row.AutomaticSize = Enum.AutomaticSize.Y
		row.LayoutOrder = i
		local bubble = frame(row, UDim2.new(0.78, 0, 0, 0), nil, mine and UI.Accent or Color3.fromRGB(58, 54, 68), mine and 0.05 or 0.15)
		bubble.AutomaticSize = Enum.AutomaticSize.Y
		bubble.AnchorPoint = Vector2.new(mine and 1 or 0, 0)
		bubble.Position = UDim2.fromScale(mine and 1 or 0, 0)
		Glass.corner(bubble, 16)
		Glass.pad(bubble, 12, 8)
		local l = Instance.new("UIListLayout")
		l.SortOrder = Enum.SortOrder.LayoutOrder
		l.Parent = bubble
		if t.id == "class" and not mine then
			wrapLabel(bubble, m[1], 11, Enum.FontWeight.Bold, UI.Love, 1)
		end
		wrapLabel(bubble, m[2], 15, Enum.FontWeight.Medium, UI.Ink, 2)
	end
	task.defer(function()
		s.CanvasPosition = Vector2.new(0, 1e5)
	end)
	return p
end

app.Messages = function()
	local p = appPage("Messages")
	local s = scroller(p)
	for i, t in threads do
		local row = card(s, i)
		local b = tap(row, UDim2.fromScale(1, 1))
		local top = frame(row, UDim2.new(1, 0, 0, 20))
		top.LayoutOrder = 1
		local name = label(top, t.icon .. "  " .. t.name, 15, Enum.FontWeight.Bold)
		name.Size = UDim2.new(1, -20, 1, 0)
		if t.unread then
			local dot = frame(top, UDim2.fromOffset(9, 9), UDim2.new(1, -10, 0.5, -4), Color3.fromRGB(10, 132, 255), 0)
			Glass.corner(dot, 5)
		end
		local last = t.messages[#t.messages]
		wrapLabel(row, last and last[2] or "No messages yet.", 13, Enum.FontWeight.Regular, UI.SubInk, 2)
		b.Activated:Connect(function()
			t.unread = false
			built["Chat_" .. t.id] = chatPage(t)
			showPage(built["Chat_" .. t.id])
		end)
	end
	return p
end

app.Notes = function()
	local p = appPage("Notes")
	local s = scroller(p)
	for i, note in Story.Notes do
		local c = card(s, i)
		wrapLabel(c, note.title, 15, Enum.FontWeight.Bold, i == 1 and UI.Accent or UI.Ink, 1)
		local b = wrapLabel(c, note.body, 13, Enum.FontWeight.Regular, UI.SubInk, 2)
		b.LineHeight = 1.15
	end
	return p
end

app.Map = function()
	local p = appPage("Campus")
	local box = frame(p, UDim2.new(1, -24, 1, -110), UDim2.fromOffset(12, 70), Color3.fromRGB(30, 34, 30), 0.1)
	box.ClipsDescendants = true
	Glass.corner(box, 18)
	mapCanvas = box
	local zonesJson = ReplicatedStorage:GetAttribute("MapZones")
	local bounds = ReplicatedStorage:GetAttribute("MapBounds") -- Vector3(x1, z1, size)
	if typeof(zonesJson) == "string" and typeof(bounds) == "Vector3" then
		local ok, zones = pcall(function()
			return HttpService:JSONDecode(zonesJson)
		end)
		if ok then
			for _, z in zones do
				local x1 = (z[2] - bounds.X) / bounds.Z
				local z1 = (z[3] - bounds.Y) / bounds.Z
				local w = (z[4] - z[2]) / bounds.Z
				local h = (z[5] - z[3]) / bounds.Z
				local r = frame(box, UDim2.fromScale(w, h), UDim2.fromScale(x1, z1), Color3.fromHex(z[6] or "#4a4a55"), 0.25)
				Glass.corner(r, 4)
				if z[1] ~= "" then
					local t = label(r, z[1], 10, Enum.FontWeight.SemiBold, UI.Ink)
					t.Size = UDim2.fromScale(1, 1)
					t.TextXAlignment = Enum.TextXAlignment.Center
					t.TextScaled = true
					local c = Instance.new("UITextSizeConstraint")
					c.MaxTextSize = 11
					c.Parent = t
				end
			end
		end
	end
	mapDots = {}
	for _, d in { { "Nyx", UI.Accent }, { "Haru", UI.Love }, { "Mirei", Color3.fromRGB(90, 220, 140) }, { "Ryo", Color3.fromRGB(255, 214, 10) } } do
		local dot = frame(box, UDim2.fromOffset(10, 10), nil, d[2], 0)
		dot.AnchorPoint = Vector2.new(0.5, 0.5)
		dot.ZIndex = 8
		Glass.corner(dot, 5)
		local st = Instance.new("UIStroke")
		st.Color = Color3.new(1, 1, 1)
		st.Thickness = 1.5
		st.Parent = dot
		dot.Visible = false
		mapDots[d[1]] = dot
	end
	local legend = label(p, "●  You    ●  Haru    ●  Mirei    ●  Ryo", 11, Enum.FontWeight.SemiBold, UI.SubInk)
	legend.Position = UDim2.new(0, 16, 1, -34)
	legend.Size = UDim2.new(1, -32, 0, 20)
	legend.TextXAlignment = Enum.TextXAlignment.Center
	return p
end

app.Profile = function()
	local p = appPage("Nyx Amane")
	local s = scroller(p)
	local c = card(s, 1)
	local rows = {}
	for i, k in { "Class", "Day", "Heartbeat", "Reputation", "Haru ♥", "Blood on you", "Ryo's suspicion", "Missing" } do
		local r = frame(c, UDim2.new(1, 0, 0, 22))
		r.LayoutOrder = i
		label(r, k, 13, Enum.FontWeight.Medium, UI.SubInk).Size = UDim2.new(0.5, 0, 1, 0)
		local v = label(r, "—", 13, Enum.FontWeight.SemiBold)
		v.Position = UDim2.fromScale(0.45, 0)
		v.Size = UDim2.new(0.55, 0, 1, 0)
		v.TextXAlignment = Enum.TextXAlignment.Right
		v.TextTruncate = Enum.TextTruncate.AtEnd
		rows[k] = v
	end
	local function refresh()
		rows["Class"].Text = "2-A"
		rows["Day"].Text = (workspace:GetAttribute("DayName") or "Monday") .. " (" .. tostring(workspace:GetAttribute("Day") or 1) .. "/5)"
		rows["Heartbeat"].Text = tostring(player:GetAttribute("BPM") or 72) .. " bpm"
		rows["Reputation"].Text = tostring(player:GetAttribute("Reputation") or 0)
		rows["Haru ♥"].Text = tostring(player:GetAttribute("Affection") or 0)
		rows["Blood on you"].Text = tostring(player:GetAttribute("Bloodiness") or 0) .. "%"
		rows["Ryo's suspicion"].Text = tostring(workspace:GetAttribute("Suspicion") or 0) .. "%"
		local m = workspace:GetAttribute("Missing")
		rows["Missing"].Text = (m and m ~= "") and m or "nobody… yet"
	end
	refresh()
	p:GetPropertyChangedSignal("Visible"):Connect(refresh)
	return p
end

local function switchRow(parent: Instance, order: number, text: string, get: () -> boolean, set: (boolean) -> ())
	local r = frame(parent, UDim2.new(1, 0, 0, 34))
	r.LayoutOrder = order
	label(r, text, 15, Enum.FontWeight.Medium).Size = UDim2.new(1, -70, 1, 0)
	local track = frame(r, UDim2.fromOffset(50, 30), UDim2.new(1, -52, 0.5, -15), Color3.fromRGB(80, 78, 90), 0)
	Glass.corner(track, 15)
	local knob = frame(track, UDim2.fromOffset(26, 26), UDim2.fromOffset(2, 2), Color3.new(1, 1, 1), 0)
	Glass.corner(knob, 13)
	local function paint()
		local on = get()
		Glass.tween(track, 0.2, { BackgroundColor3 = on and Color3.fromRGB(52, 199, 89) or Color3.fromRGB(80, 78, 90) })
		Glass.tween(knob, 0.2, { Position = on and UDim2.fromOffset(22, 2) or UDim2.fromOffset(2, 2) })
	end
	paint()
	tap(r, UDim2.fromScale(1, 1)).Activated:Connect(function()
		set(not get())
		paint()
	end)
end

app.Settings = function()
	local p = appPage("Settings")
	local s = scroller(p)
	local c = card(s, 1)
	switchRow(c, 1, "Ego camera (first person)", function()
		return CameraMode.Mode == "First"
	end, function(v)
		CameraMode.Set(v and "First" or "Third")
	end)
	switchRow(c, 2, "Voices", function()
		return Config.Voice.Enabled
	end, function(v)
		Config.Voice.Enabled = v
	end)
	switchRow(c, 3, "Nyx's inner voice", function()
		return Config.Voice.Thoughts
	end, function(v)
		Config.Voice.Thoughts = v
	end)
	switchRow(c, 4, "Music", function()
		return Config.Audio.MusicVolume > 0
	end, function(v)
		Config.Audio.MusicVolume = v and 0.32 or 0
		if Audio and Audio.RefreshMusic then
			Audio.RefreshMusic()
		end
	end)
	switchRow(c, 5, "Sound effects", function()
		return Config.Audio.SfxVolume > 0
	end, function(v)
		Config.Audio.SfxVolume = v and 1 or 0
	end)
	local info = card(s, 2)
	wrapLabel(info, "Gore: " .. Config.GorePreset, 13, Enum.FontWeight.SemiBold, UI.SubInk, 1)
	wrapLabel(info, "Keys: V camera · Tab phone · 1-9 answers", 13, Enum.FontWeight.Regular, UI.SubInk, 2)
	return p
end

-- ── home screen ──────────────────────────────────────────────────────────────
local APPS = {
	{ "Messages", "💬", Color3.fromRGB(52, 199, 89), Color3.fromRGB(30, 140, 60) },
	{ "Notes", "📝", Color3.fromRGB(255, 214, 10), Color3.fromRGB(230, 160, 0) },
	{ "Map", "🗺", Color3.fromRGB(10, 132, 255), Color3.fromRGB(0, 80, 200) },
	{ "Profile", "👤", Color3.fromRGB(191, 90, 242), Color3.fromRGB(120, 50, 200) },
	{ "Camera", "🎥", Color3.fromRGB(99, 99, 102), Color3.fromRGB(58, 58, 60) },
	{ "Settings", "⚙", Color3.fromRGB(142, 142, 147), Color3.fromRGB(90, 90, 96) },
}

local function buildHome(w: number)
	home = frame(pages, UDim2.fromScale(1, 1))
	home.ZIndex = 3
	local dayL = label(home, "MONDAY", 13, Enum.FontWeight.Bold, UI.SubInk)
	dayL.Position = UDim2.fromOffset(0, 18)
	dayL.Size = UDim2.new(1, 0, 0, 18)
	dayL.TextXAlignment = Enum.TextXAlignment.Center
	local big = label(home, "07:00", math.floor(w * 0.24), Enum.FontWeight.Bold)
	big.Position = UDim2.fromOffset(0, 34)
	big.Size = UDim2.new(1, 0, 0, w * 0.26)
	big.TextXAlignment = Enum.TextXAlignment.Center
	clockLabel = big
	local tag = label(home, "five days. one heartbeat.", 12, Enum.FontWeight.Medium, Color3.fromRGB(255, 180, 200))
	tag.Position = UDim2.fromOffset(0, 34 + w * 0.26)
	tag.Size = UDim2.new(1, 0, 0, 16)
	tag.TextXAlignment = Enum.TextXAlignment.Center
	home:SetAttribute("Day", true)
	RunService.RenderStepped:Connect(function()
		if open then
			dayL.Text = string.upper(workspace:GetAttribute("DayName") or "Monday") .. " · DAY " .. tostring(workspace:GetAttribute("Day") or 1)
		end
	end)

	local grid = frame(home, UDim2.new(1, -28, 0, 0), UDim2.fromOffset(14, 70 + w * 0.3))
	grid.AutomaticSize = Enum.AutomaticSize.Y
	local g = Instance.new("UIGridLayout")
	local icon = math.floor((w - 28) / 3.4)
	g.CellSize = UDim2.fromOffset(icon, icon + 22)
	g.CellPadding = UDim2.fromOffset(math.floor((w - 28 - icon * 3) / 2.1), 12)
	g.HorizontalAlignment = Enum.HorizontalAlignment.Center
	g.SortOrder = Enum.SortOrder.LayoutOrder
	g.Parent = grid
	for i, a in APPS do
		local cell = frame(grid, UDim2.new())
		cell.LayoutOrder = i
		local sq = frame(cell, UDim2.fromOffset(icon, icon), nil, a[3], 0)
		Glass.corner(sq, math.floor(icon * 0.24))
		local grad = Instance.new("UIGradient")
		grad.Rotation = 90
		grad.Color = ColorSequence.new(a[3], a[4])
		grad.Parent = sq
		local em = label(sq, a[2], math.floor(icon * 0.5), Enum.FontWeight.Bold)
		em.Size = UDim2.fromScale(1, 1)
		em.TextXAlignment = Enum.TextXAlignment.Center
		local name = label(cell, a[1], 12, Enum.FontWeight.Medium)
		name.Position = UDim2.fromOffset(0, icon + 4)
		name.Size = UDim2.new(1, 0, 0, 16)
		name.TextXAlignment = Enum.TextXAlignment.Center
		if a[1] == "Messages" then
			badge = label(sq, "0", 12, Enum.FontWeight.Bold)
			badge.AnchorPoint = Vector2.new(0.5, 0.5)
			badge.Position = UDim2.new(1, -4, 0, 4)
			badge.Size = UDim2.fromOffset(22, 22)
			badge.BackgroundColor3 = Color3.fromRGB(255, 59, 48)
			badge.BackgroundTransparency = 0
			badge.TextXAlignment = Enum.TextXAlignment.Center
			badge.ZIndex = 7
			badge.Visible = false
			Glass.corner(badge, 11)
		end
		local b = tap(cell, UDim2.fromScale(1, 1))
		b.Activated:Connect(function()
			if a[1] == "Camera" then
				CameraMode.Toggle()
				HUD.Toast(CameraMode.Mode == "First" and "🎥 Ego camera" or "👁 Third person", "info")
			else
				openApp(a[1])
			end
		end)
	end
	currentPage = home
end

-- ── public ───────────────────────────────────────────────────────────────────
function Phone.IsOpen(): boolean
	return open
end

function Phone.Toggle(force: boolean?)
	local want = if force ~= nil then force else not open
	if want == open then
		return
	end
	open = want
	if open then
		gui.Enabled = true
		goHome()
		device.Position = UDim2.new(device.Position.X.Scale, device.Position.X.Offset, 1.6, 0)
		Glass.tween(device, 0.45, { Position = UDim2.new(device.Position.X.Scale, device.Position.X.Offset, 0.5, 0) }, Enum.EasingStyle.Back)
	else
		Glass.tween(device, 0.3, { Position = UDim2.new(device.Position.X.Scale, device.Position.X.Offset, 1.6, 0) }, Enum.EasingStyle.Quad, Enum.EasingDirection.In)
		task.delay(0.3, function()
			if not open then
				gui.Enabled = false
			end
		end)
	end
end

function Phone.AddMessage(threadId: string, from: string, text: string)
	local t = threadFor(threadId)
	if not t then
		return
	end
	table.insert(t.messages, { from, text })
	t.unread = true
	built.Messages = nil
	built["Chat_" .. threadId] = nil
	if not (open and currentPage and currentPage.Name == "Chat") then
		unread += 1
		badge.Text = tostring(unread)
		badge.Visible = true
	end
	HUD.Toast("💬 " .. from .. ": " .. text, "info")
end

function Phone.Reset()
	threads = {}
	for _, t in Story.Threads do
		local msgs = {}
		for _, m in t.seed do
			table.insert(msgs, { m[1], m[2] })
		end
		table.insert(threads, { id = t.id, name = t.name, icon = t.icon, messages = msgs, unread = false })
	end
	unread = 0
	if badge then
		badge.Visible = false
	end
	for k, p in built do
		p:Destroy()
		built[k] = nil
	end
	Phone.Toggle(false)
end

function Phone.Init(cameraMode, hud, audio)
	CameraMode, HUD, Audio = cameraMode, hud, audio
	gui = Instance.new("ScreenGui")
	gui.Name = "Phone"
	gui.ResetOnSpawn = false
	gui.IgnoreGuiInset = true
	gui.DisplayOrder = 15
	gui.Enabled = false
	gui.Parent = player:WaitForChild("PlayerGui")

	-- tapping next to the phone closes it
	local backdrop = Instance.new("TextButton")
	backdrop.Text = ""
	backdrop.AutoButtonColor = false
	backdrop.Size = UDim2.fromScale(1, 1)
	backdrop.BackgroundColor3 = Color3.new(0, 0, 0)
	backdrop.BackgroundTransparency = 0.55
	backdrop.Parent = gui
	backdrop.Activated:Connect(function()
		Phone.Toggle(false)
	end)

	local vp = workspace.CurrentCamera.ViewportSize
	local h = math.clamp(vp.Y * 0.86, 380, 720)
	local w = math.floor(h * 0.47)
	local narrow = vp.X < 900
	device = frame(gui, UDim2.fromOffset(w, h), narrow and UDim2.fromScale(0.5, 0.5) or UDim2.new(1, -70, 0.5, 0), Color3.fromRGB(12, 12, 14), 0)
	device.AnchorPoint = narrow and Vector2.new(0.5, 0.5) or Vector2.new(1, 0.5)
	device.Active = true
	Glass.corner(device, math.floor(h * 0.075))
	local rim = Instance.new("UIStroke")
	rim.Color = Color3.fromRGB(70, 66, 80)
	rim.Thickness = 2
	rim.Parent = device

	local screen = frame(device, UDim2.new(1, -14, 1, -14), UDim2.fromOffset(7, 7), Color3.fromRGB(26, 8, 18), 0)
	screen.ClipsDescendants = true
	Glass.corner(screen, math.floor(h * 0.065))
	local wall = Instance.new("UIGradient")
	wall.Rotation = 120
	wall.Color = ColorSequence.new({
		ColorSequenceKeypoint.new(0, Color3.fromRGB(90, 10, 40)),
		ColorSequenceKeypoint.new(0.55, Color3.fromRGB(28, 8, 30)),
		ColorSequenceKeypoint.new(1, Color3.fromRGB(8, 6, 14)),
	})
	wall.Parent = screen
	local rose = label(screen, "🥀", math.floor(w * 0.7), Enum.FontWeight.Regular)
	rose.Size = UDim2.fromScale(1, 0.6)
	rose.Position = UDim2.fromScale(0, 0.4)
	rose.TextXAlignment = Enum.TextXAlignment.Center
	rose.TextTransparency = 0.88
	rose.ZIndex = 1

	-- status bar + pill-shaped camera cutout
	local status = frame(screen, UDim2.new(1, 0, 0, 36))
	status.ZIndex = 5
	local time = label(status, "07:00", 13, Enum.FontWeight.SemiBold)
	time.Position = UDim2.fromOffset(22, 8)
	time.Size = UDim2.fromOffset(60, 18)
	local right = label(status, "▂▄▆ 87%", 12, Enum.FontWeight.SemiBold)
	right.Position = UDim2.new(1, -86, 0, 8)
	right.Size = UDim2.fromOffset(70, 18)
	right.TextXAlignment = Enum.TextXAlignment.Right
	local island = frame(status, UDim2.fromOffset(math.floor(w * 0.3), 24), UDim2.new(0.5, -math.floor(w * 0.15), 0, 7), Color3.new(0, 0, 0), 0)
	island.ZIndex = 6
	Glass.corner(island, 12)

	pages = frame(screen, UDim2.new(1, 0, 1, -56), UDim2.fromOffset(0, 38))
	pages.ClipsDescendants = true
	buildHome(w - 14)

	local homeBar = tap(screen, UDim2.new(0.5, 0, 0, 18), UDim2.new(0.25, 0, 1, -20))
	local bar = frame(homeBar, UDim2.new(0.7, 0, 0, 5), UDim2.new(0.15, 0, 0.5, -2), Color3.new(1, 1, 1), 0.1)
	Glass.corner(bar, 3)
	homeBar.Activated:Connect(function()
		if currentPage == home then
			Phone.Toggle(false)
		else
			goHome()
		end
	end)

	local close = Instance.new("TextButton")
	close.Text = "✕"
	close.TextSize = 18
	close.TextColor3 = UI.Ink
	close.FontFace = Glass.font(Enum.FontWeight.Bold)
	close.AutoButtonColor = false
	close.AnchorPoint = Vector2.new(0.5, 0.5)
	close.Position = UDim2.new(1, 6, 0, -6)
	close.Size = UDim2.fromOffset(40, 40)
	close.BackgroundColor3 = Color3.fromRGB(40, 36, 50)
	close.BackgroundTransparency = 0.1
	close.ZIndex = 10
	Glass.corner(close, 20)
	local cs = Instance.new("UIStroke")
	cs.Color = Color3.new(1, 1, 1)
	cs.Transparency = 0.5
	cs.Parent = close
	close.Parent = device
	close.Activated:Connect(function()
		Phone.Toggle(false)
	end)

	RunService.RenderStepped:Connect(function()
		if not open then
			return
		end
		local t = Util.fmtClock(workspace:GetAttribute("Clock") or 420)
		time.Text = t
		clockLabel.Text = t
		-- live map dots
		if mapCanvas and mapCanvas.Parent and mapCanvas.Visible then
			local bounds = ReplicatedStorage:GetAttribute("MapBounds")
			if typeof(bounds) == "Vector3" then
				local function put(dot: Frame?, pos: Vector3?)
					if not dot then
						return
					end
					dot.Visible = pos ~= nil
					if pos then
						dot.Position = UDim2.fromScale((pos.X - bounds.X) / bounds.Z, (pos.Z - bounds.Y) / bounds.Z)
					end
				end
				local char = player.Character
				local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
				put(mapDots.Nyx, hrp and hrp.Position)
				local npcs = workspace:FindFirstChild("NPCs")
				for _, id in { "Haru", "Mirei", "Ryo" } do
					local pos = nil
					if npcs then
						for _, m in npcs:GetChildren() do
							if m:GetAttribute("NPCId") == id then
								local r = m:FindFirstChild("HumanoidRootPart") :: BasePart?
								pos = r and r.Position
							end
						end
					end
					put(mapDots[id], pos)
				end
			end
		end
	end)

	UserInputService.InputBegan:Connect(function(input, processed)
		if open and not processed and input.KeyCode == Enum.KeyCode.Escape then
			Phone.Toggle(false)
		end
	end)
	Phone.Reset()
end

return Phone
