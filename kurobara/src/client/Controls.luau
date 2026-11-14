-- KUROBARA · Input: run, sneak, phone (keyboard + touch buttons)

local ContextActionService = game:GetService("ContextActionService")
local StarterGui = game:GetService("StarterGui")

local Controls = {}
local remote: RemoteEvent
local HUD
local CameraMode
local crouching = false

local function run(_, state: Enum.UserInputState)
	if state == Enum.UserInputState.Begin then
		remote:FireServer("Run", true)
		if crouching then
			crouching = false
		end
	elseif state == Enum.UserInputState.End or state == Enum.UserInputState.Cancel then
		remote:FireServer("Run", false)
	end
	return Enum.ContextActionResult.Pass
end

local function crouch(_, state: Enum.UserInputState)
	if state == Enum.UserInputState.Begin then
		crouching = not crouching
		remote:FireServer("Crouch", crouching)
	end
	return Enum.ContextActionResult.Sink
end

local function phone(_, state: Enum.UserInputState)
	if state == Enum.UserInputState.Begin then
		HUD.TogglePhone()
	end
	return Enum.ContextActionResult.Sink
end

local function camera(_, state: Enum.UserInputState)
	if state == Enum.UserInputState.Begin and CameraMode then
		CameraMode.Toggle()
	end
	return Enum.ContextActionResult.Sink
end

-- keyboard + gamepad only; touch devices get the glass buttons from TouchUI
function Controls.Enable(on: boolean)
	if on then
		ContextActionService:BindAction("KB_Run", run, false, Enum.KeyCode.LeftShift, Enum.KeyCode.ButtonL3)
		ContextActionService:BindAction("KB_Crouch", crouch, false, Enum.KeyCode.C, Enum.KeyCode.ButtonR3)
		ContextActionService:BindAction("KB_Phone", phone, false, Enum.KeyCode.Tab, Enum.KeyCode.ButtonSelect)
		ContextActionService:BindAction("KB_Camera", camera, false, Enum.KeyCode.V, Enum.KeyCode.ButtonY)
	else
		for _, a in { "KB_Run", "KB_Crouch", "KB_Phone", "KB_Camera" } do
			ContextActionService:UnbindAction(a)
		end
	end
end

function Controls.Init(r: RemoteEvent, hud, cameraMode)
	remote = r
	HUD = hud
	CameraMode = cameraMode
	-- no reset button, no player list; keep the hotbar
	task.spawn(function()
		for _ = 1, 20 do
			local ok = pcall(function()
				StarterGui:SetCore("ResetButtonCallback", false)
			end)
			if ok then
				break
			end
			task.wait(0.5)
		end
	end)
	pcall(function()
		StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.PlayerList, false)
		StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.Health, false)
		StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.EmotesMenu, false)
	end)
end

return Controls
