-- KUROBARA · Touch controls for iPad & phones
-- A glass button arc on the right (attack, run, sneak), camera + phone top-left, and
-- context buttons that show exactly what you can do right now ("Drag · Aoi", "Shower" …).
-- Buttons scale up on tablets and respect the notch / home bar.

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local UserInputService = game:GetService("UserInputService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage:WaitForChild("Shared"):WaitForChild("Config"))
local Glass = require(script.Parent.Glass)

local TouchUI = {}
local player = Players.LocalPlayer
local UI = Config.UI

local gui: ScreenGui?
local remote: RemoteEvent
local HUD, CameraMode, Prompts
local state = { running = false, crouching = false }
local refs = {}

function TouchUI.IsTouch(): boolean
	return UserInputService.TouchEnabled
end

-- round glass button with an icon and a tiny caption
local function roundButton(parent: Instance, icon: string, caption: string, size: number, pos: UDim2, tint: Color3?)
	local b = Instance.new("TextButton")
	b.AutoButtonColor = false
	b.Text = ""
	b.AnchorPoint = Vector2.new(0.5, 0.5)
	b.Position = pos
	b.Size = UDim2.fromOffset(size, size)
	b.BackgroundColor3 = tint or Color3.fromRGB(22, 20, 30)
	b.BackgroundTransparency = 0.3
	b.BorderSizePixel = 0
	Glass.corner(b, size / 2)
	local stroke = Instance.new("UIStroke")
	stroke.Color = Color3.new(1, 1, 1)
	stroke.Transparency = 0.5
	stroke.Thickness = 1.5
	stroke.ApplyStrokeMode = Enum.ApplyStrokeMode.Border
	local sg = Instance.new("UIGradient")
	sg.Rotation = 125
	sg.Transparency = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0), NumberSequenceKeypoint.new(0.5, 0.7), NumberSequenceKeypoint.new(1, 0.3) })
	sg.Parent = stroke
	stroke.Parent = b
	local sheen = Instance.new("Frame")
	sheen.BackgroundColor3 = Color3.new(1, 1, 1)
	sheen.BackgroundTransparency = 0.86
	sheen.Size = UDim2.fromScale(1, 1)
	sheen.BorderSizePixel = 0
	Glass.corner(sheen, size / 2)
	local g = Instance.new("UIGradient")
	g.Rotation = 90
	g.Transparency = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0), NumberSequenceKeypoint.new(0.55, 1), NumberSequenceKeypoint.new(1, 1) })
	g.Parent = sheen
	sheen.Parent = b
	local i = Glass.Label(b, icon, math.floor(size * 0.38), Enum.FontWeight.Bold)
	i.AnchorPoint = Vector2.new(0.5, 0.5)
	i.Position = UDim2.fromScale(0.5, caption ~= "" and 0.42 or 0.5)
	i.Size = UDim2.fromScale(1, 0.5)
	i.TextXAlignment = Enum.TextXAlignment.Center
	local c = Glass.Label(b, caption, math.max(10, math.floor(size * 0.15)), Enum.FontWeight.SemiBold, UI.SubInk)
	c.AnchorPoint = Vector2.new(0.5, 0.5)
	c.Position = UDim2.fromScale(0.5, 0.76)
	c.Size = UDim2.fromScale(1, 0.25)
	c.TextXAlignment = Enum.TextXAlignment.Center
	local scale = Instance.new("UIScale")
	scale.Parent = b
	b.InputBegan:Connect(function(input)
		if input.UserInputType == Enum.UserInputType.Touch or input.UserInputType == Enum.UserInputType.MouseButton1 then
			Glass.tween(scale, 0.08, { Scale = 0.9 })
		end
	end)
	b.InputEnded:Connect(function(input)
		if input.UserInputType == Enum.UserInputType.Touch or input.UserInputType == Enum.UserInputType.MouseButton1 then
			Glass.tween(scale, 0.25, { Scale = 1 }, Enum.EasingStyle.Back)
		end
	end)
	b.Parent = parent
	local base = b.BackgroundColor3
	local function setActive(on: boolean, color: Color3?)
		Glass.tween(b, 0.2, { BackgroundColor3 = on and (color or UI.Accent) or base, BackgroundTransparency = on and 0.15 or 0.3 })
	end
	return b, setActive, i, c
end

