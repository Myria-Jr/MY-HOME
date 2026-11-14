-- KUROBARA · Glass UI kit (frosted panels, rim light, soft sheen)

local TweenService = game:GetService("TweenService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage:WaitForChild("Shared"):WaitForChild("Config"))

local Glass = {}

function Glass.font(weight: Enum.FontWeight?): Font
	return Font.new(Config.UI.Font, weight or Enum.FontWeight.Medium)
end

function Glass.tween(i: Instance, t: number, props, style: Enum.EasingStyle?, dir: Enum.EasingDirection?)
	local tw = TweenService:Create(i, TweenInfo.new(t, style or Enum.EasingStyle.Quint, dir or Enum.EasingDirection.Out), props)
	tw:Play()
	return tw
end

function Glass.corner(parent: Instance, radius: number)
	local c = Instance.new("UICorner")
	c.CornerRadius = UDim.new(0, radius)
	c.Parent = parent
	return c
end

function Glass.pad(parent: Instance, x: number, y: number?)
	local p = Instance.new("UIPadding")
	p.PaddingLeft = UDim.new(0, x)
	p.PaddingRight = UDim.new(0, x)
	p.PaddingTop = UDim.new(0, y or x)
	p.PaddingBottom = UDim.new(0, y or x)
	p.Parent = parent
	return p
end

-- A frosted panel: tinted body, top sheen, bright rim fading toward the bottom-right.
function Glass.Panel(parent: Instance, opts: { [string]: any }?): Frame
	opts = opts or {}
	local f = Instance.new("Frame")
	f.Name = opts.name or "Glass"
	f.BackgroundColor3 = opts.tint or Color3.fromRGB(22, 20, 30)
	f.BackgroundTransparency = opts.alpha or 0.32
	f.BorderSizePixel = 0
	f.Size = opts.size or UDim2.fromOffset(200, 60)
	f.Position = opts.pos or UDim2.new()
	f.AnchorPoint = opts.anchor or Vector2.new()
	f.ClipsDescendants = false
	local radius = opts.radius or 20
	Glass.corner(f, radius)

	local stroke = Instance.new("UIStroke")
	stroke.Color = Color3.new(1, 1, 1)
	stroke.Transparency = 0.55
	stroke.Thickness = 1
	stroke.ApplyStrokeMode = Enum.ApplyStrokeMode.Border
	local sg = Instance.new("UIGradient")
	sg.Rotation = 125
	sg.Transparency = NumberSequence.new({
		NumberSequenceKeypoint.new(0, 0),
		NumberSequenceKeypoint.new(0.45, 0.65),
		NumberSequenceKeypoint.new(1, 0.25),
	})
	sg.Parent = stroke
	stroke.Parent = f

	local sheen = Instance.new("Frame")
	sheen.Name = "Sheen"
	sheen.BackgroundColor3 = Color3.new(1, 1, 1)
	sheen.BackgroundTransparency = 0.88
	sheen.BorderSizePixel = 0
	sheen.Size = UDim2.fromScale(1, 1)
	sheen.ZIndex = 0
	Glass.corner(sheen, radius)
	local g = Instance.new("UIGradient")
	g.Rotation = 90
	g.Transparency = NumberSequence.new({
		NumberSequenceKeypoint.new(0, 0),
		NumberSequenceKeypoint.new(0.5, 0.85),
		NumberSequenceKeypoint.new(1, 1),
	})
	g.Parent = sheen
	sheen.Parent = f

	f.Parent = parent
	return f
end

function Glass.Label(parent: Instance, text: string, size: number, weight: Enum.FontWeight?, color: Color3?): TextLabel
	local t = Instance.new("TextLabel")
	t.BackgroundTransparency = 1
	t.Text = text
	t.TextSize = size
	t.FontFace = Glass.font(weight)
	t.TextColor3 = color or Config.UI.Ink
	t.TextXAlignment = Enum.TextXAlignment.Left
	t.TextYAlignment = Enum.TextYAlignment.Center
	t.Size = UDim2.new(1, 0, 0, size + 4)
	t.ZIndex = 2
	t.Parent = parent
	return t
end

function Glass.Button(parent: Instance, text: string, opts: { [string]: any }?): TextButton
	opts = opts or {}
	local b = Instance.new("TextButton")
	b.AutoButtonColor = false
	b.Text = text
	b.TextSize = opts.textSize or 20
	b.FontFace = Glass.font(Enum.FontWeight.SemiBold)
	b.TextColor3 = opts.textColor or Config.UI.Ink
	b.BackgroundColor3 = opts.tint or Color3.fromRGB(255, 255, 255)
	b.BackgroundTransparency = opts.alpha or 0.86
	b.Size = opts.size or UDim2.fromOffset(220, 52)
	b.Position = opts.pos or UDim2.new()
	b.AnchorPoint = opts.anchor or Vector2.new(0.5, 0.5)
	b.BorderSizePixel = 0
	Glass.corner(b, opts.radius or 26)
	local stroke = Instance.new("UIStroke")
	stroke.Color = Color3.new(1, 1, 1)
	stroke.Transparency = 0.5
	stroke.ApplyStrokeMode = Enum.ApplyStrokeMode.Border
	stroke.Parent = b
	local scale = Instance.new("UIScale")
	scale.Parent = b
	local base = b.BackgroundTransparency
	b.MouseEnter:Connect(function()
		Glass.tween(scale, 0.25, { Scale = 1.04 })
		Glass.tween(b, 0.25, { BackgroundTransparency = math.max(0, base - 0.1) })
	end)
	b.MouseLeave:Connect(function()
		Glass.tween(scale, 0.25, { Scale = 1 })
		Glass.tween(b, 0.25, { BackgroundTransparency = base })
	end)
	b.MouseButton1Down:Connect(function()
		Glass.tween(scale, 0.1, { Scale = 0.97 })
	end)
	b.MouseButton1Up:Connect(function()
		Glass.tween(scale, 0.2, { Scale = 1.04 })
	end)
	b.Parent = parent
	return b
end

function Glass.Bar(parent: Instance, color: Color3, size: UDim2, pos: UDim2?): (Frame, Frame)
	local track = Instance.new("Frame")
	track.BackgroundColor3 = Color3.new(1, 1, 1)
	track.BackgroundTransparency = 0.85
	track.BorderSizePixel = 0
	track.Size = size
	track.Position = pos or UDim2.new()
	track.ZIndex = 2
	Glass.corner(track, 4)
	local fill = Instance.new("Frame")
	fill.BackgroundColor3 = color
	fill.BorderSizePixel = 0
	fill.Size = UDim2.fromScale(0.5, 1)
	fill.ZIndex = 3
	Glass.corner(fill, 4)
	fill.Parent = track
	track.Parent = parent
	return track, fill
end

return Glass
