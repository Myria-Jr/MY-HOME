-- KUROBARA · Camera modes: "Third" (follow cam) and "First" (ego / first person)
-- In first person you still see Nyx's body, arms and weapon when you look down;
-- only her head, hair and face are hidden so they don't block the view.

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")

local Glass = require(script.Parent.Glass)

local CameraMode = {}
CameraMode.Mode = "Third"
CameraMode.Changed = Instance.new("BindableEvent")

local player = Players.LocalPlayer
local Mood
local suspended = true
local crosshair: Frame
local hint: TextLabel

local FIRST = { offset = Vector3.new(0, 0.15, -0.8), fov = 80 }
local THIRD = { offset = Vector3.new(0, 0.5, 0), fov = 70, minZoom = 4, maxZoom = 28 }

local function isHeadPart(p: BasePart): boolean
	return p.Name == "Head" or p:GetAttribute("Bone") == "Head"
end

local function apply()
	local cam = workspace.CurrentCamera
	if suspended then
		player.CameraMode = Enum.CameraMode.Classic
		player.CameraMinZoomDistance = THIRD.minZoom
		crosshair.Visible = false
		Mood.SetBase(Vector3.zero, THIRD.fov)
		return
	end
	if CameraMode.Mode == "First" then
		player.CameraMinZoomDistance = 0.5
		player.CameraMode = Enum.CameraMode.LockFirstPerson
		Mood.SetBase(FIRST.offset, FIRST.fov)
		cam.FieldOfView = FIRST.fov
		crosshair.Visible = true
	else
		player.CameraMode = Enum.CameraMode.Classic
		player.CameraMaxZoomDistance = THIRD.maxZoom
		-- push the camera back out of her head, then allow zooming in again
		player.CameraMinZoomDistance = 11
		task.delay(0.15, function()
			if CameraMode.Mode == "Third" then
				player.CameraMinZoomDistance = THIRD.minZoom
			end
		end)
		Mood.SetBase(THIRD.offset, THIRD.fov)
		cam.FieldOfView = THIRD.fov
		crosshair.Visible = false
	end
end

function CameraMode.Set(mode: string)
	if mode ~= "First" and mode ~= "Third" then
		return
	end
	CameraMode.Mode = mode
	apply()
	CameraMode.Changed:Fire(mode)
	if hint then
		hint.Text = mode == "First" and "EGO" or "3RD PERSON"
		hint.TextTransparency = 0
		Glass.tween(hint, 1.4, { TextTransparency = 1 }, Enum.EasingStyle.Quad, Enum.EasingDirection.In)
	end
end

function CameraMode.Toggle()
	CameraMode.Set(CameraMode.Mode == "First" and "Third" or "First")
end

-- menus / cutscenes need a free mouse
function CameraMode.Suspend(on: boolean)
	suspended = on
	apply()
end

function CameraMode.Init(mood)
	Mood = mood
	local gui = Instance.new("ScreenGui")
	gui.Name = "Crosshair"
	gui.IgnoreGuiInset = true
	gui.ResetOnSpawn = false
	gui.DisplayOrder = 4
	gui.Parent = player:WaitForChild("PlayerGui")

	crosshair = Instance.new("Frame")
	crosshair.AnchorPoint = Vector2.new(0.5, 0.5)
	crosshair.Position = UDim2.fromScale(0.5, 0.5)
	crosshair.Size = UDim2.fromOffset(6, 6)
	crosshair.BackgroundColor3 = Color3.new(1, 1, 1)
	crosshair.BackgroundTransparency = 0.25
	crosshair.Visible = false
	Glass.corner(crosshair, 3)
	local ring = Instance.new("UIStroke")
	ring.Color = Color3.new(0, 0, 0)
	ring.Transparency = 0.6
	ring.Thickness = 1
	ring.Parent = crosshair
	crosshair.Parent = gui

	hint = Glass.Label(gui, "", 15, Enum.FontWeight.Bold)
	hint.AnchorPoint = Vector2.new(0.5, 0.5)
	hint.Position = UDim2.fromScale(0.5, 0.58)
	hint.Size = UDim2.fromOffset(200, 22)
	hint.TextXAlignment = Enum.TextXAlignment.Center
	hint.TextTransparency = 1
	hint.TextStrokeTransparency = 0.6

	-- after Roblox's own camera hides the character, show her body again (not the head)
	RunService:BindToRenderStep("KB_FirstPersonBody", Enum.RenderPriority.Camera.Value + 1, function()
		if suspended or CameraMode.Mode ~= "First" then
			return
		end
		local char = player.Character
		if not char then
			return
		end
		for _, p in char:GetDescendants() do
			if p:IsA("BasePart") then
				p.LocalTransparencyModifier = isHeadPart(p) and 1 or 0
			end
		end
	end)

	player.CharacterAdded:Connect(function()
		task.wait(0.2)
		apply()
	end)
end

return CameraMode
