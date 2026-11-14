-- KUROBARA · Custom glass proximity prompts (keyboard, gamepad and touch)

local Players = game:GetService("Players")
local ProximityPromptService = game:GetService("ProximityPromptService")
local UserInputService = game:GetService("UserInputService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage:WaitForChild("Shared"):WaitForChild("Config"))
local Glass = require(script.Parent.Glass)

local Prompts = {}
local player = Players.LocalPlayer
local shown: { [ProximityPrompt]: BillboardGui } = {}

local function keyText(prompt: ProximityPrompt, inputType: Enum.ProximityPromptInputType): string
	if inputType == Enum.ProximityPromptInputType.Touch then
		return "TAP"
	elseif inputType == Enum.ProximityPromptInputType.Gamepad then
		local k = prompt.GamepadKeyCode.Name
		return (k:gsub("Button", ""))
	end
	local name = prompt.KeyboardKeyCode.Name
	return name
end

local function build(prompt: ProximityPrompt, inputType: Enum.ProximityPromptInputType)
	local pg = player:WaitForChild("PlayerGui")
	local bb = Instance.new("BillboardGui")
	bb.Name = "Prompt"
	bb.AlwaysOnTop = true
	bb.Size = UDim2.fromOffset(250, 58)
	bb.StudsOffsetWorldSpace = Vector3.new(0, 1.2, 0)
	bb.LightInfluence = 0
	bb.Active = true
	bb.Adornee = prompt.Parent :: any
	bb.ResetOnSpawn = false

	local panel = Glass.Panel(bb, { size = UDim2.fromScale(1, 1), radius = 29, alpha = 0.28 })
	panel.AutomaticSize = Enum.AutomaticSize.None
	local sc = Instance.new("UIScale")
	sc.Scale = 0.6
	sc.Parent = panel
	Glass.tween(sc, 0.35, { Scale = 1 }, Enum.EasingStyle.Back)

	local key = Instance.new("Frame")
	key.BackgroundColor3 = Color3.new(1, 1, 1)
	key.BackgroundTransparency = 0.08
	key.Size = UDim2.fromOffset(40, 40)
	key.Position = UDim2.fromOffset(9, 9)
	key.ZIndex = 3
	Glass.corner(key, 20)
	key.Parent = panel
	local kt = Glass.Label(key, keyText(prompt, inputType), 16, Enum.FontWeight.Bold, Color3.fromRGB(20, 18, 26))
	kt.Size = UDim2.fromScale(1, 1)
	kt.TextXAlignment = Enum.TextXAlignment.Center
	kt.TextScaled = #kt.Text > 3
	kt.ZIndex = 4

	-- hold progress ring (as a growing fill behind the key)
	local fill = Instance.new("Frame")
	fill.AnchorPoint = Vector2.new(0.5, 0.5)
	fill.Position = UDim2.fromScale(0.5, 0.5)
	fill.Size = UDim2.fromOffset(40, 40)
	fill.BackgroundColor3 = Config.UI.Accent
	fill.BackgroundTransparency = 1
	fill.ZIndex = 2
	Glass.corner(fill, 24)
	fill.Parent = key

	local action = Glass.Label(panel, prompt.ActionText, 17, Enum.FontWeight.SemiBold, Config.UI.Ink)
	action.Position = UDim2.fromOffset(60, 8)
	action.Size = UDim2.new(1, -70, 0, 22)
	action.TextTruncate = Enum.TextTruncate.AtEnd
	local object = Glass.Label(panel, prompt.ObjectText, 13, Enum.FontWeight.Medium, Config.UI.SubInk)
	object.Position = UDim2.fromOffset(60, 30)
	object.Size = UDim2.new(1, -70, 0, 18)
	object.TextTruncate = Enum.TextTruncate.AtEnd

	-- keep text in sync (prompts change ActionText, e.g. Drag → Drop)
	local conns = {}
	table.insert(conns, prompt:GetPropertyChangedSignal("ActionText"):Connect(function()
		action.Text = prompt.ActionText
	end))
	table.insert(conns, prompt:GetPropertyChangedSignal("KeyboardKeyCode"):Connect(function()
		kt.Text = keyText(prompt, inputType)
	end))
	table.insert(conns, prompt.PromptButtonHoldBegan:Connect(function()
		if prompt.HoldDuration > 0 then
			fill.BackgroundTransparency = 0.2
			fill.Size = UDim2.fromOffset(40, 40)
			Glass.tween(fill, prompt.HoldDuration, { Size = UDim2.fromOffset(56, 56) }, Enum.EasingStyle.Linear)
		end
	end))
	table.insert(conns, prompt.PromptButtonHoldEnded:Connect(function()
		Glass.tween(fill, 0.15, { BackgroundTransparency = 1, Size = UDim2.fromOffset(40, 40) })
	end))
	table.insert(conns, prompt.Triggered:Connect(function()
		sc.Scale = 0.9
		Glass.tween(sc, 0.25, { Scale = 1 }, Enum.EasingStyle.Back)
	end))

	-- touch / click support
	local button = Instance.new("TextButton")
	button.BackgroundTransparency = 1
	button.Text = ""
	button.Size = UDim2.fromScale(1, 1)
	button.ZIndex = 10
	button.Parent = panel
	table.insert(conns, button.InputBegan:Connect(function(input)
		if input.UserInputType == Enum.UserInputType.Touch or input.UserInputType == Enum.UserInputType.MouseButton1 then
			prompt:InputHoldBegin()
		end
	end))
	table.insert(conns, button.InputEnded:Connect(function(input)
		if input.UserInputType == Enum.UserInputType.Touch or input.UserInputType == Enum.UserInputType.MouseButton1 then
			prompt:InputHoldEnd()
		end
	end))

	bb.Parent = pg
	shown[prompt] = bb
	bb.Destroying:Connect(function()
		for _, c in conns do
			c:Disconnect()
		end
	end)
end

function Prompts.Init()
	ProximityPromptService.PromptShown:Connect(function(prompt, inputType)
		if prompt.Style ~= Enum.ProximityPromptStyle.Custom then
			return
		end
		if shown[prompt] then
			shown[prompt]:Destroy()
		end
		build(prompt, inputType)
	end)
	ProximityPromptService.PromptHidden:Connect(function(prompt)
		local bb = shown[prompt]
		if bb then
			shown[prompt] = nil
			bb:Destroy()
		end
	end)
end

return Prompts