-- pill-shaped context button for the nearest prompts
local function actionPill(parent: Instance, y: number)
	local b = Instance.new("TextButton")
	b.AutoButtonColor = false
	b.Text = ""
	b.AnchorPoint = Vector2.new(1, 1)
	b.Position = UDim2.new(1, 0, 1, y)
	b.Size = UDim2.fromOffset(230, 54)
	b.BackgroundColor3 = Color3.fromRGB(22, 20, 30)
	b.BackgroundTransparency = 0.25
	b.BorderSizePixel = 0
	b.Visible = false
	Glass.corner(b, 27)
	local stroke = Instance.new("UIStroke")
	stroke.Color = Color3.new(1, 1, 1)
	stroke.Transparency = 0.5
	stroke.ApplyStrokeMode = Enum.ApplyStrokeMode.Border
	stroke.Parent = b
	local key = Instance.new("Frame")
	key.BackgroundColor3 = Color3.new(1, 1, 1)
	key.BackgroundTransparency = 0.05
	key.Size = UDim2.fromOffset(40, 40)
	key.Position = UDim2.fromOffset(7, 7)
	Glass.corner(key, 20)
	key.Parent = b
	local fill = Instance.new("Frame")
	fill.BackgroundColor3 = UI.Accent
	fill.BackgroundTransparency = 1
	fill.Size = UDim2.fromScale(0, 1)
	fill.BorderSizePixel = 0
	fill.ZIndex = 0
	Glass.corner(fill, 27)
	fill.Parent = b
	local icon = Glass.Label(key, "✋", 18, Enum.FontWeight.Bold, Color3.fromRGB(20, 18, 26))
	icon.Size = UDim2.fromScale(1, 1)
	icon.TextXAlignment = Enum.TextXAlignment.Center
	local action = Glass.Label(b, "", 17, Enum.FontWeight.SemiBold)
	action.Position = UDim2.fromOffset(56, 7)
	action.Size = UDim2.new(1, -66, 0, 22)
	action.TextTruncate = Enum.TextTruncate.AtEnd
	local object = Glass.Label(b, "", 12, Enum.FontWeight.Medium, UI.SubInk)
	object.Position = UDim2.fromOffset(56, 29)
	object.Size = UDim2.new(1, -66, 0, 16)
	object.TextTruncate = Enum.TextTruncate.AtEnd
	b.Parent = parent
	local pill = { button = b, action = action, object = object, icon = icon, fill = fill, prompt = nil :: ProximityPrompt? }
	b.InputBegan:Connect(function(input)
		if input.UserInputType == Enum.UserInputType.Touch or input.UserInputType == Enum.UserInputType.MouseButton1 then
			local p = pill.prompt
			if p and p.Parent then
				p:InputHoldBegin()
				if p.HoldDuration > 0 then
					fill.BackgroundTransparency = 0.35
					fill.Size = UDim2.fromScale(0, 1)
					Glass.tween(fill, p.HoldDuration, { Size = UDim2.fromScale(1, 1) }, Enum.EasingStyle.Linear)
				end
			end
		end
	end)
	b.InputEnded:Connect(function(input)
		if input.UserInputType == Enum.UserInputType.Touch or input.UserInputType == Enum.UserInputType.MouseButton1 then
			local p = pill.prompt
			if p and p.Parent then
				p:InputHoldEnd()
			end
			Glass.tween(fill, 0.15, { BackgroundTransparency = 1 })
		end
	end)
	return pill
end

local ICONS = {
	Drag = "🫳", Drop = "✋", Dismember = "🪓", ["Bag it"] = "🗑", ["Pick up"] = "✋", ["Put down"] = "✋", Carry = "🗑",
	Take = "🔪", Talk = "💬", Shower = "🚿", Wash = "💧", Use = "🔥", ["Throw away"] = "🗑", Dig = "⛏",
	["Change clothes"] = "👔", Write = "📓", ["Walk to school"] = "🏫", ["Go home"] = "🏠", ["Attend class"] = "📚",
}

local function weaponIcon(tool: Tool?): (string, string)
	if not tool then
		return "✊", "—"
	end
	local id = tool:GetAttribute("ItemId")
	local map = { BoxCutter = "🔪", KitchenKnife = "🔪", Bat = "🏏", FireAxe = "🪓", Shears = "✂", Mop = "🧹", Shovel = "⛏", TrashBags = "🗑" }
	return map[id or ""] or "🔪", tool.Name
end

