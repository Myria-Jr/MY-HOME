-- KUROBARA · Dialogue window with answer choices (mouse, number keys, touch)

local Players = game:GetService("Players")
local UserInputService = game:GetService("UserInputService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage:WaitForChild("Shared"):WaitForChild("Config"))
local Glass = require(script.Parent.Glass)

local Dialogue = {}
local player = Players.LocalPlayer
local UI = Config.UI

local gui: ScreenGui
local panel: Frame
local nameLabel: TextLabel
local lineLabel: TextLabel
local list: Frame
local action: RemoteEvent
local current = nil
local typingToken = 0

local function clearOptions()
	for _, c in list:GetChildren() do
		if c:IsA("GuiObject") then
			c:Destroy()
		end
	end
end

local function choose(id: string)
	if not current then
		return
	end
	for _, c in list:GetChildren() do
		if c:IsA("TextButton") then
			c.Active = false
			c.AutoButtonColor = false
			Glass.tween(c, 0.15, { BackgroundTransparency = 0.8 })
		end
	end
	action:FireServer("Dialogue", id)
end

local function typeLine(text: string)
	typingToken += 1
	local token = typingToken
	lineLabel.Text = text
	lineLabel.MaxVisibleGraphemes = 0
	task.spawn(function()
		local n = utf8.len(text) or #text
		for i = 1, n do
			if token ~= typingToken then
				return
			end
			lineLabel.MaxVisibleGraphemes = i
			task.wait(0.018)
		end
		lineLabel.MaxVisibleGraphemes = -1
	end)
end

local function show(data)
	current = data
	nameLabel.Text = data.name
	nameLabel.TextColor3 = data.love and UI.Love or UI.Ink
	typeLine("“" .. data.line .. "”")
	clearOptions()
	for i, opt in data.options do
		local b = Instance.new("TextButton")
		b.AutoButtonColor = false
		b.LayoutOrder = i
		b.Size = UDim2.new(1, 0, 0, 44)
		b.BackgroundColor3 = opt.id == "bye" and Color3.fromRGB(60, 56, 70) or Color3.fromRGB(255, 255, 255)
		b.BackgroundTransparency = opt.id == "bye" and 0.5 or 0.88
		b.Text = ""
		b.BorderSizePixel = 0
		Glass.corner(b, 22)
		local stroke = Instance.new("UIStroke")
		stroke.Color = Color3.new(1, 1, 1)
		stroke.Transparency = 0.7
		stroke.ApplyStrokeMode = Enum.ApplyStrokeMode.Border
		stroke.Parent = b
		local key = Glass.Label(b, tostring(i), 13, Enum.FontWeight.Bold, UI.SubInk)
		key.Size = UDim2.fromOffset(30, 44)
		key.Position = UDim2.fromOffset(10, 0)
		key.TextXAlignment = Enum.TextXAlignment.Center
		local t = Glass.Label(b, opt.text, 16, Enum.FontWeight.Medium, opt.id == "follow" and UI.Accent or UI.Ink)
		t.Position = UDim2.fromOffset(42, 0)
		t.Size = UDim2.new(1, -54, 1, 0)
		t.TextTruncate = Enum.TextTruncate.AtEnd
		b.MouseEnter:Connect(function()
			Glass.tween(b, 0.15, { BackgroundTransparency = 0.78 })
		end)
		b.MouseLeave:Connect(function()
			Glass.tween(b, 0.15, { BackgroundTransparency = opt.id == "bye" and 0.5 or 0.88 })
		end)
		b.Activated:Connect(function()
			choose(opt.id)
		end)
		b.Parent = list
	end
	if not gui.Enabled then
		gui.Enabled = true
		panel.Position = panel.Position + UDim2.fromOffset(0, 30)
		local sc = panel:FindFirstChildOfClass("UIScale")
		if sc then
			sc.Scale = 0.95
			Glass.tween(sc, 0.35, { Scale = 1 }, Enum.EasingStyle.Back)
		end
		Glass.tween(panel, 0.35, { Position = panel.Position - UDim2.fromOffset(0, 30) })
	end
end

local function hide()
	current = nil
	typingToken += 1
	gui.Enabled = false
	clearOptions()
end

function Dialogue.IsOpen(): boolean
	return current ~= nil
end

function Dialogue.Init(remote: RemoteEvent, actionRemote: RemoteEvent)
	action = actionRemote
	gui = Instance.new("ScreenGui")
	gui.Name = "Dialogue"
	gui.ResetOnSpawn = false
	gui.DisplayOrder = 12
	gui.Enabled = false
	gui.Parent = player:WaitForChild("PlayerGui")

	local touch = UserInputService.TouchEnabled
	local vp = workspace.CurrentCamera.ViewportSize
	local width = touch and math.min(500, vp.X * 0.52) or math.min(620, vp.X - 40)
	panel = Glass.Panel(gui, {
		size = UDim2.fromOffset(width, 0),
		pos = touch and UDim2.new(0, 16, 1, -16) or UDim2.new(0.5, 0, 1, -24),
		anchor = touch and Vector2.new(0, 1) or Vector2.new(0.5, 1),
		radius = 26,
		alpha = 0.18,
		tint = Color3.fromRGB(16, 14, 22),
	})
	panel.AutomaticSize = Enum.AutomaticSize.Y
	Instance.new("UIScale").Parent = panel
	Glass.pad(panel, 18, 16)
	local layout = Instance.new("UIListLayout")
	layout.Padding = UDim.new(0, 8)
	layout.SortOrder = Enum.SortOrder.LayoutOrder
	layout.Parent = panel

	local header = Instance.new("Frame")
	header.BackgroundTransparency = 1
	header.Size = UDim2.new(1, 0, 0, 24)
	header.LayoutOrder = 1
	header.Parent = panel
	nameLabel = Glass.Label(header, "", 15, Enum.FontWeight.Bold)
	nameLabel.Size = UDim2.new(1, -40, 1, 0)
	local close = Instance.new("TextButton")
	close.AnchorPoint = Vector2.new(1, 0.5)
	close.Position = UDim2.new(1, 0, 0.5, 0)
	close.Size = UDim2.fromOffset(28, 28)
	close.BackgroundColor3 = Color3.new(1, 1, 1)
	close.BackgroundTransparency = 0.85
	close.Text = "✕"
	close.TextColor3 = UI.Ink
	close.TextSize = 14
	close.FontFace = Glass.font(Enum.FontWeight.Bold)
	close.ZIndex = 3
	Glass.corner(close, 14)
	close.Parent = header
	close.Activated:Connect(function()
		action:FireServer("DialogueClose")
		hide()
	end)

	lineLabel = Glass.Label(panel, "", 19, Enum.FontWeight.Medium)
	lineLabel.LayoutOrder = 2
	lineLabel.TextWrapped = true
	lineLabel.TextYAlignment = Enum.TextYAlignment.Top
	lineLabel.Size = UDim2.new(1, 0, 0, 0)
	lineLabel.AutomaticSize = Enum.AutomaticSize.Y

	list = Instance.new("Frame")
	list.BackgroundTransparency = 1
	list.Size = UDim2.new(1, 0, 0, 0)
	list.AutomaticSize = Enum.AutomaticSize.Y
	list.LayoutOrder = 3
	list.Parent = panel
	local ll = Instance.new("UIListLayout")
	ll.Padding = UDim.new(0, 6)
	ll.SortOrder = Enum.SortOrder.LayoutOrder
	ll.Parent = list

	remote.OnClientEvent:Connect(function(data)
		if data then
			show(data)
		else
			hide()
		end
	end)

	UserInputService.InputBegan:Connect(function(input, processed)
		if processed or not current then
			return
		end
		local n = input.KeyCode.Value - Enum.KeyCode.One.Value + 1
		if n >= 1 and n <= 9 then
			local opt = current.options[n]
			if opt then
				choose(opt.id)
			end
		end
	end)
end

return Dialogue