function TouchUI.Init(r: RemoteEvent, hud, cameraMode, prompts)
	remote, HUD, CameraMode, Prompts = r, hud, cameraMode, prompts
	if not TouchUI.IsTouch() then
		return
	end
	gui = Instance.new("ScreenGui")
	gui.Name = "TouchUI"
	gui.ResetOnSpawn = false
	gui.IgnoreGuiInset = true
	gui.DisplayOrder = 6
	gui.Enabled = false
	pcall(function()
		(gui :: any).ScreenInsets = Enum.ScreenInsets.DeviceSafeInsets
	end)
	gui.Parent = player:WaitForChild("PlayerGui")

	-- bigger on tablets
	local scale = Instance.new("UIScale")
	scale.Parent = gui
	local function rescale()
		local vp = workspace.CurrentCamera.ViewportSize
		scale.Scale = math.clamp(math.min(vp.X, vp.Y) / 420, 0.85, 1.45)
	end
	rescale()
	workspace.CurrentCamera:GetPropertyChangedSignal("ViewportSize"):Connect(rescale)

	-- right-hand cluster
	local cluster = Instance.new("Frame")
	cluster.BackgroundTransparency = 1
	cluster.AnchorPoint = Vector2.new(1, 1)
	cluster.Position = UDim2.new(1, -18, 1, -18)
	cluster.Size = UDim2.fromOffset(250, 250)
	cluster.Parent = gui

	local attack, _, attackIcon, attackCaption = roundButton(cluster, "🔪", "Attack", 108, UDim2.new(1, -54, 1, -54), Color3.fromRGB(120, 12, 28))
	attack.Activated:Connect(function()
		remote:FireServer("Attack")
	end)
	local run, runActive = roundButton(cluster, "🏃", "Run", 72, UDim2.new(1, -162, 1, -36))
	run.Activated:Connect(function()
		state.running = not state.running
		if state.running then
			state.crouching = false
			refs.sneakActive(false)
		end
		runActive(state.running)
		remote:FireServer("Run", state.running)
	end)
	local sneak, sneakActive = roundButton(cluster, "👣", "Sneak", 72, UDim2.new(1, -150, 1, -128))
	sneak.Activated:Connect(function()
		state.crouching = not state.crouching
		if state.crouching and state.running then
			state.running = false
			runActive(false)
			remote:FireServer("Run", false)
		end
		sneakActive(state.crouching, Color3.fromRGB(90, 70, 160))
		remote:FireServer("Crouch", state.crouching)
	end)
	local drop, _, dropIcon = roundButton(cluster, "✋", "Unequip", 60, UDim2.new(1, -62, 1, -168))
	drop.Activated:Connect(function()
		local hum = player.Character and player.Character:FindFirstChildOfClass("Humanoid")
		if hum then
			hum:UnequipTools()
		end
	end)
	refs.sneakActive = sneakActive
	refs.attack, refs.attackIcon, refs.attackCaption, refs.drop, refs.dropIcon = attack, attackIcon, attackCaption, drop, dropIcon

	-- top-left system buttons (under the clock + objective)
	local camBtn, _, camIcon, camCaption = roundButton(gui, "👁", "3rd", 56, UDim2.fromOffset(46, 160))
	camBtn.Activated:Connect(function()
		CameraMode.Toggle()
	end)
	CameraMode.Changed.Event:Connect(function(mode)
		camIcon.Text = mode == "First" and "🎥" or "👁"
		camCaption.Text = mode == "First" and "Ego" or "3rd"
	end)
	local phoneBtn = roundButton(gui, "📱", "Notes", 56, UDim2.fromOffset(110, 160))
	phoneBtn.Activated:Connect(function()
		HUD.TogglePhone()
	end)

	-- context pills above the cluster
	local pills = Instance.new("Frame")
	pills.BackgroundTransparency = 1
	pills.AnchorPoint = Vector2.new(1, 1)
	pills.Position = UDim2.new(1, -18, 1, -276)
	pills.Size = UDim2.fromOffset(230, 120)
	pills.Parent = gui
	refs.pills = { actionPill(pills, 0), actionPill(pills, -62) }

	local acc = 0
	RunService.Heartbeat:Connect(function(dt)
		if not gui or not gui.Enabled then
			return
		end
		acc += dt
		if acc < 0.15 then
			return
		end
		acc = 0
		local char = player.Character
		local tool = char and char:FindFirstChildOfClass("Tool")
		local icon, name = weaponIcon(tool)
		refs.attackIcon.Text = icon
		refs.attackCaption.Text = tool and name or "Attack"
		refs.attack.BackgroundTransparency = tool and 0.15 or 0.55
		refs.drop.Visible = tool ~= nil
		-- context buttons for the closest prompts
		local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
		local list = Prompts.Visible(hrp and hrp.Position or nil)
		for i, pill in refs.pills do
			local p = list[i]
			pill.prompt = p
			pill.button.Visible = p ~= nil
			if p then
				pill.action.Text = p.ActionText
				pill.object.Text = p.ObjectText
				pill.icon.Text = ICONS[p.ActionText] or "✋"
			end
		end
	end)

	-- the default jump button is useless here (Nyx doesn't jump)
	task.spawn(function()
		local pg = player:WaitForChild("PlayerGui")
		for _ = 1, 30 do
			local touch = pg:FindFirstChild("TouchGui")
			local jump = touch and touch:FindFirstChild("JumpButton", true)
			if jump then
				(jump :: GuiObject).Visible = false
				break
			end
			task.wait(1)
		end
	end)
end

function TouchUI.Show(on: boolean)
	if gui then
		gui.Enabled = on
		if not on then
			state.running, state.crouching = false, false
		end
	end
end

return TouchUI
